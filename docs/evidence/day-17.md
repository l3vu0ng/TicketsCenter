# Bằng chứng Ngày 17 — đối soát và chi trả mô phỏng

**Trạng thái:** Java/unit đã kiểm chứng; SQL Server integration bị chặn bởi Azure firewall, vì vậy D17 chưa được đánh dấu hoàn tất.

## TDD đỏ → xanh

| Seam | Đỏ trước sửa | Xanh sau sửa |
|---|---|---|
| F02 Java | `mvn -B -Dtest=CommissionTest test` — compile fail do thiếu `CommissionRule.calculateFee` | 2 test pass |
| Payout adapter | `mvn -B -Dtest=SimulatedPayoutGatewayTest test` — compile fail do thiếu adapter | 2 test pass; replay cùng ID trả cùng result, payload khác bị từ chối, timeout sau side effect được khôi phục bằng cùng ID |
| Service payout | `mvn -B -Dtest=SettlementServiceTest test` — compile fail do thiếu Service/Repository/DTO | 2 test pass; ADMIN reserve `PENDING` trước external I/O rồi ghi outcome, buyer bị chặn |
| HTTP admin | `mvn -B -Dtest=SettlementServletTest test` — compile fail do thiếu servlet | 1 test pass; response tiền là chuỗi VND |
| Model map | `mvn -B -Dtest=SettlementModelTest test` — compile fail do thiếu hành vi diagram | 2 test pass; amount invariant và state transition |

## Vector kỳ vọng độc lập

| Order | Gross | Refund | Remaining | Rule | Commission | Net |
|---|---:|---:|---:|---|---:|---:|
| Partial refund | 300000 | 100000 | 200000 | 10% + 1000 | 21000 | 179000 |
| Full refund | 100000 | 100000 | 0 | 10% + 1000 | 0 | 0 |
| Tổng Settlement | 400000 | 200000 | 200000 | theo từng Order | 21000 | 179000 |

`database/tests/day-17.sql` còn kiểm: F02 `300000 → 31000`, fixed fee cap, V03 không nhân join/không tính compensation, F09 giữ `UNKNOWN` và Event cancelled trước end, SP14 idempotency, SP15 snapshot, TR04/TR09 multirow, SP16 replay/fail release/overspend và V08 paid/pending/remaining/available.

## Lệnh đã chạy

- `mvn -B test` — exit 0; 118 test, 0 failure/error/skip.
- `mvn -B -Dtest=SettlementModelTest test` — exit 0; 2 test.
- `database/run-migrations.sh --plan` — exit 0; nhận 017/018/019, không có `GO`.
- Chạy migration thật — exit 1 trước khi mở database: Azure SQL firewall không cho IP hiện tại truy cập. Không migration nào của lượt chạy này được xác nhận áp dụng.
- `mvn -B -Psqlserver-it -Dit.test=Day17IT,SettlementConcurrencyIT verify` — build/WAR và 118 unit test qua; 2 integration test được chạy, 0 skip, cả hai lỗi tại kết nối SSL trước khi thực thi SQL.

## Còn bị chặn

- Chưa chạy được `mvn -B -Psqlserver-it -Dit.test=Day17IT,SettlementConcurrencyIT verify` và `database/tests/day-17.sql` trên SQL Server thật.
- Vì chưa có bằng chứng database/concurrency, các checkbox D17-T01…T04 và hai mục kiểm tra integration vẫn để trống; chỉ mục cập nhật tài liệu/grant và ghi blocker được đánh dấu.
