# Ngày 15 — Hoàn tiền mô phỏng, bù trừ và xử lý lần thử

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Ngày:** Thứ sáu, 2026-10-09. **Ước lượng:** 16 giờ công tổng của nhóm.

**Mục tiêu:** Refund thành công cập nhật vé/kho đúng một lần; UNKNOWN được đối chiếu; bù trừ không ảnh hưởng vé đã bán hợp lệ.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [SPEC](../../references/SPEC.md) §6.6, §6.9, §10, §14 SP11/TX11. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D14 SP10/Request; D11 compensation; D12 worker/outbox.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D15-T01 — Adapter hoàn mô phỏng có trạng thái theo reference

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** JAVA/integration/payment/SimulatedRefundGateway.java; TEST/integration/payment/RefundGatewayTest.java; docs/backend/refunds.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Submit/query theo refundId/reference; hỗ trợ SUCCEEDED/FAILED/UNKNOWN; trạng thái mô phỏng lưu bền để restart tra lại.

**Phụ thuộc của task:** Refund schema/reference và SP10 D14; adapter state không cần SP11 để test.

**Cách thực hiện:**

- [ ] 1. Thiết kế adapter cùng boundary tích hợp, không tạo domain Ledger/RefundBatch mới; lưu reference/provider state kỹ thuật đủ mô phỏng idempotency.
- [ ] 2. Submit cùng refundId không sinh nghĩa vụ mới; query theo reference cho kết quả đã ghi.
- [ ] 3. Tạo cấu hình test cho success, failure xác định và timeout sau side effect; public buyer không được chọn outcome.
- [ ] 4. UNKNOWN phải query trước, không tạo lần Refund mới; FAILED chỉ vận hành admin mới cho retry theo SP10/SP09.
- [ ] 5. Ghi rõ không chuyển tiền thật; kết quả adapter được backend xác minh/reference đúng trước SP11.

**Kiểm chứng bắt buộc:**

- [ ] Restart rồi query cùng reference trả kết quả cũ.
- [ ] Timeout sau success rồi retry submit không giả lập hai khoản hoàn.

**Điều kiện hoàn thành:** Adapter mô phỏng đủ kiểm tra state machine và lỗi mạng. Ghi case và kết quả trong `docs/evidence/day-15.md`.

## D15-T02 — SP11 kết quả refund và trả kho

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** SQL/D15_01_refund_result.sql; SQLTEST/day-15.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_ApplyRefundResult(refundId,verifiedResult) → Refund/Request status; chỉ worker/integration có EXECUTE.

**Phụ thuộc của task:** D14 SP10/open marker, D11 compensation và inventory rules D08.

**Cách thực hiện:**

- [ ] 1. Khóa Payment và nghĩa vụ liên quan để tổng SUCCEEDED không vượt amount, kể cả nhiều Request trên cùng Order.
- [ ] 2. SUCCEEDED lần đầu lưu reference/processedAt; CUSTOMER_REFUND hoàn Request/tickets, đóng open marker, trả Seat/standingSold và outbox nguyên tử.
- [ ] 3. PAYMENT_COMPENSATION chỉ ghi kết quả thu/hoàn bù trừ, không trả inventory hay phát hành/tắt vé của khoản mua hợp lệ.
- [ ] 4. FAILED/UNKNOWN giữ vé REFUND_PENDING và Request APPROVED cần xử lý; không tự chuyển ACTIVE. SUCCEEDED không bị callback cũ hạ trạng thái.
- [ ] 5. TX11 lỗi giữa refund update và trả kho rollback cả hai; duplicate result không trả kho lần nữa.

**Kiểm chứng bắt buộc:**

- [ ] Hoàn nhiều lần/two workers không vượt Payment.amount.
- [ ] Ghế refunded có thể bán lại chỉ nếu Event vẫn còn bán; QR cũ không có hiệu lực.

**Điều kiện hoàn thành:** SP11/TX11 hoàn thiện nghĩa vụ tiền/vé một lần. Ghi case và kết quả trong `docs/evidence/day-15.md`.

## D15-T03 — Worker hoàn, retry vận hành và email

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** JAVA/job/RefundJob.java; JAVA/service/fulfillment/RefundService.java; JAVA/controller/fulfillment/RefundOperationsServlet.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Admin yêu cầu retry failed; worker submit/query sau commit rồi SP11; public chỉ đọc.

**Phụ thuộc của task:** D15-T01/T02, D12 worker và quyền retry admin D14.

**Cách thực hiện:**

- [ ] 1. Nhận outbox có lease, tra Refund current state trước submit; UNKNOWN đi query, PENDING có timeout/đối chiếu hợp lý.
- [ ] 2. Admin retry CUSTOMER_REFUND gọi SP10 với cờ rõ; compensation retry gọi SP09 đúng obligation và quyền vận hành, không tự bật cờ khi redelivery.
- [ ] 3. Tạo attempt mới sau FAILED xác định, giữ lịch sử cũ; không tạo attempt khi còn PENDING/UNKNOWN/SUCCEEDED.
- [ ] 4. Gửi email refund completed từ outbox sau commit; fail email chỉ retry email.
- [ ] 5. Không giữ EntityManager/lock trong lúc gọi adapter; map lỗi provider sang state theo chứng cứ, không convert exception thành FAILED mặc định.

**Kiểm chứng bắt buộc:**

- [ ] Buyer gọi operations route bị từ chối; worker tự approve request khách bị DB chặn.
- [ ] Retry sau FAILED một lần thành công, FAILED cũ vẫn giữ để audit.

**Điều kiện hoàn thành:** Có vận hành refund đầy đủ không cần sửa trực tiếp DB. Ghi case và kết quả trong `docs/evidence/day-15.md`.

## D15-T04 — Bộ test tiền hoàn và khôi phục

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** TEST/acceptance/Day15IT.java; TEST/concurrency/RefundResultConcurrencyIT.java; docs/evidence/day-15.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Bao phủ customer partial/full/zero và compensation late/duplicate; V07 phản ánh đúng.

**Phụ thuộc của task:** D15-T01/T02/T03; V07/zero refund D14.

**Cách thực hiện:**

- [ ] 1. Mua hai vé thực trả 160000/240000, hoàn một vé rồi vé kia: tổng 400000, coupon consumed giữ nguyên.
- [ ] 2. Compensation trên khoản thu muộn có amount toàn khoản thu nhưng không tăng inventory sau release đã diễn ra.
- [ ] 3. Crash sau adapter success trước SP11; restart query xác nhận và áp dụng đúng một lần.
- [ ] 4. Chạy SQL multirow cập nhật ticket status với TR08; status hợp lệ được, sửa paidAmount bị chặn.
- [ ] 5. Đối chiếu request 0đ từ ngày 14 với nhánh có tiền: cùng quy tắc trạng thái/kho nhưng zero không có Refund row.

**Kiểm chứng bắt buộc:**

- [ ] SP11 chỉ principal kỹ thuật; HTTP forged SUCCEEDED không có hiệu lực.
- [ ] Tổng refund thành công <= captured Payment, kho không âm/không trả hai lần.

**Điều kiện hoàn thành:** Hoàn tiền và bù trừ đủ để triển khai hủy Event. Ghi case và kết quả trong `docs/evidence/day-15.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day15IT` và `database/tests/day-15.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day15IT verify
```
