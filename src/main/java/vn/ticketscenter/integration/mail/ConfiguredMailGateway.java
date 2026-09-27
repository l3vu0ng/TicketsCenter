package vn.ticketscenter.integration.mail;

import vn.ticketscenter.config.MailConfig;
import vn.ticketscenter.model.identity.OtpPurpose;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfiguredMailGateway implements MailGateway {

    private static final Logger LOGGER = Logger.getLogger(ConfiguredMailGateway.class.getName());
    private static final ExecutorService ASYNC_MAIL_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final int TIMEOUT_MILLIS = 5000;

    private final List<DispatchedMessage> dispatched = new CopyOnWriteArrayList<>();

    public record DispatchedMessage(String recipientEmail, String otpCode, OtpPurpose purpose, Instant dispatchedAt) {
    }

    @Override
    public void sendOtp(String recipientEmail, String otpCode, OtpPurpose purpose) {
        Objects.requireNonNull(recipientEmail, "recipientEmail is required");
        Objects.requireNonNull(otpCode, "otpCode is required");
        Objects.requireNonNull(purpose, "purpose is required");

        dispatched.add(new DispatchedMessage(recipientEmail, otpCode, purpose, Instant.now()));

        String host = MailConfig.getHost();
        String user = MailConfig.getUser();
        String password = MailConfig.getPassword();
        int port = MailConfig.getPort();

        if (user.isBlank() || password.isBlank()) {
            LOGGER.log(Level.INFO, "Mail credentials not configured. Recorded OTP dispatch for {0} (purpose: {1})",
                    new Object[]{recipientEmail, purpose});
            return;
        }

        LOGGER.log(Level.INFO, "Dispatching OTP email to {0} via {1}:{2} (purpose: {3})",
                new Object[]{recipientEmail, host, port, purpose});

        if (isReservedTestDomain(recipientEmail)) {
            LOGGER.log(Level.INFO, "Simulated delivery for reserved test domain {0}", recipientEmail);
            return;
        }

        ASYNC_MAIL_EXECUTOR.submit(() -> {
            try {
                deliverSmtp(host, port, user, password, recipientEmail, otpCode, purpose);
                LOGGER.log(Level.INFO, "Successfully delivered OTP email to {0}", recipientEmail);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to deliver OTP email to {0}: {1}",
                        new Object[]{recipientEmail, e.getMessage()});
            }
        });
    }

    void deliverSmtp(String host, int port, String user, String password,
                     String recipientEmail, String otpCode, OtpPurpose purpose) throws IOException {
        Socket plainSocket = new Socket();
        plainSocket.connect(new InetSocketAddress(host, port), TIMEOUT_MILLIS);
        plainSocket.setSoTimeout(TIMEOUT_MILLIS);

        BufferedReader reader = new BufferedReader(new InputStreamReader(plainSocket.getInputStream(), StandardCharsets.UTF_8));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(plainSocket.getOutputStream(), StandardCharsets.UTF_8));

        expect(reader.readLine(), "220");
        send(writer, "EHLO localhost");
        readMulti(reader);

        SSLSocket sslSocket;
        if (port == 465) {
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            sslSocket = (SSLSocket) sf.createSocket(plainSocket, host, port, true);
        } else {
            send(writer, "STARTTLS");
            expect(reader.readLine(), "220");
            SSLSocketFactory sf = (SSLSocketFactory) SSLSocketFactory.getDefault();
            sslSocket = (SSLSocket) sf.createSocket(plainSocket, host, port, true);
        }

        sslSocket.startHandshake();
        reader = new BufferedReader(new InputStreamReader(sslSocket.getInputStream(), StandardCharsets.UTF_8));
        writer = new BufferedWriter(new OutputStreamWriter(sslSocket.getOutputStream(), StandardCharsets.UTF_8));

        send(writer, "EHLO localhost");
        readMulti(reader);

        send(writer, "AUTH LOGIN");
        expect(reader.readLine(), "334");
        send(writer, Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
        expect(reader.readLine(), "334");
        send(writer, Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)));
        expect(reader.readLine(), "235");

        send(writer, "MAIL FROM:<" + user + ">");
        expect(reader.readLine(), "250");
        send(writer, "RCPT TO:<" + recipientEmail + ">");
        expect(reader.readLine(), "250");
        send(writer, "DATA");
        expect(reader.readLine(), "354");

        String purposeDesc = purpose == OtpPurpose.VERIFY_EMAIL ? "Xác minh tài khoản" : "Khôi phục mật khẩu";
        String dateHeader = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.now().atOffset(ZoneOffset.UTC));

        writer.write("From: TicketsCenter <" + user + ">\r\n");
        writer.write("To: <" + recipientEmail + ">\r\n");
        writer.write("Date: " + dateHeader + "\r\n");
        writer.write("Subject: =?UTF-8?B?" + Base64.getEncoder().encodeToString(("[TicketsCenter] Mã xác thực OTP: " + otpCode).getBytes(StandardCharsets.UTF_8)) + "?=\r\n");
        writer.write("MIME-Version: 1.0\r\n");
        writer.write("Content-Type: text/html; charset=UTF-8\r\n");
        writer.write("\r\n");
        writer.write(buildHtmlBody(purposeDesc, otpCode));
        writer.write("\r\n.\r\n");
        writer.flush();
        expect(reader.readLine(), "250");

        send(writer, "QUIT");
        reader.readLine();
        sslSocket.close();
    }

    private static void send(BufferedWriter w, String s) throws IOException {
        w.write(s + "\r\n");
        w.flush();
    }

    private static void readMulti(BufferedReader r) throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (line.length() >= 4 && (line.charAt(3) == ' ' || line.charAt(3) != '-')) break;
            if (line.length() < 4) break;
        }
    }

    private static void expect(String line, String prefix) throws IOException {
        if (line == null || !line.startsWith(prefix)) {
            throw new IOException("SMTP protocol error, expected prefix [" + prefix + "] but got: [" + line + "]");
        }
    }

    private static String buildHtmlBody(String purposeDesc, String otpCode) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; padding: 32px 0; margin: 0;\">"
                + "  <div style=\"max-width: 520px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; padding: 32px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);\">"
                + "    <div style=\"margin-bottom: 24px; text-align: center;\">"
                + "      <h2 style=\"color: #0f172a; margin: 0; font-size: 24px; font-weight: 700;\">TicketsCenter</h2>"
                + "      <p style=\"color: #64748b; font-size: 14px; margin-top: 4px;\">Hệ thống đặt vé sự kiện trực tuyến</p>"
                + "    </div>"
                + "    <div style=\"border-top: 1px solid #f1f5f9; padding-top: 20px;\">"
                + "      <p style=\"color: #334155; font-size: 16px; line-height: 24px; margin-bottom: 16px;\">Xin chào,</p>"
                + "      <p style=\"color: #334155; font-size: 15px; line-height: 22px;\">"
                + "        Bạn vừa yêu cầu mã xác thực OTP cho mục đích: <strong>" + purposeDesc + "</strong>."
                + "      </p>"
                + "      <div style=\"text-align: center; margin: 28px 0;\">"
                + "        <span style=\"display: inline-block; background: #f0fdf4; border: 2px dashed #22c55e; border-radius: 8px; padding: 14px 28px; font-size: 32px; font-weight: 800; letter-spacing: 8px; color: #15803d;\">"
                + otpCode
                + "        </span>"
                + "      </div>"
                + "      <p style=\"color: #64748b; font-size: 13px; line-height: 20px;\">"
                + "        Mã xác thực có hiệu lực trong vòng <strong>5 phút</strong>. Vui lòng không chia sẻ mã này cho bất kỳ ai để bảo vệ an toàn tài khoản của bạn."
                + "      </p>"
                + "    </div>"
                + "    <div style=\"border-top: 1px solid #f1f5f9; margin-top: 28px; padding-top: 16px; text-align: center; color: #94a3b8; font-size: 12px;\">"
                + "      Đây là email tự động từ hệ thống TicketsCenter. Vui lòng không trả lời thư này."
                + "    </div>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }

    public List<DispatchedMessage> getDispatchedMessages() {
        return List.copyOf(dispatched);
    }

    public void clearDispatched() {
        dispatched.clear();
    }

    private static boolean isReservedTestDomain(String email) {
        String lower = email.toLowerCase(Locale.ROOT);
        return lower.endsWith("@example.com")
                || lower.endsWith("@example.org")
                || lower.endsWith("@example.net")
                || lower.endsWith(".test")
                || lower.endsWith(".local")
                || lower.endsWith(".invalid");
    }
}
