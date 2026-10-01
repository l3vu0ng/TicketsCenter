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

class RefundResultConcurrencyIT {
    @Test
    void twoWorkersReturnInventoryOnce() throws Exception {
        SQLServerDataSource dataSource = dataSource();
        cleanupStaleFixtures(dataSource);
        Fixture fixture = new Fixture();
        try {
            seed(dataSource, fixture);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> { apply(dataSource, fixture.refund, ready, start); return null; });
                var second = executor.submit(() -> { apply(dataSource, fixture.refund, ready, start); return null; });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            }
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("""
                    SELECT r.status,z.sold_quantity,t.status,
                           (SELECT COUNT(*) FROM dbo.tc_outbox WHERE idempotency_key=CONCAT('refund-succeeded:',r.id))
                    FROM dbo.tc_refunds r CROSS JOIN dbo.tc_zones z CROSS JOIN dbo.tc_tickets t
                    WHERE r.id=? AND z.id=? AND t.id=?
                    """)) {
                statement.setObject(1, fixture.refund); statement.setObject(2, fixture.zone); statement.setObject(3, fixture.ticket);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals("SUCCEEDED", result.getString(1));
                    assertEquals(0, result.getInt(2));
                    assertEquals("REFUNDED", result.getString(3));
                    assertEquals(1, result.getInt(4));
                }
            }
        } finally {
            cleanup(dataSource, fixture);
        }
    }

    private static void apply(SQLServerDataSource dataSource, UUID refund, CountDownLatch ready, CountDownLatch start) throws Exception {
        try (Connection connection = dataSource.getConnection(); var call = connection.prepareCall("{call dbo.usp_ApplyRefundResult(?,?)}")) {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("race barrier timed out");
            call.setObject(1, refund);
            call.setString(2, "{\"status\":\"SUCCEEDED\",\"providerReference\":\"race\"}");
            call.execute();
        }
    }

    private static void seed(SQLServerDataSource dataSource, Fixture f) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("INSERT dbo.tc_users(id,email,normalized_email,password_hash,full_name,email_verified_at) VALUES('" + f.buyer + "','d15-race-" + f.buyer + "@example.test','d15-race-" + f.buyer + "@example.test','test','Race',SYSUTCDATETIME())");
            statement.executeUpdate("INSERT dbo.tc_organizations(id,name,contact_email) VALUES('" + f.organization + "','D15 Race','d15-race@example.test')");
            statement.executeUpdate("INSERT dbo.tc_event_categories(id,name,slug) VALUES('" + f.category + "','D15 Race','d15-race-" + f.category + "')");
            statement.executeUpdate("INSERT dbo.tc_events(id,organization_id,category_id,title,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status) VALUES('" + f.event + "','" + f.organization + "','" + f.category + "','Race','Venue','Address',DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()),DATEADD(day,2,SYSUTCDATETIME()),DATEADD(day,3,SYSUTCDATETIME()),'DRAFT')");
            statement.executeUpdate("INSERT dbo.tc_zones(id,event_id,name,type,price,capacity,sold_quantity) VALUES('" + f.zone + "','" + f.event + "','Standing','STANDING',100,1,1)");
            statement.executeUpdate("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id='" + f.event + "'");
            statement.executeUpdate("INSERT dbo.tc_ticket_holds(id,user_id,event_id,status,expires_at) VALUES('" + f.hold + "','" + f.buyer + "','" + f.event + "','CONSUMED',DATEADD(minute,10,SYSUTCDATETIME()))");
            statement.executeUpdate("INSERT dbo.tc_orders(id,user_id,event_id,hold_id,order_code,subtotal_amount,discount_amount,total_amount,status,paid_at) VALUES('" + f.order + "','" + f.buyer + "','" + f.event + "','" + f.hold + "','D15-RACE-" + f.order + "',100,0,100,'PAID',SYSUTCDATETIME())");
            statement.executeUpdate("INSERT dbo.tc_order_items(id,order_id,zone_id,quantity,unit_price,zone_name_snapshot) VALUES('" + f.item + "','" + f.order + "','" + f.zone + "',1,100,'Standing')");
            statement.executeUpdate("INSERT dbo.tc_payments(id,order_id,txn_ref,amount,status,captured_at) VALUES('" + f.payment + "','" + f.order + "','D15-RACE-" + f.payment + "',100,'CAPTURED',SYSUTCDATETIME())");
            statement.executeUpdate("INSERT dbo.tc_tickets(id,order_item_id,ticket_code,qr_secret_hash,paid_amount,status) VALUES('" + f.ticket + "','" + f.item + "','D15-RACE-" + f.ticket + "',0x01,100,'REFUND_PENDING')");
            statement.executeUpdate("INSERT dbo.tc_refund_requests(id,order_id,requester_id,reason,reason_type,status) VALUES('" + f.request + "','" + f.order + "','" + f.buyer + "','Race','CUSTOMER_REQUEST','APPROVED')");
            statement.executeUpdate("INSERT dbo.tc_refund_request_tickets(refund_request_id,ticket_id) VALUES('" + f.request + "','" + f.ticket + "')");
            statement.executeUpdate("INSERT dbo.tc_refunds(id,refund_request_id,payment_id,purpose,amount) VALUES('" + f.refund + "','" + f.request + "','" + f.payment + "','CUSTOMER_REFUND',100)");
            connection.commit();
        }
    }

    private static void cleanup(SQLServerDataSource dataSource, Fixture f) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("DELETE FROM dbo.tc_outbox WHERE aggregate_id='" + f.refund + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_refunds WHERE id='" + f.refund + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_refund_request_tickets WHERE refund_request_id='" + f.request + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_refund_requests WHERE id='" + f.request + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_tickets WHERE id='" + f.ticket + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_payments WHERE id='" + f.payment + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_order_items WHERE id='" + f.item + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_orders WHERE id='" + f.order + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_ticket_holds WHERE id='" + f.hold + "'");
            statement.executeUpdate("UPDATE dbo.tc_events SET status='DRAFT' WHERE id='" + f.event + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_zones WHERE id='" + f.zone + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_events WHERE id='" + f.event + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_event_categories WHERE id='" + f.category + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_organizations WHERE id='" + f.organization + "'");
            statement.executeUpdate("DELETE FROM dbo.tc_users WHERE id='" + f.buyer + "'");
            connection.commit();
        }
    }

    private static void cleanupStaleFixtures(SQLServerDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.executeUpdate("""
                    DELETE x FROM dbo.tc_outbox x
                    JOIN dbo.tc_refunds r ON r.id = x.aggregate_id
                    JOIN dbo.tc_payments p ON p.id = r.payment_id
                    JOIN dbo.tc_orders o ON o.id = p.order_id
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE r FROM dbo.tc_refunds r
                    JOIN dbo.tc_payments p ON p.id = r.payment_id
                    JOIN dbo.tc_orders o ON o.id = p.order_id
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE t FROM dbo.tc_refund_request_tickets t
                    JOIN dbo.tc_refund_requests q ON q.id = t.refund_request_id
                    JOIN dbo.tc_orders o ON o.id = q.order_id
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE q FROM dbo.tc_refund_requests q
                    JOIN dbo.tc_orders o ON o.id = q.order_id
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE t FROM dbo.tc_tickets t
                    JOIN dbo.tc_order_items i ON i.id = t.order_item_id
                    JOIN dbo.tc_orders o ON o.id = i.order_id
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE p FROM dbo.tc_payments p
                    JOIN dbo.tc_orders o ON o.id = p.order_id
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE i FROM dbo.tc_order_items i
                    JOIN dbo.tc_orders o ON o.id = i.order_id
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE o FROM dbo.tc_orders o
                    JOIN dbo.tc_users u ON u.id = o.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE h FROM dbo.tc_ticket_holds h
                    JOIN dbo.tc_users u ON u.id = h.user_id
                    WHERE u.normalized_email LIKE 'd15-race-%@example.test'
                    """);
            statement.executeUpdate("""
                    UPDATE e SET status = 'DRAFT' FROM dbo.tc_events e
                    JOIN dbo.tc_organizations o ON o.id = e.organization_id
                    WHERE o.contact_email = 'd15-race@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE z FROM dbo.tc_zones z
                    JOIN dbo.tc_events e ON e.id = z.event_id
                    JOIN dbo.tc_organizations o ON o.id = e.organization_id
                    WHERE o.contact_email = 'd15-race@example.test'
                    """);
            statement.executeUpdate("""
                    DELETE e FROM dbo.tc_events e
                    JOIN dbo.tc_organizations o ON o.id = e.organization_id
                    WHERE o.contact_email = 'd15-race@example.test'
                    """);
            statement.executeUpdate("DELETE FROM dbo.tc_event_categories WHERE slug LIKE 'd15-race-%'");
            statement.executeUpdate("DELETE FROM dbo.tc_organizations WHERE contact_email = 'd15-race@example.test'");
            statement.executeUpdate("DELETE FROM dbo.tc_users WHERE normalized_email LIKE 'd15-race-%@example.test'");
            connection.commit();
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

    private static final class Fixture {
        private final UUID buyer=UUID.randomUUID(), organization=UUID.randomUUID(), category=UUID.randomUUID(), event=UUID.randomUUID(),
                zone=UUID.randomUUID(), hold=UUID.randomUUID(), order=UUID.randomUUID(), item=UUID.randomUUID(), payment=UUID.randomUUID(),
                ticket=UUID.randomUUID(), request=UUID.randomUUID(), refund=UUID.randomUUID();
    }
}
