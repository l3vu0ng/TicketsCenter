# Dữ liệu integration dùng chung

Fixture nằm tại `database/seeds/ticketing-test-fixtures.sql`, chỉ chạy trên database test. Java harness đặt hash mật khẩu vào `SESSION_CONTEXT('test_password_hash')`; ưu tiên password từ `TC_TEST_PASSWORD`, nếu thiếu thì sinh credential ngẫu nhiên chỉ sống trong lần test. File SQL không chứa password hoặc hash dùng lại được.

## ID cố định

- User `...0001`: MANAGER tổ chức A và CHECK_IN_STAFF tổ chức B.
- User `...0002`: MANAGER tổ chức B và platform ADMIN.
- User `...0003`: buyer ACTIVE chưa xác minh email.
- User `...0004`: buyer DISABLED đã xác minh email.
- Organization A/B: `...0010` / `...0020`.
- Event A: `...0101` PUBLISHED, `...0102` DRAFT, `...0103` PENDING_APPROVAL, `...0104` CANCELLED.
- Event B: `...0201` PUBLISHED, `...0202` DRAFT, `...0203` PENDING_APPROVAL, `...0204` CANCELLED.

Mỗi lần chạy seed cập nhật lịch sự kiện theo UTC hiện tại để fixture không hết hạn. Event bán của A có khu SEATED 2×3 giá 300000, khu STANDING capacity 3 giá 200000 và khu miễn phí giá 0. Coupon gồm phần trăm 30%, fixed lớn/maxUses=1 và coupon hết hạn.

## Chạy

Fixture được chạy hai lần trong `SchemaConstraintsIT` để chứng minh idempotency. Không reset/drop database và không dùng dữ liệu người thật.
