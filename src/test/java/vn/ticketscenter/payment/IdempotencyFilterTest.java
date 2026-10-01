package vn.ticketscenter.payment;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.filter.IdempotencyFilter;
import vn.ticketscenter.payment.service.IdempotencyService;
import vn.ticketscenter.payment.service.IdempotencyService.AcquireResult;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("IdempotencyFilter Unit Tests")
class IdempotencyFilterTest {

    static class StubIdempotencyService extends IdempotencyService {
        private final Map<String, String> store = new HashMap<>();
        boolean markSuccessCalled = false;
        boolean markFailedCalled = false;

        public StubIdempotencyService() {
            super();
        }

        @Override
        public AcquireResult tryAcquire(String idempotencyKey) {
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("Idempotency key không được để trống");
            }
            if (store.containsKey(idempotencyKey)) {
                return AcquireResult.duplicate(store.get(idempotencyKey));
            }
            store.put(idempotencyKey, "PROCESSING");
            return AcquireResult.acquired();
        }

        @Override
        public void markSuccess(String idempotencyKey, String responsePayload) {
            markSuccessCalled = true;
            store.put(idempotencyKey, "COMPLETED:" + (responsePayload != null ? responsePayload : ""));
        }

        @Override
        public void markFailed(String idempotencyKey, String errorMessage) {
            markFailedCalled = true;
            store.put(idempotencyKey, "FAILED:" + (errorMessage != null ? errorMessage : ""));
        }
    }

    @Test
    @DisplayName("Request GET bỏ qua kiểm tra Idempotency")
    void getRequestBypassesIdempotency() throws ServletException, IOException {
        StubIdempotencyService service = new StubIdempotencyService();
        IdempotencyFilter filter = new IdempotencyFilter(service);

        Map<String, Object> reqState = new HashMap<>();
        reqState.put("method", "GET");
        reqState.put("header:X-Idempotency-Key", "test-key-1");

        HttpServletRequest req = createMockRequest(reqState);
        HttpServletResponse resp = createMockResponse(new StringWriter(), new int[]{200});

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertTrue(chainCalled.get(), "Chain phải được gọi với request GET");
        assertFalse(service.markSuccessCalled, "markSuccess không được gọi khi bypass");
    }

    @Test
    @DisplayName("Request POST với Key mới: đi tới DBS và đánh dấu thành công")
    void postWithNewKeyProceedsToDatabase() throws ServletException, IOException {
        StubIdempotencyService service = new StubIdempotencyService();
        IdempotencyFilter filter = new IdempotencyFilter(service);

        Map<String, Object> reqState = new HashMap<>();
        reqState.put("method", "POST");
        reqState.put("header:X-Idempotency-Key", "order-txn-12345");

        HttpServletRequest req = createMockRequest(reqState);
        HttpServletResponse resp = createMockResponse(new StringWriter(), new int[]{200});

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> {
            chainCalled.set(true);
            assertEquals("order-txn-12345", req.getAttribute(IdempotencyFilter.ATTR_IDEMPOTENCY_KEY));
        };

        filter.doFilter(req, resp, chain);

        assertTrue(chainCalled.get(), "Request phải được chuyển tiếp tới DBS");
        assertTrue(service.markSuccessCalled, "Phải đánh dấu markSuccess khi request hoàn thành");
    }

    @Test
    @DisplayName("Request POST với Key trùng lặp: fallback ngay lập tức với HTTP 409 Conflict, không tới DBS")
    void duplicateKeyFallsBackImmediatelyWithConflict() throws ServletException, IOException {
        StubIdempotencyService service = new StubIdempotencyService();
        service.tryAcquire("order-txn-duplicate"); // Giả lập key đã tồn tại từ request trước

        IdempotencyFilter filter = new IdempotencyFilter(service);

        Map<String, Object> reqState = new HashMap<>();
        reqState.put("method", "POST");
        reqState.put("header:X-Idempotency-Key", "order-txn-duplicate");

        StringWriter outputWriter = new StringWriter();
        int[] statusHolder = new int[]{200};
        HttpServletRequest req = createMockRequest(reqState);
        HttpServletResponse resp = createMockResponse(outputWriter, statusHolder);

        AtomicBoolean chainCalled = new AtomicBoolean(false);
        FilterChain chain = (r, s) -> chainCalled.set(true);

        filter.doFilter(req, resp, chain);

        assertFalse(chainCalled.get(), "KHÔNG ĐƯỢC gọi chain xuống DBS khi key đã tồn tại");
        assertEquals(409, statusHolder[0], "Phải trả về HTTP 409 Conflict");
        assertTrue(outputWriter.toString().contains("DUPLICATE_TRANSACTION"), "Response phải chứa mã lỗi DUPLICATE_TRANSACTION");
    }

    @Test
    @DisplayName("Nếu DBS xử lý ném lỗi thì markFailed được gọi")
    void whenDatabaseFailsMarkFailedIsInvoked() {
        StubIdempotencyService service = new StubIdempotencyService();
        IdempotencyFilter filter = new IdempotencyFilter(service);

        Map<String, Object> reqState = new HashMap<>();
        reqState.put("method", "POST");
        reqState.put("header:X-Idempotency-Key", "order-txn-failed");

        HttpServletRequest req = createMockRequest(reqState);
        HttpServletResponse resp = createMockResponse(new StringWriter(), new int[]{200});

        FilterChain failingChain = (r, s) -> {
            throw new RuntimeException("Database timeout / balance deduction failed");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(req, resp, failingChain));
        assertTrue(service.markFailedCalled, "markFailed phải được gọi khi xảy ra ngoại lệ");
    }

    private HttpServletRequest createMockRequest(Map<String, Object> state) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMethod".equals(name)) {
                        return state.getOrDefault("method", "POST");
                    }
                    if ("getHeader".equals(name) && args.length == 1) {
                        return state.get("header:" + args[0]);
                    }
                    if ("setAttribute".equals(name) && args.length == 2) {
                        state.put("attr:" + args[0], args[1]);
                        return null;
                    }
                    if ("getAttribute".equals(name) && args.length == 1) {
                        return state.get("attr:" + args[0]);
                    }
                    return null;
                }
        );
    }

    private HttpServletResponse createMockResponse(StringWriter writer, int[] statusHolder) {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setStatus".equals(name) && args.length == 1) {
                        statusHolder[0] = (int) args[0];
                        return null;
                    }
                    if ("getWriter".equals(name)) {
                        return new PrintWriter(writer);
                    }
                    return null;
                }
        );
    }
}
