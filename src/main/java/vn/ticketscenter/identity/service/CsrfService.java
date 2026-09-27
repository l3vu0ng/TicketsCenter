package vn.ticketscenter.identity.service;

import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class CsrfService {

    public static final String SESSION_ATTRIBUTE = "ticketscenter.csrf";
    private final SecureRandom random = new SecureRandom();

    public String token(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ATTRIBUTE);
        if (existing instanceof String token) return token;
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(SESSION_ATTRIBUTE, token);
        return token;
    }

    public boolean matches(HttpSession session, String supplied) {
        Object expected = session.getAttribute(SESSION_ATTRIBUTE);
        return expected instanceof String token && supplied != null
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII));
    }
}
