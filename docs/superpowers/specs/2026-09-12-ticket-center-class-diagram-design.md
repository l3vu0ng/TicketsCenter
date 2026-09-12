# Ticket Center Class Diagram — Thiết kế củng cố

Ngày: 2026-09-12  
Phạm vi: `diagram.md` (diagrams.net XML)

## 1. Mục tiêu

Củng cố Class Diagram để phản ánh đúng các quyết định nghiệp vụ trong `spec.md`, loại bỏ connector hỏng, làm rõ aggregate root và giúp sơ đồ đọc được ở mức tổng quan mà vẫn giữ đủ chi tiết để chuyển sang thiết kế database/API.

## 2. Nguyên tắc mô hình

- Giữ kiến trúc Modular Monolith và nhóm lớp theo các module nghiệp vụ.
- Domain entity không phụ thuộc lớp giao diện. `CheckInScreen` được loại khỏi domain diagram; hành vi quét vé được thể hiện qua `CheckIn` và quan hệ với nhân sự, phiên diễn, vé.
- Tài khoản và vai trò dùng composition thay vì inheritance. `User` liên kết với `OrganizationMembership`; quyền tổ chức nằm ở `OrganizationRole`, quyền quản trị hệ thống nằm ở `PlatformRole`.
- Mỗi quan hệ có đầu nối hợp lệ, tên quan hệ rõ nghĩa và multiplicity nhất quán.
- Trạng thái nghiệp vụ dùng enum thay cho `String` khi tập giá trị hữu hạn.
- Chỉ tô vàng phần tiêu đề của aggregate root/lớp trọng tâm; phần thuộc tính và phương thức giữ nền trắng.

## 3. Các cụm nghiệp vụ

### Identity và Organization

- `User` sở hữu `RefreshToken`, đặt `Order`, tạo `TicketHold`, nhận `Notification` và có các `OrganizationMembership`.
- `OrganizationMembership` liên kết đúng một `User` với đúng một `Organization`, có `OrganizationRole` và thời điểm tham gia.
- `PlatformRole` biểu diễn quyền cấp hệ thống, trong đó có `ADMIN`.
- Không dùng các lớp kế thừa `Customer`, `StaffMember`, `PlatformAdmin`; vai trò được xác định từ assignment.

### Event và Seating

- `Organization` quản lý `Event`, `Venue`, `SeatingPlan`, `Coupon` và `CommissionRule`.
- `Event` chứa nhiều `EventSession` và `EventApprovalHistory`; mỗi event thuộc một `EventCategory`.
- `SeatingPlan` chứa `SeatingZone`; zone có `SeatingRow`; row có `SeatingSeat`.
- `EventSession` tham chiếu `Venue` và có thể dùng `SeatingPlan`; dữ liệu bán vé của phiên được snapshot thành `ScheduleZone` và `SessionSeat`.

### Ticketing, Order và Payment

- `EventSession` chứa `TicketType`; mỗi `TicketType` có một `TicketInventory`.
- `TicketHold` chứa nhiều `TicketHoldItem`; mỗi item khóa một `TicketType` và có thể khóa một `SessionSeat`.
- `Order` chứa nhiều `OrderItem`; một order item đại diện cho đúng một loại vé và tối đa một ghế. Với vé tự do, `quantity` có thể lớn hơn một; với ghế đánh số, mỗi item có `quantity = 1`.
- `Order` có thể có nhiều lần thử `Payment`; chỉ một payment thành công được dùng để xác nhận đơn. `VNPayPayment` là chuyên biệt hóa của `Payment`.
- `OrderItem` phát hành một hoặc nhiều `Ticket`; `Ticket` gắn với `EventSession` và có thể gắn với `SessionSeat`.

### Check-in, Refund và Settlement

- `CheckIn` tham chiếu đúng một `Ticket`, một `EventSession` và người thực hiện là `OrganizationMembership` có vai trò `CHECKIN_STAFF` hoặc quyền phù hợp.
- `RefundRequest` thuộc một `Order`, chứa nhiều `RefundRequestItem`; mỗi item nhắm tới đúng một `Ticket`.
- `Refund` ghi nhận giao dịch hoàn tiền thực tế, liên kết với `RefundRequest` và payment gốc.
- `Settlement` thuộc một `Organization`, chứa `SettlementItem`, tạo `Payout`; mỗi item đối soát một `Order`.
- `AuditLog` ghi lại các thao tác kiểm duyệt, hoàn tiền và xác nhận đối soát cần truy vết.

## 4. Lớp trọng tâm được tô vàng

Các lớp sau có phần tiêu đề màu vàng `#FDE68A`, viền `#D97706`:

- `User`
- `Organization`
- `Event`
- `EventSession`
- `TicketInventory`
- `TicketHold`
- `Order`
- `Payment`
- `Ticket`
- `RefundRequest`
- `Coupon`
- `Settlement`

## 5. Bố cục

Sơ đồ được bố trí từ trái sang phải theo dòng nghiệp vụ:

`Identity/Organization → Event/Seating → Ticketing/Hold → Order/Payment → Ticket/Check-in/Refund → Settlement`

Các package/module có nền nhạt khác nhau, khoảng cách đều, connector ưu tiên đường vuông góc ngắn và không dùng đầu nối tự do. Chú giải giải thích màu vàng, composition, association và generalization.

## 6. Kiểm tra hoàn thành

- XML parse được và mở được bằng diagrams.net.
- Không còn connector thiếu `source` hoặc `target`.
- Định danh connector khớp với lớp nguồn/đích thực tế.
- Không còn thuộc tính trùng trong cùng lớp.
- Tất cả lớp trọng tâm có đúng màu tiêu đề quy định; lớp phụ không bị tô vàng.
- Các quan hệ cốt lõi và multiplicity khớp với quy tắc nghiệp vụ trong `spec.md`.
- Không sửa các artifact không liên quan đang tồn tại trong worktree.
