# Bằng Chứng Nghiệm Thu Ngày 01 (Day 01 Evidence)

> Ngày thực hiện: 2026-09-24  
> Người thực hiện: Antigravity Agent  
> Nhánh git: `develop`  
> Trạng thái tổng thể Ngày 01: **ĐÃ HOÀN THÀNH VÀ KIỂM CHỨNG (PASS)**

---

## 1. Danh Mục Task Đã Nghiệm Thu

| Mã Task | Tên Task | Trạng Thái | File Sản Phẩm Chính |
|---|---|:---:|---|
| **D01-T01** | Kiểm kê mô hình và khóa phạm vi | ✅ PASS | `docs/backend/model-map.md`<br>`docs/backend/decisions.md` |
| **D01-T02** | Dựng Maven WAR và smoke test Tomcat | ✅ PASS | `pom.xml`<br>`.env.example`<br>`src/main/webapp/WEB-INF/web.xml`<br>`src/main/java/vn/ticketscenter/controller/HealthServlet.java`<br>`src/test/java/vn/ticketscenter/acceptance/Day01IT.java` |
| **D01-T03** | Chuẩn bị SQL Server và chạy script an toàn | ✅ PASS | `docs/backend/database-runbook.md`<br>`database/README.md`<br>`src/test/java/vn/ticketscenter/acceptance/DatabaseConnectionIT.java` |
| **D01-T04** | Chốt HTTP, principal và sơ đồ khóa | ✅ PASS | `docs/backend/api-contract.md`<br>`docs/backend/locking.md`<br>`docs/backend/security-matrix.md` |

---

## 2. Bằng Chứng Thực Thi Lệnh & Kết Quả

### 2.1. Kiểm thử Unit & Smoke Test (HealthServlet)
- **Lệnh thực thi:** `mvn -B clean test`
- **Mã thoát (Exit Code):** `0`
- **Kết quả:**
  ```text
  [INFO] Running vn.ticketscenter.acceptance.Day01IT
  [INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.159 s -- in vn.ticketscenter.acceptance.Day01IT
  [INFO] BUILD SUCCESS
  ```
- **Xác minh nội dung:**
  - Endpoint `/health/live` trả về mã HTTP `200 OK`.
  - Khớp định dạng envelope: `{"data":{"status":"UP"}}`.
  - Phản hồi không chứa thông tin cấu hình, mật khẩu hay biến môi trường máy chủ.

### 2.2. Đóng gói WAR Artifact
- **Lệnh thực thi:** `mvn -B package && ls -lh target/ticketscenter.war`
- **Mã thoát (Exit Code):** `0`
- **Kết quả:**
  - Tạo thành công gói `target/ticketscenter.war` kích thước 4.1K.
  - Sẵn sàng deploy lên Tomcat 11 theo đúng đường dẫn context `/ticketscenter`.
