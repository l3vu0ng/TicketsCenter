package vn.ticketscenter.concurrency;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.DatabaseConfig;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementConcurrencyIT {
    private static final UUID ADMIN = UUID.fromString("17000000-0000-0000-0000-000000000001");
    private static final UUID ORGANIZATION = UUID.fromString("17000000-0000-0000-0000-000000000002");
    private static final UUID CATEGORY = UUID.fromString("17000000-0000-0000-0000-000000000003");
    private static final UUID RULE = UUID.fromString("17000000-0000-0000-0000-000000000004");
    private static final UUID EVENT = UUID.fromString("17000000-0000-0000-0000-000000000005");
    private static final UUID SETTLEMENT = UUID.fromString("17000000-0000-0000-0000-000000000006");

    @Test
    void concurrentPayoutReservationsCannotExceedNetPayable() throws Exception {
        SQLServerDataSource dataSource = dataSource();
        seedReusableSnapshot(dataSource);
        clearPendingAttempts(dataSource);
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            int succeeded;
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> reserve(dataSource, firstId, ready, start));
                var second = executor.submit(() -> reserve(dataSource, secondId, ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                succeeded = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                        + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            }
            assertEquals(1, succeeded);
            try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         SELECT COALESCE(SUM(CASE WHEN status IN ('PENDING','SUCCEEDED') THEN amount ELSE 0 END),0),
                                COUNT(*)
                         FROM dbo.tc_payouts WHERE settlement_id='17000000-0000-0000-0000-000000000006'
                         """)) {
                assertTrue(result.next());
                assertEquals(new BigDecimal("80"), result.getBigDecimal(1));
                assertEquals(1, result.getInt(2));
            }
        } finally {
            clearPendingAttempts(dataSource);
        }
    }

    private static boolean reserve(SQLServerDataSource dataSource, UUID payoutId,
                                   CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var call = connection.prepareCall("{call dbo.usp_RecordPayout(?,?,?,?,?,?)}")) {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race barrier timed out");
            call.setObject(1, SETTLEMENT);
            call.setObject(2, payoutId);
            call.setObject(3, ADMIN);
            call.setBigDecimal(4, new BigDecimal("80"));
            call.setString(5, "SIM-RACE-" + payoutId);
            call.setString(6, "PENDING");
            call.execute();
            return true;
        } catch (SQLException expectedConflict) {
            return false;
        }
    }

    private static void seedReusableSnapshot(SQLServerDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_users WHERE id='17000000-0000-0000-0000-000000000001')
                    BEGIN
                      INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at)
                      VALUES('17000000-0000-0000-0000-000000000001','day17-race@example.test','day17-race@example.test','test','Day 17 Race',SYSUTCDATETIME());
                      INSERT dbo.tc_user_platform_roles(user_id,role)
                      VALUES('17000000-0000-0000-0000-000000000001','ADMIN');
                    END;
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_organizations WHERE id='17000000-0000-0000-0000-000000000002')
                      INSERT dbo.tc_organizations(id,name,contact_email)
                      VALUES('17000000-0000-0000-0000-000000000002','Day 17 Race','day17-race@example.test');
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_event_categories WHERE id='17000000-0000-0000-0000-000000000003')
                      INSERT dbo.tc_event_categories(id,name,slug)
                      VALUES('17000000-0000-0000-0000-000000000003','Day 17 Race','day-17-race');
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_commission_rules WHERE id='17000000-0000-0000-0000-000000000004')
                      INSERT dbo.tc_commission_rules(id,organization_id,rate_percent,fixed_fee,effective_from,effective_to)
                      VALUES('17000000-0000-0000-0000-000000000004','17000000-0000-0000-0000-000000000002',0,0,
                             DATEADD(day,-30,SYSUTCDATETIME()),DATEADD(day,30,SYSUTCDATETIME()));
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_events WHERE id='17000000-0000-0000-0000-000000000005')
                      INSERT dbo.tc_events(id,organization_id,category_id,commission_rule_id,title,venue_name,venue_address,
                                            sale_start,sale_end,start_time,end_time,status)
                      VALUES('17000000-0000-0000-0000-000000000005','17000000-0000-0000-0000-000000000002',
                             '17000000-0000-0000-0000-000000000003','17000000-0000-0000-0000-000000000004',
                             'Day 17 Race','Venue','Address',DATEADD(day,-5,SYSUTCDATETIME()),DATEADD(day,-4,SYSUTCDATETIME()),
                             DATEADD(day,-2,SYSUTCDATETIME()),DATEADD(day,-1,SYSUTCDATETIME()),'PUBLISHED');
                    IF NOT EXISTS(SELECT 1 FROM dbo.tc_settlements WHERE id='17000000-0000-0000-0000-000000000006')
                      INSERT dbo.tc_settlements(id,event_id,gross_revenue,total_refund,total_commission,net_payable,status,confirmed_at)
                      VALUES('17000000-0000-0000-0000-000000000006','17000000-0000-0000-0000-000000000005',100,0,0,100,'CONFIRMED',SYSUTCDATETIME());
                    """);
        }
    }

    private static void clearPendingAttempts(SQLServerDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM dbo.tc_payouts WHERE settlement_id='17000000-0000-0000-0000-000000000006' AND status='PENDING'");
        }
    }

    private static SQLServerDataSource dataSource() {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(DatabaseConfig.getHost()); dataSource.setPortNumber(DatabaseConfig.getPort());
        dataSource.setDatabaseName(DatabaseConfig.getDatabaseName()); dataSource.setUser(DatabaseConfig.getUser());
        dataSource.setPassword(DatabaseConfig.getPassword()); dataSource.setEncrypt(String.valueOf(DatabaseConfig.isEncrypt()));
        dataSource.setTrustServerCertificate(DatabaseConfig.isTrustServerCertificate()); dataSource.setLoginTimeout(DatabaseConfig.getLoginTimeout());
        return dataSource;
    }
}
