package vn.ticketscenter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ModularConfig Unit Test — Kiểm tra các cấu hình Server, VnPay và Mail riêng biệt")
public class ModularPaymentServiceTest {

    @Test
    @DisplayName("ServerConfig nạp chính xác cấu hình máy chủ web")
    public void testServerConfig() {
        assertEquals("http://localhost:8080/ticketscenter", ServerConfig.getBaseUrl());
        assertEquals("development", ServerConfig.getEnv());
        assertTrue(ServerConfig.isDevelopment());
        assertFalse(ServerConfig.isProduction());
    }

    @Test
    @DisplayName("VnPayConfig nạp chính xác cấu hình VNPAY Sandbox")
    public void testVnPayConfig() {
        assertNotNull(VnPayConfig.getTmnCode());
        assertNotNull(VnPayConfig.getHashSecret());
        assertTrue(VnPayConfig.getPayUrl().contains("vnpayment.vn"));
        assertTrue(VnPayConfig.getReturnUrl().contains("/api/payments/vnpay/return"));
    }

    @Test
    @DisplayName("MailConfig nạp chính xác cấu hình SMTP")
    public void testMailConfig() {
        assertEquals("smtp.gmail.com", MailConfig.getHost());
        assertEquals(587, MailConfig.getPort());
        assertNotNull(MailConfig.getUser());
        assertNotNull(MailConfig.getPassword());
    }
}
