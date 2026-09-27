# Bằng chứng schema và constraint

Ngày chạy: 2026-09-27. Nhánh kiểm tra: `develop`. Không ghi credential, connection string hoặc dữ liệu bí mật vào tài liệu này.

## ERD và từ điển dữ liệu

| Case | Kỳ vọng | Thực tế | Trạng thái |
|---|---|---|---|
| Kiểm kê model | 23 lớp nghiệp vụ có mapping vật lý duy nhất | `business_tables=23`, `physical_mappings=23` | PASS |
| Kiểm kê bảng | 23 bảng nghiệp vụ và bảng kỹ thuật được mô tả | 29 bảng gồm migration history | PASS |
| Chính sách xóa | Không cascade-delete lịch sử | Không có `ON DELETE CASCADE`; mọi FK schema nền là `NO ACTION` | PASS |
| Kiểu/null/default/FK | Người viết DDL không phải đoán cột | `docs/backend/data-dictionary.md` ghi từng cột và quan hệ | PASS |

Lệnh kiểm tra đã chạy:

```text
rg -c '^\| [0-9]+ \|' docs/backend/model-map.md                  # 23
rg -c '^### `tc_' docs/backend/data-dictionary.md                 # 29
rg -c '\| `tc_[a-z_]+` \| D02' docs/backend/model-map.md         # 23
rg -n -i 'on delete cascade' docs/backend/{erd,data-dictionary,model-map}.md  # no matches
```

## Migration và C01–C20

| Case | Kỳ vọng | Thực tế | Trạng thái |
|---|---|---|---|
| TDD RED schema | Test catalog thất bại trước migration | Thiếu toàn bộ 29 bảng được liệt kê | PASS |
| Migration lần đầu | Tạo bảng, FK, constraint trong một transaction | `001_create_ticketing_schema` và `002_add_ticketing_constraints` được áp dụng | PASS |
| Migration lần hai | Không thực thi lại file đã áp dụng | Exit 0, không có dòng `Applied` | PASS |
| Checksum thay đổi | Dừng trước khi chạy DDL | `Migration checksum mismatch: 002_add_ticketing_constraints`, exit 1; file được khôi phục nguyên checksum | PASS |
| TDD RED C06 NULL | Coupon không có giá trị giảm phải bị chặn | Hai nhánh NULL lọt qua constraint cũ; regression `C06 NULL ... escaped` thất bại | PASS |
| Forward-fix C06 | Không sửa checksum migration đã áp dụng | `003_enforce_coupon_discount_values` thay constraint và hai regression NULL pass | PASS |
| Catalog constraint | Đủ tên hợp đồng C01–C20 | `SchemaConstraintsIT.databaseContainsRequiredConstraintCatalog` pass | PASS |
| Hành vi constraint | Nhận fixture hợp lệ; chặn C01–C20, NULL, enum sai, FK sai | SQL test rollback toàn bộ, JUnit pass | PASS |
| Tiền bằng 0 | Payment/Refund/Payout bị chặn; Ticket/Zone được nhận | SQL behavioral test pass | PASS |

Migration dùng `sp_getapplock` và checksum SHA-256. Máy không có `sqlcmd`, nên runner dùng JDBC driver đã có trong Maven cache; không thêm dependency.

## Seed và integration harness

| Case | Kỳ vọng | Thực tế | Trạng thái |
|---|---|---|---|
| TDD RED fixture | Test thất bại trước khi có seed | `NoSuchFileException: database/seeds/ticketing-test-fixtures.sql` | PASS |
| TDD RED trạng thái User | Fixture phải có buyer chưa xác minh và User DISABLED | Test yêu cầu 4 user thất bại với actual 2 | PASS |
| Idempotency | Chạy seed hai lần không nhân User/Organization/Membership | Count lần lượt 4/2/3; có 1 ACTIVE chưa xác minh và 1 DISABLED | PASS |
| Credential | Không có password/hash cố định trong SQL | PBKDF2-SHA256 tạo trong Java từ `TC_TEST_PASSWORD` hoặc credential ngẫu nhiên tạm, truyền qua session context | PASS |
| Dữ liệu dùng chung | Hai tổ chức, role chéo, trạng thái Event, 2×3 Seat, standing capacity 3, coupon biên | `SchemaConstraintsIT.fixtureIsIdempotent` pass | PASS |
| UTF-8 | Chuỗi tiếng Việt round-trip | `Đêm nhạc Việt` đọc lại nguyên vẹn | PASS |

## Quyền schema và mapping

| Case | Kỳ vọng | Thực tế | Trạng thái |
|---|---|---|---|
| 23 mapping | Đúng 23 bảng nghiệp vụ | `SchemaMappingIT.businessMappingAndDeletePolicyMatchContract` pass | PASS |
| Nullability | CheckIn.ticket, OrderItem.seat, Refund.request optional; Payment.order/Ticket.orderItem bắt buộc | Catalog assertions pass | PASS |
| Chính sách xóa | Không cascade FK | `delete_referential_action <> 0` trả 0 | PASS |
| Kiểu JDBC | `decimal(19,0)` và UTC `datetime2(3)` round-trip | BigDecimal 9e18 và Instant millisecond pass | PASS |
| Hibernate | Schema do migration sở hữu, runtime dùng `validate` | Mapping/config policy đã khóa; chạy Hibernate validate thực tế ở task entity khi dependency được duyệt | PASS theo phạm vi chuẩn bị mapping |
| Quyền | Runtime không có DDL/hard-delete; grant theo View/SP khi object tồn tại | `security-matrix.md` mục 8 | PASS |

## Lệnh và kết quả cuối

```text
mvn -B test
  Tests run: 3, Failures: 0, Errors: 0, Skipped: 0

database/run-migrations.sh --plan
  3 migration chức năng, exit 0

database/run-migrations.sh
  chạy lại no-op, exit 0

mvn -B -Psqlserver-it -Dit.test=SchemaConstraintsIT,SchemaMappingIT verify
  Tests run: 7, Failures: 0, Errors: 0, Skipped: 0

git diff --check; bash -n database/run-migrations.sh; quét tên artefact/bí mật
  FINAL_STATIC_CHECKS_OK
```
