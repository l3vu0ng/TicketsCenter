package vn.ticketscenter.e2e;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reusable HTTP test client for executing end-to-end integration tests
 * against the live TicketsCenter Jetty HTTP server.
 */
public class E2ETestClient {

    private static final String DEFAULT_BASE_URL = "http://localhost:8081";
    private final String baseUrl;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private String cachedCsrfToken;

    public record Response(int statusCode, String body, Map<String, List<String>> headers) {
        public boolean is2xx() {
            return statusCode >= 200 && statusCode < 300;
        }

        public String extractString(String fieldName) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        }

        public Long extractLong(String fieldName) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*([0-9]+)");
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
            return null;
        }

        public Boolean extractBoolean(String fieldName) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*(true|false)");
            Matcher matcher = pattern.matcher(body);
            if (matcher.find()) {
                return Boolean.parseBoolean(matcher.group(1));
            }
            return null;
        }
    }

    public E2ETestClient() {
        this(System.getProperty("test.server.url", DEFAULT_BASE_URL));
    }

    public E2ETestClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Response get(String path) throws IOException, InterruptedException {
        return get(path, Map.of());
    }

    public Response get(String path, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizePath(path)))
                .timeout(Duration.ofSeconds(15))
                .GET();

        headers.forEach(builder::header);
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers().map());
    }

    public Response post(String path, String jsonBody) throws IOException, InterruptedException {
        return post(path, jsonBody, true, Map.of());
    }

    public Response post(String path, String jsonBody, boolean includeCsrf) throws IOException, InterruptedException {
        return post(path, jsonBody, includeCsrf, Map.of());
    }

    public Response post(String path, String jsonBody, boolean includeCsrf, Map<String, String> extraHeaders)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + normalizePath(path)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json");

        if (includeCsrf) {
            String token = getOrFetchCsrfToken();
            if (token != null) {
                builder.header("X-CSRF-Token", token);
            }
        }

        extraHeaders.forEach(builder::header);

        builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body(), response.headers().map());
    }

    public String fetchCsrfToken() throws IOException, InterruptedException {
        Response response = get("/api/auth/csrf");
        if (response.is2xx()) {
            cachedCsrfToken = response.extractString("token");
            return cachedCsrfToken;
        }
        return null;
    }

    public String getOrFetchCsrfToken() throws IOException, InterruptedException {
        if (cachedCsrfToken == null) {
            return fetchCsrfToken();
        }
        return cachedCsrfToken;
    }

    public Response login(String email, String password) throws IOException, InterruptedException {
        fetchCsrfToken();
        String body = String.format("{\"email\":\"%s\",\"password\":\"%s\"}", email, password);
        Response response = post("/api/auth/login", body, true);
        if (response.is2xx()) {
            fetchCsrfToken();
        }
        return response;
    }

    public Response logout() throws IOException, InterruptedException {
        Response response = post("/api/auth/logout", "{}", true);
        cachedCsrfToken = null;
        return response;
    }

    private String normalizePath(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }
}
