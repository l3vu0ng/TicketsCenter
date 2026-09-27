package vn.ticketscenter.identity.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.UUID;

public final class SessionService {

    public static final String USER_ID = "ticketscenter.userId";
    public static final String AUTH_VERSION = "ticketscenter.authVersion";

    public void login(HttpServletRequest request, UUID userId, int authVersion) {
        HttpSession existing = request.getSession(false);
        if (existing != null) existing.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(USER_ID, userId);
        session.setAttribute(AUTH_VERSION, authVersion);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
