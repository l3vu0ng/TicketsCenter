package vn.ticketscenter.concurrency;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationRaceIT {
    @Test
    void twoWorkersCannotDuplicateCancellationWork() throws Exception {
        SQLServerDataSource dataSource = dataSource();
        cleanupStaleFixtures(dataSource);
        UUID admin = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        UUID event = UUID.randomUUID();
        try {
            seed(dataSource, admin, organization, category, event);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> { cancel(dataSource, event, admin, ready, start); return null; });
                var second = executor.submit(() -> { cancel(dataSource, event, admin, ready, start); return null; });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            }
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT e.status, COUNT(x.id)
                         FROM dbo.tc_events e
                         LEFT JOIN dbo.tc_outbox x ON x.aggregate_id=e.id AND x.event_type='EVENT_CANCELLED'
                         WHERE e.id=? GROUP BY e.status
                         """)) {
                statement.setObject(1, event);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("CANCELLED", result.getString(1));
                    assertEquals(1, result.getInt(2));
                }
            }
        } finally {
            cleanup(dataSource, admin, organization, category, event);
        }
    }

    private static void cancel(SQLServerDataSource dataSource, UUID event, UUID admin,
                               CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var call = connection.prepareCall("{call dbo.usp_CancelEvent(?,?)}")) {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race barrier timed out");
            call.setObject(1, event);
            call.setObject(2, admin);
            call.execute();
        }
    }

    private static void seed(SQLServerDataSource dataSource, UUID admin, UUID organization,
                             UUID category, UUID event) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement user = connection.prepareStatement("""
                    INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at)
                    VALUES(?,?,?,?,?,SYSUTCDATETIME())
                    """)) {
                String email = "day16-race-" + admin + "@example.test";
                user.setObject(1, admin); user.setString(2, email); user.setString(3, email);
                user.setString(4, "test"); user.setString(5, "Day 16 Race"); user.executeUpdate();
            }
            execute(connection, "INSERT dbo.tc_user_platform_roles(user_id,role) VALUES(?,'ADMIN')", admin);
            execute(connection, "INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,'Day 16 Race','race@example.test')", organization);
            try (PreparedStatement value = connection.prepareStatement(
                    "INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,'Day 16 Race',?)")) {
                value.setObject(1, category); value.setString(2, "day-16-race-" + category); value.executeUpdate();
            }
            try (PreparedStatement value = connection.prepareStatement("""
                    INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,
                                          sale_start,sale_end,start_time,end_time,status)
                    VALUES(?,?,?,'Race','Venue','Address',DATEADD(day,-1,SYSUTCDATETIME()),
                           DATEADD(hour,1,SYSUTCDATETIME()),DATEADD(hour,2,SYSUTCDATETIME()),
                           DATEADD(hour,3,SYSUTCDATETIME()),'PUBLISHED')
                    """)) {
                value.setObject(1, event); value.setObject(2, organization); value.setObject(3, category); value.executeUpdate();
            }
            connection.commit();
        }
    }

    private static void cleanup(SQLServerDataSource dataSource, UUID admin, UUID organization,
                                UUID category, UUID event) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            execute(connection, "DELETE FROM dbo.tc_outbox WHERE aggregate_id=?", event);
            execute(connection, "DELETE FROM dbo.tc_events WHERE id=?", event);
            execute(connection, "DELETE FROM dbo.tc_event_categories WHERE id=?", category);
            execute(connection, "DELETE FROM dbo.tc_organizations WHERE id=?", organization);
            execute(connection, "DELETE FROM dbo.tc_user_platform_roles WHERE user_id=?", admin);
            execute(connection, "DELETE FROM dbo.tc_users WHERE id=?", admin);
            connection.commit();
        }
    }

    private static void cleanupStaleFixtures(SQLServerDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    DELETE x FROM dbo.tc_outbox x JOIN dbo.tc_events e ON e.id=x.aggregate_id
                    JOIN dbo.tc_organizations o ON o.id=e.organization_id
                    WHERE x.aggregate_type='EVENT' AND o.contact_email='race@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE e FROM dbo.tc_events e JOIN dbo.tc_organizations o ON o.id=e.organization_id
                    WHERE o.contact_email='race@example.test'
                    """);
            statement.executeUpdate("DELETE FROM dbo.tc_event_categories WHERE slug LIKE 'day-16-race-%'");
            statement.executeUpdate("DELETE FROM dbo.tc_organizations WHERE contact_email='race@example.test'");
            statement.executeUpdate("""
                    DELETE r FROM dbo.tc_user_platform_roles r JOIN dbo.tc_users u ON u.id=r.user_id
                    WHERE u.normalized_email LIKE 'day16-race-%@example.test'
                    """);
            statement.executeUpdate("DELETE FROM dbo.tc_users WHERE normalized_email LIKE 'day16-race-%@example.test'");
            connection.commit();
        }
    }

    private static void execute(Connection connection, String sql, UUID id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private static SQLServerDataSource dataSource() {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(DatabaseConfig.getHost());
        dataSource.setPortNumber(DatabaseConfig.getPort());
        dataSource.setDatabaseName(DatabaseConfig.getDatabaseName());
        dataSource.setUser(DatabaseConfig.getUser());
        dataSource.setPassword(DatabaseConfig.getPassword());
        dataSource.setEncrypt(String.valueOf(DatabaseConfig.isEncrypt()));
        dataSource.setTrustServerCertificate(DatabaseConfig.isTrustServerCertificate());
        dataSource.setLoginTimeout(DatabaseConfig.getLoginTimeout());
        return dataSource;
    }
}
