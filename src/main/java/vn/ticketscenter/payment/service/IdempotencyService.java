package vn.ticketscenter.payment.service;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.config.redis.RedisClientProvider;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quản lý Idempotency Key (khóa chống trùng lặp giao dịch) lưu trữ trên Redis với TTL 24 giờ.
 * Đảm bảo tính nguyên tử (atomic) bằng lệnh SET ... NX EX.
 */
public class IdempotencyService {

    private static final Logger LOGGER = Logger.getLogger(IdempotencyService.class.getName());
    public static final long TTL_24_HOURS_SECONDS = 24 * 60 * 60L; // 86,400 giây (24h)
    public static final String KEY_PREFIX = "idempotency:txn:";

    private final RedisClientProvider redisProvider;

    public IdempotencyService() {
        this(RedisClientProvider.getInstance());
    }

    public IdempotencyService(RedisClientProvider redisProvider) {
        this.redisProvider = Objects.requireNonNull(redisProvider, "redisProvider cannot be null");
    }

    public record AcquireResult(boolean isNew, String status, String cachedResponse) {
        public static AcquireResult acquired() {
            return new AcquireResult(true, "PROCESSING", null);
        }

        public static AcquireResult duplicate(String cachedValue) {
            String status = "PROCESSING";
            String response = null;
            if (cachedValue != null) {
                if (cachedValue.startsWith("COMPLETED:")) {
                    status = "COMPLETED";
                    response = cachedValue.substring("COMPLETED:".length());
                } else if (cachedValue.startsWith("FAILED:")) {
                    status = "FAILED";
                    response = cachedValue.substring("FAILED:".length());
                } else {
                    status = cachedValue;
                }
            }
            return new AcquireResult(false, status, response);
        }
    }

    /**
     * Kiểm tra và giữ khóa Idempotency Key trong 24 giờ một cách nguyên tử (Atomic).
     *
     * @param idempotencyKey khóa duy nhất gửi từ client
     * @return AcquireResult: isNew = true nếu khóa chưa từng có (cho phép đi tới DBS),
     *                        isNew = false nếu khóa đã có (cần fallback ngay lập tức)
     */
    public AcquireResult tryAcquire(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key không được để trống");
        }

        String fullKey = KEY_PREFIX + idempotencyKey.trim();
        try (Jedis jedis = redisProvider.getResource()) {
            SetParams params = SetParams.setParams().nx().ex(TTL_24_HOURS_SECONDS);
            String result = jedis.set(fullKey, "PROCESSING", params);

            if ("OK".equalsIgnoreCase(result)) {
                return AcquireResult.acquired();
            }

            // Key đã tồn tại -> lấy trạng thái hiện tại để fallback chính xác
            String existingValue = jedis.get(fullKey);
            return AcquireResult.duplicate(existingValue);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Lỗi kết nối Redis khi kiểm tra Idempotency Key: " + idempotencyKey, e);
            throw new IllegalStateException("Hệ thống kiểm tra chống trùng lặp tạm thời không khả dụng: " + e.getMessage(), e);
        }
    }

    /**
     * Đánh dấu giao dịch đã hoàn thành và lưu kết quả phản hồi vào Redis với TTL 24h.
     */
    public void markSuccess(String idempotencyKey, String responsePayload) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        String fullKey = KEY_PREFIX + idempotencyKey.trim();
        try (Jedis jedis = redisProvider.getResource()) {
            String value = "COMPLETED:" + (responsePayload != null ? responsePayload : "");
            jedis.setex(fullKey, TTL_24_HOURS_SECONDS, value);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể cập nhật trạng thái COMPLETED cho Idempotency Key: " + idempotencyKey, e);
        }
    }

    /**
     * Đánh dấu giao dịch thất bại.
     */
    public void markFailed(String idempotencyKey, String errorMessage) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        String fullKey = KEY_PREFIX + idempotencyKey.trim();
        try (Jedis jedis = redisProvider.getResource()) {
            String value = "FAILED:" + (errorMessage != null ? errorMessage : "");
            jedis.setex(fullKey, 300L, value); // Lưu 5 phút để tránh retry spam liên tục
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể cập nhật trạng thái FAILED cho Idempotency Key: " + idempotencyKey, e);
        }
    }

    /**
     * Xóa key khỏi Redis (cho phép client gửi lại khi cần rollback).
     */
    public void release(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return;
        String fullKey = KEY_PREFIX + idempotencyKey.trim();
        try (Jedis jedis = redisProvider.getResource()) {
            jedis.del(fullKey);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể xóa Idempotency Key: " + idempotencyKey, e);
        }
    }
}
