package vn.ticketscenter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseConfig Unit Test — Kiểm tra cấu hình Database tách biệt")
public class DatabasePaymentServiceTest {

    @Test
    @DisplayName("DatabaseConfig cung cấp đầy đủ thông số kết nối strongly-typed")
    public void testDatabaseConfigProperties() {
        assertEquals("devonxjz.database.windows.net", DatabaseConfig.getHost());
        assertEquals(1433, DatabaseConfig.getPort());
        assertEquals("TicketCenterticke", DatabaseConfig.getDatabaseName());
        assertEquals("adminTicket@devonxjz", DatabaseConfig.getUser());
        assertNotNull(DatabaseConfig.getPassword());
        assertTrue(DatabaseConfig.isEncrypt());
        assertFalse(DatabaseConfig.isTrustServerCertificate());
        assertEquals(30, DatabaseConfig.getLoginTimeout());
    }

    @Test
    @DisplayName("DatabaseConfig sinh JDBC URL chính xác")
    public void testJdbcUrl() {
        String url = DatabaseConfig.getJdbcUrl();
        assertNotNull(url);
        assertTrue(url.contains("devonxjz.database.windows.net"));
        assertTrue(url.contains("database=TicketCenterticke") || url.contains("databaseName=TicketCenterticke"));
    }
}
