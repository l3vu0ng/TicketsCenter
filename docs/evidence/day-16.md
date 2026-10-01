# Bằng chứng Ngày 16 — Hủy sự kiện và tiếp tục sau restart

## TDD unit

- Red: `mvn -B -Dtest=EventCancellationServiceTest,EventCancellationJobTest test` thất bại biên dịch vì các seam ngày 16 chưa tồn tại.
- Green: test service/worker/servlet đều pass; `mvn -B test` chạy 99 test, 0 failure/error/skip, `BUILD SUCCESS`.
- Hành vi: manager bị chặn; admin hủy/đọc tiến độ; response không lộ buyer; worker batch 2 xử lý phần còn lại ở lần chạy kế tiếp theo thứ tự ổn định.

## SQL/integration trên SQL Server thật

- `database/tests/day-16.sql`: SP13 lặp an toàn, TX13 rollback khi outbox lỗi, mua/check-in bị chặn ngay, partial request tạo đủ nghĩa vụ không trùng, late payment tạo compensation, reject không khôi phục ACTIVE, TX17 rollback riêng Order, USED/UNKNOWN giữ progress chưa complete.
- `Day16IT`: chạy script trên SQL Server thật.
- `CancellationRaceIT`: hai connection đồng thời gọi SP13; kỳ vọng một Event CANCELLED và đúng một outbox.
- Lệnh nghiệm thu: `mvn -B -Psqlserver-it -Dtest=__none__ -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Day16IT,CancellationRaceIT verify` — 2 test, 0 failure/error/skip, `BUILD SUCCESS`.

## Trạng thái bằng chứng

- Unit/build và SQL Server integration/concurrency: đạt. Migration plan, JSON OpenAPI và `git diff --check` cũng pass.
