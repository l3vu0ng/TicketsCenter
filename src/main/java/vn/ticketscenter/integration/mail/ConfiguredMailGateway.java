package vn.ticketscenter.integration.mail;

import vn.ticketscenter.config.MailConfig;
import vn.ticketscenter.model.identity.OtpPurpose;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ConfiguredMailGateway implements MailGateway {

    private static final Logger LOGGER = Logger.getLogger(ConfiguredMailGateway.class.getName());
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
        if (user.isBlank()) {
            LOGGER.log(Level.INFO, "Mail credentials not configured. Recorded OTP dispatch for {0} (purpose: {1})",
                    new Object[]{recipientEmail, purpose});
            return;
        }

        LOGGER.log(Level.INFO, "Dispatching OTP email to {0} via {1}:{2} (purpose: {3})",
                new Object[]{recipientEmail, host, MailConfig.getPort(), purpose});
    }

    public List<DispatchedMessage> getDispatchedMessages() {
        return List.copyOf(dispatched);
    }

    public void clearDispatched() {
        dispatched.clear();
    }
}
