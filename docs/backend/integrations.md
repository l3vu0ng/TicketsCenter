# Tài liệu tích hợp dịch vụ bên ngoài

## Dịch vụ gửi email (MailGateway)

Hệ thống giao tiếp qua hợp đồng `MailGateway`. Phản hồi từ nhà cung cấp không được trực tiếp làm thay đổi trạng thái nghiệp vụ.

### So sánh các nhà cung cấp email

| Tiêu chí | Gmail SMTP | Resend API | Amazon SES | SendGrid |
|---|---|---|---|---|
| Giao thức | SMTP TLS qua cổng 587 | HTTPS REST API | HTTPS API hoặc SMTP | HTTPS API hoặc SMTP |
| Hạn mức thử nghiệm | 500 email mỗi ngày | 3000 email mỗi tháng | 200 email mỗi ngày | 100 email mỗi ngày |
| Xác minh người gửi | Bật App Password trong tài khoản Google | Xác thực bản ghi DKIM và SPF theo domain | Xác thực domain và email sandbox | Xác thực domain và Single Sender |
| Phù hợp hiện tại | Phù hợp giai đoạn phát triển cục bộ và sandbox | Rất tốt cho môi trường staging | Phù hợp production quy mô lớn | Phù hợp marketing và notification |

### Chiến lược xử lý timeout và retry

- Gửi mã OTP ưu tiên độ trễ thấp dưới 2 giây. Mã quá hạn không được tiếp tục gửi lại.
- Thao tác gửi email chạy bất đồng bộ hoặc có thời gian timeout nghiêm ngặt dưới 5 giây. Cơ chế này không giữ khóa connection cơ sở dữ liệu.
- Email thông báo vé và giao dịch tài chính ở Ngày 12 sẽ sử dụng bảng transactional outbox `tc_outbox` để đảm bảo không mất mát dữ liệu khi nhà cung cấp gặp sự cố.

### Biến cấu hình

Cấu hình email được đọc từ tập tin `application.properties`:
- `mail.smtp.host`: Địa chỉ máy chủ SMTP.
- `mail.smtp.port`: Cổng kết nối SMTP.
- `mail.smtp.user`: Tên đăng nhập hoặc địa chỉ email gửi tin.
- `mail.smtp.password`: Mật khẩu ứng dụng đã mã hóa hoặc lấy từ biến môi trường.

Nếu thông tin đăng nhập để trống, `ConfiguredMailGateway` sẽ ghi nhận dispatch an toàn vào log mà không để lộ nội dung mã OTP.

## Dịch vụ lưu trữ tệp (Storage)

Hệ thống tách biệt hoàn toàn việc lưu trữ tài nguyên tĩnh ra ngoài container Tomcat.

### Nguyên tắc thiết kế

- Ứng dụng không ghi tệp tải lên vào hệ thống tệp cục bộ của Tomcat.
- Mỗi ảnh hoặc tài liệu được cấp quyền upload và delete theo namespace riêng biệt của ứng dụng.
- Đường dẫn truy cập công khai luôn sử dụng giao thức bảo mật HTTPS.
- Không lưu trữ hoặc phản hồi bất kỳ khóa truy cập hoặc credential nào trong response và log hệ thống.
