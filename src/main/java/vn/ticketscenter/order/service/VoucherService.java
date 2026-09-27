package vn.ticketscenter.order.service;

import vn.ticketscenter.order.model.OrderEnums;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.order.dto.VoucherDtos.VoucherValidationRequest;
import vn.ticketscenter.order.dto.VoucherDtos.VoucherValidationResult;
import vn.ticketscenter.order.model.Coupon;
import vn.ticketscenter.order.repository.CouponRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Xử lý nghiệp vụ mã khuyến mãi (Voucher / Coupon) kết nối với đơn hàng và phân quyền.
 */
public class VoucherService {

    private final TransactionManager transactions;
    private final CouponRepository coupons;

    public VoucherService(TransactionManager transactions, CouponRepository coupons) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.coupons = Objects.requireNonNull(coupons, "coupons must not be null");
    }

    /**
     * Xác thực và tính toán giá trị giảm giá của Voucher đối với đơn hàng.
     */
    public VoucherValidationResult validateAndCalculate(VoucherValidationRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            return new VoucherValidationResult(false, "", BigDecimal.ZERO, BigDecimal.ZERO, "Mã voucher không được để trống");
        }

        BigDecimal orderAmount = request.orderAmount() != null ? request.orderAmount() : BigDecimal.ZERO;
        if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return new VoucherValidationResult(false, request.code(), BigDecimal.ZERO, BigDecimal.ZERO, "Giá trị đơn hàng không hợp lệ");
        }

        return transactions.execute(DatabasePrincipal.BUYER, em -> {
            Optional<Coupon> opt = coupons.findByCode(em, request.code());
            if (opt.isEmpty()) {
                return new VoucherValidationResult(false, request.code(), BigDecimal.ZERO, orderAmount, "Mã voucher không tồn tại");
            }

            Coupon coupon = opt.get();
            if (!coupon.isActive()) {
                return new VoucherValidationResult(false, request.code(), BigDecimal.ZERO, orderAmount, "Mã voucher đã bị vô hiệu hóa");
            }

            Instant now = Instant.now();
            if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
                return new VoucherValidationResult(false, request.code(), BigDecimal.ZERO, orderAmount, "Mã voucher chưa đến thời gian áp dụng");
            }
            if (coupon.getValidTo() != null && !now.isBefore(coupon.getValidTo())) {
                return new VoucherValidationResult(false, request.code(), BigDecimal.ZERO, orderAmount, "Mã voucher đã hết hạn");
            }

            // Kiểm tra ràng buộc tổ chức
            if (coupon.getOrganization() != null && request.organizationId() != null) {
                if (!coupon.getOrganization().getId().equals(request.organizationId())) {
                    return new VoucherValidationResult(false, request.code(), BigDecimal.ZERO, orderAmount, "Voucher không áp dụng cho tổ chức này");
                }
            }

            // Tính số tiền được giảm
            BigDecimal discount = BigDecimal.ZERO;
            if (coupon.getDiscountType() == OrderEnums.DiscountType.PERCENTAGE) {
                BigDecimal pct = coupon.getPercentageValue() != null ? coupon.getPercentageValue() : BigDecimal.ZERO;
                discount = orderAmount.multiply(pct)
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
                if (coupon.getMaxDiscountAmount() != null && discount.compareTo(coupon.getMaxDiscountAmount()) > 0) {
                    discount = coupon.getMaxDiscountAmount();
                }
            } else if (coupon.getDiscountType() == OrderEnums.DiscountType.FIXED_AMOUNT) {
                discount = coupon.getFixedAmount() != null ? coupon.getFixedAmount() : BigDecimal.ZERO;
            }

            if (discount.compareTo(orderAmount) > 0) {
                discount = orderAmount;
            }

            BigDecimal finalAmount = orderAmount.subtract(discount);
            return new VoucherValidationResult(true, coupon.getCode(), discount, finalAmount, "Áp dụng voucher thành công");
        });
    }
}
