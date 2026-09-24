# Hợp Đồng Giao Diện Lập Trình Ứng Dụng (API Contract)

> Ngày cập nhật: 2026-09-24  
> Nguồn: [CONVENTIONS](../tasks/CONVENTIONS.md) §3; [API-MAP](../tasks/API-MAP.md); [SPEC](../../references/SPEC.md)

---

## 1. Cấu Trúc Envelope Chuẩn

Mọi phản hồi từ backend đều sử dụng định dạng JSON UTF-8 thống nhất:

### Thành công (Single Object):
```json
{
  "data": {
    "id": "c1f29b4e-862d-4b89-a29d-43c3d526ef11",
    "status": "UP"
  }
}
```

### Thành công (Danh sách có phân trang):
```json
{
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "total": 150
  }
}
```

### Lỗi nghiệp vụ hoặc hệ thống:
```json
{
  "error": {
    "code": "HOLD_EXPIRED",
    "message": "Lượt giữ vé đã hết hạn",
    "correlationId": "d8205f3b-6325-4c27-99ea-bfd6c97a76e9"
  }
}
```

---

## 2. Quy Chuẩn Kiểu Dữ Liệu trên HTTP

- **UUID:** Định dạng chuỗi 36 ký tự (ví dụ `"c1f29b4e-862d-4b89-a29d-43c3d526ef11"`).
- **Tiền VND:** Định dạng chuỗi số nguyên để bảo toàn độ chính xác tuyệt đối (ví dụ `"350000"`).
- **Thời gian:** Chuỗi ISO-8601 UTC (ví dụ `"2026-10-02T14:30:00Z"`).
- **Số lượng (Quantity):** Số nguyên dương.

---

## 3. Quy Tắc Bảo Mật và Phiên Làm Việc

1. **Mutation:** Mọi thao tác thay đổi trạng thái (POST, PUT, DELETE) bắt buộc kèm CSRF token thông qua header `X-CSRF-Token` hoặc form field `_csrf`.
2. **Xác thực:** Dựa vào Session Cookie (`JSESSIONID`) có cờ `HttpOnly`, `Secure` (trên production) và `SameSite=Lax`.
3. **Principal & Actor:** Backend đọc `userId` từ session đã xác thực, tuyệt đối không tin cậy `userId`, `role` hay `organizationId` do client gửi trong request body/query params để phân quyền.

---

## 4. Bảng Ánh Xạ Endpoint Phục Vụ 24 Màn Hình (UI-01 đến UI-24)

| UI Code | Màn Hình / Nghiệp Vụ | Method | Endpoint Hợp Đồng | Trạng Thái Trả Về |
|:---:|---|:---:|---|:---:|
| **UI-01** | Trang chủ / Danh sách sự kiện nổi bật | `GET` | `/api/events` | 200 |
| **UI-02** | Tìm kiếm sự kiện theo từ khóa & danh mục | `GET` | `/api/events/search` | 200 |
| **UI-03** | Chi tiết sự kiện & sơ đồ chỗ | `GET` | `/api/events/{id}` | 200 / 404 |
| **UI-04** | Đăng ký tài khoản người mua | `POST` | `/api/auth/register` | 201 / 400 |
| **UI-05** | Xác thực OTP email | `POST` | `/api/auth/verify-otp` | 200 / 400 |
| **UI-06** | Đăng nhập tài khoản | `POST` | `/api/auth/login` | 200 / 401 |
| **UI-07** | Quên mật khẩu & gửi OTP khôi phục | `POST` | `/api/auth/forgot-password` | 200 |
| **UI-08** | Đặt lại mật khẩu mới bằng OTP | `POST` | `/api/auth/reset-password` | 200 / 400 |
| **UI-09** | Chọn chỗ & Thực hiện Giữ vé (Hold) | `POST` | `/api/holds` | 201 / 409 |
| **UI-10** | Hủy lượt giữ vé hiện tại | `POST` | `/api/holds/{id}/release` | 200 |
| **UI-11** | Tạo đơn hàng từ Hold & áp dụng Coupon | `POST` | `/api/orders` | 201 / 400 |
| **UI-12** | Khởi tạo thanh toán VNPAY | `POST` | `/api/payments/vnpay/init` | 200 |
| **UI-13** | Xử lý VNPAY IPN Callback (kênh ngầm) | `POST` | `/api/payments/vnpay/ipn` | 200 (VNPAY code) |
| **UI-14** | VNPAY Return URL (hiển thị kết quả) | `GET` | `/api/payments/vnpay/return` | 200 / 302 |
| **UI-15** | Xem vé đã mua & ảnh mã QR | `GET` | `/api/tickets/{id}` | 200 / 403 |
| **UI-16** | Gửi yêu cầu hoàn vé | `POST` | `/api/refund-requests` | 201 / 400 |
| **UI-17** | Ứng dụng quét mã Check-in vé | `POST` | `/api/checkin/scan` | 200 |
| **UI-18** | Đăng ký mở Tổ chức tổ chức sự kiện | `POST` | `/api/organization-requests` | 201 / 400 |
| **UI-19** | Quản lý thành viên & phân quyền ban tổ chức | `GET`/`POST` | `/api/organizations/{id}/members` | 200 / 403 |
| **UI-20** | Tạo & chỉnh sửa sự kiện, cấu hình khu vé | `POST` | `/api/events/manage` | 201 / 400 |
| **UI-21** | Gửi sự kiện yêu cầu phê duyệt | `POST` | `/api/events/{id}/submit` | 200 |
| **UI-22** | Admin duyệt sự kiện / duyệt yêu cầu tổ chức | `POST` | `/api/admin/reviews` | 200 / 403 |
| **UI-23** | Xem báo cáo doanh thu & đối soát kỳ | `GET` | `/api/reports/settlement` | 200 / 403 |
| **UI-24** | Xuất báo cáo CSV & kiểm toán Audit Log | `GET` | `/api/reports/export.csv` | 200 / 403 |
