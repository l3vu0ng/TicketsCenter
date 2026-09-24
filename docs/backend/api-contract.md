# Hợp đồng HTTP backend

> Nguồn chuẩn: [CONVENTIONS](../tasks/CONVENTIONS.md) §3, [API-MAP](../tasks/API-MAP.md) và [SPEC](../references/SPEC.md) §9.
> Tài liệu này mô tả backend dự kiến; không tuyên bố endpoint đã được cài đặt. Không có phạm vi frontend.

## 1. Quy ước chung

- Base path nằm dưới context WAR `/ticketscenter`; các route dưới đây dùng tiền tố logic `/api` khi cấu hình servlet.
- Thành công: `{"data":{...}}`; danh sách thêm `page`, `pageSize`, `total`. Lỗi: `{"error":{"code":"...","message":"...","correlationId":"..."}}`.
- UUID là chuỗi; VND là chuỗi số nguyên; thời gian JSON là ISO-8601 UTC. `page >= 1`, `pageSize` mặc định 20, tối đa 100.
- `400` sai input; `401` chưa đăng nhập/session hết hiệu lực; `403` thiếu quyền; `404` không tồn tại hoặc ngoài scope; `409` xung đột trạng thái; `413` quá kích thước; `429` rate limit; `503` dependency tạm lỗi.
- Mọi mutation từ browser dùng `POST` và CSRF. IPN là ngoại lệ public có chữ ký; return URL chỉ đọc trạng thái.
- `POST /holds` bắt buộc header `Idempotency-Key` là UUID do client sinh cho một thao tác giữ vé. Backend lưu hash/key kỹ thuật duy nhất theo `(userId, key)`: replay cùng payload trả Hold cũ; cùng key khác payload trả 409. Key không phải lớp nghiệp vụ mới.
- Session là nguồn `actorId`. Backend không nhận `actorId`, role, principal DB, trạng thái tài chính, server time hoặc số tiền tin cậy từ body/query.
- ID tổ chức/đối tượng trong URL chỉ là input: Service luôn kiểm tra owner, membership đang active và phạm vi tổ chức từ database.
- Response không chứa passwordHash, OTP/HMAC, secret provider, connection data, stack trace hoặc QR ngoài endpoint QR đã kiểm quyền.

## 2. Ánh xạ đúng UI-01…UI-24

| UI | Nghiệp vụ | Endpoint backend | Quyền/owner ghi |
|---|---|---|---|
| UI-01 | Danh sách/tìm kiếm sự kiện | `GET /events` | Public, chỉ PUBLISHED; V01 |
| UI-02 | Chi tiết/khu/giữ vé | `GET /events/{id}`; `GET /events/{id}/zones`; `POST /holds` | Hai GET public cho Event PUBLISHED; riêng POST cần Buyer active + verified, `Idempotency-Key`; SP02 |
| UI-03 | Auth/OTP/reset | `GET /auth/csrf`; `POST /auth/register`; `/auth/login`; `/auth/logout`; `/auth/otp/send`; `/auth/otp/verify`; `/auth/password/forgot`; `/auth/password/reset` | Auth principal tối thiểu; OTP one-use/rate-limit |
| UI-04 | Hold/order/coupon/pay | `GET /me/hold`; `POST /holds/{id}/cancel`; `POST /orders`; `POST /orders/{id}/coupon`; `POST /orders/{id}/payments`; `GET /orders/{id}/coupon-eligibility` | Owner; SP03/SP06/SP07/SP08/SP09-zero |
| UI-05 | Kết quả payment | `GET /payments/vnpay/return`; `GET /orders/{id}/payment-status`; `GET /payments/vnpay/ipn` | Return/status chỉ đọc; IPN worker → SP09 |
| UI-06 | Đơn của tôi | `GET /me/orders`; `GET /orders/{id}` | Owner; V03 |
| UI-07 | Vé/QR | `GET /me/tickets`; `GET /tickets/{id}`; `GET /tickets/{id}/qr` | Owner đúng Ticket; V06 không chứa QR |
| UI-08 | Yêu cầu hoàn | `GET /orders/{id}/refundable-tickets`; `POST /refund-requests`; `GET /me/refund-requests`; `GET /refund-requests/{id}` | Owner; SP05/F04/V07 |
| UI-09 | Yêu cầu tạo tổ chức | `POST /organization-requests`; `GET /me/organization-requests` | User đăng nhập; applicant từ session |
| UI-10 | Chọn/tổng quan tổ chức | `GET /me/memberships`; `GET /organizations/{id}/overview` | Memberships: mọi user đăng nhập xem của mình; overview: Manager active đúng tổ chức; V10 |
| UI-11 | Thành viên | `GET/POST /organizations/{id}/members`; `POST /organizations/{id}/members/{userId}/role`; `/deactivate`; `/activate` | Manager đúng tổ chức; giữ manager cuối |
| UI-12 | Event nội bộ | `GET /organizations/{id}/events`; `GET /organizations/{id}/events/{eventId}` | Manager đúng tổ chức; draft không dùng public V01 |
| UI-13 | Event/khu/ảnh | `POST /organizations/{id}/events`; `POST /events/{id}/edit`; `/delete`; `/submit`; `POST /events/{id}/zones`; `POST /zones/{id}/edit`; `/delete`; `POST /events/{id}/cover` | Manager; layout khóa sau publish |
| UI-14 | Coupon | `GET/POST /organizations/{id}/coupons`; `POST /coupons/{id}/edit`; `/activate`; `/deactivate`; `/delete`; `GET /admin/coupons` | Manager own org hoặc admin; SP03 là owner apply |
| UI-15 | Chọn Event check-in | `GET /organizations/{id}/check-in-events`; `GET /events/{id}/check-in-window` | Manager hoặc CHECK_IN_STAFF đúng tổ chức; F07 |
| UI-16 | Quét/history | `POST /check-ins`; `GET /events/{id}/check-ins` | Manager/check-in đúng tổ chức; SP04/V05 |
| UI-17 | Report tổ chức/CSV | `GET /organizations/{id}/reports`; `GET /reports/export` | Manager đúng tổ chức; V02–V05/V08/F05/F10 |
| UI-18 | Tổng quan admin | `GET /admin/overview` | Platform ADMIN |
| UI-19 | Duyệt tổ chức | `GET /admin/organization-requests`; `GET /admin/organization-requests/{id}`; `POST /admin/organization-requests/{id}/approve`; `/reject` | Admin; SP01 |
| UI-20 | Duyệt/hủy Event | `GET /admin/events`; `GET /admin/events/{id}`; `POST /admin/events/{id}/publish`; `/reject`; `/cancel`; `GET /admin/events/{id}/cancellation-progress` | Admin; SP12/SP13, worker SP17 |
| UI-21 | Duyệt/vận hành hoàn | `GET /admin/refund-requests`; `GET /admin/refund-requests/{id}`; `POST /admin/refund-requests/{id}/decision`; `/retry`; `POST /admin/payments/{id}/compensation/retry` | Admin quyết định SP10; worker ghi result SP09/SP11 |
| UI-22 | Rule/settlement/payout | `GET/POST /admin/organizations/{id}/commission-rules`; `POST /admin/commission-rules/{id}/edit`; `GET /admin/events/{id}/settlement`; `/settlement-blockers`; `POST /admin/events/{id}/settlement/recalculate`; `POST /admin/settlements/{id}/confirm`; `/payouts`; `GET /admin/settlements/{id}/payouts` | Admin; SP14–SP16 |
| UI-23 | Report/audit hệ thống | `GET /admin/reports`; `GET /reports/export`; `GET /admin/audit-logs`; `GET /admin/audit-logs/{id}` | Admin; audit append-only |
| UI-24 | Hồ sơ | `GET /me/profile`; `GET /me/memberships`; `POST /auth/otp/send` | User của session |

Danh sách endpoint chi tiết, input/output và task triển khai được duy trì một lần tại [API-MAP](../tasks/API-MAP.md). Khi thay endpoint phải sửa cả hai file trong cùng commit và giữ nguyên mã UI.

## 3. Endpoint kỹ thuật ngoài UI

| Đường chạy | Hợp đồng |
|---|---|
| `GET /health/live` | Luôn nhẹ, không gọi DB; `200 {"data":{"status":"UP"}}`; không lộ cấu hình |
| `GET /health/ready` | Kiểm dependency với timeout hữu hạn; lỗi trả 503, không trả connection string |
| VNPAY IPN | Xác minh signature, merchant, txnRef, amount, currency trước SP09; trả response đúng protocol, không dùng envelope chung |
| Worker hold/payment/refund/cancel/outbox | Không public cho browser; principal kỹ thuật hẹp; idempotency và lease lưu DB |

## 4. Ví dụ kiểm tra trust boundary

Request sau phải bị từ chối hoặc bỏ qua các trường giả mạo; quyền vẫn lấy từ session/database:

```http
POST /api/admin/events/10000000-0000-0000-0000-000000000001/publish
Content-Type: application/json
X-CSRF-Token: <runtime-token>

{"actorId":"admin-id","role":"PLATFORM_ADMIN","dbPrincipal":"tc_platform_admin","status":"PUBLISHED"}
```

Không endpoint browser nào được tên `/mark-paid`, `/mark-refunded`, `/mark-payout-success` hoặc nhận `CAPTURED`/`SUCCEEDED` làm sự thật. Kết quả provider chỉ đi qua adapter đã xác minh và SP owner tương ứng.

## 5. Idempotency và mất response

| Ghi | Khóa tra lại trước retry |
|---|---|
| Duyệt tổ chức | `requestId` → organization đã tạo |
| Giữ vé | `(userId, Idempotency-Key)` → Hold đã tạo; replay khác payload trả 409 |
| Tạo Order | `holdId` duy nhất |
| Coupon | `orderId` + redemption hiện hành |
| Payment | `paymentId`/`txnRef` |
| Check-in | Ticket + CheckIn đã ghi |
| Refund request | request mở theo tập ticket |
| Refund | `refundId` và providerReference |
| Settlement/Payout | `eventId`/`settlementId`/`payoutId` |

Timeout sau commit không được diễn giải là thất bại. Caller đọc trạng thái đã lưu rồi mới quyết định trả kết quả hoặc retry hữu hạn.
