# Bằng chứng ngày 03 — Model, JPA và transaction

**Ngày chạy:** 2026-09-27
**Trạng thái:** PARTIAL — phần local, Hibernate validate và role migration đã PASS; provisioning login/user riêng và rollback ngoài Stored Procedure chưa chạy vì chưa có credential/runtime SP tương ứng.

## Kết quả

| Phạm vi | Trạng thái | Bằng chứng |
|---|---|---|
| D03-T01 — 23 entity | PASS baseline | `ModelMappingTest`: đúng 23 `@Entity`/table; `ModelInvariantTest`: lịch Event, giá/capacity Zone; `Day03IT`: Hibernate validate 23 entity với SQL Server |
| D03-T02 — transaction/lifecycle | PASS local / PARTIAL SQL | `TransactionManagerTest`: begin→flush→commit→close và rollback→close; pool theo principal, tổng budget 5; chưa có SP runtime để chứng minh outer rollback/clear sau SP |
| D03-T03 — principal | PASS role / BLOCKED login | migration `004_create_runtime_roles` đã applied; `Day03IT` xác nhận 6 role và không có broad DML/DDL; script local/Azure có sẵn nhưng chưa provision user/login vì chưa có 6 credential/identity |
| D03-T04 — validation/readiness | PASS baseline | `InputParserTest` từ chối UUID/VND sai trước persistence; `ReadinessServletTest` xác nhận 503 không lộ DB detail; liveness vẫn độc lập DB |

## Lệnh đã chạy

```text
mvn -B test
→ PASS: 18 tests, 0 failures, 0 errors, 0 skipped

./database/run-migrations.sh
→ Applied 004_create_runtime_roles

mvn -B -Psqlserver-it verify
→ PASS: 18 unit tests + 11 integration tests (gồm 2 Day03IT), 0 failures/errors/skips
```

Lần gọi migration đầu tiên bị SQL Server trả `database is not currently available`; không có migration mới được báo applied. Lần chạy lại sau khi database online đã áp dụng `004` thành công.

## Việc chưa đủ bằng chứng

- Chưa chạy `database/security/local-logins.sql` hoặc `azure-users.sql`: cần credential/identity riêng cho buyer, manager, check-in, admin, auth và worker.
- Chưa có Stored Procedure nghiệp vụ runtime để kiểm tra outer transaction rollback và persistence-context refresh; thực hiện cùng SP đầu tiên, không tạo SP giả chỉ để tick checklist.
- Chưa chạy kiểm tra reuse connection với actor session context; cần hoàn tất khi Service bắt đầu truyền actor ở D04.
