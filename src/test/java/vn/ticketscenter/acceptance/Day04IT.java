package vn.ticketscenter.acceptance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.*;
import vn.ticketscenter.config.AdminSeeder;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.service.identity.AccountService;
import vn.ticketscenter.service.identity.PasswordHasher;
import vn.ticketscenter.transaction.DatabasePrincipal;
import vn.ticketscenter.transaction.TransactionManager;

import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class Day04IT {

    private HikariDataSource dataSource;
    private EntityManagerFactory factory;
    private TransactionManager transactions;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
        config.setUsername(DatabaseConfig.getUser());
        config.setPassword(DatabaseConfig.getPassword());
        config.setMaximumPoolSize(2);
        dataSource = new HikariDataSource(config);
        factory = Persistence.createEntityManagerFactory("ticketscenter", Map.of(
                "jakarta.persistence.nonJtaDataSource", dataSource));
        transactions = new TransactionManager(Map.of(DatabasePrincipal.AUTH, factory));
    }

    @AfterEach
    void tearDown() {
        factory.close();
        dataSource.close();
        System.clearProperty("admin.seed.enabled");
        System.clearProperty("admin.email");
        System.clearProperty("admin.password");
        System.clearProperty("app.env");
    }

    @Test
    void registrationIsNormalizedHashedAndDisabledLoginIsRejected() throws Exception {
        String marker = UUID.randomUUID().toString();
        String normalized = "day04-" + marker + "@example.com";
        String submitted = " DAY04-" + marker + "@Example.COM ";
        String password = "Correct Horse Battery Staple";
        AccountService accounts = accounts();
        try {
            accounts.register(submitted, password);
            accounts.register(normalized, password);

            try (Connection connection = dataSource.getConnection();
                 var statement = connection.prepareStatement(
                         "SELECT COUNT(*), MIN(password_hash), MIN(email_verified_at) FROM dbo.tc_users WHERE normalized_email = ?")) {
                statement.setString(1, normalized);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1));
                    assertNotEquals(password, result.getString(2));
                    assertNull(result.getTimestamp(3));
                }
            }
            assertTrue(accounts.authenticate(normalized, password).isPresent());
            assertTrue(accounts.authenticate(normalized, "Wrong Password 123").isEmpty());
            updateStatus(normalized, "DISABLED");
            assertTrue(accounts.authenticate(normalized, password).isEmpty());
            assertTrue(accounts.current(userId(normalized)).isEmpty());
        } finally {
            deleteUser(normalized);
        }
    }

    @Test
    void authDatabasePermissionsStayNarrow() throws Exception {
        String assertions = Files.readString(Path.of("database/tests/day-04.sql"));
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.execute(assertions);
        }
    }

    @Test
    void adminSeedIsIdempotentAndDoesNotOverwriteHash() throws Exception {
        String normalized = "admin-day04-" + UUID.randomUUID() + "@example.com";
        System.setProperty("app.env", "development");
        System.setProperty("admin.seed.enabled", "true");
        System.setProperty("admin.email", normalized);
        System.setProperty("admin.password", "Local Admin Password 123");
        AdminSeeder seeder = new AdminSeeder(new PasswordHasher(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        try {
            seeder.seed(transactions);
            String firstHash = passwordHash(normalized);
            seeder.seed(transactions);
            assertEquals(firstHash, passwordHash(normalized));
            assertTrue(accounts().authenticate("admin", "Local Admin Password 123").orElseThrow().admin());
        } finally {
            deleteUser(normalized);
        }
    }

    private AccountService accounts() {
        return new AccountService(transactions, new PasswordHasher(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    private String passwordHash(String email) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT password_hash FROM dbo.tc_users WHERE normalized_email = ?")) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getString(1);
            }
        }
    }

    private UUID userId(String email) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT id FROM dbo.tc_users WHERE normalized_email = ?")) {
            statement.setString(1, email);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getObject(1, UUID.class);
            }
        }
    }

    private void updateStatus(String email, String status) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("UPDATE dbo.tc_users SET status = ? WHERE normalized_email = ?")) {
            statement.setString(1, status);
            statement.setString(2, email);
            statement.executeUpdate();
        }
    }

    private void deleteUser(String email) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            try (var roles = connection.prepareStatement(
                    "DELETE role FROM dbo.tc_user_platform_roles role JOIN dbo.tc_users user_account ON user_account.id = role.user_id WHERE user_account.normalized_email = ?")) {
                roles.setString(1, email);
                roles.executeUpdate();
            }
            try (var user = connection.prepareStatement("DELETE FROM dbo.tc_users WHERE normalized_email = ?")) {
                user.setString(1, email);
                user.executeUpdate();
            }
        }
    }
}
