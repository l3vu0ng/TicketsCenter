# Sổ Tay Vận Hành Cơ Sở Dữ Liệu SQL Server (Database Runbook)

> Ngày cập nhật: 2026-09-24  
> Nguồn: [CONVENTIONS](../tasks/CONVENTIONS.md); [SPEC](../../references/SPEC.md) §14

---

## 1. Môi Trường Cơ Sở Dữ Liệu

Dự án tách biệt ba môi trường database độc lập để đảm bảo an toàn dữ liệu:

1. **Development (`TicketsCenter_Dev`):** Dành cho lập trình viên chạy thử nghiệm cục bộ.
2. **Integration Test (`TicketsCenter_Test`):** Dành cho CI/CD và chạy bộ kiểm thử `*IT.java` hoặc `database/tests/day-NN.sql`. Database này có thể được tự động tạo lại và reset an toàn.
3. **Benchmark (`TicketsCenter_Bench`):** Dành cho đo đạc hiệu năng chỉ mục, tải đồng thời cao ở Day 19–20.

> [!CAUTION]
> Tuyệt đối không chạy script xóa/reset dữ liệu trên database demo hoặc database dùng chung khi chưa có xác nhận target cụ thể.

---

## 2. Biến Môi Trường Kết Nối

Cấu hình kết nối được đọc từ biến môi trường của hệ thống, không lưu password hay connection string trong code Git:

```bash
TC_SQL_HOST=localhost
TC_SQL_PORT=1433
TC_SQL_DEV_DB=TicketsCenter_Dev
TC_SQL_TEST_DB=TicketsCenter_Test
TC_SQL_LOGIN=sa
# Mật khẩu được nạp an toàn, ví dụ qua biến môi trường hoặc file .env cục bộ
TC_SQL_PASSWORD=YourStrong@Passw0rd
```

---

## 3. Lệnh Thực Thi Bằng `sqlcmd`

Sử dụng lệnh `sqlcmd` với mật khẩu truyền an toàn qua biến `SQLCMDPASSWORD` (không dùng tham số `-P` lộ trên tiến trình hệ điều hành):

```bash
# Thiết lập biến an toàn cho phiên làm việc
export SQLCMDPASSWORD="$TC_SQL_PASSWORD"

# 1. Chạy một file migration
sqlcmd -S "$TC_SQL_HOST,$TC_SQL_PORT" -d "$TC_SQL_DEV_DB" -U "$TC_SQL_LOGIN" -b -i database/migrations/D02_01_tables.sql

# 2. Chạy file kiểm thử ngày
sqlcmd -S "$TC_SQL_HOST,$TC_SQL_PORT" -d "$TC_SQL_TEST_DB" -U "$TC_SQL_LOGIN" -b -i database/tests/day-08.sql

# Xóa biến môi trường sau khi dùng
unset SQLCMDPASSWORD
```

---

## 4. Kiểm Thử Kết Nối Tích Hợp (Java Integration)

Chạy integration test qua Maven profile `sqlserver-it`:

```bash
# Chạy integration test kiểm tra kết nối DB
mvn -B -Psqlserver-it -Dit.test=DatabaseConnectionIT verify
```

Profile `sqlserver-it` được cấu hình fail rõ ràng nếu thông số kết nối sai hoặc cơ sở dữ liệu không khả dụng, không skip âm thầm.
