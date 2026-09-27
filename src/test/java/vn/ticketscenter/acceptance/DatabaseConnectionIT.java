package vn.ticketscenter.acceptance;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Day 01 Database Smoke Test — SQL Server Real Connection")
public class DatabaseConnectionIT {

    @Test
    @DisplayName("Kết nối SQL Server thật và thực hiện SELECT 1 thành công")
    public void testDatabaseConnectionSuccess() throws Exception {
        String host = AppConfig.get("TC_SQL_HOST");
        String port = AppConfig.get("TC_SQL_PORT", "1433");
        String dbName = AppConfig.get("TC_SQL_TEST_DB");
        String user = AppConfig.get("TC_SQL_LOGIN");
        String password = AppConfig.get("TC_SQL_PASSWORD");
        String encryptValue = AppConfig.get("TC_SQL_ENCRYPT", "true");
        String trustCertificateValue = AppConfig.get("TC_SQL_TRUST_SERVER_CERT", "false");
        String timeoutValue = AppConfig.get("TC_SQL_CONNECT_TIMEOUT_SEC", "30");

        assertNotNull(host, "Thuộc tính TC_SQL_HOST (hoặc db.host) không được để trống trong application.properties");
        assertNotNull(dbName, "Thuộc tính TC_SQL_TEST_DB (hoặc db.name) không được để trống trong application.properties");
        assertNotNull(user, "Thuộc tính TC_SQL_LOGIN (hoặc db.user) không được để trống trong application.properties");
        assertNotNull(password, "Thuộc tính TC_SQL_PASSWORD (hoặc db.password) không được để trống trong application.properties");
        assertFalse("YOUR_AZURE_SQL_PASSWORD_HERE".equals(password) || "{your_password_here}".equals(password),
                "Vui lòng cấu hình mật khẩu thực tế trong application.properties (db.password)");


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
