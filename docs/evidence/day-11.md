# Bằng chứng Ngày 11 — Ghi nhận thanh toán, phát hành vé và sinh mã QR

## 1. Unit test & TDD

- Phân bổ số tiền từng vé qua `FulfillmentService` và `FulfillmentRepository`.
- Sinh mã QR cho vé qua thư viện ZXing (`QRCodeWriter`).
- Kiểm thử unit test: 93 tests toàn hệ thống đều PASS (`BUILD SUCCESS`).

## 2. SQL Integration Test

- File kiểm thử SQL: `database/tests/day-11.sql`
- Test nghiệm thu: `Day11IT` (`mvn -B -Psqlserver-it -Dtest=__none__ -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Day11IT verify`) — **PASS (1 test)**
- Xác nhận:
  - Gọi thủ tục `usp_ApplyPaymentResult` với thông tin thanh toán hợp lệ.
  - Đơn hàng chuyển sang trạng thái `PAID`.
  - Vé (`tc_tickets`) được phát hành tương ứng với `paid_amount` chính xác và mã hash bí mật QR (`qrSecretHashHex`).
  - Tồn kho `held_quantity` giảm về 0 và `sold_quantity` tăng 1.
  - Bản ghi giữ chỗ `tc_ticket_holds` chuyển sang `CONSUMED`.
