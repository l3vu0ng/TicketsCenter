package vn.ticketscenter.service.identity;

import jakarta.persistence.PersistenceException;
import vn.ticketscenter.model.identity.User;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.repository.identity.UserRepository;
import vn.ticketscenter.transaction.DatabasePrincipal;
import vn.ticketscenter.transaction.TransactionManager;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class AccountService {

    private final TransactionManager transactions;
    private final PasswordHasher passwords;
    private final Clock clock;

    public AccountService(TransactionManager transactions, PasswordHasher passwords, Clock clock) {
        this.transactions = transactions;
        this.passwords = passwords;
        this.clock = clock;
    }

    public void register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        try {
            transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
                UserRepository users = new UserRepository(entityManager);
                if (users.findByNormalizedEmail(normalizedEmail).isPresent()) return null;
                String displayEmail = email.trim();
                String defaultName = displayEmail.substring(0, displayEmail.indexOf('@'));
                users.save(new User(displayEmail, normalizedEmail, passwords.hash(password), defaultName, clock.instant()));
                return null;
            });
        } catch (PersistenceException exception) {
            if (!isDuplicate(exception)) throw exception;
        }
    }

    public Optional<AuthenticatedAccount> authenticate(String identifier, String password) {
        String normalizedEmail;
        if ("admin".equalsIgnoreCase(identifier == null ? "" : identifier.trim())) {
            normalizedEmail = normalizeEmail(AppConfig.get("admin.email", "admin@ticketscenter.local"));
        } else {
            normalizedEmail = normalizeEmail(identifier);
        }
        return transactions.execute(DatabasePrincipal.AUTH, entityManager -> {
            Optional<User> found = new UserRepository(entityManager).findByNormalizedEmail(normalizedEmail);
            User user = found.orElse(null);
            boolean passwordMatches = passwords.matches(password, user == null ? null : user.getPasswordHash());
            if (!passwordMatches || user == null || !user.isActive()) return Optional.empty();
            return Optional.of(new AuthenticatedAccount(
                    user.getId(), user.getAuthVersion(), user.getEmail(), user.hasPlatformRole("ADMIN")));
        });
    }

    public Optional<AuthenticatedAccount> current(UUID userId) {
        return transactions.execute(DatabasePrincipal.AUTH, entityManager ->
                new UserRepository(entityManager).findById(userId)
                        .filter(User::isActive)
                        .map(user -> new AuthenticatedAccount(
                                user.getId(), user.getAuthVersion(), user.getEmail(), user.hasPlatformRole("ADMIN"))));
    }

    public record AuthenticatedAccount(UUID id, int authVersion, String email, boolean admin) {
    }

    public static String normalizeEmail(String email) {
        if (email == null) throw new IllegalArgumentException("email is required");
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        int separator = normalized.indexOf('@');
        if (normalized.length() > 320 || separator <= 0 || separator != normalized.lastIndexOf('@')
                || separator == normalized.length() - 1 || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("email is invalid");
        }
        return normalized;
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 256) {
            throw new IllegalArgumentException("password length must be between 12 and 256 characters");
        }
    }

    private static boolean isDuplicate(Throwable throwable) {
        return Arrays.stream(new Throwable[]{throwable, throwable.getCause(), throwable.getCause() == null ? null : throwable.getCause().getCause()})
                .filter(java.util.Objects::nonNull)
                .filter(SQLException.class::isInstance)
                .map(SQLException.class::cast)
                .anyMatch(error -> error.getErrorCode() == 2601 || error.getErrorCode() == 2627);
    }
}
