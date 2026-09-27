package vn.ticketscenter.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quản lý nạp cấu hình tập trung từ classpath resource application.properties.
 * Ưu tiên: System Properties -> Environment Variables -> application.properties.
 */
public final class AppConfig {

    private static final Logger LOGGER = Logger.getLogger(AppConfig.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";
    private static final Properties PROPERTIES = new Properties();

    static {
        loadProperties();
    }

    private AppConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    private static void loadProperties() {
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                PROPERTIES.load(is);
                return;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi đọc file " + PROPERTIES_FILE, e);
        }

        // Fallback nạp application.properties.example nếu chưa có application.properties cục bộ (cho CI và unit test)
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("application.properties.example")) {
            if (is != null) {
                PROPERTIES.load(is);
            } else {
                LOGGER.log(Level.WARNING, "Không tìm thấy file application.properties hoặc application.properties.example trong classpath");
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi đọc file application.properties.example", e);
        }
    }

    /**
     * Lấy giá trị cấu hình theo key.
     * Thứ tự ưu tiên:
     * 1. System property (-Dkey=value)
     * 2. Environment variable
     * 3. application.properties
     *
     * @param key Khóa cấu hình (ví dụ: "db.host" hoặc "TC_SQL_HOST")
     * @return Giá trị cấu hình hoặc null nếu không tồn tại
     */
    public static String get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        // 1. Kiểm tra System Property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }

        // 2. Kiểm tra System Environment
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String normalizedEnvKey = key.replace('.', '_').toUpperCase();
        String normalizedEnvVal = System.getenv(normalizedEnvKey);
        if (normalizedEnvVal != null && !normalizedEnvVal.isBlank()) {
            return normalizedEnvVal.trim();
        }

        // 3. Kiểm tra Properties file
        String propValue = PROPERTIES.getProperty(key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue.trim();
        }

        // 4. Fallback lookup giữa dạng dot notation và TC_SQL_* notation
        String mappedKey = resolveMappedKey(key);
        if (mappedKey != null) {
            String mappedValue = PROPERTIES.getProperty(mappedKey);
            if (mappedValue != null && !mappedValue.isBlank()) {
                return mappedValue.trim();
            }
        }

        return null;
    }

    /**
     * Lấy giá trị cấu hình với giá trị mặc định.
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * Lấy giá trị cấu hình kiểu int.
     */
    public static int getInt(String key, int defaultValue) {
        String val = get(key);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Lấy giá trị cấu hình kiểu boolean.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val.trim());
    }

    private static String resolveMappedKey(String key) {
        return switch (key) {
            case "TC_SQL_HOST" -> "db.host";
            case "TC_SQL_PORT" -> "db.port";
            case "TC_SQL_DEV_DB", "TC_SQL_TEST_DB", "TC_SQL_BENCH_DB" -> "db.name";
            case "TC_SQL_LOGIN" -> "db.user";
            case "TC_SQL_PASSWORD" -> "db.password";
            case "TC_SQL_ENCRYPT" -> "db.encrypt";
            case "TC_SQL_TRUST_SERVER_CERT" -> "db.trustServerCertificate";
            case "TC_SQL_CONNECT_TIMEOUT_SEC" -> "db.loginTimeout";
            case "db.host" -> "TC_SQL_HOST";
            case "db.port" -> "TC_SQL_PORT";
            case "db.name" -> "TC_SQL_TEST_DB";
            case "db.user" -> "TC_SQL_LOGIN";
            case "db.password" -> "TC_SQL_PASSWORD";
            case "db.encrypt" -> "TC_SQL_ENCRYPT";
            case "db.trustServerCertificate" -> "TC_SQL_TRUST_SERVER_CERT";
            case "db.loginTimeout" -> "TC_SQL_CONNECT_TIMEOUT_SEC";
            default -> null;
        };
    }
}
