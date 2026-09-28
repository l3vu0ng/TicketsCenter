package vn.ticketscenter.order.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.order.dto.VoucherDtos.VoucherValidationRequest;
import vn.ticketscenter.order.dto.VoucherDtos.VoucherValidationResult;
import vn.ticketscenter.order.repository.CouponRepository;
import vn.ticketscenter.order.service.VoucherService;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@WebServlet(name = "VoucherServlet", urlPatterns = {"/api/vouchers", "/api/vouchers/*"})
public class VoucherServlet extends HttpServlet {

    private final VoucherService voucherService;

    public VoucherServlet() {
        this.voucherService = null;
    }

    public VoucherServlet(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String pathInfo = req.getPathInfo();
        if ("/validate".equals(pathInfo)) {
            handleValidate(req, resp);
            return;
        }
        HttpResponses.error(resp, 404, "NOT_FOUND", "Endpoint không tồn tại");
    }

    private void handleValidate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        VoucherService service = resolveService(req, resp);
        if (service == null) return;

        Map<String, String> body = JsonObjectParser.parse(req.getReader());
        String code = body.get("code");
        String amountStr = body.get("orderAmount");
        String orgIdStr = body.get("organizationId");

        if (code == null || code.isBlank() || amountStr == null || amountStr.isBlank()) {
            HttpResponses.error(resp, 400, "VALIDATION_FAILED", "Mã code và orderAmount là bắt buộc");
            return;
        }

        BigDecimal orderAmount;
        try {
            orderAmount = new BigDecimal(amountStr.trim());
        } catch (NumberFormatException e) {
            HttpResponses.error(resp, 400, "VALIDATION_FAILED", "orderAmount không hợp lệ");
            return;
        }

        UUID orgId = null;
        if (orgIdStr != null && !orgIdStr.isBlank()) {
            try {
                orgId = UUID.fromString(orgIdStr.trim());
            } catch (IllegalArgumentException ignored) {}
        }

        VoucherValidationResult result = service.validateAndCalculate(
                new VoucherValidationRequest(code, orderAmount, orgId)
        );

        if (!result.valid()) {
            HttpResponses.error(resp, 400, "VOUCHER_INVALID", result.message());
            return;
        }

        String json = "{\"code\":" + HttpResponses.jsonString(result.code())
                + ",\"discountAmount\":" + HttpResponses.jsonString(result.discountAmount().toPlainString())
                + ",\"finalAmount\":" + HttpResponses.jsonString(result.finalAmount().toPlainString())
                + ",\"message\":" + HttpResponses.jsonString(result.message()) + "}";
        HttpResponses.data(resp, json);
    }

    private VoucherService resolveService(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (this.voucherService != null) {
            return this.voucherService;
        }
        Object configured = req.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
        if (!(configured instanceof PersistenceRegistry registry)) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DEPENDENCY_UNAVAILABLE", "Voucher service is temporarily unavailable");
            return null;
        }
        return new VoucherService(registry.transactionManager(), new CouponRepository());
    }
}
