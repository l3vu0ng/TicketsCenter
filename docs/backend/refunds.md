# Hoàn tiền mô phỏng

`SimulatedRefundGateway` chỉ mô phỏng provider, không chuyển tiền thật. `submit(refundId, amount)` ghi trạng thái kỹ thuật vào file trước khi trả response; cùng `refundId` luôn dùng reference `SIM-{refundId}` và không tạo nghĩa vụ thứ hai. `query(reference)` đọc lại trạng thái đó sau restart.

Outcome `SUCCEEDED`, `FAILED` hoặc `UNKNOWN` và chế độ timeout-after-side-effect chỉ lấy từ cấu hình server. Buyer không có trường HTTP để chọn outcome. Timeout không được đổi thành `FAILED`: `RefundJob` để lần chạy sau đọc trạng thái bền vững trước khi gọi SP11.

Worker đọc `vw_RefundWork`, gọi adapter ngoài transaction database và áp dụng kết quả đã xác minh bằng `dbo.usp_ApplyRefundResult`. SP11 khóa Payment và mọi Refund liên quan, chặn tổng hoàn thành công vượt Payment, trả kho/vô hiệu QR đúng một lần cho `CUSTOMER_REFUND`; `PAYMENT_COMPENSATION` chỉ cập nhật nghĩa vụ tiền. Email hoàn tất đi qua outbox `REFUND_SUCCEEDED` sau commit.

Admin có thể gọi `POST /api/admin/refund-requests/{id}/retry` hoặc `POST /api/admin/payments/{id}/compensation/retry`. Retry chỉ tạo attempt mới khi attempt cũ đã `FAILED`; `PENDING`, `UNKNOWN` và `SUCCEEDED` không được nhân đôi.
