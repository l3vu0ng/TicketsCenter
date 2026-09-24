# Ma Trận Phân Quyền và Vai Trò Người Dùng (Security Matrix)

> Ngày cập nhật: 2026-09-24  
> Nguồn: [CONVENTIONS](../tasks/CONVENTIONS.md) §3; [SPEC](../../references/SPEC.md) §14.10

---

## 1. Các Vai Trò Trong Hệ Thống

1. **GUEST (Khách vãng lai):** Người dùng chưa đăng nhập.
2. **BUYER (Người mua vé):** Tài khoản người dùng đã đăng nhập và xác minh email.
3. **ORGANIZER_STAFF (Nhân viên tổ chức):** Thành viên tổ chức sự kiện với vai trò kiểm soát, hỗ trợ.
4. **ORGANIZER_CHECKIN (Nhân viên soát vé):** Thành viên chuyên trách quét mã QR check-in tại cổng sự kiện.
5. **ORGANIZER_MANAGER (Quản lý tổ chức):** Trưởng ban tổ chức, toàn quyền cấu hình sự kiện, quản lý thành viên tổ chức, nhận tiền quyết toán.
6. **PLATFORM_ADMIN (Quản trị hệ thống):** Toàn quyền kiểm duyệt tổ chức, kiểm duyệt sự kiện, phê duyệt hoàn tiền ngoại lệ, xem audit log hệ thống.
7. **SYSTEM_WORKER (Tiến trình ngầm):** Tài khoản kỹ thuật chuyên biệt chạy các background jobs (hết hạn hold, kiểm tra thanh toán, outbox email, đối soát).

---

## 2. Ma Trận Quyền Hạn Chi Tiết Theo Nghiệp Vụ

| Nhóm Tài Nguyên / Nghiệp Vụ | GUEST | BUYER | ORGANIZER_STAFF | ORGANIZER_CHECKIN | ORGANIZER_MANAGER | PLATFORM_ADMIN | SYSTEM_WORKER |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Xem danh mục sự kiện công khai** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| **Đăng ký / Đăng nhập / Khôi phục MK** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ❌ |
| **Giữ chỗ (Hold) & Đặt vé** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Thanh toán & Nhận vé** | ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Xem vé của tôi** | ❌ | ✅ (chỉ vé mình) | ❌ | ❌ | ❌ | ✅ (xem toàn bộ) | ❌ |
| **Gửi yêu cầu hoàn vé** | ❌ | ✅ (đơn của mình) | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Quét mã QR Check-in** | ❌ | ❌ | ❌ | ✅ (sự kiện của BTC) | ✅ (sự kiện của BTC) | ✅ | ❌ |
| **Đăng ký mở Tổ chức (Organization)**| ❌ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| **Duyệt / Từ chối Tổ chức** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Tạo & Cấu hình Sự kiện (Draft)** | ❌ | ❌ | ✅ (soạn thảo) | ❌ | ✅ | ❌ | ❌ |
| **Gửi duyệt Sự kiện** | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| **Duyệt Sự kiện công khai** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Quản lý thành viên ban tổ chức** | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ | ❌ |
| **Duyệt yêu cầu hoàn tiền** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
| **Xem báo cáo đối soát sự kiện** | ❌ | ❌ | ❌ | ❌ | ✅ (sự kiện của mình) | ✅ (toàn hệ thống) | ❌ |
| **Xử lý Outbox & Quét Hold hết hạn**| ❌ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| **Xem Audit Log hệ thống** | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ | ❌ |
