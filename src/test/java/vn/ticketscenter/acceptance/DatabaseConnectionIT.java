package vn.ticketscenter.acceptance;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Day 01 Database Smoke Test — SQL Server Real Connection")
public class DatabaseConnectionIT {

    @Test
    @DisplayName("Kết nối SQL Server thật và thực hiện SELECT 1 thành công")
    public void testDatabaseConnectionSuccess() throws Exception {
        String host = System.getenv("TC_SQL_HOST");
        String port = System.getenv("TC_SQL_PORT");
        String dbName = System.getenv("TC_SQL_TEST_DB");
        String user = System.getenv("TC_SQL_LOGIN");
        String password = System.getenv("TC_SQL_PASSWORD");
        String encryptValue = System.getenv().getOrDefault("TC_SQL_ENCRYPT", "true");
        String trustCertificateValue = System.getenv().getOrDefault("TC_SQL_TRUST_SERVER_CERT", "false");
        String timeoutValue = System.getenv().getOrDefault("TC_SQL_CONNECT_TIMEOUT_SEC", "5");

        // Profile sqlserver-it yêu cầu biến môi trường phải đầy đủ, nếu thiếu phải fail đỏ
        assertNotNull(host, "Biến TC_SQL_HOST không được để trống khi chạy profile sqlserver-it");
        assertNotNull(dbName, "Biến TC_SQL_TEST_DB không được để trống khi chạy profile sqlserver-it");
        assertNotNull(user, "Biến TC_SQL_LOGIN không được để trống khi chạy profile sqlserver-it");
        assertNotNull(password, "Biến TC_SQL_PASSWORD không được để trống khi chạy profile sqlserver-it");

        if (port == null || port.isBlank()) {
            port = "1433";
        }

        assertTrue(encryptValue.equalsIgnoreCase("true") || encryptValue.equalsIgnoreCase("false"),
                "TC_SQL_ENCRYPT chỉ nhận true/false");
        assertTrue(trustCertificateValue.equalsIgnoreCase("true") || trustCertificateValue.equalsIgnoreCase("false"),
                "TC_SQL_TRUST_SERVER_CERT chỉ nhận true/false");

        int timeout = Integer.parseInt(timeoutValue);
        assertTrue(timeout > 0 && timeout <= 60, "TC_SQL_CONNECT_TIMEOUT_SEC phải trong khoảng 1..60");

        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(host);
        dataSource.setPortNumber(Integer.parseInt(port));
        dataSource.setDatabaseName(dbName);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        dataSource.setEncrypt(encryptValue);
        dataSource.setTrustServerCertificate(Boolean.parseBoolean(trustCertificateValue));
        dataSource.setLoginTimeout(timeout);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 AS alive")) {

            assertTrue(rs.next(), "Query SELECT 1 phải trả về ít nhất 1 dòng");
            assertEquals(1, rs.getInt("alive"), "Giá trị kiểm tra kết nối phải là 1");
        }
    }
}
