# Bằng chứng Ngày 10 — VNPAY Sandbox & Xác thực kết quả thanh toán

## 1. Kiểm thử tích hợp VNPAY Sandbox (5 thẻ kiểm thử NCB)

- **Cổng thanh toán:** `https://sandbox.vnpayment.vn/paymentv2/vpcpay.html`
- **Mã định danh:** `vnp_TmnCode=N57OM2H4`
- **Thuật toán ký:** HMAC-SHA512 (`vnp_SecureHash`)

### Kết quả kiểm thử thực tế trên cổng Sandbox:

| STT | Số thẻ | Tên chủ thẻ | Ngày phát hành | Kịch bản / Kết quả | Trạng thái | Mã lỗi / Giao dịch |
|---|---|---|---|---|:---:|---|
| 1 | `9704198526191432198` | NGUYEN VAN A | `07/15` | Thanh toán thành công qua OTP `123456` | **THÀNH CÔNG** | `vnp_ResponseCode=00`, Mã GD: `15691654` |
| 2 | `9704195798459170488` | NGUYEN VAN A | `07/15` | Tài khoản của khách hàng không đủ số dư | **THẤT BẠI** | Code `51` |
| 3 | `9704192181368742` | NGUYEN VAN A | `07/15` | Thẻ chưa được kích hoạt | **THẤT BẠI** | Code `07` |
| 4 | `9704193370791314` | NGUYEN VAN A | `07/15` | Thẻ bị khóa | **THẤT BẠI** | Code `10` |
| 5 | `9704194841945513` | NGUYEN VAN A | `07/15` | Thẻ bị hết hạn | **THẤT BẠI** | Code `09` |

## 2. Kiểm thử Callback & Verify Signature

- URL Return: `http://localhost:8081/vnpay/vnpay_return.jsp`
- Kiểm tra tính toàn vẹn chữ ký HMAC-SHA512 qua `Config.hashAllFields`: **HỢP LỆ**
- Hiển thị kết quả thành công: `<span class="label label-success">Thành công (00)</span>`
- Unit tests cấu hình VNPAY: `ModularConfigTest.testVnPayConfig` — **PASS**
