package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.service.AccountService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthRateLimiter {

    public static final int MAX_LOGIN_ATTEMPTS_PER_ACCOUNT = 5;
    public static final int MAX_LOGIN_ATTEMPTS_PER_IP = 20;
    public static final int MAX_REGISTER_ATTEMPTS_PER_IP = 10;
    public static final int MAX_REGISTER_ATTEMPTS_PER_ACCOUNT = 5;
    public static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<String, List<Instant>> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, List<Instant>> registrationAttempts = new ConcurrentHashMap<>();

    public AuthRateLimiter() {
        this(Clock.systemUTC());
    }

    public AuthRateLimiter(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public void checkLogin(String rawEmail, String clientIp) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(RATE_LIMIT_WINDOW);

        if (clientIp != null && !clientIp.isBlank()) {
            List<Instant> ipHistory = loginFailures.getOrDefault("ip:" + clientIp.trim(), List.of());
            long ipCount = ipHistory.stream().filter(t -> t.isAfter(windowStart)).count();
            if (ipCount >= MAX_LOGIN_ATTEMPTS_PER_IP) {
                throw new IllegalStateException("IP login rate limit exceeded. Please wait before trying again.");
            }
        }

        if (rawEmail != null && !rawEmail.isBlank()) {
            String key = "account:" + normalizeEmailSafe(rawEmail);
            List<Instant> accountHistory = loginFailures.getOrDefault(key, List.of());
            long accountCount = accountHistory.stream().filter(t -> t.isAfter(windowStart)).count();
            if (accountCount >= MAX_LOGIN_ATTEMPTS_PER_ACCOUNT) {
                throw new IllegalStateException("Account login rate limit exceeded. Please wait before trying again.");
            }
        }
    }

    public void recordLoginFailure(String rawEmail, String clientIp) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(RATE_LIMIT_WINDOW);

        if (rawEmail != null && !rawEmail.isBlank()) {
            String key = "account:" + normalizeEmailSafe(rawEmail);
            loginFailures.compute(key, (k, list) -> appendAndPrune(list, now, windowStart));
        }

        if (clientIp != null && !clientIp.isBlank()) {
            String key = "ip:" + clientIp.trim();
            loginFailures.compute(key, (k, list) -> appendAndPrune(list, now, windowStart));
        }
    }

    public void recordLoginSuccess(String rawEmail, String clientIp) {
        if (rawEmail != null && !rawEmail.isBlank()) {
            loginFailures.remove("account:" + normalizeEmailSafe(rawEmail));
        }
    }

    public void checkRegister(String rawEmail, String clientIp) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(RATE_LIMIT_WINDOW);

        if (clientIp != null && !clientIp.isBlank()) {
            List<Instant> ipHistory = registrationAttempts.getOrDefault("ip:" + clientIp.trim(), List.of());
            long ipCount = ipHistory.stream().filter(t -> t.isAfter(windowStart)).count();
            if (ipCount >= MAX_REGISTER_ATTEMPTS_PER_IP) {
                throw new IllegalStateException("IP registration rate limit exceeded. Please wait before trying again.");
            }
        }

        if (rawEmail != null && !rawEmail.isBlank()) {
            String key = "account:" + normalizeEmailSafe(rawEmail);
            List<Instant> accountHistory = registrationAttempts.getOrDefault(key, List.of());
            long accountCount = accountHistory.stream().filter(t -> t.isAfter(windowStart)).count();
            if (accountCount >= MAX_REGISTER_ATTEMPTS_PER_ACCOUNT) {
                throw new IllegalStateException("Account registration rate limit exceeded. Please wait before trying again.");
            }
        }
    }

    public void recordRegister(String rawEmail, String clientIp) {
        Instant now = clock.instant();
        Instant windowStart = now.minus(RATE_LIMIT_WINDOW);

        if (rawEmail != null && !rawEmail.isBlank()) {
            String key = "account:" + normalizeEmailSafe(rawEmail);
            registrationAttempts.compute(key, (k, list) -> appendAndPrune(list, now, windowStart));
        }

        if (clientIp != null && !clientIp.isBlank()) {
            String key = "ip:" + clientIp.trim();
            registrationAttempts.compute(key, (k, list) -> appendAndPrune(list, now, windowStart));
        }
    }

    public void clear() {
        loginFailures.clear();
        registrationAttempts.clear();
    }

    private static List<Instant> appendAndPrune(List<Instant> existing, Instant now, Instant windowStart) {
        List<Instant> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
        updated.removeIf(t -> !t.isAfter(windowStart));
        updated.add(now);
        return updated;
    }

    private static String normalizeEmailSafe(String rawEmail) {
        try {
            return AccountService.normalizeEmail(rawEmail);
        } catch (IllegalArgumentException e) {
            return rawEmail.trim().toLowerCase(Locale.ROOT);
        }
    }
}
