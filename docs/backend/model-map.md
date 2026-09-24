# Bảng ánh xạ 23 Lớp Nghiệp Vụ và Cơ chế Kỹ Thuật (Model Map)

> Ngày cập nhật: 2026-09-24  
> Nguồn đặc tả: [SPEC](../../references/SPEC.md) §5, §14; [CONVENTIONS](../tasks/CONVENTIONS.md)

---

## 1. Bảng 23 Lớp Nghiệp Vụ Chuẩn

Theo đặc tả SPEC §5, hệ thống gồm đúng **23 lớp nghiệp vụ** (22 lớp cốt lõi + 1 lớp nghiệp vụ bổ sung `OrganizationRequest`):

| STT | Tên Lớp (Model) | Bảng SQL Tương Ứng | Khối Nghiệp Vụ | Thuộc Tính & Hành Vi Chính | Ngày Triển Khai |
|:---:|---|---|---|---|:---:|
| 1 | `User` | `tc_users` | Danh tính | `id`, `email`, `fullName`, `phone`, `passwordHash`, `status`, `authVersion`, `emailVerifiedAt` | Day 04 |
| 2 | `OrganizationRequest` | `tc_organization_requests` | Tổ chức | `id`, `requesterId`, `name`, `contactEmail`, `contactPhone`, `status`, `reviewerId`, `rejectionReason` | Day 06 |
| 3 | `Category` | `tc_categories` | Sự kiện | `id`, `name`, `description`, `iconUrl`, `isActive` | Day 07 |
| 4 | `Event` | `tc_events` | Sự kiện | `id`, `organizationId`, `categoryId`, `title`, `description`, `coverImageUrl`, `status`, `saleStart`, `saleEnd`, `startTime`, `endTime`, `venueName`, `venueAddress` | Day 07 |
| 5 | `SeatingZone` | `tc_seating_zones` | Địa điểm & Chỗ | `id`, `eventId`, `name`, `type` (`SEATED`/`STANDING`), `capacity`, `price` | Day 07 |
| 6 | `ScheduleZone` | `tc_schedule_zones` | Lịch trình & Phân bổ | `id`, `eventId`, `zoneId`, `allocatedCapacity`, `soldCount` | Day 07 |
| 7 | `SeatingSeat` | `tc_seating_seats` | Ghế mẫu | `id`, `zoneId`, `rowName`, `seatNumber`, `label`, `isAccessible` | Day 07 |
| 8 | `SessionSeat` | `tc_session_seats` | Ghế phiên | `id`, `scheduleZoneId`, `seatId`, `status` (`AVAILABLE`, `HELD`, `SOLD`), `price`, `version` (optimistic lock) | Day 07 |
| 9 | `TicketHold` | `tc_ticket_holds` | Giữ vé | `id`, `userId`, `eventId`, `status` (`ACTIVE`, `RELEASED`, `EXPIRED`, `CONVERTED`), `expiresAt` (10 phút) | Day 08 |
| 10 | `HoldItem` | `tc_hold_items` | Chi tiết giữ vé | `id`, `holdId`, `zoneId`, `sessionSeatId` (NULL nếu vé đứng), `quantity`, `unitPrice` | Day 08 |
| 11 | `Order` | `tc_orders` | Đơn hàng | `id`, `orderCode`, `userId`, `eventId`, `holdId`, `totalAmount`, `discountAmount`, `finalAmount`, `status` (`PENDING`, `PAID`, `CANCELLED`, `REFUNDED`), `expiresAt` | Day 09 |
| 12 | `OrderItem` | `tc_order_items` | Chi tiết đơn | `id`, `orderId`, `zoneId`, `sessionSeatId`, `quantity`, `unitPrice`, `subtotal` | Day 09 |
| 13 | `Coupon` | `tc_coupons` | Khuyến mãi | `id`, `code`, `discountType` (`PERCENTAGE`, `FIXED_AMOUNT`), `discountValue`, `maxDiscountAmount`, `maxUses`, `currentUses`, `validFrom`, `validTo`, `status` | Day 09 |
| 14 | `Payment` | `tc_payments` | Thanh toán | `id`, `orderId`, `gateway` (`VNPAY`), `txnRef`, `amount`, `status` (`PENDING`, `CAPTURED`, `FAILED`, `UNKNOWN`), `gatewayTxnNo`, `paidAt` | Day 10 |
| 15 | `Ticket` | `tc_tickets` | Phát hành vé | `id`, `ticketCode`, `orderItemId`, `userId`, `status` (`ISSUED`, `USED`, `CANCELLED`, `REFUNDED`), `qrCodeHash`, `issuedAt` | Day 11 |
| 16 | `CheckIn` | `tc_check_ins` | Soát vé | `id`, `ticketId`, `scannerId`, `scannedAt`, `result` (`SUCCESS`, `ALREADY_USED`, `INVALID_EVENT`, `CANCELLED`), `notes` | Day 13 |
| 17 | `RefundRequest` | `tc_refund_requests` | Yêu cầu hoàn | `id`, `orderId`, `requesterId`, `reasonType` (`BUYER_REQUEST`, `EVENT_CANCELLED`), `reason`, `status` (`PENDING`, `APPROVED`, `REJECTED`), `refundAmount` | Day 14 |
| 18 | `Refund` | `tc_refunds` | Xử lý hoàn tiền | `id`, `refundRequestId`, `paymentId`, `amount`, `status` (`SUCCESS`, `FAILED`, `PENDING_SETTLEMENT`), `processedAt` | Day 15 |
| 19 | `CommissionRule` | `tc_commission_rules` | Chính sách phí | `id`, `organizationId`, `ratePercent`, `fixedFeePerTicket`, `effectiveFrom`, `status` | Day 06 |
| 20 | `Settlement` | `tc_settlements` | Đối soát | `id`, `eventId`, `organizationId`, `totalRevenue`, `platformFee`, `netPayoutAmount`, `status` (`DRAFT`, `FROZEN`, `APPROVED`, `PAID`), `settledAt` | Day 17 |
| 21 | `SettlementItem` | `tc_settlement_items` | Chi tiết đối soát | `id`, `settlementId`, `orderId`, `orderAmount`, `feeAmount`, `netAmount` | Day 17 |
| 22 | `Payout` | `tc_payouts` | Chi trả | `id`, `settlementId`, `amount`, `bankAccount`, `bankName`, `status` (`SUCCESS`, `FAILED`), `transferredAt` | Day 17 |
| 23 | `AuditLog` | `tc_audit_logs` | Nhật ký kiểm toán | `id`, `actorId`, `action`, `entityType`, `entityId`, `oldDataJson`, `newDataJson`, `createdAt`, `ipAddress` | Day 18 |

---

## 2. Bảng Kỹ Thuật & Cơ Chế Persistence (Không tính là lớp nghiệp vụ mới)

1. `tc_organizations`: Tổ chức tạo ra khi duyệt `OrganizationRequest`.
2. `tc_organization_members`: Lưu mối quan hệ thành viên và vai trò (`MANAGER`, `STAFF`, `CHECKIN`).
3. `tc_otp_verifications`: Lưu mã OTP băm HMAC, mục đích (`EMAIL_VERIFY`, `PASSWORD_RESET`), số lần thử sai, thời hạn 5 phút.
4. `tc_coupon_redemptions`: Quản lý trạng thái lượt giữ/sử dụng coupon theo đơn hàng (`RESERVED`, `CONSUMED`, `RELEASED`) đảm bảo không vượt `maxUses`.
5. `tc_outbox_messages`: Transactional Outbox pattern cho worker xử lý bất đồng bộ (gửi mail, kiểm tra trạng thái thanh toán, dọn dẹp ảnh).

---

## 3. Đối Chiếu 3 Luồng Nghiệp Vụ Mẫu

### Luồng 1: Mua một ghế ngồi (Seated Seat)
1. `User` (email verified) chọn sự kiện `Event` đang mở bán.
2. Gọi tạo `TicketHold`: khóa lạc quan `SessionSeat` (`version`), tạo `TicketHold` và `HoldItem` (`sessionSeatId` NOT NULL, `quantity = 1`), trạng thái ghế chuyển sang `HELD`.
3. Tạo `Order` từ `TicketHold`: tạo `OrderItem` tương ứng.
4. Thanh toán VNPAY `Payment`: thành công (`CAPTURED`).
5. SP phát hành vé: tạo `Ticket`, sinh QR code hash, chuyển trạng thái `SessionSeat` sang `SOLD`. Chuyển `Order` sang `PAID`.

### Luồng 2: Mua vé đứng có áp dụng Coupon
1. `User` chọn `SeatingZone` loại `STANDING`, yêu cầu giữ số lượng vé (ví dụ 3 vé).
2. Tạo `TicketHold`: kiểm tra và tăng `heldCount` tại `ScheduleZone` trong 1 transaction an toàn, `HoldItem` có `sessionSeatId = NULL`, `quantity = 3`.
3. Tạo `Order`, áp dụng `Coupon`:
   - Xác thực điều kiện coupon (hạn dùng, `currentUses < maxUses`, mức giảm <= 30%).
   - Ghi nhận `tc_coupon_redemptions` trạng thái `RESERVED`.
   - Tính toán `discountAmount`, `finalAmount`.
4. Thanh toán VNPAY `Payment` thành công.
5. Chuyển coupon redemption sang `CONSUMED`, tăng `Coupon.currentUses`, phát hành 3 `Ticket` độc lập.

### Luồng 3: Thu muộn, hủy sự kiện và hoàn tiền bù trừ
1. Giao dịch mạng timeout lúc callback VNPAY, outbox worker đối soát truy vấn trạng thái và ghi nhận `Payment` thành công muộn.
2. Ban tổ chức gửi yêu cầu hủy sự kiện: `Event` chuyển sang `CANCELLED`.
3. Hệ thống tạo hàng loạt `RefundRequest` với `reasonType = EVENT_CANCELLED`.
4. Worker xử lý `Refund`:
   - Hoàn tiền mô phỏng về tài khoản người mua.
   - Ghi nhận bù trừ vào bảng `SettlementItem` của sự kiện.
   - Vô hiệu hóa `Ticket` (`status = CANCELLED`), giải phóng ghế hoặc hoàn kho.
