# Bằng chứng Ngày 18 — báo cáo, CSV và audit

**Trạng thái:** Java/unit và SQL Server integration đã kiểm chứng. Inventory đã phát hiện ba caller còn thiếu và chuyển rõ sang D19, không tính các object đó là hoàn tất ở mapping.

## TDD đỏ và xanh

| Ca | Đỏ trước sửa | Xanh sau sửa |
|---|---|---|
| Report filter/scope | Test compile fail vì chưa có `ReportFilter`/`ReportService` | Range `[from,to)`, tối đa 366 ngày, page 1–100, sort allowlist và khóa org manager đều pass |
| CSV Excel safety | Hai assertion fail vì dấu nháy đơn được chèn sau whitespace/control | Chèn dấu nháy đơn ở đầu ô; quote comma/quote/newline; tiền server-generated giữ numeric |

## Kiểm chứng đã chạy

- `mvn -B -Dtest=ReportServiceTest,CsvExportServletTest test` lần đầu: exit 1, 15 lỗi compile do implementation chưa tồn tại.
- Cùng lệnh sau implementation: exit 1, 2 CSV assertion fail đúng ca whitespace/control.
- Cùng lệnh sau sửa gốc: exit 0, 4 test pass.
- `mvn -B test`: exit 0, 124 test pass, không failure/error/skip.
- `database/run-migrations.sh`: exit 0; áp dụng 020/021, sau đó 022 vào database test theo allowlist.
- `mvn -B -Dit.test=Day18IT failsafe:integration-test failsafe:verify`: exit 0; 1 integration test pass trên SQL Server thật.

## Ca SQL đã viết

`database/tests/day-18.sql` kiểm cohort F05 khác cash-flow F10 khi đơn mua ngoài kỳ được hoàn trong kỳ, nhiều Payment/Refund attempt, event rỗng, full refund commission bằng 0, V04 không nhân tổng, TR10 chặn UPDATE/DELETE nhiều dòng và savepoint rollback vẫn xóa được log vừa insert.

`database/tests/inventory.sql` đối chiếu đúng tên 10 View, 17 SP, 10 Function và 10 Trigger. `docs/backend/sql-usage.md` ghi caller/test và ba lỗ hổng chuyển D19: V05/V07, SP08 và direct F03.

## Lỗi test đã sửa

- Fixture ban đầu thêm Zone sau khi Event đã `PUBLISHED`; TR01 chặn đúng. Fixture đổi sang DRAFT → thêm Zone → PUBLISHED.
- Tamper test ban đầu tiếp tục dùng transaction đã bị trigger `THROW` làm uncommittable. Mỗi ca UPDATE/DELETE nay có transaction riêng; rollback INSERT dùng transaction sạch.
- Inventory query thêm `COLLATE DATABASE_DEFAULT` khi so tên với `sys.objects` để chạy trên database có catalog collation khác.
