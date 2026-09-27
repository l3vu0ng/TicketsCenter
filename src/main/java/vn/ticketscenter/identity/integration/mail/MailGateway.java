package vn.ticketscenter.identity.integration.mail;

import vn.ticketscenter.identity.model.OtpPurpose;

public interface MailGateway {
    void sendOtp(String recipientEmail, String otpCode, OtpPurpose purpose);
}
