package vn.ticketscenter.acceptance;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Day 01 Database Smoke Test — SQL Server Real Connection")
public class DatabaseConnectionIT {

    @Test
    @DisplayName("Kết nối SQL Server thật và thực hiện SELECT 1 thành công")
    public void testDatabaseConnectionSuccess() throws Exception {
        String host = DatabaseConfig.getHost();
        int port = DatabaseConfig.getPort();
        String dbName = DatabaseConfig.getDatabaseName();
        String user = DatabaseConfig.getUser();
        String password = DatabaseConfig.getPassword();
        boolean encrypt = DatabaseConfig.isEncrypt();
        boolean trustCertificate = DatabaseConfig.isTrustServerCertificate();
        int timeout = DatabaseConfig.getLoginTimeout();

        assertNotNull(host, "Thuộc tính db.host (hoặc TC_SQL_HOST) không được để trống");
        assertNotNull(dbName, "Thuộc tính db.name (hoặc TC_SQL_TEST_DB) không được để trống");
        assertNotNull(user, "Thuộc tính db.user (hoặc TC_SQL_LOGIN) không được để trống");
        assertNotNull(password, "Thuộc tính db.password (hoặc TC_SQL_PASSWORD) không được để trống");
        assertFalse("<your_password_here>".equals(password) || "YOUR_AZURE_SQL_PASSWORD_HERE".equals(password),
                "Vui lòng cấu hình mật khẩu thực tế trong application.properties (db.password)");

        assertTrue(timeout > 0 && timeout <= 60, "Timeout kết nối phải trong khoảng 1..60");

        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(host);
        dataSource.setPortNumber(port);
        dataSource.setDatabaseName(dbName);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        dataSource.setEncrypt(String.valueOf(encrypt));
        dataSource.setTrustServerCertificate(trustCertificate);
        dataSource.setLoginTimeout(timeout);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 AS alive")) {

            assertTrue(rs.next(), "Query SELECT 1 phải trả về ít nhất 1 dòng");
            assertEquals(1, rs.getInt("alive"), "Giá trị kiểm tra kết nối phải là 1");
        }
    }
}
