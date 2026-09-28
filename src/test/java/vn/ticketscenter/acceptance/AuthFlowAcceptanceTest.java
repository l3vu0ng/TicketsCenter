package vn.ticketscenter.acceptance;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.identity.controller.AuthServlet;
import vn.ticketscenter.identity.controller.OtpServlet;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.filter.CsrfFilter;
import vn.ticketscenter.identity.integration.mail.ConfiguredMailGateway;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.service.AccountService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.SessionService;
import vn.ticketscenter.config.persistence.DatabasePrincipal;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Complete Auth & Security End-to-End Acceptance Test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthFlowAcceptanceTest {

    private static final String TEST_EMAIL = "acceptance.test@ticketscenter.vn";
    private static final String OLD_PASS = "SuperSecurePassword1234!";
    private static final String NEW_PASS = "NewUpdatedPassword5678!";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-27T10:00:00Z"));
    private final ConfiguredMailGateway mailGateway = new ConfiguredMailGateway();
    private final PasswordHasher hasher = new PasswordHasher();
    private final MockDatabase db = new MockDatabase();

    @Test
    @Order(1)
    void completeAuthenticationAndSecurityLifecycle() throws Exception {
        PersistenceRegistry registry = createRegistry(db);
        AuthServlet authServlet = new AuthServlet();
        OtpServlet otpServlet = new OtpServlet();
        CsrfFilter csrfFilter = new CsrfFilter();
        AuthenticationFilter authFilter = new AuthenticationFilter();

        SessionHolder sessionHolder = new SessionHolder();

        // 1. GET /api/auth/csrf -> Lấy CSRF token ban đầu
        HttpResponse csrfRes = executeGet(authServlet, "/api/auth/csrf", sessionHolder, registry);
        assertEquals(200, csrfRes.status());
        String csrfToken = extractJsonField(csrfRes.body(), "token");
        assertNotNull(csrfToken);
        assertFalse(csrfToken.isBlank());

        // 2. POST /api/auth/register không có CSRF -> Bị chặn 403 CSRF_INVALID
        HttpResponse noCsrfRegister = executePostWithFilter(csrfFilter, authServlet, "/api/auth/register",
                "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + OLD_PASS + "\"}", null, sessionHolder, registry);
        assertEquals(403, noCsrfRegister.status());
        assertTrue(noCsrfRegister.body().contains("CSRF_INVALID"));

        // 3. POST /api/auth/register mật khẩu < 12 ký tự -> Bị từ chối 400
        HttpResponse shortPassRegister = executePostWithFilter(csrfFilter, authServlet, "/api/auth/register",
                "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"Short1\"}", csrfToken, sessionHolder, registry);
        assertEquals(400, shortPassRegister.status());
        assertTrue(shortPassRegister.body().contains("VALIDATION_FAILED"));

        // 4. POST /api/auth/register hợp lệ -> Thành công 200
        HttpResponse validRegister = executePostWithFilter(csrfFilter, authServlet, "/api/auth/register",
                "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + OLD_PASS + "\"}", csrfToken, sessionHolder, registry);
        assertEquals(200, validRegister.status());
        assertTrue(validRegister.body().contains("accepted"));

        // 5. POST /api/auth/login với sai mật khẩu -> Thất bại 401 INVALID_CREDENTIALS
        HttpResponse badLogin = executePostWithFilter(csrfFilter, authServlet, "/api/auth/login",
                "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"WrongPassword999\"}", csrfToken, sessionHolder, registry);
        assertEquals(401, badLogin.status());
        assertTrue(badLogin.body().contains("INVALID_CREDENTIALS"));

        // 6. POST /api/auth/login với đúng mật khẩu -> Thành công 200, nạp session
        HttpResponse goodLogin = executePostWithFilter(csrfFilter, authServlet, "/api/auth/login",
                "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + OLD_PASS + "\"}", csrfToken, sessionHolder, registry);
        assertEquals(200, goodLogin.status());
        assertTrue(goodLogin.body().contains("authenticated"));
        assertNotNull(sessionHolder.current().getAttribute(SessionService.USER_ID));
        assertEquals(0, sessionHolder.current().getAttribute(SessionService.AUTH_VERSION));

        // Session fixation: Làm mới CSRF token sau khi login tạo session mới
        HttpResponse postLoginCsrfRes = executeGet(authServlet, "/api/auth/csrf", sessionHolder, registry);
        assertEquals(200, postLoginCsrfRes.status());
        String postLoginCsrfToken = extractJsonField(postLoginCsrfRes.body(), "token");
        assertNotNull(postLoginCsrfToken);

        // 7. GET /api/me qua AuthenticationFilter -> Trả thông tin tài khoản hợp lệ
        HttpResponse meRes = executeGetWithAuthFilter(authFilter, authServlet, "/api/me", sessionHolder, registry);
        assertEquals(200, meRes.status());
        assertTrue(meRes.body().contains(TEST_EMAIL));
        assertTrue(meRes.body().contains("\"admin\":false"));

        // 8. POST /api/auth/otp/send (VERIFY_EMAIL) -> Gửi mã OTP vào mailGateway
        HttpResponse sendOtpRes = executePostWithFilter(csrfFilter, otpServlet, "/api/auth/otp/send",
                "{\"email\":\"" + TEST_EMAIL + "\",\"purpose\":\"VERIFY_EMAIL\"}", postLoginCsrfToken, sessionHolder, registry);
        assertEquals(200, sendOtpRes.status());
        assertEquals(1, mailGateway.getDispatchedMessages().size());
        String emailOtpCode = mailGateway.getDispatchedMessages().getFirst().otpCode();
        assertNotNull(emailOtpCode);

        // 9. POST /api/auth/otp/verify sai mã -> Thất bại 400 OTP_INVALID
        HttpResponse wrongOtpRes = executePostWithFilter(csrfFilter, otpServlet, "/api/auth/otp/verify",
                "{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"000000\",\"purpose\":\"VERIFY_EMAIL\"}", postLoginCsrfToken, sessionHolder, registry);
        assertEquals(400, wrongOtpRes.status());
        assertTrue(wrongOtpRes.body().contains("OTP_INVALID"));

        // 10. POST /api/auth/otp/verify đúng mã -> Thành công 200, cập nhật emailVerifiedAt
        HttpResponse goodOtpRes = executePostWithFilter(csrfFilter, otpServlet, "/api/auth/otp/verify",
                "{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"" + emailOtpCode + "\",\"purpose\":\"VERIFY_EMAIL\"}", postLoginCsrfToken, sessionHolder, registry);
        assertEquals(200, goodOtpRes.status());
        assertTrue(goodOtpRes.body().contains("verified"));
        User userInDb = db.findUser(TEST_EMAIL);
        assertNotNull(userInDb);
        assertNotNull(userInDb.getEmailVerifiedAt());

        // 11. POST /api/auth/password/forgot -> Nhận yêu cầu và phát hành reset OTP
        HttpResponse forgotRes = executePostWithFilter(csrfFilter, otpServlet, "/api/auth/password/forgot",
                "{\"email\":\"" + TEST_EMAIL + "\"}", postLoginCsrfToken, sessionHolder, registry);
        assertEquals(200, forgotRes.status());
        assertTrue(forgotRes.body().contains("accepted"));
        assertEquals(2, mailGateway.getDispatchedMessages().size());
        String resetOtpCode = mailGateway.getDispatchedMessages().getLast().otpCode();

        // 12. POST /api/auth/otp/verify (RESET_PASSWORD) -> Nhận resetToken
        HttpResponse verifyResetOtpRes = executePostWithFilter(csrfFilter, otpServlet, "/api/auth/otp/verify",
                "{\"email\":\"" + TEST_EMAIL + "\",\"code\":\"" + resetOtpCode + "\",\"purpose\":\"RESET_PASSWORD\"}", postLoginCsrfToken, sessionHolder, registry);
        assertEquals(200, verifyResetOtpRes.status());
        String resetToken = extractJsonField(verifyResetOtpRes.body(), "resetToken");
        assertNotNull(resetToken);

        // 13. POST /api/auth/password/reset -> Đặt lại mật khẩu mới, tăng authVersion lên 1
        HttpResponse resetPassRes = executePostWithFilter(csrfFilter, otpServlet, "/api/auth/password/reset",
                "{\"resetToken\":\"" + resetToken + "\",\"newPassword\":\"" + NEW_PASS + "\"}", postLoginCsrfToken, sessionHolder, registry);
        assertEquals(200, resetPassRes.status());
        assertTrue(resetPassRes.body().contains("reset"));
        assertEquals(1, userInDb.getAuthVersion());

        // 14. Phiên cũ (authVersion=0) gọi /api/me -> AuthenticationFilter hủy session và trả 401
        HttpResponse staleSessionRes = executeGetWithAuthFilter(authFilter, authServlet, "/api/me", sessionHolder, registry);
        assertEquals(401, staleSessionRes.status());
        assertTrue(sessionHolder.isInvalidated());

        // 15. Đăng nhập lại bằng mật khẩu cũ -> Thất bại 401
        SessionHolder sessionHolder2 = new SessionHolder();
        HttpResponse csrfRes3 = executeGet(authServlet, "/api/auth/csrf", sessionHolder2, registry);
        String csrfToken3 = extractJsonField(csrfRes3.body(), "token");

        HttpResponse loginOldPass = executePostWithFilter(csrfFilter, authServlet, "/api/auth/login",
                "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + OLD_PASS + "\"}", csrfToken3, sessionHolder2, registry);
        assertEquals(401, loginOldPass.status());

        // 16. Đăng nhập lại bằng mật khẩu mới -> Thành công 200, authVersion=1
        HttpResponse loginNewPass = executePostWithFilter(csrfFilter, authServlet, "/api/auth/login",
                "{\"email\":\"" + TEST_EMAIL + "\",\"password\":\"" + NEW_PASS + "\"}", csrfToken3, sessionHolder2, registry);
        assertEquals(200, loginNewPass.status());
        assertEquals(1, sessionHolder2.current().getAttribute(SessionService.AUTH_VERSION));

        HttpResponse csrfRes4 = executeGet(authServlet, "/api/auth/csrf", sessionHolder2, registry);
        String csrfToken4 = extractJsonField(csrfRes4.body(), "token");

        // 17. POST /api/auth/logout -> Đăng xuất và xóa phiên
        HttpResponse logoutRes = executePostWithFilter(csrfFilter, authServlet, "/api/auth/logout",
                "{}", csrfToken4, sessionHolder2, registry);
        assertEquals(200, logoutRes.status());
        assertTrue(logoutRes.body().contains("loggedOut"));
        assertTrue(sessionHolder2.isInvalidated());

        // 18. GET /api/me sau khi đăng xuất -> Bị chặn 401 AUTHENTICATION_REQUIRED
        HttpResponse afterLogoutMe = executeGetWithAuthFilter(authFilter, authServlet, "/api/me", sessionHolder2, registry);
        assertEquals(401, afterLogoutMe.status());
    }

    private HttpResponse executeGet(AuthServlet servlet, String uri, SessionHolder sessionHolder, PersistenceRegistry registry) throws Exception {
        return executeHttp(servlet, "GET", uri, null, null, sessionHolder, registry);
    }

    private HttpResponse executePostWithFilter(CsrfFilter filter, jakarta.servlet.http.HttpServlet servlet,
                                               String uri, String body, String csrfToken,
                                               SessionHolder sessionHolder, PersistenceRegistry registry) throws Exception {
        final int[] status = new int[]{200};
        final StringWriter writer = new StringWriter();

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                    case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> new PrintWriter(writer);
                    default -> null;
                });

        ServletContext servletContext = createServletContext(registry);

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "POST";
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getContentType" -> "application/json";
                    case "getHeader" -> "X-CSRF-Token".equalsIgnoreCase((String) args[0]) ? csrfToken : null;
                    case "getSession" -> sessionHolder.getSession(args.length == 0 || Boolean.TRUE.equals(args[0]));
                    case "getServletContext" -> servletContext;
                    case "getReader" -> new BufferedReader(new StringReader(body == null ? "" : body));
                    case "getRemoteAddr" -> "127.0.0.1";
                    case "getAttribute" -> null;
                    default -> null;
                });

        filter.doFilter(request, response, (req, res) -> {
            try {
                servlet.service(req, res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return new HttpResponse(status[0], writer.toString());
    }

    private HttpResponse executeGetWithAuthFilter(AuthenticationFilter filter, AuthServlet servlet,
                                                  String uri, SessionHolder sessionHolder,
                                                  PersistenceRegistry registry) throws Exception {
        final int[] status = new int[]{200};
        final StringWriter writer = new StringWriter();

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                    case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> new PrintWriter(writer);
                    default -> null;
                });

        final Map<String, Object> reqAttrs = new HashMap<>();
        ServletContext servletContext = createServletContext(registry);

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMethod" -> "GET";
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getSession" -> sessionHolder.getSession(args.length == 0 || Boolean.TRUE.equals(args[0]));
                    case "getServletContext" -> servletContext;
                    case "getAttribute" -> reqAttrs.get(args[0]);
                    case "setAttribute" -> { reqAttrs.put((String) args[0], args[1]); yield null; }
                    default -> null;
                });

        filter.doFilter(request, response, (req, res) -> {
            try {
                servlet.service(req, res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        return new HttpResponse(status[0], writer.toString());
    }

    private HttpResponse executeHttp(jakarta.servlet.http.HttpServlet servlet, String method,
                                    String uri, String body, String csrfToken,
                                    SessionHolder sessionHolder, PersistenceRegistry registry) throws Exception {
        final int[] status = new int[]{200};
        final StringWriter writer = new StringWriter();

        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, methodObj, args) -> switch (methodObj.getName()) {
                    case "setStatus" -> { status[0] = (int) args[0]; yield null; }
                    case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> new PrintWriter(writer);
                    default -> null;
                });

        ServletContext servletContext = createServletContext(registry);

        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, methodObj, args) -> switch (methodObj.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getContentType" -> "application/json";
                    case "getHeader" -> "X-CSRF-Token".equalsIgnoreCase((String) args[0]) ? csrfToken : null;
                    case "getSession" -> sessionHolder.getSession(args.length == 0 || Boolean.TRUE.equals(args[0]));
                    case "getServletContext" -> servletContext;
                    case "getReader" -> new BufferedReader(new StringReader(body == null ? "" : body));
                    case "getRemoteAddr" -> "127.0.0.1";
                    case "getAttribute" -> null;
                    default -> null;
                });

        servlet.service(request, response);
        return new HttpResponse(status[0], writer.toString());
    }

    private ServletContext createServletContext(PersistenceRegistry registry) {
        return (ServletContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> {
                        if (PersistenceListener.REGISTRY_ATTRIBUTE.equals(args[0])) yield registry;
                        if (OtpServlet.MAIL_GATEWAY_ATTRIBUTE.equals(args[0])) yield mailGateway;
                        if (OtpServlet.CLOCK_ATTRIBUTE.equals(args[0])) yield clock;
                        yield null;
                    }
                    default -> null;
                });
    }

    private PersistenceRegistry createRegistry(MockDatabase database) throws Exception {
        PersistenceRegistry registry = new PersistenceRegistry();
        Field field = PersistenceRegistry.class.getDeclaredField("factories");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<DatabasePrincipal, jakarta.persistence.EntityManagerFactory> map =
                (Map<DatabasePrincipal, jakarta.persistence.EntityManagerFactory>) field.get(registry);
        jakarta.persistence.EntityManagerFactory factory = (jakarta.persistence.EntityManagerFactory) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManagerFactory.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createEntityManager" -> database.mockEntityManager();
                    default -> null;
                });
        map.put(DatabasePrincipal.AUTH, factory);
        return registry;
    }

    private String extractJsonField(String json, String key) {
        String search = "\"" + key + "\":\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int start = idx + search.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private record HttpResponse(int status, String body) {}

    private static final class SessionHolder {
        private MockSession current = new MockSession();

        HttpSession getSession(boolean create) {
            if (current != null && current.isInvalidated()) {
                current = null;
            }
            if (current == null && create) {
                current = new MockSession();
            }
            return current;
        }

        MockSession current() {
            return current;
        }

        boolean isInvalidated() {
            return current == null || current.isInvalidated();
        }
    }

    private static final class MockSession implements HttpSession {
        private final Map<String, Object> attributes = new HashMap<>();
        private boolean invalidated = false;

        boolean isInvalidated() { return invalidated; }

        @Override public Object getAttribute(String s) { return attributes.get(s); }
        @Override public void setAttribute(String s, Object o) { attributes.put(s, o); }
        @Override public void removeAttribute(String s) { attributes.remove(s); }
        @Override public void invalidate() { invalidated = true; attributes.clear(); }

        @Override public long getCreationTime() { return 0; }
        @Override public String getId() { return "mock-session-id"; }
        @Override public long getLastAccessedTime() { return 0; }
        @Override public ServletContext getServletContext() { return null; }
        @Override public void setMaxInactiveInterval(int i) {}
        @Override public int getMaxInactiveInterval() { return 0; }
        @Override public Enumeration<String> getAttributeNames() { return Collections.enumeration(attributes.keySet()); }
        @Override public boolean isNew() { return false; }
    }

    private static final class MutableClock extends Clock {
        private Instant current;
        MutableClock(Instant initial) { this.current = initial; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }

    private static final class MockDatabase {
        final Map<UUID, User> users = new HashMap<>();
        final List<Otp> otps = new ArrayList<>();

        User findUser(String email) {
            return users.values().stream()
                    .filter(u -> u.getNormalizedEmail().equalsIgnoreCase(email))
                    .findFirst()
                    .orElse(null);
        }

        @SuppressWarnings("unchecked")
        jakarta.persistence.EntityManager mockEntityManager() {
            var transaction = (jakarta.persistence.EntityTransaction) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityTransaction.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "isActive" -> false;
                        default -> null;
                    });

            return (jakarta.persistence.EntityManager) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.EntityManager.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTransaction" -> transaction;
                        case "flush", "close" -> null;
                        case "find" -> {
                            if (args[0] == User.class) yield users.get(args[1]);
                            yield null;
                        }
                        case "persist" -> {
                            if (args[0] instanceof Otp o) {
                                otps.add(o);
                            } else if (args[0] instanceof User u) {
                                UUID id = u.getId();
                                if (id == null) {
                                    try {
                                        Field idF = User.class.getDeclaredField("id");
                                        idF.setAccessible(true);
                                        id = UUID.randomUUID();
                                        idF.set(u, id);
                                    } catch (Exception ignored) {}
                                }
                                users.put(id, u);
                            }
                            yield null;
                        }
                        case "createQuery" -> createQueryMock((String) args[0]);
                        default -> null;
                    });
        }

        @SuppressWarnings("unchecked")
        private Object createQueryMock(String ql) {
            final Map<String, Object> params = new HashMap<>();
            return Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{jakarta.persistence.TypedQuery.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "setParameter" -> {
                            params.put((String) args[0], args[1]);
                            yield proxy;
                        }
                        case "setLockMode", "setMaxResults" -> proxy;
                        case "executeUpdate" -> {
                            if (ql.startsWith("update Otp")) {
                                otps.forEach(otp -> {
                                    if (otp.getConsumedAt() == null && otp.getInvalidatedAt() == null) {
                                        otp.invalidate(Instant.now());
                                    }
                                });
                            }
                            yield 1;
                        }
                        case "getResultStream" -> {
                            if (ql.contains("User user where user.normalizedEmail = :email")) {
                                String email = (String) params.get("email");
                                yield users.values().stream()
                                        .filter(u -> u.getNormalizedEmail().equalsIgnoreCase(email));
                            }
                            if (ql.contains("from Otp o where o.emailNormalized = :email and o.purpose = :purpose")) {
                                String email = (String) params.get("email");
                                var purpose = params.get("purpose");
                                yield otps.stream()
                                        .filter(o -> o.getEmailNormalized().equalsIgnoreCase(email) && o.getPurpose() == purpose)
                                        .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                            }
                            yield java.util.stream.Stream.empty();
                        }
                        default -> null;
                    });
        }
    }
}
