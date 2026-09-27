package vn.ticketscenter.acceptance;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("SQL Server entity mapping contract")
public class SchemaMappingIT {

    private static final Set<String> BUSINESS_TABLES = Set.of(
            "tc_users", "tc_organizations", "tc_organization_memberships", "tc_organization_requests",
            "tc_event_categories", "tc_events", "tc_zones", "tc_seats", "tc_ticket_holds",
            "tc_ticket_hold_items", "tc_orders", "tc_order_items", "tc_payments", "tc_coupons",
            "tc_tickets", "tc_check_ins", "tc_refund_requests", "tc_refunds", "tc_commission_rules",
            "tc_settlements", "tc_settlement_items", "tc_payouts", "tc_audit_logs"
    );

    @Test
    @DisplayName("23 entity map tới 23 bảng nghiệp vụ và không FK nào cascade delete")
    void businessMappingAndDeletePolicyMatchContract() throws Exception {
        try (Connection connection = openConnection()) {
            Set<String> actualTables = new HashSet<>();
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT name FROM sys.tables
                         WHERE schema_id = SCHEMA_ID('dbo')
                         """)) {
                while (result.next()) {
                    if (BUSINESS_TABLES.contains(result.getString(1))) {
                        actualTables.add(result.getString(1));
                    }
                }
            }
            assertEquals(BUSINESS_TABLES, actualTables);

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("""
                         SELECT COUNT(*)
                         FROM sys.foreign_keys
                         WHERE delete_referential_action <> 0
                           AND schema_id = SCHEMA_ID('dbo')
                         """)) {
                result.next();
                assertEquals(0, result.getInt(1), "Schema không được cascade-delete lịch sử");
            }
        }
    }

    @Test
    @DisplayName("Nullable mapping giữ đúng các quan hệ optional quan trọng")
    void optionalRelationshipsRemainNullable() throws Exception {
        try (Connection connection = openConnection()) {
            assertEquals(1, nullable(connection, "tc_check_ins", "ticket_id"));
            assertEquals(1, nullable(connection, "tc_order_items", "seat_id"));
            assertEquals(1, nullable(connection, "tc_refunds", "refund_request_id"));
            assertEquals(0, nullable(connection, "tc_payments", "order_id"));
            assertEquals(0, nullable(connection, "tc_tickets", "order_item_id"));
        }
    }

    @Test
    @DisplayName("decimal(19,0) và UTC datetime2(3) round-trip qua JDBC")
    void vndAndUtcValuesRoundTrip() throws Exception {
        BigDecimal expectedAmount = new BigDecimal("9000000000000000000");
        LocalDateTime expectedUtc = LocalDateTime.of(2026, 9, 27, 6, 30, 15, 123_000_000);

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT CAST(? AS decimal(19,0)) AS amount, CAST(? AS datetime2(3)) AS occurred_at")) {
            statement.setBigDecimal(1, expectedAmount);
            statement.setObject(2, expectedUtc);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                assertEquals(expectedAmount, result.getBigDecimal("amount"));
                LocalDateTime actualUtc = result.getObject("occurred_at", LocalDateTime.class);
                assertEquals(Instant.parse("2026-09-27T06:30:15.123Z"), actualUtc.toInstant(ZoneOffset.UTC));
            }
        }
    }

    private int nullable(Connection connection, String table, String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT c.is_nullable
                FROM sys.columns c
                JOIN sys.tables t ON t.object_id = c.object_id
                WHERE t.schema_id = SCHEMA_ID('dbo') AND t.name = ? AND c.name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private Connection openConnection() throws Exception {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(AppConfig.get("TC_SQL_HOST"));
        dataSource.setPortNumber(AppConfig.getInt("TC_SQL_PORT", 1433));
        dataSource.setDatabaseName(AppConfig.get("TC_SQL_TEST_DB"));
        dataSource.setUser(AppConfig.get("TC_SQL_LOGIN"));
        dataSource.setPassword(AppConfig.get("TC_SQL_PASSWORD"));
        dataSource.setEncrypt(AppConfig.get("TC_SQL_ENCRYPT", "true"));
        dataSource.setTrustServerCertificate(AppConfig.getBoolean("TC_SQL_TRUST_SERVER_CERT", false));
        dataSource.setLoginTimeout(AppConfig.getInt("TC_SQL_CONNECT_TIMEOUT_SEC", 30));
        return dataSource.getConnection();
    }
}
