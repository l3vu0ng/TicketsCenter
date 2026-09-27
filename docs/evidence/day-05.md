# Bằng chứng kiểm thử Ngày 05

Tài liệu này ghi nhận kết quả kiểm chứng cho các chức năng OTP email, khôi phục mật khẩu và tích hợp nhà cung cấp.

## Danh sách ca kiểm thử bắt buộc

| Mã ca kiểm thử | Mô tả nghiệp vụ | Kết quả kỳ vọng | Trạng thái thực tế |
|---|---|---|---|
| TC-D05-01 | Sinh mã OTP 6 chữ số | Giữ số 0 ở đầu nếu mã nhỏ hơn 100000 | Đạt. Mã `000001` sinh đủ 6 ký tự |
| TC-D05-02 | Cooldown gửi lại mã OTP | Chặn gửi ở giây 59 và cho phép ở giây 60 | Đạt. Ném lỗi ở giây 59 và gửi thành công ở giây 60 |
| TC-D05-03 | Vô hiệu hóa mã cũ khi resend | Mã mới vô hiệu toàn bộ mã chưa dùng trước đó | Đạt. `invalidatedAt` được gán mốc thời gian ngay lập tức |
| TC-D05-04 | Khóa mã sau 5 lần nhập sai | Lần sai thứ 5 khóa mã vĩnh viễn | Đạt. `failedAttempts` tăng lên 5 và gán `invalidatedAt` |
| TC-D05-05 | Kiểm tra thời hạn hiệu lực 5 phút | Từ chối mã khi thời gian vượt quá 5 phút | Đạt. Trả lỗi `OTP_EXPIRED` |
| TC-D05-06 | Xác minh email | Cập nhật `emailVerifiedAt` và tiêu thụ mã | Đạt. User được cập nhật thời gian xác thực |
| TC-D05-07 | Quyền đặt lại mật khẩu một lần | Cấp reset token có hạn 10 phút | Đạt. Token ký HMAC chứa thời hạn và hash mật khẩu |
| TC-D05-08 | Vô hiệu hóa phiên sau reset | Tăng `authVersion` và chặn phiên đăng nhập cũ | Đạt. Mật khẩu cũ bị từ chối và session cũ bị hủy |
| TC-D05-09 | Giới hạn kích thước payload | Chặn body vượt quá 64 KiB tại boundary | Đạt. Trả về mã HTTP 413 `PAYLOAD_TOO_LARGE` |
| TC-D05-10 | Chống dò quét tài khoản tại endpoint forgot | Trả phản hồi giống nhau khi email có hoặc không tồn tại | Đạt. Trả thông điệp thành công nhất quán |

## Kết quả chạy kiểm thử tự động

Lệnh thực thi kiểm thử:
```bash
mvn test
```

Kết quả thực tế từ Surefire:
```text
[INFO] Running vn.ticketscenter.identity.OtpTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.217 s -- in vn.ticketscenter.identity.OtpTest
[INFO] Running vn.ticketscenter.identity.PasswordResetIT
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.322 s -- in vn.ticketscenter.identity.PasswordResetIT
[INFO] Running vn.ticketscenter.acceptance.Day05IT
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.272 s -- in vn.ticketscenter.acceptance.Day05IT
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

## Kiểm tra bảo mật và dữ liệu

1. Bảng `tc_otps` chỉ lưu trữ `secret_hash` varbinary(64). Plaintext OTP không xuất hiện trong database hay trong bất kỳ log nào.
2. Token reset mật khẩu được ký bằng thuật toán HMAC-SHA256 kết hợp hash mật khẩu hiện tại. Ngay khi mật khẩu được cập nhật, token tự động vô hiệu hóa hoàn toàn mà không thể dùng lại.
3. Toàn bộ 45 kiểm thử đơn vị và tích hợp đều đạt 100%.
