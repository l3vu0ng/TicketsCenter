package vn.ticketscenter.integration.mail;

import vn.ticketscenter.model.identity.OtpPurpose;

public interface MailGateway {
    void sendOtp(String recipientEmail, String otpCode, OtpPurpose purpose);
}
