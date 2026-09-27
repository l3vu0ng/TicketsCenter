# Bằng chứng ngày 04 — Tài khoản, session và phân quyền

**Ngày chạy:** 2026-09-27  
**Trạng thái:** PASS local — code, unit test, SQL integration, migration và HTTP cookie-jar trên Tomcat đã PASS; HTTPS/proxy ngoài môi trường local chưa chạy.

## Kết quả

| Phạm vi | Trạng thái | Bằng chứng |
|---|---|---|
| D04-T01 — đăng ký/hash | PASS | `PasswordHasherTest`, `AccountValidationTest`, `Day04IT`: email được chuẩn hóa, duplicate chỉ còn một row, PBKDF2 có salt, DB không lưu plaintext, user chưa verified |
| D04-T02 — login/session | PASS local | `SessionServiceTest`: đổi session ID và logout cô lập; `Day04IT`: đúng/sai password, DISABLED và reload current user; Tomcat cookie-jar xác nhận login→me→logout→401 |
| D04-T03 — HTTP security/quyền | PASS baseline | `CsrfServiceTest`, `HttpSecurityTest`, `AuthorizationServiceTest`: CSRF, giới hạn 64 KiB, deny mặc định và membership lấy từ DB; migration `005` đã applied |
| D04-T04 — admin seed/HTTP | PASS | `AdminSeederTest` và `Day04IT`: production secret fail-fast, seed hai lần giữ nguyên hash và alias admin; Tomcat flow csrf→register→login→me→logout đã pass |

## Lệnh và kết quả đã quan sát

```text
mvn -B clean package
→ PASS: 45 tests, 0 failures/errors/skips; WAR chứa đầy đủ class và dependency

mvn -B -Psqlserver-it -Dit.test=Day04IT verify
→ PASS: 45 unit tests + 3 Day04IT, 0 failures/errors/skips

JdbcMigrationRunner.java với riêng database/migrations/005_grant_auth_baseline.sql
→ Applied 005_grant_auth_baseline

Tomcat 11 + curl cookie jar
→ csrf=200 register=200 login=200 me=200 logout=200 after_logout=401
→ Set-Cookie có HttpOnly và SameSite=Lax trên HTTP local
```

## Việc chưa chạy

- Chưa có môi trường HTTPS/reverse proxy online để quan sát trực tiếp thuộc tính Secure; Tomcat local chỉ xác nhận HttpOnly và SameSite=Lax.
