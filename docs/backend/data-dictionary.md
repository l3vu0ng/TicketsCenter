# Từ điển dữ liệu ticketing

Quy ước: `NN` = `NOT NULL`, `NULL` = cho phép rỗng; `D:` là default; `FK:` là đích tham chiếu. UUID dùng `uniqueidentifier`, tiền VND dùng `decimal(19,0)`, thời gian là UTC `datetime2(3)`, enum lưu tên bằng `varchar`. Mọi cột secret/hash bị cấm xuất qua DTO.

## Tài khoản và tổ chức

### `tc_users`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()` — định danh User.
- `email nvarchar(320) NN` — email hiển thị; `normalized_email nvarchar(320) NN` — email trim/lower dùng đăng nhập và C01.
- `password_hash nvarchar(255) NN` — hash mật khẩu, secret; `full_name nvarchar(200) NN`, `phone nvarchar(30) NULL` — hồ sơ.
- `status varchar(20) NN D:'ACTIVE'` — `ACTIVE/DISABLED`; `email_verified_at datetime2(3) NULL` — thời điểm xác minh.
- `auth_version int NN D:0` — vô hiệu phiên cũ; `version bigint NN D:0` — optimistic version; `created_at datetime2(3) NN D:SYSUTCDATETIME()`.

### `tc_user_platform_roles` (kỹ thuật)

- `user_id uniqueidentifier NN FK:tc_users.id`, `role varchar(30) NN` — quyền `ADMIN`; cặp này là PK.
- `granted_at datetime2(3) NN D:SYSUTCDATETIME()` — thời điểm cấp.

### `tc_organizations`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()` — định danh Organization.
- `name nvarchar(200) NN`, `contact_email nvarchar(320) NN`, `contact_phone nvarchar(30) NULL`, `description nvarchar(2000) NULL` — thông tin tổ chức.
- `created_at datetime2(3) NN D:SYSUTCDATETIME()`.

### `tc_organization_memberships`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()` — định danh membership.
- `user_id uniqueidentifier NN FK:tc_users.id`, `organization_id uniqueidentifier NN FK:tc_organizations.id` — C02.
- `role varchar(30) NN` — `MANAGER/CHECK_IN_STAFF`; `active bit NN D:1`; `joined_at datetime2(3) NN D:SYSUTCDATETIME()`; `version bigint NN D:0`.

### `tc_organization_requests`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()` — định danh yêu cầu.
- `applicant_id uniqueidentifier NN FK:tc_users.id`, `reviewer_id uniqueidentifier NULL FK:tc_users.id`, `organization_id uniqueidentifier NULL FK:tc_organizations.id`.
- `name nvarchar(200) NN`, `contact_email nvarchar(320) NN`, `contact_phone nvarchar(30) NULL`, `description nvarchar(2000) NULL` — dữ liệu đăng ký.
- `status varchar(30) NN D:'PENDING'`, `requested_at datetime2(3) NN D:SYSUTCDATETIME()`, `decided_at datetime2(3) NULL`, `rejection_reason nvarchar(1000) NULL`, `version bigint NN D:0`.

### `tc_event_categories`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()` — định danh danh mục.
- `name nvarchar(120) NN`, `slug varchar(120) NN` — slug là khóa ứng viên duy nhất.

### `tc_commission_rules`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `organization_id uniqueidentifier NN FK:tc_organizations.id`.
- `rate_percent decimal(7,4) NN`, `fixed_fee decimal(19,0) NN` — hai thành phần phí không âm.
- `effective_from datetime2(3) NN`, `effective_to datetime2(3) NN` — khoảng hiệu lực C16.

## Sự kiện và tồn kho

### `tc_events`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `organization_id uniqueidentifier NN FK:tc_organizations.id`, `category_id uniqueidentifier NN FK:tc_event_categories.id`, `commission_rule_id uniqueidentifier NULL FK:tc_commission_rules.id`.
- `title nvarchar(250) NN`, `description nvarchar(max) NULL`, `cover_image_url nvarchar(2048) NULL`, `venue_name nvarchar(250) NN`, `venue_address nvarchar(500) NN`.
- `sale_start/sale_end/start_time/end_time datetime2(3) NN` — lịch C04.
- `status varchar(30) NN D:'DRAFT'`; `version bigint NN D:0`; `created_at datetime2(3) NN D:SYSUTCDATETIME()`.

### `tc_zones`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `event_id uniqueidentifier NN FK:tc_events.id`, `name nvarchar(120) NN`.
- `type varchar(20) NN` — `SEATED/STANDING`; `price decimal(19,0) NN` — cho phép 0.
- `capacity int NULL`, `held_quantity int NN D:0`, `sold_quantity int NN D:0` — chỉ khu đứng dùng quota C05.
- `version bigint NN D:0`.

### `tc_seats`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `zone_id uniqueidentifier NN FK:tc_zones.id`.
- `row_name nvarchar(20) NN`, `seat_number int NN`, `label nvarchar(50) NN` — khóa ứng viên trong Zone.
- `status varchar(20) NN D:'AVAILABLE'` — `AVAILABLE/HELD/SOLD`; `version bigint NN D:0`.

## Giữ vé, đơn và thanh toán

### `tc_ticket_holds`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `user_id uniqueidentifier NN FK:tc_users.id`, `event_id uniqueidentifier NN FK:tc_events.id`.
- `status varchar(20) NN D:'ACTIVE'` — `ACTIVE/RELEASED/CONSUMED`; `created_at datetime2(3) NN D:SYSUTCDATETIME()`, `expires_at datetime2(3) NN`; `version bigint NN D:0`.

### `tc_ticket_hold_items`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `hold_id uniqueidentifier NN FK:tc_ticket_holds.id`, `zone_id uniqueidentifier NN FK:tc_zones.id`, `seat_id uniqueidentifier NULL FK:tc_seats.id`.
- `quantity int NN`, `unit_price decimal(19,0) NN` — lựa chọn và giá snapshot C07/C12.

### `tc_coupons`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `organization_id uniqueidentifier NN FK:tc_organizations.id`, `code varchar(80) NN` — code duy nhất trong tổ chức.
- `discount_type varchar(30) NN` — `PERCENTAGE/FIXED_AMOUNT`; `percentage_value decimal(5,2) NULL`, `fixed_amount decimal(19,0) NULL` — đúng một trường có giá trị.
- `max_discount_amount decimal(19,0) NULL`, `max_uses int NN`, `valid_from/valid_to datetime2(3) NN`, `active bit NN D:1`, `version bigint NN D:0`.

### `tc_orders`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `user_id uniqueidentifier NN FK:tc_users.id`, `event_id uniqueidentifier NN FK:tc_events.id`, `hold_id uniqueidentifier NN FK:tc_ticket_holds.id`, `coupon_id uniqueidentifier NULL FK:tc_coupons.id`.
- `order_code varchar(64) NN` — khóa nghiệp vụ duy nhất.
- `subtotal_amount/discount_amount/total_amount decimal(19,0) NN` — tổng snapshot C08.
- `status varchar(30) NN D:'PENDING_PAYMENT'`, `created_at datetime2(3) NN D:SYSUTCDATETIME()`, `paid_at datetime2(3) NULL`, `version bigint NN D:0`.

### `tc_order_items`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `order_id uniqueidentifier NN FK:tc_orders.id`, `zone_id uniqueidentifier NN FK:tc_zones.id`, `seat_id uniqueidentifier NULL FK:tc_seats.id`.
- `quantity int NN`, `unit_price decimal(19,0) NN`, `zone_name_snapshot nvarchar(120) NN`, `seat_label_snapshot nvarchar(50) NULL` — dữ liệu tại lúc mua.

### `tc_payments`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `order_id uniqueidentifier NN FK:tc_orders.id`, `txn_ref varchar(100) NN` — C09.
- `amount decimal(19,0) NN`, `currency char(3) NN D:'VND'`, `status varchar(20) NN D:'PENDING'`.
- `provider_reference nvarchar(200) NULL`, `created_at datetime2(3) NN D:SYSUTCDATETIME()`, `captured_at datetime2(3) NULL`, `version bigint NN D:0`.

### `tc_coupon_redemptions` (kỹ thuật)

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `coupon_id uniqueidentifier NN FK:tc_coupons.id`, `order_id uniqueidentifier NN FK:tc_orders.id` — Order duy nhất.
- `status varchar(20) NN D:'RESERVED'`, `reserved_at datetime2(3) NN D:SYSUTCDATETIME()`, `expires_at datetime2(3) NN`, `consumed_at/released_at datetime2(3) NULL`.

### `tc_tickets`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `order_item_id uniqueidentifier NN FK:tc_order_items.id`.
- `ticket_code varchar(100) NN` — khóa nghiệp vụ; `qr_secret_hash varbinary(64) NN` — secret, không DTO/log.
- `paid_amount decimal(19,0) NN`, `status varchar(30) NN D:'ACTIVE'`, `issued_at datetime2(3) NN D:SYSUTCDATETIME()`, `version bigint NN D:0`.

## Check-in và hoàn tiền

### `tc_check_ins`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `event_id uniqueidentifier NN FK:tc_events.id`, `ticket_id uniqueidentifier NULL FK:tc_tickets.id`, `actor_id uniqueidentifier NN FK:tc_users.id`.
- `result varchar(30) NN` — `SUCCESS/NOT_FOUND/WRONG_EVENT/TOO_EARLY/TOO_LATE/ALREADY_USED/NOT_ACTIVE/EVENT_CANCELLED`; `scanned_at datetime2(3) NN D:SYSUTCDATETIME()`.

### `tc_refund_requests`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `order_id uniqueidentifier NN FK:tc_orders.id`, `requester_id uniqueidentifier NN FK:tc_users.id`, `reviewer_id uniqueidentifier NULL FK:tc_users.id`.
- `reason nvarchar(2000) NN`, `reason_type varchar(30) NN`, `status varchar(20) NN D:'PENDING'`.
- `requested_at datetime2(3) NN D:SYSUTCDATETIME()`, `decided_at datetime2(3) NULL`, `rejection_reason nvarchar(1000) NULL`, `version bigint NN D:0`.

### `tc_refund_request_tickets` (kỹ thuật)

- `refund_request_id uniqueidentifier NN FK:tc_refund_requests.id`, `ticket_id uniqueidentifier NN FK:tc_tickets.id` — cặp là PK.
- `is_open bit NN D:1` — filtered unique trên Ticket khi 1; `added_at datetime2(3) NN D:SYSUTCDATETIME()`.

### `tc_refunds`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `refund_request_id uniqueidentifier NULL FK:tc_refund_requests.id`, `payment_id uniqueidentifier NN FK:tc_payments.id`.
- `purpose varchar(30) NN`, `amount decimal(19,0) NN`, `status varchar(20) NN D:'PENDING'`, `provider_reference nvarchar(200) NULL`.
- `created_at datetime2(3) NN D:SYSUTCDATETIME()`, `processed_at datetime2(3) NULL`, `version bigint NN D:0`.

## Đối soát và lịch sử

### `tc_settlements`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `event_id uniqueidentifier NN FK:tc_events.id` — một Settlement/Event.
- `gross_revenue/total_refund/total_commission/net_payable decimal(19,0) NN D:0` — tổng snapshot C18.
- `status varchar(20) NN D:'DRAFT'`, `confirmed_at datetime2(3) NULL`, `created_at datetime2(3) NN D:SYSUTCDATETIME()`, `version bigint NN D:0`.

### `tc_settlement_items`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `settlement_id uniqueidentifier NN FK:tc_settlements.id`, `order_id uniqueidentifier NN FK:tc_orders.id` — mỗi Order xuất hiện tối đa một lần.
- `gross_amount/refund_amount/commission_amount/net_amount decimal(19,0) NN` — snapshot C17.

### `tc_payouts`

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `settlement_id uniqueidentifier NN FK:tc_settlements.id`.
- `amount decimal(19,0) NN`, `reference varchar(100) NN`, `status varchar(20) NN D:'PENDING'`.
- `created_at datetime2(3) NN D:SYSUTCDATETIME()`, `paid_at datetime2(3) NULL`, `version bigint NN D:0`.

### `tc_audit_logs`

- `id bigint IDENTITY NN` — PK append-only; `actor_id uniqueidentifier NULL FK:tc_users.id`.
- `action varchar(100) NN`, `aggregate_type varchar(100) NN`, `aggregate_id uniqueidentifier NN`, `detail nvarchar(max) NULL`, `created_at datetime2(3) NN D:SYSUTCDATETIME()`.

### `tc_otps` (kỹ thuật)

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `user_id uniqueidentifier NULL FK:tc_users.id`, `email_normalized nvarchar(320) NN`, `purpose varchar(30) NN`.
- `secret_hash varbinary(64) NN` — HMAC/hash, secret; `expires_at datetime2(3) NN`, `failed_attempts tinyint NN D:0`, `consumed_at/invalidated_at datetime2(3) NULL`, `created_at datetime2(3) NN D:SYSUTCDATETIME()`.

### `tc_outbox` (kỹ thuật)

- `id uniqueidentifier NN D:NEWSEQUENTIALID()`, `event_type varchar(120) NN`, `aggregate_type varchar(100) NN`, `aggregate_id uniqueidentifier NN`.
- `payload nvarchar(max) NN` — dữ liệu gửi đã lọc secret; `idempotency_key varchar(200) NN` — duy nhất.
- `status varchar(20) NN D:'PENDING'`, `available_at datetime2(3) NN D:SYSUTCDATETIME()`, `lease_owner varchar(120) NULL`, `lease_until datetime2(3) NULL`, `attempts int NN D:0`, `created_at datetime2(3) NN D:SYSUTCDATETIME()`, `published_at datetime2(3) NULL`.

### `tc_schema_migrations` (kỹ thuật)

- `version varchar(100) NN` — PK là tên file migration; `checksum_sha256 char(64) NN` — phát hiện sửa file đã áp dụng.
- `applied_at datetime2(3) NN D:SYSUTCDATETIME()`, `applied_by sysname NN D:ORIGINAL_LOGIN()` — audit triển khai.

## Secret và DTO

Không bao giờ serialize/log `password_hash`, `qr_secret_hash`, `secret_hash`, payload có token, connection string hoặc credential. DTO chỉ nhận ID/trạng thái/giá trị nghiệp vụ cần cho use case; quan hệ được tải theo truy vấn use case thay vì serialize entity graph.
