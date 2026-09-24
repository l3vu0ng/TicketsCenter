# Ngày 14 — Yêu cầu hoàn vé và quyết định của admin

> Dành cho người thực hiện: triển khai lần lượt các task dưới đây; nếu dùng agent, dùng skill superpowers:executing-plans. Chỉ đánh dấu khi có bằng chứng.

**Ngày:** Thứ năm, 2026-10-08. **Ước lượng:** 18 giờ công tổng của nhóm.

**Mục tiêu:** Chủ đơn chọn vé để hoàn; admin duyệt/từ chối; vé 0đ hoàn tất không tạo giao dịch tiền giả.

**Kiến trúc:** Servlet → Service → Model/Repository → SQL Server; SP sở hữu đường ghi được phân công, một transaction/connection cho use case.

**Công nghệ:** Java 25, Tomcat 11, JPA/Hibernate, SQL Server; phiên bản cụ thể theo quyết định ngày 1.

**Nguồn:** [SPEC](../../references/SPEC.md) §6.9, §14 SP05/SP10/F04/V07/TX05/TX10. Đọc [quy ước chung](../CONVENTIONS.md), [lịch tổng](../README.md) và [truy vết yêu cầu](../COVERAGE.md) trước khi làm.

**Phụ thuộc đầu ngày:** D11 Ticket/paidAmount; D13 check-in; D12 outbox; xử lý provider refund có tiền nối ngày 15.

**Ràng buộc chung:** tuân toàn bộ CONVENTIONS; không thêm dependency chưa duyệt, không đổi lịch sử tài chính, không dùng principal toàn quyền để né lỗi. Bước SQL cần schema/khóa và quyền đã chốt. A/B/C là vai trò phân công, không phải tên người.

**Thứ tự trong ngày:** đọc hợp đồng và viết test trước; phần SQL và Java có thể chuẩn bị theo hợp đồng nhưng chỉ nghiệm thu tích hợp khi cả hai đã chạy. Task dùng đầu ra task khác phải chờ đầu ra đó, dù cùng ngày.

## D14-T01 — F04, SP05 và một yêu cầu mở mỗi vé

**Phụ trách đề xuất:** B. **Giờ công:** 5h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** SQL/D14_01_refund_request.sql; SQLTEST/day-14.sql. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** F04 refundable tickets; usp_CreateRefundRequest(orderId,actorId,ticketIds,reason) → requestId/requestedAmount.

**Phụ thuộc của task:** D11 ticket/paidAmount, D13 check-in và refund schema.

**Cách thực hiện:**

- [ ] 1. F04 trả Ticket ACTIVE, trước startTime, chưa request mở; endpoint kiểm owner trước đọc, SP kiểm lại sau khóa.
- [ ] 2. SP05 reject danh sách rỗng/trùng/khác Order, USED/inactive, đúng hoặc sau startTime; all-or-nothing.
- [ ] 3. Create RefundRequest CUSTOMER_REQUEST, bảng nối có dấu open và tổng paidAmount; chuyển từng vé REFUND_PENDING cùng transaction.
- [ ] 4. Không tính lại giá/coupon hiện tại; không nhận amount hoặc reasonType EVENT_CANCELLATION từ buyer.
- [ ] 5. TX05 inject lỗi item cuối/bảng nối, rollback cả request/status; race hai yêu cầu cùng vé và race check-in chỉ một chuyển hợp lệ.

**Kiểm chứng bắt buộc:**

- [ ] Một vé không có hai open request; unique index và khóa đều được kiểm chứng.
- [ ] 200000+300000 giảm100000: hoàn vé thứ nhất yêu cầu160000 dù giá hiện tại đã đổi.

**Điều kiện hoàn thành:** SP05/TX05 chặn check-in ngay khi yêu cầu được gửi. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## D14-T02 — SP10 duyệt/từ chối và hoàn 0đ

**Phụ trách đề xuất:** B+A. **Giờ công:** 6h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** SQL/D14_02_refund_decision.sql; JAVA/model/fulfillment/RefundRequest.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** usp_DecideRefundRequest theo SPEC, default retryFailed=false; admin quyết CUSTOMER_REQUEST, system chỉ EVENT_CANCELLATION hợp lệ.

**Phụ thuộc của task:** D14-T01 request; D12 outbox; schema Payment/Refund đã có.

**Cách thực hiện:**

- [ ] 1. Khóa Event/Order/Request/ticket theo hợp đồng; request của khách chỉ admin quyết định, từ chối bắt buộc reason audit.
- [ ] 2. Từ chối khôi phục ACTIVE chỉ khi Event chưa cancelled, đóng dấu open, outbox thông báo; không thay reason khách bằng lý do admin.
- [ ] 3. Duyệt có tiền tạo một Refund PENDING CUSTOMER_REFUND gắn Payment hợp lệ và outbox, Request APPROVED; chưa đánh REFUNDED.
- [ ] 4. Request 0đ hoàn tất vé/trả kho và COMPLETED, không tạo Refund/Payment; dùng cùng quy tắc tồn kho như SP11 sắp triển khai.
- [ ] 5. Lặp quyết định trả kết quả cũ, trái quyết định conflict; retryFailed chỉ admin được phép và không có PENDING/UNKNOWN/SUCCEEDED cho nghĩa vụ đó.

**Kiểm chứng bắt buộc:**

- [ ] TX10 lỗi tạo Refund rollback APPROVED; approve hai lần chỉ một Refund pending.
- [ ] Reject đua Event cancel không khôi phục vé Event đã hủy; worker không tự duyệt Event chưa cancelled.

**Điều kiện hoàn thành:** SP10 có đầy đủ zero/reject/approve/retry guard, luồng tiền chờ ngày 15. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## D14-T03 — API refund và V07 tổng hợp không nhân tiền

**Phụ trách đề xuất:** A. **Giờ công:** 4h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** SQL/D14_03_refund_reads.sql; JAVA/controller/fulfillment/RefundServlet.java; JAVA/service/fulfillment/RefundService.java; JAVA/repository/fulfillment/RefundRepository.java. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** GET /orders/{id}/refundable-tickets; POST /refund-requests; GET /me/refund-requests; GET/POST /admin/refund-requests/{id}/decision.

**Phụ thuộc của task:** Contract D14-T01/T02; V07 chuẩn bị song song từ schema.

**Cách thực hiện:**

- [ ] 1. DTO chọn ticketIds/reason, actor/session; admin decision APPROVE/REJECT mapping rõ sang SP10 và API-MAP.
- [ ] 2. V07 aggregate tập vé riêng, Refund attempts riêng rồi join theo Request; không join trực tiếp hai tập gây nhân paidAmount.
- [ ] 3. Trả requestedAmount, status, lần hoàn cần xử lý và rejectionReason từ AuditLog theo đúng quyết định.
- [ ] 4. Request COMPLETED 0đ hiển thị hoàn tất dù không có Refund row; tiền pending không gọi là đã hoàn.
- [ ] 5. Kết nối email rejection với EmailJob và idempotency key; email lỗi không khôi phục ticket/request.

**Kiểm chứng bắt buộc:**

- [ ] User chỉ xem request của mình; manager không được duyệt dù Event của tổ chức.
- [ ] Hai failed attempts và một success sau này không nhân requestedAmount ở V07.

**Điều kiện hoàn thành:** API đủ cho khách/admin theo dõi hoàn tiền không cần UI. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## D14-T04 — Nghiệm thu tuần 2 và race refund/check-in

**Phụ trách đề xuất:** C. **Giờ công:** 3h. **Trạng thái:** chưa làm.

**File cần tạo/cập nhật:** TEST/acceptance/Day14IT.java; TEST/concurrency/RefundRequestConcurrencyIT.java; docs/evidence/day-14.md. Các tiền tố JAVA/TEST/SQL được giải thích trong CONVENTIONS.

**Hợp đồng vào/ra:** Chuỗi từ mua vé đến quét hoặc gửi hoàn được kiểm tra qua HTTP/SQL thật.

**Phụ thuộc của task:** D14-T01/T02/T03; SP04 từ D13 để race thật.

**Cách thực hiện:**

- [ ] 1. Barrier SP04 và SP05 cùng ACTIVE ticket: kết quả cuối USED hoặc REFUND_PENDING, không cả hai use case thành công.
- [ ] 2. Test thời điểm start−1 tick/startTime, ticketIds cross-order và duplicate, reason quá dài.
- [ ] 3. Kiểm refund pending không check-in; rejected trở lại ACTIVE chỉ khi Event hợp lệ; zero approve trả kho đúng một lần.
- [ ] 4. Chạy lại flow coupon/paidAmount qua request và chứng minh coupon CONSUMED không được release.
- [ ] 5. Chốt backlog tuần 2; chuyển các Refund PENDING/bù trừ vào fixture worker ngày 15, không tự đánh SUCCEEDED cho sạch.

**Kiểm chứng bắt buộc:**

- [ ] SP05/SP10, F04, V07 có caller thật và TX05/TX10 evidence.
- [ ] Ngày 14 kết thúc với refund có tiền đang chờ là trạng thái dự kiến, chưa tuyên bố đã hoàn tiền.

**Điều kiện hoàn thành:** Toàn bộ bán vé/check-in và phần yêu cầu/duyệt hoàn đã có. Ghi case và kết quả trong `docs/evidence/day-14.md`.

## Kiểm tra cuối ngày

- [ ] Chạy unit test phần thay đổi, integration `Day14IT` và `database/tests/day-14.sql` nếu ngày này có SQL. Tạo/bổ sung các file test này từ ca kiểm chứng ở trên; không báo thành công với test rỗng hoặc bị skip.
- [ ] Với logic có nhánh/quyền/tiền: giữ bằng chứng test đỏ trước sửa và xanh sau sửa; test dữ liệu cuối ở SQL Server thật. Mỗi trigger có ca nhiều dòng; mỗi SP ghi có commit/rollback và kiểm tra transaction ngoài khi áp dụng.
- [ ] Cập nhật `docs/backend/api-contract.md`, mapping SQL/Model và grant cho object mới; ghi endpoint/SP/UDF thực sự được gọi.
- [ ] Lưu lỗi còn mở, người xử lý và task bị ảnh hưởng; chưa đủ bằng chứng thì để chưa đạt. Kiểm tra diff và bí mật trước commit theo Conventional Commits.

Lệnh tham chiếu (tooling được tạo ngày 1–2; chọn đúng auth SQL theo runbook):

```bash
mvn -B test
mvn -B -Psqlserver-it -Dit.test=Day14IT verify
```
