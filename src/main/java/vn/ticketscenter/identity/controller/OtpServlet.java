package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.identity.integration.mail.MailGateway;
import vn.ticketscenter.identity.model.IdentityEnums;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;
import vn.ticketscenter.config.web.JsonObjectParser;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@WebServlet(name = "OtpServlet", urlPatterns = {
        "/api/auth/otp/send", "/api/auth/otp/verify",
        "/api/auth/password/forgot", "/api/auth/password/reset",
        "/auth/otp/send", "/auth/otp/verify",
        "/auth/password/forgot", "/auth/password/reset"
})
public final class OtpServlet extends HttpServlet {

    public static final String MAIL_GATEWAY_ATTRIBUTE = "ticketscenter.mailGateway";
    public static final String CLOCK_ATTRIBUTE = "ticketscenter.clock";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (!(configured instanceof PersistenceRegistry registry)) {
            HttpResponses.error(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE", "Authentication is temporarily unavailable");
            return;
        }

        Clock clock = resolveClock(request);
        MailGateway mailGateway = resolveMailGateway(request);
        OtpService otpService = new OtpService(registry.transactionManager(), mailGateway, clock);
        PasswordResetService resetService = new PasswordResetService(
                registry.transactionManager(), otpService, new PasswordHasher(), clock);

        String path = request.getRequestURI();
        try {
            Map<String, String> body = parseBody(request);

            if (path.endsWith("/auth/otp/send")) {
                handleSendOtp(request, response, body, otpService);
                return;
            }

            if (path.endsWith("/auth/otp/verify")) {
                handleVerifyOtp(response, body, otpService, resetService);
                return;
            }

            if (path.endsWith("/auth/password/forgot")) {
                handleForgotPassword(request, response, body, resetService);
                return;
            }

            if (path.endsWith("/auth/password/reset")) {
                handlePasswordReset(response, body, resetService);
                return;
            }

            HttpResponses.error(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "Resource not found");
        } catch (IllegalStateException exception) {
            HttpResponses.error(response, 429, "RATE_LIMITED", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
        }
    }

    private void handleSendOtp(HttpServletRequest request, HttpServletResponse response,
                               Map<String, String> body, OtpService otpService) throws IOException {
        String purposeStr = body.get("purpose");
        if (purposeStr == null || purposeStr.isBlank()) {
            throw new IllegalArgumentException("purpose is required");
        }
        IdentityEnums.OtpPurpose purpose = IdentityEnums.OtpPurpose.valueOf(purposeStr.trim().toUpperCase());

        String email = body.get("email");
        UUID userId = null;
        Object accountObj = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (accountObj instanceof AccountService.AuthenticatedAccount account) {
            userId = account.id();
            if (email == null || email.isBlank()) {
                email = account.email();
            }
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }

        String clientIp = resolveClientIp(request);
        otpService.sendOtp(userId, email, purpose, clientIp);
        HttpResponses.data(response, "{\"sent\":true}");
    }

    private void handleVerifyOtp(HttpServletResponse response, Map<String, String> body,
                                 OtpService otpService, PasswordResetService resetService) throws IOException {
        String email = value(body.get("email"), "email is required");
        String code = value(body.get("code"), "code is required");
        String purposeStr = value(body.get("purpose"), "purpose is required");
        IdentityEnums.OtpPurpose purpose = IdentityEnums.OtpPurpose.valueOf(purposeStr.trim().toUpperCase());

        if (purpose == IdentityEnums.OtpPurpose.RESET_PASSWORD) {
            var resetResult = resetService.verifyResetOtp(email, code);
            if (!resetResult.successful()) {
                HttpResponses.error(response, HttpServletResponse.SC_BAD_REQUEST,
                        resetResult.error(), "Reset OTP verification failed");
                return;
            }
            HttpResponses.data(response, "{\"verified\":true,\"resetToken\":"
                    + HttpResponses.jsonString(resetResult.token()) + "}");
            return;
        }

        var verifyResult = otpService.verifyOtp(email, code, purpose);
        if (!verifyResult.successful()) {
            HttpResponses.error(response, HttpServletResponse.SC_BAD_REQUEST,
                    verifyResult.error(), "OTP verification failed");
            return;
        }

        HttpResponses.data(response, "{\"verified\":true}");
    }

    private void handleForgotPassword(HttpServletRequest request, HttpServletResponse response,
                                       Map<String, String> body, PasswordResetService resetService) throws IOException {
        String email = value(body.get("email"), "email is required");
        String clientIp = resolveClientIp(request);
        resetService.requestReset(email, clientIp);
        HttpResponses.data(response, "{\"accepted\":true,\"message\":\"If the email exists, a reset code has been sent.\"}");
    }

    public static String resolveClientIp(HttpServletRequest request) {
        boolean trustedProxy = vn.ticketscenter.config.AppConfig.getBoolean("app.proxy.trusted", false);
        if (trustedProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void handlePasswordReset(HttpServletResponse response, Map<String, String> body,
                                     PasswordResetService resetService) throws IOException {
        String resetToken = value(body.get("resetToken"), "resetToken is required");
        String newPassword = value(body.get("newPassword"), "newPassword is required");
        resetService.resetPassword(resetToken, newPassword);
        HttpResponses.data(response, "{\"reset\":true}");
    }

    private MailGateway resolveMailGateway(HttpServletRequest request) {
        Object attr = request.getServletContext().getAttribute(MAIL_GATEWAY_ATTRIBUTE);
        if (attr instanceof MailGateway gateway) {
            return gateway;
        }
        return new ConfiguredMailGateway();
    }

    private Clock resolveClock(HttpServletRequest request) {
        Object attr = request.getServletContext().getAttribute(CLOCK_ATTRIBUTE);
        if (attr instanceof Clock clock) {
            return clock;
        }
        return Clock.systemUTC();
    }

    private Map<String, String> parseBody(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType != null && contentType.startsWith("application/x-www-form-urlencoded")) {
            return Map.of(
                    "email", orEmpty(request.getParameter("email")),
                    "code", orEmpty(request.getParameter("code")),
                    "purpose", orEmpty(request.getParameter("purpose")),
                    "resetToken", orEmpty(request.getParameter("resetToken")),
                    "newPassword", orEmpty(request.getParameter("newPassword"))
            );
        }
        return JsonObjectParser.parse(request.getReader());
    }

    private String value(String str, String message) {
        if (str == null || str.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return str.trim();
    }

    private String orEmpty(String val) {
        return val == null ? "" : val;
    }
}
