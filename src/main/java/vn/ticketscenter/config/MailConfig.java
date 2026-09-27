package vn.ticketscenter.config;

/**
 * Cấu hình dịch vụ gửi email (SMTP).
 */
public final class MailConfig {

    private MailConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static String getHost() {
        String host = AppConfig.get("mail.smtp.host");
        return (host != null && !host.isBlank()) ? host : AppConfig.get("MAIL_SMTP_HOST", "smtp.gmail.com");
    }

    public static int getPort() {
        int port = AppConfig.getInt("mail.smtp.port", 0);
        return port > 0 ? port : AppConfig.getInt("MAIL_SMTP_PORT", 587);
    }

    public static String getUser() {
        String user = AppConfig.get("mail.smtp.user");
        String val = (user != null && !user.isBlank()) ? user : AppConfig.get("MAIL_SMTP_USER", "");
        return val.trim();
    }

    public static String getPassword() {
        String pass = AppConfig.get("mail.smtp.password");
        String val = (pass != null && !pass.isBlank()) ? pass : AppConfig.get("MAIL_SMTP_PASSWORD", "");
        return val.replaceAll("\\s+", "");
    }
}
