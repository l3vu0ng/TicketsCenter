# Bằng chứng nghiệm thu ngày 01

> Ngày kiểm tra: 2026-09-24
> Nhánh: `develop`
> Ngoại lệ đã duyệt: không kết nối SQL Server trong lần kiểm tra này. Mọi case cần database thật giữ `NOT RUN`, không ghi PASS.

## 1. Kết quả theo task

| Task | Trạng thái | Bằng chứng |
|---|---|---|
| D01-T01 — model | PASS theo baseline SPEC hiện tại | Diagram có 23 class và các quan hệ bắt buộc; model-map có 23 dòng duy nhất, enum và ba luồng. QEA chỉ có use case; diagram 37 lớp trong lịch sử là thiết kế cũ, nên không tuyên bố đã kiểm một nguồn 22 lớp không tồn tại |
| D01-T02 — WAR/Tomcat | PASS | Maven build tạo WAR; `scripts/verify-tomcat.sh` deploy, gọi health, dừng, redeploy và gọi health lần hai trên Tomcat thật |
| D01-T03 — SQL preparation | PARTIAL / SQL NOT RUN | Runbook, environment names, migration runner/history/checksum, transaction savepoint và test fail-fast có sẵn; runner chỉ syntax-check do chưa có migration/SQL Server; không có bằng chứng connect/SELECT 1 theo ngoại lệ đã duyệt |
| D01-T04 — HTTP/security/locking | PASS (document contract) | UI-01…UI-24 khớp API-MAP; app roles và 4 DB principals + technical principals; flow lock/transaction/lost-response đầy đủ |

## 2. Toolchain thực tế

| Thành phần | Giá trị đã dùng |
|---|---|
| Java | `25.0.4` |
| Maven | `3.9.16` |
| Tomcat | `11.0.22 (Ubuntu)` |
| Servlet API compile | `6.1.0`, scope `provided` |
| WAR | `target/ticketscenter.war`, context `/ticketscenter` |

Tomcat 11.0.22 là phiên bản thật có trên máy kiểm tra; không ghi 11.0.25 như thể đã chạy. Baseline dự án vẫn là Tomcat 11/Servlet 6.1 và cần kiểm lại nếu môi trường deploy dùng patch version khác.

## 3. Lệnh kiểm chứng

### 3.1. Unit/contract test và đóng gói

```bash
mvn -B clean verify
```

Kỳ vọng/đã quan sát: exit `0`, `Day01IT` chạy 1 test, không skipped; WAR được tạo. `Day01IT` là servlet contract test trực tiếp, **không được dùng riêng nó làm bằng chứng deploy Tomcat**.

### 3.2. Deploy và redeploy Tomcat

```bash
scripts/verify-tomcat.sh
```

Script tạo `CATALINA_BASE` tạm, dùng cổng `18080` mặc định, deploy `ticketscenter.war`, gọi:

```text
GET http://127.0.0.1:18080/ticketscenter/health/live
200 {"data":{"status":"UP"}}
```

Sau đó script dừng Tomcat, xóa exploded/work cache, copy WAR lại, khởi động và kiểm tra cùng endpoint lần hai. Kết quả:

```text
PASS: WAR deploy và redeploy trả HTTP 200 với JSON health cố định.
```

Thư mục tạm được dọn bằng trap; không chạm instance/system webapps đang chạy.
Script kiểm cổng phải trống trước mỗi lần start và kiểm child PID còn sống trước khi chấp nhận health. Ca negative dùng một HTTP server khác chiếm cổng 18081: script exit `1` với thông báo không thể chứng minh health thuộc Tomcat test.

### 3.3. SQL profile thiếu cấu hình

```bash
env -u TC_SQL_HOST -u TC_SQL_PORT -u TC_SQL_TEST_DB \
    -u TC_SQL_LOGIN -u TC_SQL_PASSWORD \
    mvn -B -Psqlserver-it -Dit.test=DatabaseConnectionIT verify
```

Kỳ vọng: exit khác `0`, lỗi nêu tên biến thiếu và không skipped thành xanh. Đây chỉ chứng minh fail-fast. Không chứng minh driver kết nối được SQL Server.

Các case `SELECT 1`, host sai và password sai: **NOT RUN theo phạm vi đã duyệt**.

### 3.4. Migration runner và cấu trúc tài liệu

```bash
bash -n database/run-migrations.sh scripts/verify-tomcat.sh
git diff --check
```

Kết quả: exit `0`. Runner migration có chế độ `--plan`, checksum/history và application lock nhưng chưa chạy với migration/SQL Server thật; không suy diễn syntax-check thành SQL PASS.

## 4. Kiểm tra tài liệu

- Diagram/model-map: đúng 23 lớp; gồm quan hệ Event/Organization–CommissionRule, EventCategory–Event, OrganizationRequest–User/Organization và CheckIn–Ticket 0..1. Baseline attributes/methods được chốt trong model-map vì repo không có diagram 22 lớp để đối chiếu độc lập.
- API contract: đủ 24 mã UI, không tái định nghĩa UI-02 thành search hoặc UI-04 thành register.
- Security: role tổ chức chỉ `MANAGER`, `CHECK_IN_STAFF`; khả năng buyer chồng lên membership; browser không chọn DB principal.
- Locking: có Hold cũ khác Event, coupon A↔B, payment, refund, cancel, settlement/payout, pool tổng và khôi phục sau commit chưa rõ.
- Database: SP độc lập mới commit; SP trong transaction ngoài dùng savepoint và không commit transaction caller.

## 5. Phần chưa nghiệm thu

Không có SQL Server được cấu hình trong lần chạy này. Vì vậy D01-T03 chưa PASS toàn phần và D02/D03 không được dùng tài liệu này để tuyên bố schema, quyền, transaction hoặc concurrency đã chạy thật.
