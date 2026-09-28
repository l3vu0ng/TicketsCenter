package vn.ticketscenter.identity.integration.mail;

import vn.ticketscenter.identity.model.IdentityEnums;

public interface MailGateway {
    void sendOtp(String recipientEmail, String otpCode, IdentityEnums.OtpPurpose purpose);
}
