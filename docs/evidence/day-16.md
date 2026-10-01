# Bằng chứng Ngày 16 — Hủy sự kiện và tiếp tục sau restart

## TDD unit

- Red: `mvn -B -Dtest=EventCancellationServiceTest,EventCancellationJobTest test` thất bại biên dịch vì các seam ngày 16 chưa tồn tại.
- Green: cùng lệnh chạy 3 test, 0 failure/error/skip, `BUILD SUCCESS`.
- Hành vi: manager bị chặn; admin hủy/đọc tiến độ; worker batch 2 xử lý phần còn lại ở lần chạy kế tiếp theo thứ tự ổn định.

## SQL/integration cần chạy trên SQL Server thật

- `database/tests/day-16.sql`: SP13 lặp an toàn, TX13 rollback khi outbox lỗi, mua/check-in bị chặn ngay, partial request tạo đủ nghĩa vụ không trùng, late payment tạo compensation, reject không khôi phục ACTIVE, TX17 rollback riêng Order, USED/UNKNOWN giữ progress chưa complete.
- `Day16IT`: chạy script trên SQL Server thật.
- `CancellationRaceIT`: hai connection đồng thời gọi SP13; kỳ vọng một Event CANCELLED và đúng một outbox.
- Lệnh nghiệm thu: `mvn -B -Psqlserver-it -Dtest=__none__ -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Day16IT,CancellationRaceIT verify`.

## Trạng thái bằng chứng

- Unit/build: đã chạy cục bộ; xem kết quả lệnh trong phiên triển khai.
- SQL Server integration/concurrency: chỉ đánh dấu đạt sau khi lệnh profile trên kết nối được database test thật; không coi compile/skip là bằng chứng SQL.
