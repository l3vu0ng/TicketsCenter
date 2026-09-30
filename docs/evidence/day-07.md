# Bằng chứng Ngày 07

## TDD và unit test

- Red: `mvn -B -Dtest=EventServiceTest,LocalImageStorageTest test` thất bại biên dịch vì seam EventService/ImageStorage chưa tồn tại.
- Green: cùng lệnh chạy 4 test, 0 lỗi; kiểm lịch, giá 0, khu SEATED/STANDING, hàng A…AA, file giả và giới hạn byte.
- Regression: `mvn -B test` chạy 87 test, 0 failure/error/skipped.

## SQL và integration

- `database/migrations/014_add_event_management_api.sql`: thêm `rejection_reason` và quyền tối thiểu cho đường API Ngày 07.
- `database/tests/day-07.sql`: kiểm SP12, V01, audit đúng một dòng, cho đổi giá và chặn thay layout/ghế sau publish bằng statement nhiều dòng.
- Cấu hình trong `application.properties` đã được profile `sqlserver-it` nạp. Sau khi Azure SQL sẵn sàng, `database/run-migrations.sh` áp dụng các migration còn thiếu `006`–`014`; `mvn -B -Psqlserver-it -Dtest=__none__ -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Day07IT verify` chạy 1 test, 0 lỗi.
- Trong lúc áp dụng migration, đã sửa hai lỗi SQL nền được SQL Server phát hiện: table variable nullable primary key ở migration 009 và các phép cập nhật join dùng `version` mơ hồ ở migration 010.

## Cấu hình

- `event.image.directory` phải trỏ tới volume bền vững ngoài WAR; ví dụ production `/var/lib/ticketscenter/event-images`.
