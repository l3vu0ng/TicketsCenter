package vn.ticketscenter.config;

/**
 * Cấu hình môi trường và máy chủ Web (Base URL, Môi trường, Secret Key).
 */
public final class ServerConfig {

    private ServerConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static String getBaseUrl() {
        String url = AppConfig.get("app.base.url");
        return (url != null && !url.isBlank()) ? url : AppConfig.get("APP_BASE_URL", "http://localhost:8080/ticketscenter");
    }

    public static String getEnv() {
        String env = AppConfig.get("app.env");
        return (env != null && !env.isBlank()) ? env : AppConfig.get("APP_ENV", "development");
    }

    public static String getSecretKey() {
        String secret = AppConfig.get("app.secret.key");
        return (secret != null && !secret.isBlank()) ? secret : AppConfig.get("APP_SECRET_KEY", "");
    }

    public static boolean isDevelopment() {
        return "development".equalsIgnoreCase(getEnv());
    }

    public static boolean isProduction() {
        return "production".equalsIgnoreCase(getEnv());
    }

    public static boolean isTest() {
        return "test".equalsIgnoreCase(getEnv());
    }
}
