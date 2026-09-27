package vn.ticketscenter.identity.dto;

import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(String login, String password) {}

    public record RegisterRequest(String email, String password, String fullName, String phone) {}

    public record OtpVerificationRequest(String email, String code, String purpose) {}

    public record PasswordResetRequest(String email, String otpCode, String newPassword) {}

    public record AuthResponse(UUID id, String email, String fullName, boolean admin) {}
}
