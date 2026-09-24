# Ma trận quyền ứng dụng và principal database

> Nguồn: [SPEC](../references/SPEC.md) §4, §14.10 và [API contract](api-contract.md).
> Vai trò không loại trừ nhau: cùng một `User` có thể mua vé, là `MANAGER` ở tổ chức A và `CHECK_IN_STAFF` ở tổ chức B. Backend chọn ngữ cảnh/principal theo **use case đã kiểm quyền**, không theo role client gửi.

## 1. Vai trò ứng dụng

| Tác nhân/điều kiện | Khả năng |
|---|---|
| Guest | Đọc Event PUBLISHED; đăng ký, đăng nhập, quên mật khẩu |
| User đăng nhập chưa verify | Đọc Event, profile, OTP, gửi yêu cầu tạo tổ chức; chưa được giữ vé |
| Buyer đã verify | Hold/order/payment của mình, vé/QR của mình, refund request của mình |
| `OrganizationRole.MANAGER` active | Quản lý member/Event/Zone/Coupon/report/check-in của đúng tổ chức |
| `OrganizationRole.CHECK_IN_STAFF` active | Chọn Event và check-in của đúng tổ chức; không đọc tài chính hoặc sửa Event/member |
| `PlatformRole.ADMIN` | Duyệt tổ chức/Event/refund, hủy Event, policy/settlement/payout, report/audit toàn hệ thống |

Không tồn tại `ORGANIZER_STAFF`, `ORGANIZER_MANAGER` hay `ORGANIZER_CHECKIN` trong domain. `MANAGER` và `CHECK_IN_STAFF` luôn kèm organizationId và membership active.

## 2. Principal database vận hành

| Mã | Role / Login / User local | Backend chọn khi | Cho phép chính | Cấm rõ |
|---|---|---|---|---|
| R01 | `tc_buyer` / `tc_buyer_login` / `tc_buyer_user` | Use case mua/đọc dữ liệu cá nhân sau owner check | V01/V02; own order/ticket/refund/membership; SP02, SP03, SP05–SP08; SP09 chỉ đơn 0đ và owner | Paid SP09, admin report, cross-user/org |
| R02 | `tc_manager` / `tc_manager_login` / `tc_manager_user` | Membership MANAGER active và object thuộc org | Draft Event/Zone/Seat, Coupon/member, scoped V02–V10/report, SP04 | Admin approval/refund/payout; org khác |
| R03 | `tc_checkin` / `tc_checkin_login` / `tc_checkin_user` | MANAGER hoặc CHECK_IN_STAFF active, đúng Event/org | SP04, V05, F07, thông tin Event tối thiểu | V03/V04/V06–V08/F05/F10 và bảng tài chính; sửa Event/member |
| R04 | `tc_platform_admin` / `tc_admin_login` / `tc_admin_user` | Session có PlatformRole.ADMIN hiện hành | SP01, SP10, SP12–SP16; policy/report/audit | Không `sysadmin`/`db_owner`; không endpoint tự khai paid/succeeded |

Role database không tạo row-level security tự động. Service/SP vẫn kiểm `actorId`, owner, membership, organization và trạng thái ở mỗi use case.

## 3. Principal kỹ thuật

| Principal | Nguồn gọi | Quyền tối thiểu | Không được làm |
|---|---|---|---|
| `tc_auth` | Register/login/OTP/reset | User/auth/session/OTP cần thiết; tăng authVersion | Event, payment, refund, report |
| `tc_worker` | Job nội bộ đã xác thực cấu hình | SP07, paid branch SP09, SP11, SP17 và cancellation-only SP10 | DDL, member/admin UI, ghi bảng tùy ý |
| `tc_migration` | Pipeline/operator | DDL/migration history trong cửa sổ migration | Runtime request/job |
| `tc_fixture` | Integration test database riêng | Seed/reset fixture test | Dev/demo/shared database |

Các principal kỹ thuật không phải role HTTP, không xuất hiện trong session/menu và browser không được chọn chúng.

## 4. Ma trận endpoint

| Nhóm endpoint | Guest | User/Buyer | Manager | Check-in staff | Admin | Worker | DB principal |
|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| Public Event `GET /events*` | ✓ | ✓ | ✓ | ✓ | ✓ | – | read/public hoặc R01 hạn chế |
| Auth/OTP/password | ✓/session | ✓ own | ✓ own | ✓ own | ✓ own | – | `tc_auth` |
| Hold/order/payment own | – | ✓ verified | ✓ khi hành động như buyer | ✓ khi hành động như buyer | ✓ khi hành động như buyer | paid result only | R01 / `tc_worker` |
| Ticket/QR/refund request own | – | ✓ own | ✓ own nếu là owner | ✓ own nếu là owner | Chỉ admin endpoint được chỉ định | Refund result | R01 / R04 / worker |
| Organization request own | – | ✓ | ✓ | ✓ | Review only | – | R01 / R04 |
| Member/Event/Zone/Coupon org | – | – | ✓ scoped | – | Chỉ admin endpoint được chỉ định | – | R02 / R04 |
| Check-in | – | – | ✓ scoped | ✓ scoped | ✓ theo quyền admin được quy định | – | R02 hoặc R03 |
| Org report | – | – | ✓ scoped | – | ✓ | – | R02 / R04 |
| Admin review/settlement/audit | – | – | – | – | ✓ | Cancel/refund subflow only | R04 / worker |

“Manager/check-in acting as buyer” vẫn phải active, email verified và owner của đơn/vé; membership không cấp quyền đọc đơn của người khác.

## 5. Luồng chọn connection

1. Filter xác thực session, User status và `authVersion`.
2. Servlet parse/validate input; loại bỏ mọi actor/role/principal do client tự khai.
3. Service tải owner/membership hiện hành và xác định use case.
4. Chọn một datasource/principal phù hợp trước khi mở transaction.
5. Một transaction giữ nguyên connection/principal đến commit/rollback; Repository/SP không đổi principal giữa chừng.
6. Nếu quyền đổi giữa request, fail `401/403`; không fallback sang admin connection.

## 6. Pool budget

- Ngân sách khởi đầu toàn ứng dụng: **tối đa 5 connection đồng thời tổng cộng** trên profile free/local giới hạn, không phải 5 cho mỗi principal.
- Mỗi pool theo principal có `minimumIdle=0`; ngoài giới hạn riêng từng pool, datasource router dùng **một semaphore toàn ứng dụng 5 permit** bao quanh `getConnection()` và trả permit khi connection đóng. Đây là giới hạn tổng bắt buộc cho R01–R04, auth và worker; không chỉ là tổng cấu hình trên giấy.
- Không giữ pool migration/fixture trong runtime. Chúng chạy ở process/operator riêng; runtime semaphore chỉ bao phủ sáu principal vận hành.
- Auth/worker dùng connection ngắn; không giữ connection khi gọi email, VNPAY, storage hoặc chờ người dùng.
- Hết pool trả lỗi dependency/timeout đã lọc; không dùng R04 làm fallback.

## 7. Ca kiểm chứng quyền bắt buộc

- Body giả `actorId`, `ADMIN`, `tc_platform_admin`, `CAPTURED` không thay đổi actor/principal/trạng thái.
- User là MANAGER A và CHECK_IN_STAFF B: quản lý A, check-in B, không quản lý B; vẫn mua vé của chính mình qua R01.
- Manager A dùng ID Event/Order tổ chức B nhận 404/403 theo policy và không lộ dữ liệu.
- Check-in principal bị DENY dữ liệu tài chính kể cả gọi View/bảng trực tiếp.
- Worker chỉ chạy SP được cấp; migration principal không được dùng cho HTTP runtime.
- REVOKE được thử khi không còn nguồn grant khác; DENY tài chính check-in được thử riêng bằng `EXECUTE AS USER`/`REVERT`.
