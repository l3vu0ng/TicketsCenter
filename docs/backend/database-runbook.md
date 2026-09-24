# Runbook SQL Server

> Nguồn: [SPEC](../references/SPEC.md) §8, §14 và [CONVENTIONS](../tasks/CONVENTIONS.md).
> Day 1 hiện chỉ nghiệm thu cấu hình fail-fast và quy trình an toàn. Kết nối SQL Server/`SELECT 1` được **chủ dự án loại khỏi lần nghiệm thu này** và phải bổ sung evidence khi có môi trường.

## 1. Môi trường tách biệt

| Môi trường | Database mặc định | Mục đích | Có được reset tự động? |
|---|---|---|:---:|
| Development | `TicketsCenter_Dev` | Phát triển local | Không, trừ khi operator xác nhận target |
| Integration | `TicketsCenter_Test` | `*IT.java`, SQL tests, concurrency | Có, chỉ bằng fixture principal và guard tên DB |
| Benchmark | `TicketsCenter_Bench` | Plan/logical reads/load | Có, sau khi xác nhận target |

Không dùng database demo/shared cho reset hoặc benchmark.

## 2. Biến môi trường

```text
TC_SQL_HOST
TC_SQL_PORT                 # mặc định vận hành: 1433
TC_SQL_DEV_DB
TC_SQL_TEST_DB
TC_SQL_BENCH_DB
TC_SQL_LOGIN
TC_SQL_PASSWORD
TC_SQL_MIGRATION_LOGIN
TC_SQL_MIGRATION_PASSWORD
TC_SQL_ENCRYPT              # true ngoài local
TC_SQL_TRUST_SERVER_CERT    # chỉ true cho local có chứng thư tự ký
TC_SQL_CONNECT_TIMEOUT_SEC  # đề xuất 5
```

Runtime thực tế tách credential theo principal: buyer, manager, check-in, admin, auth và worker. Migration dùng credential riêng, không được cấu hình làm datasource HTTP.

Không commit giá trị thật. Không truyền password bằng `sqlcmd -P`; nạp tạm qua `SQLCMDPASSWORD` và `unset` ngay sau lệnh.

## 3. Kiểm tra cấu hình Java

```bash
mvn -B -Psqlserver-it -Dit.test=DatabaseConnectionIT verify
```

- Thiếu biến bắt buộc: build đỏ với tên biến thiếu, không skip.
- Sai host/password: fail trong timeout và không in password/URL chứa secret.
- Khi có môi trường được phép: test mở JDBC TLS phù hợp và xác nhận `SELECT 1 = 1`.
- Không báo SQL integration PASS nếu chỉ build driver hoặc test bị skip.

## 4. Chạy migration an toàn

1. Xác nhận `TC_SQL_HOST`, database target và environment bằng output đã lọc; không in credential.
2. Kiểm target thuộc allowlist Dev/Test/Bench và không phải database shared/demo.
3. Dùng migration principal và đặt `TC_SQL_TARGET_DB` rõ ràng.
4. Chạy `database/run-migrations.sh --plan` để kiểm tên/thứ tự/SHA-256 mà không kết nối.
5. Chạy `database/run-migrations.sh`; runner dùng một transaction + `sp_getapplock`, đối chiếu `dbo.tc_schema_migrations`, include file chưa áp dụng và ghi history/checksum trước commit. Exit khác 0 hoặc checksum mismatch rollback/dừng toàn batch.
6. Chạy SQL test và application IT tương ứng; lưu command, exit code, commit SHA và log đã lọc.

Ví dụ local SQL authentication:

```bash
export TC_SQL_TARGET_DB="$TC_SQL_DEV_DB"
database/run-migrations.sh --plan
database/run-migrations.sh
unset TC_SQL_TARGET_DB
```

Entra dùng `-G` chỉ khi môi trường đã cấu hình. Không ghép `-G` với giả định local SQL login.

## 5. Reset integration database

Trước destructive operation, script phải kiểm `DB_NAME()`/target bằng giá trị explicit và chỉ chấp nhận `TicketsCenter_Test` hoặc tên test tạm theo quy ước. Operator phải thấy chính xác target sẽ drop/reset. Không dùng wildcard hoặc biến rỗng.

Thứ tự dựng mới: create test DB → migrations → grants → `test_fixtures.sql` → SQL assertions → Java IT. Fixture dùng email/UUID giả cố định, không dữ liệu người thật.

## 6. Transaction và quyền

- SP dùng savepoint khi caller đã có transaction; không commit transaction của caller.
- Bốn runtime role/login/user theo [security matrix](security-matrix.md); auth/worker/migration/fixture tách quyền.
- SQL write owner là SP01–SP17; Java không vừa dirty-write entity vừa gọi SP lặp cùng mutation.
- Một transaction giữ nguyên connection/principal; total connection pool bắt đầu tối đa 5 trên profile giới hạn.

## 7. Evidence còn phải bổ sung khi có SQL Server

| Case | Kết quả cần lưu |
|---|---|
| Cấu hình đúng | JDBC connect và `SELECT 1` thành công |
| Host sai | Fail trong timeout, message đã lọc |
| Password sai | Authentication fail, không lộ password |
| Thiếu biến | Maven profile đỏ, không skipped |
| Migration mới | history + checksum đúng, chạy lại no-op |
| Checksum bị sửa | runner dừng trước file tiếp theo |

Cho đến khi sáu case có log thật, phần SQL connection vẫn ghi `NOT RUN`, không được đổi thành PASS.
