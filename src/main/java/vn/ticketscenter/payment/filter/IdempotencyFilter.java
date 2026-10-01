package vn.ticketscenter.payment.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.payment.service.IdempotencyService;

import java.io.IOException;

/**
 * Filter bảo vệ chống trùng lặp giao dịch (Idempotency) sử dụng Redis.
 * Tự động kích hoạt khi request có header "X-Idempotency-Key" hoặc "Idempotency-Key".
 * Key được bảo lưu trên Redis trong 24 giờ.
 * Nếu key đã tồn tại -> Fallback lập tức với HTTP 409 Conflict hoặc cache cũ.
 * Nếu key chưa tồn tại -> Chuyển tiếp request tới DBS để thực hiện trừ tiền.
 */
@WebFilter(filterName = "IdempotencyFilter", urlPatterns = {"/api/*", "/vnpayajax", "/vnpayajax/*"})
public class IdempotencyFilter implements Filter {

    public static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";
    public static final String HEADER_ALT_KEY = "Idempotency-Key";
    public static final String ATTR_IDEMPOTENCY_KEY = "ticketscenter.idempotencyKey";

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter() {
        this(new IdempotencyService());
    }

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpRequest.getMethod();
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            chain.doFilter(request, response);
            return;
        }

        String idempotencyKey = httpRequest.getHeader(HEADER_IDEMPOTENCY_KEY);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = httpRequest.getHeader(HEADER_ALT_KEY);
        }

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        idempotencyKey = idempotencyKey.trim();

        // Kiểm tra và atomic acquire trên Redis (TTL 24h)
        IdempotencyService.AcquireResult result;
        try {
            result = idempotencyService.tryAcquire(idempotencyKey);
        } catch (Exception e) {
            HttpResponses.error(httpResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "IDEMPOTENCY_SERVICE_UNAVAILABLE", e.getMessage());
            return;
        }

        // Fallback ngay lập tức khi key đã tồn tại
        if (!result.isNew()) {
            if ("COMPLETED".equals(result.status()) && result.cachedResponse() != null) {
                httpResponse.setStatus(HttpServletResponse.SC_OK);
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.setHeader("X-Idempotency-Cache", "HIT");
                httpResponse.getWriter().write(result.cachedResponse());
                return;
            }

            HttpResponses.error(httpResponse, HttpServletResponse.SC_CONFLICT, "DUPLICATE_TRANSACTION",
                    "Giao dịch với Idempotency Key này đang được xử lý hoặc đã hoàn tất. Vui lòng không gửi lại.");
            return;
        }

        // Key chưa tồn tại -> Cho phép đi tiếp tới DBS trừ tiền
        httpRequest.setAttribute(ATTR_IDEMPOTENCY_KEY, idempotencyKey);

        try {
            chain.doFilter(request, response);
            idempotencyService.markSuccess(idempotencyKey, null);
        } catch (Exception e) {
            idempotencyService.markFailed(idempotencyKey, e.getMessage());
            throw e;
        }
    }
}
