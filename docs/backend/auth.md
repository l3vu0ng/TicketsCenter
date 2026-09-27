# Xác thực và phiên

## Luồng HTTP đã triển khai

| Endpoint | Yêu cầu | Kết quả |
|---|---|---|
| `GET /api/auth/csrf` | Không cần đăng nhập. Tạo hoặc đọc session | Trả CSRF token gắn với session |
| `POST /api/auth/register` | JSON hoặc form `{email,password}` và `X-CSRF-Token` | Chuẩn hóa email. Tạo User ACTIVE chưa verified. Luôn trả `accepted` khi email hợp lệ |
| `POST /api/auth/login` | JSON hoặc form `{email,password}` và `X-CSRF-Token` | Đổi session ID. Lưu `userId` và `authVersion` |
| `POST /api/auth/logout` | Session hợp lệ và `X-CSRF-Token` | Chỉ vô hiệu session hiện tại |
| `GET /api/me` | Session hợp lệ | Trả `id`, `email`, `admin`. Không trả hash hoặc `authVersion` |
| `POST /api/auth/otp/send` | JSON hoặc form `{purpose, email}` và `X-CSRF-Token` | Gửi mã OTP 6 chữ số qua MailGateway. Giới hạn cooldown 60 giây và TTL 5 phút |
| `POST /api/auth/otp/verify` | JSON hoặc form `{purpose, email, code}` và `X-CSRF-Token` | Khóa và đối chiếu HMAC. Xác minh email hoặc sinh quyền reset mật khẩu một lần |
| `POST /api/auth/password/forgot` | JSON hoặc form `{email}` và `X-CSRF-Token` | Gửi OTP đặt lại mật khẩu nếu email tồn tại. Phản hồi nhất quán để chống dò quét tài khoản |
| `POST /api/auth/password/reset` | JSON hoặc form `{resetToken, newPassword}` và `X-CSRF-Token` | Cập nhật mật khẩu mới. Hủy quyền reset. Tăng `authVersion` để vô hiệu mọi session cũ |

Các mutation nhận tối đa 64 KiB. Hệ thống chỉ chấp nhận JSON hoặc form URL-encoded. Phản hồi auth luôn đặt `Cache-Control: no-store`.

## Mật khẩu và session

- Mật khẩu dài 12 đến 256 ký tự và không trim.
- Email được chuẩn hóa trim và chuyển chữ thường.
- Hash dùng PBKDF2-HMAC-SHA256 với salt ngẫu nhiên 16 byte và 210.000 vòng lặp.
- Login sai mật khẩu hoặc user không tồn tại cùng trả mã lỗi `INVALID_CREDENTIALS`.
- Mỗi request có auth đọc lại User ACTIVE và `authVersion`. Đổi version làm session cũ mất hiệu lực ngay lập tức.
- Cookie session có cờ HttpOnly và SameSite=Lax. Tomcat thêm Secure khi request chạy qua HTTPS/proxy được cấu hình đúng.

## Admin seed và phân quyền nền

- Seed chỉ chạy khi `admin.seed.enabled=true`. Admin được verified ngay, có role `ADMIN`, và chạy lại không đổi hash.
- Môi trường production từ chối khởi động nếu thiếu mật khẩu admin hoặc dùng giá trị demo `admin`. Không có endpoint HTTP cấp role admin.
- `AuthorizationService` kiểm owner/admin trực tiếp và đọc membership ACTIVE hiện tại từ database. Endpoint phải trả `403` khi thiếu quyền và `404` cho ID ngoài scope.

## Cơ chế OTP và khôi phục mật khẩu

- Mã OTP gồm đúng 6 chữ số ngẫu nhiên sinh bằng `SecureRandom`. Các chữ số 0 ở đầu được giữ nguyên vẹn.
- Bảng `tc_otps` chỉ lưu HMAC-SHA256 của mã kết hợp normalized email và purpose. Tuyệt đối không lưu mã rõ.
- Cooldown giữa hai lần gửi mã liên tiếp là 60 giây. Mã mới tạo sẽ vô hiệu toàn bộ mã chưa dùng trước đó cùng purpose.
- Thời hạn hiệu lực của mỗi mã là 5 phút.
- Mỗi mã chỉ cho phép tối đa 5 lần thử sai. Lần sai thứ 5 sẽ lập tức vô hiệu hóa mã vĩnh viễn. Lần thử sai được commit trực tiếp vào database.
- Xác minh thành công OTP mục đích `RESET_PASSWORD` cấp quyền reset một lần có hạn 10 phút.
- Đặt lại mật khẩu thành công sẽ tăng `authVersion` của người dùng trong cùng transaction. Toàn bộ phiên đăng nhập đang hoạt động của người dùng sẽ bị từ chối ở request tiếp theo.
