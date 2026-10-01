# Bằng chứng Ngày 15

## TDD

- RED `RefundGatewayTest`: thiếu boundary/gateway; GREEN: restart đọc lại `SUCCEEDED`, timeout sau side effect không tạo file thứ hai.
- RED `RefundJobTest`: thiếu job/repository; GREEN: adapter được gọi ngoài transaction và restart áp dụng kết quả đúng một lần.
- RED `RefundServiceTest`: thiếu service/retry methods; GREEN: buyer bị từ chối, admin đi đúng hai đường retry rõ ràng.
- RED `RefundOperationsServletTest`: route retry chưa có dependency test seam; GREEN: buyer nhận 403 trước khi chạm repository.
- `OutboxWorkerTest`: email lỗi chuyển `FAILED` và được chọn lại để gửi, không chạy lại SP11.

## Hợp đồng và an toàn

- Migration `016_complete_refund_processing.sql` tạo hai view worker hẹp, index hàng đợi và hoàn thiện SP11/TX11 với khóa Payment/Refund/Ticket, giới hạn tổng tiền, idempotency và rollback nguyên tử.
- HTTP không nhận trạng thái provider; worker duy nhất có quyền gọi SP11. Retry giữ attempt `FAILED` cũ để audit.

## Lệnh kiểm chứng

- `mvn -B -Dtest=RefundGatewayTest,RefundJobTest,RefundServiceTest,RefundOperationsServletTest,OutboxWorkerTest test`
- `mvn -B test`
- `mvn -B -Psqlserver-it -Dit.test=Day15IT,RefundResultConcurrencyIT verify`

Kết quả hiện tại:
- Unit suite: `106/106` xanh (`mvn -B test`).
- SQL Server integration & concurrency (`Day15IT`, `RefundResultConcurrencyIT`): `2/2` passed trên SQL Server thật (`BUILD SUCCESS`), chứng minh:
  - `Day15IT` (`day-15.sql`): Hoàn vé 160k/240k thành công, coupon consumed giữ nguyên, compensation không tăng inventory, TR08 chặn sửa immutable paid_amount trên multirow update, zero refund xử lý đúng không sinh dòng refund.
  - `RefundResultConcurrencyIT`: Hai worker đồng thời gọi `dbo.usp_ApplyRefundResult` trên cùng refund record; kết quả đạt trạng thái `SUCCEEDED`, kho hoàn đúng 1 lần (`sold_quantity = 0`), vé chuyển `REFUNDED` và outbox idempotency key `refund-succeeded:*` sinh đúng 1 bản ghi duy nhất.
