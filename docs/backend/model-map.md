# Bản đồ mô hình nghiệp vụ chuẩn

> Nguồn bắt buộc: [SPEC](../references/SPEC.md) §5–8, §14.14 và [class diagram](../classdiagram/diagram.md).
> Phạm vi được khóa ở **23 lớp nghiệp vụ**. OTP, coupon redemption, outbox, bảng nối refund–ticket, DTO, enum, principal và job là chi tiết kỹ thuật, không phải lớp nghiệp vụ mới.

## 1. Kiểm kê 23 lớp

| # | Lớp | Thuộc tính/hành vi bắt buộc | Bảng dự kiến | Task triển khai |
|---:|---|---|---|---|
| 1 | `User` | id, email, passwordHash, fullName, phone, status, emailVerifiedAt, authVersion, createdAt; `updateProfile()`, `changePassword()`, `verifyEmail()`, `disable()` | `tc_users` | D02–D05 |
| 2 | `Organization` | id, name, contactEmail, contactPhone, description, createdAt; `addMember()`, `updatePolicy()` | `tc_organizations` | D02, D06 |
| 3 | `OrganizationMembership` | id, role, active, joinedAt; `changeRole()`, `activate()`, `deactivate()` | `tc_organization_memberships` | D02, D06 |
| 4 | `OrganizationRequest` | id, applicant, reviewer, name/contact/description, status, requestedAt, decidedAt, rejectionReason, organization; `approve()`, `reject()` | `tc_organization_requests` | D02, D06 |
| 5 | `EventCategory` | id, name, slug | `tc_event_categories` | D02, D07 |
| 6 | `Event` | id, organization, category, commissionRule, title, description, coverImageUrl, venueName/address, saleStart/saleEnd, startTime/endTime, status; `submitForApproval()`, `publish()`, `reject()`, `cancel()` | `tc_events` | D02, D07, D16 |
| 7 | `Zone` | id, event, name, type, price, capacity, heldQuantity, soldQuantity; `updatePrice()`, `reserve()`, `release()`, `sell()`, `restore()` | `tc_zones` | D02, D07–D09 |
| 8 | `Seat` | id, zone, rowName, seatNumber, label, status; `hold()`, `release()`, `markSold()`, `restore()` | `tc_seats` | D02, D07–D08, D11, D15 |
| 9 | `TicketHold` | id, user, event, status, createdAt, expiresAt; `isExpired()`, `release()`, `consume()` | `tc_ticket_holds` | D02–D03, D08, D12 |
| 10 | `TicketHoldItem` | id, hold, zone, seat?, quantity, unitPrice; `calculateSubtotal()`, `validateSeatQuantity()` | `tc_ticket_hold_items` | D02–D03, D08 |
| 11 | `Order` | id, user, event, hold, coupon?, orderCode, subtotalAmount, discountAmount, totalAmount, status, createdAt, paidAt; `calculateTotal()`, `markPaid()`, `cancel()`, `expire()` | `tc_orders` | D02–D03, D09–D11 |
| 12 | `OrderItem` | id, order, zone, seat?, quantity, unitPrice, zoneNameSnapshot, seatLabelSnapshot; `calculateSubtotal()`, `validateSeatQuantity()` | `tc_order_items` | D02–D03, D09, D11 |
| 13 | `Payment` | id, order, txnRef, amount, currency, status, providerReference, createdAt, capturedAt; `initiate()`, `markCaptured()`, `markFailed()`, `markUnknown()` | `tc_payments` | D02–D03, D10–D12 |
| 14 | `Coupon` | id, organization, code, discountType/value, maxDiscountAmount, maxUses, validity, active; `validateConditions()`, `calculateDiscount()` | `tc_coupons` | D02–D03, D09 |
| 15 | `Ticket` | id, orderItem, ticketCode, qrSecretHash, paidAmount, status, issuedAt; `generateQRCode()`, `markUsed()`, `markRefundPending()`, `reactivate()`, `markRefunded()`, `invalidate()` | `tc_tickets` | D02–D03, D11, D13–D16 |
| 16 | `CheckIn` | id, event, ticket?, actor, result, scannedAt; `recordResult()` | `tc_check_ins` | D02–D03, D13 |
| 17 | `RefundRequest` | id, order, requester, reviewer?, reason, reasonType, status, requestedAt, decidedAt, rejectionReason; `approve()`, `reject()`, `complete()` | `tc_refund_requests` | D02–D03, D14–D16 |
| 18 | `Refund` | id, request?, payment, purpose, amount, status, providerReference, createdAt, processedAt; `markSucceeded()`, `markFailed()`, `markUnknown()` | `tc_refunds` | D02–D03, D15–D16 |
| 19 | `CommissionRule` | id, organization, ratePercent, fixedFee, effectiveFrom/effectiveTo; `calculateFee()` | `tc_commission_rules` | D02–D03, D06–D07, D17 |
| 20 | `Settlement` | id, event, grossRevenue, totalRefund, totalCommission, netPayable, status, confirmedAt; `calculateTotals()`, `confirm()`, `markPaid()` | `tc_settlements` | D02–D03, D17 |
| 21 | `SettlementItem` | id, settlement, order, grossAmount, refundAmount, commissionAmount, netAmount; `validateAmounts()` | `tc_settlement_items` | D02–D03, D17 |
| 22 | `Payout` | id, settlement, amount, reference, status, createdAt, paidAt; `markSucceeded()`, `markFailed()` | `tc_payouts` | D02–D03, D17 |
| 23 | `AuditLog` | id, actorId?, action, aggregateType, aggregateId, detail, createdAt; append-only | `tc_audit_logs` | D02–D03, D06–D18 |

Đếm kiểm chứng: **23 dòng, 23 tên lớp duy nhất**. Mọi lớp có task trong [COVERAGE](../tasks/COVERAGE.md) §23 lớp nghiệp vụ.

## 2. Trạng thái và enum chuẩn

| Đối tượng | Miền giá trị |
|---|---|
| User | `ACTIVE`, `DISABLED` |
| OrganizationRequest | `PENDING`, `APPROVED`, `REJECTED` |
| Event | `DRAFT`, `PENDING_APPROVAL`, `REJECTED`, `PUBLISHED`, `CANCELLED` |
| ZoneType | `SEATED`, `STANDING` |
| Seat | `AVAILABLE`, `HELD`, `SOLD` |
| TicketHold | `ACTIVE`, `RELEASED`, `CONSUMED`; hết hạn là `RELEASED` + `expiresAt`, không có `EXPIRED` |
| Order | `PENDING_PAYMENT`, `PAID`, `CANCELLED`, `EXPIRED` |
| Payment | `PENDING`, `CAPTURED`, `FAILED`, `UNKNOWN` |
| Ticket | `ACTIVE`, `USED`, `REFUND_PENDING`, `REFUNDED`, `INVALIDATED` |
| RefundRequest | `PENDING`, `APPROVED`, `REJECTED`, `COMPLETED` |
| Refund | `PENDING`, `SUCCEEDED`, `FAILED`, `UNKNOWN` |
| Settlement | `DRAFT`, `CONFIRMED`, `PAID` |
| Payout | `PENDING`, `SUCCEEDED`, `FAILED` |
| DiscountType | `PERCENTAGE`, `FIXED_AMOUNT` |
| RefundPurpose | `CUSTOMER_REFUND`, `PAYMENT_COMPENSATION` |
| RefundRequestReason | `CUSTOMER_REQUEST`, `EVENT_CANCELLATION` |

## 3. Quan hệ phải giữ

- `User` 1–N `OrganizationMembership`; `Organization` 1–N membership và `(userId, organizationId)` duy nhất.
- `OrganizationRequest` thuộc applicant, có tối đa một reviewer và sau duyệt gắn đúng một `Organization`.
- `Organization` 1–N `Event`/`Coupon`/`CommissionRule`; Event PUBLISHED có đúng một commission rule cùng tổ chức.
- `Event` 1–N `Zone`; khu ngồi có `Seat`, khu đứng không có Seat và dùng capacity.
- `TicketHold` thuộc đúng một User/Event, chứa `TicketHoldItem`; một User tối đa một Hold ACTIVE toàn hệ thống.
- `Order` được tạo tối đa một lần từ Hold; item giữ snapshot giá/khu/ghế. `OrderItem → Seat` là 0..1 và `OrderItem → Ticket` là 0..*.
- `Payment` thuộc Order; `Ticket` thuộc OrderItem; `CheckIn → Ticket` là 0..1 để giữ lần quét mã không tồn tại.
- `RefundRequest` thuộc một Order và tập Ticket qua bảng nối kỹ thuật; một Request có thể có nhiều lần `Refund` để giữ lịch sử retry.
- `Settlement` duy nhất theo Event; `SettlementItem` duy nhất theo Order trong Settlement; `Payout` thuộc Settlement.

Không cascade-delete `Order`, `Payment`, `Ticket`, `Refund` hoặc `AuditLog` đã phát sinh.

## 4. Ba luồng kiểm kê

| Luồng | Lớp tham gia | FK/snapshot/trạng thái cần bảo vệ |
|---|---|---|
| Mua một ghế | User → Event/Zone/Seat → TicketHold/Item → Order/Item → Payment → Ticket | Seat đúng Zone/Event; item quantity=1; giá/nhãn snapshot; `AVAILABLE→HELD→SOLD`; tổng `Ticket.paidAmount` bằng Order.totalAmount |
| Vé đứng có coupon | User → Event/Zone → Hold/Item → Order/Item → Coupon → Payment → Ticket | Không Seat; capacity không âm; redemption là bảng kỹ thuật; giảm tối đa 30%; coupon `RESERVED→CONSUMED` |
| Thu tiền đến muộn | Payment → Order/Hold/Event → Refund | Lưu `CAPTURED`, không phát hành vé; tạo đúng một `PAYMENT_COMPENSATION`; UNKNOWN phải đối chiếu trước retry |

## 5. Ranh giới kỹ thuật

Các bảng `tc_otps`, `tc_coupon_redemptions`, `tc_refund_request_tickets`, `tc_outbox`, session/role mapping và migration history là persistence kỹ thuật. Chúng không làm tăng số lớp nghiệp vụ. Repository/Service chỉ tạo khi use case cần; không tạo một bộ Controller–Service–Repository cho từng bảng.
