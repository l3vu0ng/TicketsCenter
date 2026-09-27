package vn.ticketscenter.payment.service;

import jakarta.servlet.http.HttpServletRequest;
import vn.ticketscenter.config.VnPayConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Cấu hình và tiện ích tích hợp Cổng thanh toán VNPAY Sandbox cho TicketsCenter.
 */
public class Config {

    public static String vnp_PayUrl = VnPayConfig.getPayUrl();
    public static String vnp_ReturnUrl = VnPayConfig.getReturnUrl();
    public static String vnp_TmnCode = VnPayConfig.getTmnCode();
    public static String secretKey = VnPayConfig.getHashSecret();
    public static String vnp_ApiUrl = VnPayConfig.getApiUrl();

    public static String getPayUrl() {
        String url = VnPayConfig.getPayUrl();
        return (url != null && !url.isBlank()) ? url : vnp_PayUrl;
    }

    public static String getReturnUrl() {
        String url = VnPayConfig.getReturnUrl();
        return (url != null && !url.isBlank()) ? url : vnp_ReturnUrl;
    }

    public static String getTmnCode() {
        String code = VnPayConfig.getTmnCode();
        return (code != null && !code.isBlank()) ? code : vnp_TmnCode;
    }

    public static String getSecretKey() {
        String key = VnPayConfig.getHashSecret();
        return (key != null && !key.isBlank()) ? key : secretKey;
    }

    public static String getApiUrl() {
        String url = VnPayConfig.getApiUrl();
        return (url != null && !url.isBlank()) ? url : vnp_ApiUrl;
    }

    public static String md5(String message) {
        if (message == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "";
        }
    }

    public static String Sha256(String message) {
        if (message == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            return "";
        }
    }

    // Util for VNPAY
    public static String hashAllFields(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                sb.append(fieldName);
                sb.append("=");
                sb.append(fieldValue);
            }
            if (itr.hasNext()) {
                sb.append("&");
            }
        }
        return hmacSHA512(getSecretKey(), sb.toString());
    }

    public static String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                return "";
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKeySpec = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKeySpec);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    public static String getIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String ipAddress = request.getHeader("X-FORWARDED-FOR");
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isBlank()) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        if ("0:0:0:0:0:0:0:1".equals(ipAddress)) {
            ipAddress = "127.0.0.1";
        }
        return (ipAddress != null && !ipAddress.isBlank()) ? ipAddress : "127.0.0.1";
    }

    public static String getRandomNumber(int len) {
        Random rnd = new Random();
        String chars = "0123456789";
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
