package vn.ticketscenter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AppConfig Unit Test — Kiểm tra nạp cấu hình từ application.properties")
public class AppConfigTest {

    @Test
    @DisplayName("AppConfig nạp thành công các thuộc tính từ application.properties")
    public void testLoadProperties() {
        String host = AppConfig.get("db.host");
        assertNotNull(host, "db.host phải được nạp từ application.properties");
        assertEquals("devonxjz.database.windows.net", host);

        int port = AppConfig.getInt("db.port", 0);
        assertEquals(1433, port);

        String dbName = AppConfig.get("db.name");
        assertEquals("TicketCenterticke", dbName);

        String user = AppConfig.get("db.user");
        assertEquals("adminTicket@devonxjz", user);

        boolean encrypt = AppConfig.getBoolean("db.encrypt", false);
        assertTrue(encrypt);

        int timeout = AppConfig.getInt("db.loginTimeout", 0);
        assertEquals(30, timeout);
    }

    @Test
    @DisplayName("AppConfig hỗ trợ alias TC_SQL_* tương thích")
    public void testCompatibilityAlias() {
        String host = AppConfig.get("TC_SQL_HOST");
        assertEquals("devonxjz.database.windows.net", host);

        String defaultVal = AppConfig.get("non.existent.key", "default123");
        assertEquals("default123", defaultVal);
    }

    @Test
    @DisplayName("AppConfig.Keys tập trung đầy đủ các hằng số key cấu hình trong 1 file")
    public void testKeysConstants() {
        assertEquals("db.host", AppConfig.Keys.DB_HOST);
        assertEquals("app.base.url", AppConfig.Keys.APP_BASE_URL);
        assertEquals("vnpay.tmn.code", AppConfig.Keys.VNPAY_TMN_CODE);
        assertEquals("mail.smtp.host", AppConfig.Keys.MAIL_HOST);
    }
}
