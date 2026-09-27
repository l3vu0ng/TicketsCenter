package vn.ticketscenter.config;

/**
 * Cấu hình tích hợp Cổng thanh toán VNPAY Sandbox.
 */
public final class VnPayConfig {

    private VnPayConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static String getTmnCode() {
        String code = AppConfig.get("vnpay.tmn.code");
        return (code != null && !code.isBlank()) ? code : AppConfig.get("VNPAY_TMN_CODE", "");
    }

    public static String getHashSecret() {
        String secret = AppConfig.get("vnpay.hash.secret");
        return (secret != null && !secret.isBlank()) ? secret : AppConfig.get("VNPAY_HASH_SECRET", "");
    }

    public static String getPayUrl() {
        String url = AppConfig.get("vnpay.pay.url");
        return (url != null && !url.isBlank()) ? url : AppConfig.get("VNPAY_PAY_URL", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
    }

    public static String getReturnUrl() {
        String url = AppConfig.get("vnpay.return.url");
        return (url != null && !url.isBlank()) ? url : AppConfig.get("VNPAY_RETURN_URL", "http://localhost:8080/ticketscenter/api/payments/vnpay/return");
    }
}
