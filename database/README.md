# Quy Chuẩn Quản Lý Database và Migration Script

> Thư mục chứa toàn bộ mã nguồn SQL Server của dự án TicketsCenter theo [CONVENTIONS](../docs/tasks/CONVENTIONS.md).

---

## 1. Cấu Trúc Thư Mục

```text
database/
├── migrations/     # Các script DDL/DML thay đổi schema tuần tự
├── seeds/          # Dữ liệu mẫu phục vụ kiểm thử (fixtures)
└── tests/          # Script kiểm thử SQL độc lập (chạy qua sqlcmd)
```

---

## 2. Quy Tắc Đặt Tên Script

- **Migrations:** Đánh số tăng dần theo Ngày và Thứ tự thực thi:
  - `D02_01_tables.sql`: Tạo bảng, kiểu dữ liệu, khóa chính, khóa ngoại.
  - `D02_02_constraints.sql`: Ràng buộc C01–C20, check constraint, unique index.
  - `D06_01_organization.sql`: Thêm nghiệp vụ tổ chức, Stored Procedures, Views.
  - **Quy tắc bất biến:** Không sửa migration đã áp dụng chung; mọi thay đổi tiếp theo phải tạo file migration mới.

- **Seeds:**
  - `dev_seed.sql`: Dữ liệu cho môi trường phát triển cục bộ.
  - `test_fixtures.sql`: Dữ liệu kiểm thử dùng chung theo quy định (2 tổ chức A/B, các loại buyer, event, coupon).

- **Tests:**
  - `day-NN.sql`: Chạy kiểm thử tự động cho ngày tương ứng (ví dụ `day-08.sql`).
  - `inventory.sql`: Kiểm kê danh mục View, SP, Function, Trigger đã tạo.

---

## 3. Quy Chuẩn Script SQL Server

Mọi script chạy trong hệ thống phải tuân thủ:
1. Thiết lập đầu file:
   ```sql
   SET NOCOUNT ON;
   SET XACT_ABORT ON;
   ```
2. Mọi kiểm thử phải dùng `THROW` khi sai điều kiện, không chỉ dùng `PRINT`:
   ```sql
   IF @actual IS NULL OR @actual <> @expected
       THROW 51000, N'Assert failed: value mismatch', 1;
   ```
3. Xử lý Transaction trong Stored Procedure:
   ```sql
   BEGIN TRY
       BEGIN TRANSACTION;
       -- Logic nghiệp vụ
       COMMIT TRANSACTION;
   END TRY
   BEGIN CATCH
       IF XACT_STATE() <> 0
           ROLLBACK TRANSACTION;
       THROW;
   END CATCH;
   ```
