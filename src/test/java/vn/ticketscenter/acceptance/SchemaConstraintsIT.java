package vn.ticketscenter.acceptance;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SQL Server ticketing schema contract")
public class SchemaConstraintsIT {

    private static final Set<String> REQUIRED_TABLES = Set.of(
            "tc_users", "tc_user_platform_roles", "tc_organizations",
            "tc_organization_memberships", "tc_organization_requests", "tc_event_categories",
            "tc_commission_rules", "tc_events", "tc_zones", "tc_seats",
            "tc_ticket_holds", "tc_ticket_hold_items", "tc_coupons", "tc_orders",
            "tc_order_items", "tc_payments", "tc_coupon_redemptions", "tc_tickets",
            "tc_check_ins", "tc_refund_requests", "tc_refund_request_tickets", "tc_refunds",
            "tc_settlements", "tc_settlement_items", "tc_payouts", "tc_audit_logs",
            "tc_otps", "tc_outbox", "tc_schema_migrations"
    );

    private static final Set<String> REQUIRED_CONSTRAINTS = Set.of(
            "UQ_User_NormalizedEmail", "UQ_Membership_User_Organization", "UQ_Seat_Zone_Row_Number",
            "CK_Event_TimeRange", "CK_Zone_Price_Quota", "CK_Coupon_Discount_Limit",
            "CK_HoldItem_Seat_Quantity", "CK_OrderItem_Seat_Quantity", "CK_Order_Amounts",
            "UQ_Payment_TxnRef", "DF_OrganizationRequest_Status", "CK_OrganizationRequest_Status",
            "CK_TicketHold_TimeRange", "CK_HoldItem_UnitPrice", "CK_OrderItem_UnitPrice",
            "CK_Ticket_PaidAmount", "CK_Payment_PositiveAmount", "CK_Refund_PositiveAmount",
            "CK_CommissionRule_Terms", "CK_SettlementItem_Amounts", "CK_Settlement_Amounts",
            "CK_Payout_PositiveAmount", "CK_Refund_Purpose_Request"
    );

    @Test
    @DisplayName("Database chứa đúng toàn bộ bảng nghiệp vụ và kỹ thuật ticketing")
    void databaseContainsRequiredTicketingTables() throws Exception {
        Set<String> actualTables = new HashSet<>();

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT t.name
                     FROM sys.tables t
                     JOIN sys.schemas s ON s.schema_id = t.schema_id
                     WHERE s.name = 'dbo' AND t.name LIKE 'tc[_]%'
                     """)) {
            while (resultSet.next()) {
                actualTables.add(resultSet.getString(1));
            }
        }

        Set<String> missingTables = new HashSet<>(REQUIRED_TABLES);
        missingTables.removeAll(actualTables);
        assertEquals(Set.of(), missingTables, "Thiếu bảng ticketing");
    }

    @Test
    @DisplayName("Catalog chứa đủ constraint C01-C20 theo tên hợp đồng")
    void databaseContainsRequiredConstraintCatalog() throws Exception {
        Set<String> actualConstraints = new HashSet<>();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     SELECT name FROM sys.objects
                     WHERE schema_id = SCHEMA_ID('dbo')
                       AND type IN ('C', 'D', 'UQ')
                     """)) {
            while (result.next()) {
                if (REQUIRED_CONSTRAINTS.contains(result.getString(1))) {
                    actualConstraints.add(result.getString(1));
                }
            }
        }
        assertEquals(REQUIRED_CONSTRAINTS, actualConstraints);
    }

    @Test
    @DisplayName("C01-C20 nhận dữ liệu hợp lệ và chặn dữ liệu vi phạm")
    void databaseEnforcesTicketingConstraints() throws Exception {
        String sql = Files.readString(Path.of("database/tests/ticketing-schema-constraints.sql"));
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    @DisplayName("Fixture dùng ID cố định, UTF-8 và chạy lặp không nhân dữ liệu")
    void fixtureIsIdempotent() throws Exception {
        String seedSql = Files.readString(Path.of("database/seeds/ticketing-test-fixtures.sql"));

        try (Connection connection = openConnection()) {
            setFixturePasswordHash(connection);
            try (Statement statement = connection.createStatement()) {
                statement.execute(seedSql);
                statement.execute(seedSql);
            }

            assertEquals(4, count(connection,
                    "SELECT COUNT(*) FROM dbo.tc_users WHERE id IN ('10000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000002','10000000-0000-0000-0000-000000000003','10000000-0000-0000-0000-000000000004')"));
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM dbo.tc_users WHERE id = '10000000-0000-0000-0000-000000000003' AND email_verified_at IS NULL AND status = 'ACTIVE'"));
            assertEquals(1, count(connection,
                    "SELECT COUNT(*) FROM dbo.tc_users WHERE id = '10000000-0000-0000-0000-000000000004' AND status = 'DISABLED'"));
            assertEquals(2, count(connection,
                    "SELECT COUNT(*) FROM dbo.tc_organizations WHERE id IN ('10000000-0000-0000-0000-000000000010','10000000-0000-0000-0000-000000000020')"));
            assertEquals(3, count(connection,
                    "SELECT COUNT(*) FROM dbo.tc_organization_memberships WHERE id IN ('10000000-0000-0000-0000-000000000011','10000000-0000-0000-0000-000000000012','10000000-0000-0000-0000-000000000021')"));

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT title FROM dbo.tc_events WHERE id = '10000000-0000-0000-0000-000000000101'")) {
                result.next();
                assertEquals("Đêm nhạc Việt", result.getNString(1));
            }
        }
    }

    private void setFixturePasswordHash(Connection connection) throws Exception {
        String password = System.getenv("TC_TEST_PASSWORD");
        if (password == null || password.isBlank()) {
            password = UUID.randomUUID().toString();
        }
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, 120_000, 256);
        byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(keySpec).getEncoded();
        String encoded = "{PBKDF2-SHA256}120000$" + Base64.getEncoder().encodeToString(salt)
                + "$" + Base64.getEncoder().encodeToString(hash);

        try (PreparedStatement statement = connection.prepareStatement(
                "EXEC sys.sp_set_session_context @key=N'test_password_hash', @value=?")) {
            statement.setString(1, encoded);
            statement.execute();
        }
    }

    private int count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private Connection openConnection() throws Exception {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(DatabaseConfig.getHost());
        dataSource.setPortNumber(DatabaseConfig.getPort());
        dataSource.setDatabaseName(DatabaseConfig.getDatabaseName());
        dataSource.setUser(DatabaseConfig.getUser());
        dataSource.setPassword(DatabaseConfig.getPassword());
        dataSource.setEncrypt(String.valueOf(DatabaseConfig.isEncrypt()));
        dataSource.setTrustServerCertificate(DatabaseConfig.isTrustServerCertificate());
        dataSource.setLoginTimeout(DatabaseConfig.getLoginTimeout());
        return dataSource.getConnection();
    }
}
