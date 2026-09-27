# ERD vật lý ticketing

Nguồn: [SPEC](../references/SPEC.md) §5, §8.2, §14.3 và [model map](model-map.md). Tên vật lý dùng tiền tố `tc_` để tránh từ khóa `USER`, `ORDER`; toàn bộ FK mặc định `NO ACTION`. Không cascade-delete lịch sử đơn hàng, thanh toán, vé, hoàn tiền hoặc audit.

```mermaid
erDiagram
    tc_users ||--o{ tc_user_platform_roles : has
    tc_users ||--o{ tc_organization_memberships : joins
    tc_organizations ||--o{ tc_organization_memberships : has
    tc_users ||--o{ tc_organization_requests : applies
    tc_users o|--o{ tc_organization_requests : reviews
    tc_organizations o|--o{ tc_organization_requests : created_from

    tc_organizations ||--o{ tc_commission_rules : owns
    tc_organizations ||--o{ tc_events : owns
    tc_event_categories ||--o{ tc_events : classifies
    tc_commission_rules o|--o{ tc_events : selected_policy
    tc_events ||--o{ tc_zones : contains
    tc_zones ||--o{ tc_seats : contains

    tc_users ||--o{ tc_ticket_holds : owns
    tc_events ||--o{ tc_ticket_holds : reserves_for
    tc_ticket_holds ||--|{ tc_ticket_hold_items : contains
    tc_zones ||--o{ tc_ticket_hold_items : prices
    tc_seats o|--o{ tc_ticket_hold_items : selects

    tc_users ||--o{ tc_orders : buys
    tc_events ||--o{ tc_orders : sold_for
    tc_ticket_holds ||--o| tc_orders : becomes
    tc_coupons o|--o{ tc_orders : discounts
    tc_orders ||--|{ tc_order_items : contains
    tc_zones ||--o{ tc_order_items : snapshots
    tc_seats o|--o{ tc_order_items : snapshots
    tc_orders ||--o{ tc_payments : attempts
    tc_order_items ||--o{ tc_tickets : issues

    tc_organizations ||--o{ tc_coupons : owns
    tc_coupons ||--o{ tc_coupon_redemptions : reserves
    tc_orders ||--o| tc_coupon_redemptions : uses

    tc_events ||--o{ tc_check_ins : receives
    tc_tickets o|--o{ tc_check_ins : checked
    tc_users ||--o{ tc_check_ins : performs

    tc_orders ||--o{ tc_refund_requests : requested_for
    tc_users ||--o{ tc_refund_requests : requests
    tc_users o|--o{ tc_refund_requests : reviews
    tc_refund_requests ||--|{ tc_refund_request_tickets : includes
    tc_tickets ||--o{ tc_refund_request_tickets : requested
    tc_refund_requests o|--o{ tc_refunds : causes
    tc_payments ||--o{ tc_refunds : refunds

    tc_events ||--o| tc_settlements : settles
    tc_settlements ||--|{ tc_settlement_items : contains
    tc_orders ||--o| tc_settlement_items : snapshots
    tc_settlements ||--o{ tc_payouts : pays

    tc_users o|--o{ tc_otps : receives
    tc_users o|--o{ tc_audit_logs : acts
```

Các bảng kỹ thuật không tạo lớp nghiệp vụ mới:

- `tc_user_platform_roles`: quyền nền tảng; role tổ chức vẫn ở membership.
- `tc_otps`: chỉ giữ hash/HMAC và trạng thái sử dụng, không giữ OTP rõ.
- `tc_coupon_redemptions`: một dòng trên Order, trạng thái `RESERVED/CONSUMED/RELEASED`.
- `tc_refund_request_tickets`: bảng nối có `is_open` để unique filtered index bảo vệ một yêu cầu mở trên mỗi Ticket.
- `tc_outbox`: thông điệp hậu giao dịch có idempotency key và lease.
- `tc_schema_migrations`: lịch sử/checksum do migration runner sở hữu.

## Chuẩn hóa và lưu dư có chủ đích

Khóa ứng viên được khóa bằng UNIQUE cho email chuẩn hóa, membership, mã đơn/vé, `txnRef`, slug, Hold→Order, Event→Settlement và Order→SettlementItem. Các phụ thuộc chính là `User.id → email/profile/status`, `Event.id → organization/category/times/status`, `Order.id → user/event/hold/amounts/status`, và `OrderItem.id → order/zone/seat/price/snapshot`. Phần lớn thuộc tính không khóa phụ thuộc vào khóa của chính bảng.

Các ngoại lệ lưu dư có chủ đích gồm `Order.holdId → userId,eventId` và `OrderItem.seatId → zoneId` để khóa phạm vi giao dịch, cùng snapshot `OrderItem.unitPrice/zoneNameSnapshot/seatLabelSnapshot`, `Ticket.paidAmount`, các tổng trên `Order`, `Settlement` và `SettlementItem`, và bộ đếm khu đứng. Chúng phải giữ lịch sử hoặc giảm tranh chấp truy vấn; SP của use case chịu trách nhiệm kiểm tra cùng phạm vi và cập nhật/đối chiếu, không thay snapshot bằng join tới giá trị hiện tại.

## Chính sách xóa

- Không FK nào dùng cascade trong schema nền.
- Dữ liệu lịch sử `tc_orders`, `tc_payments`, `tc_tickets`, `tc_refunds`, `tc_settlements`, `tc_payouts`, `tc_audit_logs` chỉ chuyển trạng thái hoặc forward-fix.
- Xóa cấu phần draft chỉ được bổ sung ở task nghiệp vụ có kiểm tra trạng thái và test; schema hiện tại ưu tiên từ chối xóa khi còn tham chiếu.
