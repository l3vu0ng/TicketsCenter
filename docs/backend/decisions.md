# Quyết Định Kỹ Thuật và Đề Xuất Phê Duyệt Thư Viện (Decisions Log)

> Ngày cập nhật: 2026-09-24  
> Nguồn đặc tả: [SPEC](../references/SPEC.md) §1–5; [CONVENTIONS](../tasks/CONVENTIONS.md)

---

## 1. Quyết Định Kỹ Thuật Nền Tảng

| Vấn đề | Quyết định | Rationale / Căn cứ |
|---|---|---|
| **Java Platform** | JDK 25 LTS, release 25 | Đáp ứng yêu cầu chuẩn hiện đại, tận dụng Virtual Threads cho background worker nếu cần |
| **Servlet Container** | Baseline Tomcat 11 / Servlet 6.1; smoke local ngày 2026-09-24 dùng Tomcat 11.0.22 | Namespace `jakarta.*`, kiến trúc monolithic MVC không dùng Spring Framework; evidence phải ghi phiên bản thực chạy |
| **Đóng gói** | Maven WAR (`finalName`: `ticketscenter`) | Chuẩn Tomcat deployment, context path cố định |
| **Database** | Microsoft SQL Server (2022+ / Azure SQL) | Hỗ trợ transaction đầy đủ, Stored Procedures, Views, Triggers, Table-valued Functions |
| **Đồng hồ hệ thống** | UTC (`Instant`, `datetime2`) | Mọi mốc thời gian lưu trữ và so sánh ở UTC; JSON API format ISO-8601 UTC |
| **Đơn vị tiền tệ** | VND dạng chuỗi số nguyên (`decimal(19,0)`) | Ví dụ `"300000"`, không dùng float/double để tránh sai số dấu phẩy động |
| **Định danh duy nhất** | UUID chuỗi (`uniqueidentifier` trong SQL) | Sinh mã không phụ thuộc auto-increment, tránh lộ số lượng bản ghi qua URL |
| **Phân trang** | `page >= 1`, mặc định `pageSize = 20`, tối đa `100` | Sắp xếp ổn định kèm khóa phụ `id` để tránh sót hoặc lặp bản ghi |
| **Giới hạn tải lên** | Ảnh bìa sự kiện tối đa 5MB, format JPEG/PNG/WebP | Kiểm tra kích thước và MIME type hợp lệ trước khi lưu trữ |
| **Nguồn model chuẩn** | SPEC hiện tại + `docs/classdiagram/diagram.md` + `model-map.md` là baseline 23 lớp | QEA hiện chỉ có use case; diagram 37 lớp trong lịch sử Git thuộc thiết kế cũ và mâu thuẫn SPEC §5. Baseline ngày 1 chốt lại attributes/methods/relations trong hai file hiện hành thay vì tuyên bố đã đối chiếu một nguồn 22 lớp không tồn tại |

---

## 2. Dependency hiện có và ứng viên chưa duyệt

Không thêm hoặc nâng dependency trong lần sửa tài liệu này. Ba mục đánh dấu **hiện có** đã nằm trong `pom.xml`; các mục còn lại chỉ là ứng viên và không được thêm trước khi chủ dự án duyệt.

| Thư viện / Plugin | GroupId:ArtifactId | Phiên bản ứng viên | Phạm vi (Scope) | Mục đích sử dụng |
|---|---|:---:|:---:|---|
| **Servlet API — hiện có** | `jakarta.servlet:jakarta.servlet-api` | `6.1.0` | `provided` | Chuẩn Servlet 6.1 cho Tomcat 11 |
| **JUnit 5 — hiện có** | `org.junit.jupiter:junit-jupiter` | `5.12.0` | `test` | Kiểm thử đơn vị (Unit Test) |
| **MSSQL JDBC — hiện có** | `com.microsoft.sqlserver:mssql-jdbc` | `12.8.1.jre11` | `test` | Chỉ dùng profile integration; chưa chứng minh kết nối thật |
| **JSON Processor** | `com.fasterxml.jackson.core:jackson-databind` | `2.18.2` | `compile` | Serialize/deserialize JSON UTF-8 an toàn |
| **Connection Pool** | `com.zaxxer:HikariCP` | `6.2.1` | `compile` | Pool kết nối hiệu năng cao cho Servlet/JPA |
| **Hibernate Core** | `org.hibernate.orm:hibernate-core` | `6.6.9.Final` | `compile` | JPA Provider theo kiến trúc resource-local |
| **QR Code** | `com.google.zxing:core` | `3.5.3` | `compile` | Sinh mã QR vé tham dự |

### Plugin Maven Build:
- `maven-compiler-plugin:3.14.0` (Java release 25)
- `maven-war-plugin:3.4.0` (WAR packaging, `failOnMissingWebXml: false`)
- `maven-surefire-plugin:3.5.2` (Unit testing `*Test.java`)
- `maven-failsafe-plugin:3.5.2` (Acceptance & Concurrency testing `*IT.java` với profile `sqlserver-it`)
