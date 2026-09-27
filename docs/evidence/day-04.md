# Bằng chứng ngày 04 — Tài khoản, session và phân quyền

**Ngày chạy:** 2026-09-27  
**Trạng thái:** PARTIAL — code, unit test, SQL integration và migration quyền đã PASS; HTTP cookie-jar trên Tomcat/proxy chưa chạy.

## Kết quả

| Phạm vi | Trạng thái | Bằng chứng |
|---|---|---|
| D04-T01 — đăng ký/hash | PASS | `PasswordHasherTest`, `AccountValidationTest`, `Day04IT`: email được chuẩn hóa, duplicate chỉ còn một row, PBKDF2 có salt, DB không lưu plaintext, user chưa verified |
| D04-T02 — login/session | PASS contract / PARTIAL container | `SessionServiceTest`: đổi session ID và logout cô lập; `Day04IT`: đúng/sai password, DISABLED và reload current user; chưa chạy cookie jar trên Tomcat thật |
| D04-T03 — HTTP security/quyền | PASS baseline | `CsrfServiceTest`, `HttpSecurityTest`, `AuthorizationServiceTest`: CSRF, giới hạn 64 KiB, deny mặc định và membership lấy từ DB; migration `005` đã applied |
| D04-T04 — admin seed | PASS seed / PARTIAL HTTP | `AdminSeederTest` và `Day04IT`: production secret fail-fast, seed hai lần giữ nguyên hash và alias admin; chưa có HTTP container flow csrf→register→login→me→logout |

## Lệnh và kết quả đã quan sát

```text
mvn -B -Dtest=PasswordHasherTest,AccountValidationTest,SessionServiceTest,CsrfServiceTest,JsonObjectParserTest,HttpSecurityTest,AdminSeederTest,AuthorizationServiceTest test
→ PASS trước khi các file Day 5 xuất hiện trong workspace

mvn -B -Psqlserver-it -Dit.test=Day04IT verify
→ PASS: 2 tests, 0 failures/errors/skips (lượt đầu)

JdbcMigrationRunner.java với riêng database/migrations/005_grant_auth_baseline.sql
→ Applied 005_grant_auth_baseline
```

## Việc chưa đủ bằng chứng

- Chưa chạy WAR trên Tomcat bằng HTTP client giữ cookie để chứng minh toàn luồng và thuộc tính cookie dưới HTTPS/proxy.
- Lượt xác minh cuối bị chặn tạm thời bởi `OtpTest` Day 5 đang được chỉnh đồng thời và không thuộc phạm vi Day 4; phải chạy lại toàn bộ Maven sau khi nhánh làm việc ổn định.
