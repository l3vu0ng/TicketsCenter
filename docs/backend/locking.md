# Thứ tự khóa, transaction và khôi phục

> Nguồn: [SPEC](../references/SPEC.md) §8, §14.2, §14.5, §14.9 và [CONVENTIONS](../tasks/CONVENTIONS.md) §4.
> Đây là hợp đồng cho SP01–SP17 và caller JPA. Không gọi mạng hoặc chờ người dùng khi đang giữ transaction.

## 1. Quy tắc chung

1. Khám phá ID liên quan bằng truy vấn bind parameter trước khi lấy khóa. Không quyết định nghiệp vụ từ snapshot chưa khóa.
2. Backend kiểm quyền rồi chọn **một principal/connection** trước transaction; không đổi connection/principal giữa use case.
3. Lấy khóa theo các pha dưới đây. Nhiều row cùng pha luôn `ORDER BY id ASC` với `UPDLOCK, HOLDLOCK`; `ROWLOCK` chỉ là hint, không phải bảo đảm.
4. Nếu sau khi khóa phát hiện tập Event/Order thay đổi so với bước khám phá, rollback toàn bộ và retry hữu hạn từ đầu nếu thao tác idempotent.
5. Deadlock victim chỉ retry hữu hạn (đề xuất tối đa 3 lần với jitter) cho request có khóa idempotency. Không retry mù payment/refund/payout UNKNOWN.

## 2. Thứ tự khóa toàn cục

```text
P0  User (khi use case phụ thuộc one-active-hold hoặc membership)
P1  Event — tất cả eventId liên quan, tăng dần
P2  Aggregate coordinator theo thứ tự:
    OrganizationRequest → TicketHold → Order → RefundRequest → Settlement
P3  Inventory: Zone → Seat, mỗi loại theo id tăng dần
P4  Coupon → CouponRedemption, couponId tăng dần
P5  Provider/history rows: Payment → Refund → Ticket → Payout
P6  AuditLog → Outbox (append cuối transaction)
```

Một đường không cần User bắt đầu ở Event và không được quay lại lấy User sau đó. Lookup `paymentId`, `refundId`, `payoutId` chỉ dùng để khám phá Order/Event/Settlement; khóa chính thức vẫn bắt đầu từ pha thấp nhất cần thiết.

## 3. Các luồng chính

### SP02 — Create Hold, gồm Hold cũ ở Event khác

1. Khóa User để tuần tự hóa invariant một Hold ACTIVE toàn hệ thống.
2. Tìm Hold ACTIVE hiện tại và Event của nó; lập tập `{oldEventId?, requestedEventId}`, khóa Event theo ID tăng dần.
3. Khóa Hold cũ. Nếu `now >= expiresAt`, gọi nhánh release trong cùng transaction; nếu còn hạn thì trả 409, không tự thay thế.
4. Khóa Zone rồi Seat của lựa chọn theo ID tăng dần; kiểm cùng Event, 1–8 vé, capacity/status và không trùng seat.
5. Tạo Hold/Item, cập nhật kho toàn bộ hoặc rollback toàn bộ; audit/outbox cuối.

### SP07 — Release Hold

Event → TicketHold → Order (nếu có) → Zone → Seat → Coupon/Redemption → audit/outbox. Release lặp trả trạng thái hiện tại; không trả redemption `CONSUMED` và không đổi Payment thành FAILED.

### SP03 — Apply/swap coupon A↔B

Event → Order → hai Coupon theo `couponId ASC` (gồm mã cũ và mới) → redemption. Luôn khóa cả A/B theo ID, không khóa “mã cũ trước, mã mới sau”; vì vậy request A→B và B→A không tạo chu trình. Nếu mã mới lỗi/quota hết, rollback giữ nguyên mã cũ.

### SP08/SP09 — Begin/apply payment result

- SP08: Event → TicketHold → Order → inventory nếu cần đối chiếu → Coupon/Redemption → Payment.
- SP09: dùng payment/txnRef khám phá Order/Event, sau đó Event → TicketHold → Order → Zone/Seat → Coupon/Redemption → Payment → Refund bù trừ (nếu có) → Ticket → audit/outbox.
- Callback duplicate đọc kết quả đã ghi. CAPTURED không bị hạ cấp bởi callback cũ. Thu muộn/Event hủy tạo một compensation Refund, không phát hành Ticket.

### SP05/SP10/SP11 — Refund

- Request: Event → Order → RefundRequest → Ticket theo ID → audit.
- Decision: Event → Order → RefundRequest → Payment → Refund → Ticket theo ID → audit/outbox.
- Result: dùng refundId khám phá, rồi Event → Order → RefundRequest → Zone/Seat → Payment → Refund → Ticket → audit/outbox.
- UNKNOWN giữ nguyên nghĩa vụ; chỉ retry khi đã đối chiếu và flow vận hành explicit cho phép.

### SP13/SP17 — Cancel Event

- SP13 khóa Event, chuyển CANCELLED và append outbox rồi commit ngắn.
- Worker xử lý từng Order theo `orderId ASC`, mỗi Order một transaction: Event → TicketHold → Order → RefundRequest → Zone/Seat → Payment → Refund → Ticket → audit/outbox.
- Không giữ một transaction cho toàn Event; restart tiếp tục từ trạng thái từng Order.

### SP14–SP16 — Settlement/Payout

- Recalculate/confirm: Event → các Order tăng dần → RefundRequest liên quan → Settlement → Payment/Refund/Ticket → SettlementItem → audit.
- Confirm lấy cùng Event/Order order như payment/refund để snapshot không đua với SP09/SP11; F09 còn blocker thì không CONFIRMED.
- Payout: Event → Settlement → Payout theo ID; tổng `SUCCEEDED + PENDING` không vượt netPayable. Không cần khóa User.

### Membership và publish

- Membership: User ID liên quan tăng dần → Organization/membership; giữ invariant manager active cuối cùng.
- Publish: Event → CommissionRule; trigger/audit không lấy ngược Organization/User. Edit layout dùng Event → Zone → Seat như publish/cancel.

## 4. Transaction ownership cho stored procedure

Mỗi SP phát hiện transaction ngoài và chỉ commit transaction do chính nó mở:

```sql
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
IF @started = 1
    BEGIN TRANSACTION;
ELSE
    SAVE TRANSACTION tc_savepoint;

BEGIN TRY
    -- validate, lock theo hợp đồng, mutate, audit/outbox
    IF @started = 1 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @started = 1 AND XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    ELSE IF @started = 0 AND XACT_STATE() = 1
        ROLLBACK TRANSACTION tc_savepoint;
    -- Outer transaction + XACT_STATE() = -1: không thể rollback savepoint;
    -- THROW để caller sở hữu transaction thực hiện full rollback.
    THROW;
END CATCH;
```

Khi transaction ngoài bị uncommittable (`XACT_STATE() = -1`), SP chỉ `THROW`; caller sở hữu transaction phải full rollback. SP không được tự xóa toàn bộ công việc của caller. Caller JPA clear/refresh persistence context sau SP mutation.

## 5. I/O ngoài database

```text
TX-A: ghi Payment/Refund/Outbox PENDING → COMMIT
ngoài TX: gọi VNPAY/email/storage
TX-B: xác minh response/query provider → SP09/SP11 cập nhật idempotent → COMMIT
```

Không giữ connection khi gọi mạng. Outbox có lease, attempt, nextAttemptAt và idempotency key; job nhận lại công việc khi lease hết hạn.

## 6. Mất response/commit chưa rõ

| Luồng | Tra trước retry |
|---|---|
| Approve organization | requestId và organizationId đã tạo |
| Hold | unique `(userId, Idempotency-Key)` và payload hash; replay trả Hold cũ, key đổi payload trả 409 |
| Order | unique holdId |
| Coupon | redemption theo orderId |
| Payment | paymentId/txnRef, Order/Ticket/compensation hiện tại |
| Check-in | Ticket status và CheckIn đã ghi |
| Refund request/result | request mở theo tickets; refundId/providerReference |
| Settlement/Payout | eventId/settlementId/payoutId |

Caller trả trạng thái đã lưu hoặc tiếp tục cùng identity; không tạo identity mới chỉ vì timeout.

## 7. Ca concurrency bắt buộc

- Hai createHold cùng User và Hold hết hạn ở Event khác.
- Hai request lấy cùng Seat hoặc quota đứng còn một.
- Đổi coupon A→B đồng thời B→A và hai buyer tranh coupon maxUses=1.
- Payment callback trùng/đến muộn đua expiry/cancel.
- Check-in đua refund request; hai scan một Ticket.
- Refund result đua settlement confirm.
- Hai payout không vượt balance; replay cùng payoutId không nhân tiền.

Test dùng hai connection SQL Server thật, barrier rõ và timeout hữu hạn; mock/H2 không chứng minh lock order.
