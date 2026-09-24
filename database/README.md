# Quy chuẩn SQL Server và migration

Nguồn: [CONVENTIONS](../docs/tasks/CONVENTIONS.md) và [database runbook](../docs/backend/database-runbook.md).

## 1. Cấu trúc

```text
database/
├── migrations/     # DDL/DML tuần tự, bất biến sau khi dùng chung
├── seeds/          # Fixture dev/test, không chứa dữ liệu thật
└── tests/          # Assertion SQL bằng THROW
```

Tên migration: `D<ngày>_<thứ-tự>_<mô-tả>.sql`, ví dụ `D02_01_tables.sql`. Thứ tự trong ngày: bảng → constraint/index → UDF/View → SP/trigger → grant. Không sửa migration đã áp dụng; tạo migration mới để sửa.

## 2. Lịch sử và checksum

Database có bảng kỹ thuật `dbo.tc_schema_migrations`:

```sql
CREATE TABLE dbo.tc_schema_migrations (
    version        varchar(100)  NOT NULL PRIMARY KEY,
    checksum_sha256 char(64)     NOT NULL,
    applied_at     datetime2(3)  NOT NULL DEFAULT SYSUTCDATETIME(),
    applied_by     sysname       NOT NULL DEFAULT ORIGINAL_LOGIN()
);
```

Runner `database/run-migrations.sh` xử lý từng file theo tên tăng dần:

1. Tính SHA-256 trên byte của file (`sha256sum database/migrations/*.sql`).
2. Nếu chưa có `version`, chạy file bằng `sqlcmd -b -X` với migration principal, rồi insert version/checksum trong cùng migration transaction khi file cho phép.
3. Nếu version đã có và checksum giống, bỏ qua.
4. Nếu version đã có nhưng checksum khác, dừng ngay; không chạy tiếp và không sửa lịch sử.
5. `sqlcmd -b` hoặc `THROW` khác 0 dừng batch. Migration chưa commit không được ghi lịch sử.

Runner tạo một wrapper duy nhất: mở transaction, lấy `sp_getapplock` tên `TicketsCenterMigration` với owner `Transaction`, kiểm toàn bộ checksum, include từng file chưa áp dụng và ghi lịch sử trước commit. Vì file được include trong transaction, migration không được chứa `GO` hoặc tự `BEGIN/COMMIT`; runner fail-fast nếu thấy `GO`.

```bash
# Chỉ kiểm tên/thứ tự/checksum, không kết nối
database/run-migrations.sh --plan

# Chạy thật khi đã có môi trường được phép
TC_SQL_TARGET_DB="$TC_SQL_DEV_DB" database/run-migrations.sh
```

## 3. Header và assertion

```sql
SET NOCOUNT ON;
SET XACT_ABORT ON;
```

Test phải fail thật:

```sql
IF @actual IS NULL OR @actual <> @expected
    THROW 51000, N'Assert failed: value mismatch', 1;
```

Không dùng `PRINT` làm assertion. Test trigger có câu lệnh nhiều dòng chứa một row sai; test SP phải có lỗi sau mutation để chứng minh rollback.

## 4. Stored procedure tham gia transaction caller

```sql
DECLARE @started bit = CASE WHEN @@TRANCOUNT = 0 THEN 1 ELSE 0 END;
IF @started = 1 BEGIN TRANSACTION;
ELSE SAVE TRANSACTION tc_savepoint;

BEGIN TRY
    -- validation, locks, mutation
    IF @started = 1 COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @started = 1 AND XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    ELSE IF @started = 0 AND XACT_STATE() = 1 ROLLBACK TRANSACTION tc_savepoint;
    -- Outer transaction uncommittable: THROW, caller thực hiện full rollback.
    THROW;
END CATCH;
```

SP độc lập commit transaction do nó mở; SP được JPA/SP khác gọi không commit transaction của caller. Lock order theo [locking.md](../docs/backend/locking.md).

## 5. Guard môi trường

- Dev, test và benchmark là ba database riêng. Reset chỉ được phép trên `TicketsCenter_Test` hoặc database tạm có tên được xác nhận rõ.
- Script destructive kiểm `DB_NAME()` allowlist và yêu cầu biến/operator flag explicit; không mặc định vào database hiện hành.
- Migration/fixture principal tách khỏi bốn runtime principal; secrets chỉ qua environment/secret store.
- Không ghi password, connection URL đầy đủ, OTP, QR hoặc token vào output/evidence.
