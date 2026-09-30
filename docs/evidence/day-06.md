# Bằng chứng Ngày 06

- TDD đỏ: `mvn -q -Dtest=OrganizationServiceTest test` thất bại vì các DTO/Repository/Service chưa tồn tại.
- Unit xanh: `mvn -q -Dtest=OrganizationServiceTest,AdminServiceTest,OpenApiServletTest test` — exit 0.
- Toàn bộ unit/regression: `mvn -q test` — exit 0.
- Migration plan: `database/run-migrations.sh --plan` — 13 migration hợp lệ, không có `GO`; `git diff --check` — exit 0.
- SQL Server integration: `Day06IT` và `database/tests/day-06.sql` đã được tạo nhưng chưa chạy được; Azure SQL chặn IP hiện tại bằng firewall. Chưa coi TX01/TR06 là đã nghiệm thu integration cho tới khi lệnh `mvn -Psqlserver-it -Dit.test=Day06IT verify` chạy xanh.
