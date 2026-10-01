package vn.ticketscenter.settlement.controller;

import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.filter.AuthenticationFilter;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;
import vn.ticketscenter.settlement.dto.SettlementDtos.PayoutBalance;
import vn.ticketscenter.settlement.dto.SettlementDtos.SettlementBlocker;
import vn.ticketscenter.settlement.dto.SettlementDtos.SettlementSnapshot;
import vn.ticketscenter.settlement.integration.PayoutGateway;
import vn.ticketscenter.settlement.integration.SimulatedPayoutGateway;
import vn.ticketscenter.settlement.repository.SettlementRepository;
import vn.ticketscenter.settlement.service.SettlementService;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@WebServlet(name = "SettlementServlet", urlPatterns = {"/api/admin/events/*", "/api/admin/settlements/*"})
public final class SettlementServlet extends HttpServlet {
    private final SettlementService service;

    public SettlementServlet() {
        service = null;
    }

    public SettlementServlet(SettlementService service) {
        this.service = service;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handlePost(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handleGet(request, response);
    }

    public void handlePost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, true);
    }

    public void handleGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, false);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, boolean mutation) throws IOException {
        AuthenticatedAccount account = account(request, response);
        if (account == null) return;
        SettlementService settlementService = resolveService(request, response);
        if (settlementService == null) return;
        try {
            String[] parts = parts(request);
            if (mutation && parts.length == 3 && "settlement".equals(parts[1])
                    && "recalculate".equals(parts[2])) {
                HttpResponses.data(response, json(settlementService.recalculate(account, uuid(parts[0]))));
                return;
            }
            if (mutation && parts.length == 2 && "confirm".equals(parts[1])) {
                HttpResponses.data(response, json(settlementService.confirm(account, uuid(parts[0]))));
                return;
            }
            if (mutation && parts.length == 2 && "payouts".equals(parts[1])) {
                Map<String, String> body = JsonObjectParser.parse(request.getReader());
                HttpResponses.data(response, json(settlementService.payout(account, uuid(parts[0]),
                        uuid(require(body, "payoutId")), amount(require(body, "amount")))));
                return;
            }
            if (!mutation && parts.length == 2 && "settlement-blockers".equals(parts[1])) {
                HttpResponses.data(response, blockers(settlementService.blockers(account, uuid(parts[0]))));
                return;
            }
            if (!mutation && parts.length == 2 && "payouts".equals(parts[1])) {
                HttpResponses.data(response, json(settlementService.balance(account, uuid(parts[0]))));
                return;
            }
            HttpResponses.error(response, 404, "NOT_FOUND", "Endpoint đối soát không tồn tại");
        } catch (SecurityException exception) {
            HttpResponses.error(response, 403, "FORBIDDEN", "Quyền Quản trị viên (ADMIN) là bắt buộc");
        } catch (NoSuchElementException exception) {
            HttpResponses.error(response, 404, "NOT_FOUND", exception.getMessage());
        } catch (IllegalArgumentException exception) {
            HttpResponses.error(response, 400, "VALIDATION_FAILED", exception.getMessage());
        } catch (SimulatedPayoutGateway.Timeout exception) {
            HttpResponses.error(response, 503, "PAYOUT_RESULT_UNKNOWN", "Kết quả chi trả cần được đối chiếu bằng payoutId");
        } catch (IllegalStateException | PersistenceException exception) {
            HttpResponses.error(response, 409, "CONFLICT", "Không thể xử lý đối soát hoặc chi trả");
        }
    }

    private SettlementService resolveService(HttpServletRequest request,
                                             HttpServletResponse response) throws IOException {
        if (service != null) return service;
        Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (configured instanceof PersistenceRegistry registry) {
            PayoutGateway gateway = new SimulatedPayoutGateway(
                    Path.of(AppConfig.get(AppConfig.Keys.PAYOUT_STATE_DIRECTORY, "data/payout-gateway")),
                    PayoutGateway.Status.valueOf(AppConfig.get(
                            AppConfig.Keys.PAYOUT_SIMULATED_OUTCOME, "SUCCEEDED").toUpperCase()),
                    Boolean.parseBoolean(AppConfig.get(
                            AppConfig.Keys.PAYOUT_TIMEOUT_AFTER_SIDE_EFFECT, "false")));
            return new SettlementService(registry.transactionManager(), new SettlementRepository(), gateway);
        }
        HttpResponses.error(response, 503, "DEPENDENCY_UNAVAILABLE",
                "Settlement service is temporarily unavailable");
        return null;
    }

    private static AuthenticatedAccount account(HttpServletRequest request,
                                                HttpServletResponse response) throws IOException {
        Object value = request.getAttribute(AuthenticationFilter.ACCOUNT_ATTRIBUTE);
        if (value instanceof AuthenticatedAccount account) return account;
        HttpResponses.error(response, 401, "UNAUTHORIZED", "Yêu cầu đăng nhập để truy cập");
        return null;
    }

    private static String[] parts(HttpServletRequest request) {
        String[] values = Arrays.stream(request.getPathInfo() == null ? new String[0]
                        : request.getPathInfo().split("/"))
                .filter(part -> !part.isBlank()).toArray(String[]::new);
        if (values.length > 0 && ("events".equals(values[0]) || "settlements".equals(values[0]))) {
            return Arrays.copyOfRange(values, 1, values.length);
        }
        return values;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("id không hợp lệ");
        }
    }

    private static BigDecimal amount(String value) {
        try {
            return new BigDecimal(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("amount không hợp lệ");
        }
    }

    private static String require(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " là bắt buộc");
        return value;
    }

    private static String json(SettlementSnapshot value) {
        return "{\"settlementId\":" + nullable(value.settlementId())
                + ",\"status\":" + string(value.status())
                + ",\"grossRevenue\":" + money(value.grossRevenue())
                + ",\"totalRefund\":" + money(value.totalRefund())
                + ",\"totalCommission\":" + money(value.totalCommission())
                + ",\"netPayable\":" + money(value.netPayable()) + "}";
    }

    private static String json(PayoutBalance value) {
        return "{\"settlementId\":" + string(value.settlementId().toString())
                + ",\"eventId\":" + string(value.eventId().toString())
                + ",\"status\":" + string(value.status())
                + ",\"grossRevenue\":" + money(value.grossRevenue())
                + ",\"totalRefund\":" + money(value.totalRefund())
                + ",\"totalCommission\":" + money(value.totalCommission())
                + ",\"netPayable\":" + money(value.netPayable())
                + ",\"paidAmount\":" + money(value.paidAmount())
                + ",\"pendingAmount\":" + money(value.pendingAmount())
                + ",\"remainingAmount\":" + money(value.remainingAmount())
                + ",\"availableAmount\":" + money(value.availableAmount()) + "}";
    }

    private static String blockers(List<SettlementBlocker> values) {
        return "{\"blockers\":[" + values.stream().map(value -> "{\"type\":" + string(value.type())
                + ",\"blockerId\":" + string(value.blockerId().toString())
                + ",\"relatedId\":" + nullable(value.relatedId()) + "}")
                .reduce((left, right) -> left + "," + right).orElse("") + "]}";
    }

    private static String money(BigDecimal value) {
        return string(value.toPlainString());
    }

    private static String nullable(UUID value) {
        return value == null ? "null" : string(value.toString());
    }

    private static String string(String value) {
        return HttpResponses.jsonString(value);
    }
}
