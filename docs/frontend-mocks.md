# CTicket — Bộ Page Frames & Giao Diện Hoàn Chỉnh

> Dự án: **TicketsCenter / CTicket**  
> Tuân thủ đặc tả nghiệp vụ: [SPEC.md](../SPEC.md) (Phiên bản 24/09/2026)  
> Phong cách thiết kế: **Human-crafted Commercial Ticketing Platform** (Lấy cảm hứng 1:1 từ ảnh thực tế CTicket & Ticketbox, loại bỏ hoàn toàn các cliché "AI slop" như double-bezel lặp lại, gradient tím vô căn cứ, khung mockup giả lập).

---

## 1. Thiết kế thương hiệu & Bố cục thực tế (CTicket Authentic UI)

1. **Header thanh lịch & đúng chuẩn thương hiệu**:
   - Logo thương hiệu: Chữ `C` màu hồng đỏ (`#e11d48`) trên nền tròn lồng chữ `Ticket` màu xanh biển (`#0284c7`).
   - Khung tìm kiếm tích hợp: Pill bo tròn gồm bộ chọn địa điểm `Tất cả địa điểm ▾` tách rời bằng vạch phân cách với ô `Tìm kiếm sự kiện, nghệ sĩ,...`.
   - Các nút hành động: `Đăng nhập`, `Đăng ký`, chọn ngôn ngữ `🇻🇳 VN ▾` và icon hồ sơ người dùng.
2. **Hero Banner toàn cảnh**:
   - Banner góc rộng độ phân giải cao hình ảnh *Bảo tàng Phụ nữ Việt Nam* với giàn hoa giấy rực rỡ, hàng cây xanh và kiến trúc bảo tàng đúng như ảnh mẫu.
   - Nút mũi tên điều hướng hai bên (`‹` và `›`) cùng các chấm phân trang (`••••••••`).
3. **Thanh phân loại danh mục "Sự kiện"**:
   - Tiêu đề **Sự kiện** đậm nét, trang nhã.
   - Hàng tab danh mục dạng chữ/pill cuộn mượt: *Tất cả, Nhạc sống, Fan Meeting, Merchandise, Sân khấu & Nghệ thuật, Thể thao, Hội thảo & Cộng đồng, Khoá học, Nightlife, Livestream, Tham quan* kèm mũi tên điều hướng cuộn phải `›`.
4. **Thẻ sự kiện (Event Cards)**:
   - Tỷ lệ chuẩn 16:10, hiệu ứng hover zoom ảnh nhẹ nhàng.
   - Badge trạng thái: `Đang mở bán`, địa điểm `Hà Nội`, ngày giờ rõ ràng (`20 Th10, 2026 • 19:30`), giá khởi điểm nổi bật (`Từ 40.000 đ`).

---

## 2. Danh mục 24 Page Frames (UI-01 đến UI-24)

| Mã màn hình | Tên màn hình trong SPEC.md | File HTML độc lập | Tính năng chính |
|---|---|---|---|
| **UI-01** | Danh sách & Tìm kiếm sự kiện | [`src/main/webapp/index.html`](../src/main/webapp/index.html) | Bố cục 1:1 với ảnh mẫu CTicket, bộ lọc danh mục, Hero banner, thẻ sự kiện |
| **UI-02** | Chi tiết sự kiện & Chọn vé | [`src/main/webapp/pages/frames/UI-02-chi-tiet-chon-ve.html`](../src/main/webapp/pages/frames/UI-02-chi-tiet-chon-ve.html) | Sơ đồ rạp hát thực tế (Stage, Hàng A-B VIP, C-D Thường, Khu đứng GA, giỏ vé cố định) |
| **UI-03** | Đăng ký, Đăng nhập, OTP 6 số | [`frames/UI-03-dang-nhap-otp.html`](frames/UI-03-dang-nhap-otp.html) | Form xác thực tối giản, 6 ô số OTP, đếm lùi 60s, giới hạn 5 lần sai |
| **UI-04** | Lượt giữ vé & Thanh toán | [`frames/UI-04-giu-ve-thanh-toan.html`](frames/UI-04-giu-ve-thanh-toan.html) | Đồng hồ đếm ngược 10 phút, kiểm tra coupon &le; 30% trần, cổng VNPAY / đơn 0đ |
| **UI-05** | Kết quả thanh toán VNPAY | [`frames/UI-05-ket-qua-thanh-toan.html`](frames/UI-05-ket-qua-thanh-toan.html) | Hóa đơn giao dịch điện tử, trạng thái CAPTURED, UNKNOWN, bù trừ |
| **UI-06** | Đơn hàng của tôi | [`frames/UI-06-don-cua-toi.html`](frames/UI-06-don-cua-toi.html) | Lịch sử mua vé, mã đơn hàng, đường dẫn xem vé và yêu cầu hoàn tiền |
| **UI-07** | Vé điện tử & Mã QR | [`frames/UI-07-ve-ma-qr.html`](frames/UI-07-ve-ma-qr.html) | Thẻ vé máy bay/sự kiện thực tế có răng cưa perforation, mã QR vector sắc nét, chia sẻ |
| **UI-08** | Yêu cầu hoàn vé | [`frames/UI-08-yeu-cau-hoan-ve.html`](frames/UI-08-yeu-cau-hoan-ve.html) | Chọn vé chưa dùng, tính paidAmount, lý do khách gửi (CUSTOMER_REQUEST) |
| **UI-09** | Đăng ký tạo tổ chức | [`frames/UI-09-yeu-cau-tao-to-chuc.html`](frames/UI-09-yeu-cau-tao-to-chuc.html) | Form nộp hồ sơ xin làm ban tổ chức sự kiện |
| **UI-10** | Bảng điều khiển Tổ chức | [`frames/UI-10-tong-quan-to-chuc.html`](frames/UI-10-tong-quan-to-chuc.html) | Giao diện SaaS chuyên nghiệp kiểu Stripe (sidebar trái, biểu đồ KPI, sự kiện) |
| **UI-11** | Quản lý thành viên | [`frames/UI-11-thanh-vien-to-chuc.html`](frames/UI-11-thanh-vien-to-chuc.html) | Phân quyền MANAGER / CHECK_IN_STAFF, bảo vệ người quản lý cuối cùng |
| **UI-12** | Danh sách sự kiện tổ chức | [`frames/UI-12-danh-sach-su-kien-to-chuc.html`](frames/UI-12-danh-sach-su-kien-to-chuc.html) | Trạng thái DRAFT, PENDING, PUBLISHED, hiển thị lý do nếu bị từ chối |
| **UI-13** | Biên tập sự kiện, khu & ghế | [`frames/UI-13-bien-tap-su-kien-ghe.html`](frames/UI-13-bien-tap-su-kien-ghe.html) | Ràng buộc `saleStart < saleEnd <= startTime < endTime`, tạo số hàng ghế |
| **UI-14** | Quản lý mã giảm giá | [`frames/UI-14-ma-giam-gia.html`](frames/UI-14-ma-giam-gia.html) | Quản lý mã coupon của tổ chức, kiểm tra hạn mức `maxUses` |
| **UI-15** | Chọn sự kiện soát vé | [`frames/UI-15-chon-su-kien-checkin.html`](frames/UI-15-chon-su-kien-checkin.html) | Giới hạn theo khung giờ hợp lệ `[startTime - 60 phút, endTime)` |
| **UI-16** | Cổng kiểm soát vé (Scanner) | [`frames/UI-16-quet-ma-ve.html`](frames/UI-16-quet-ma-ve.html) | Giao diện mobile-first, viewfinder camera có tia laser, phản hồi hợp lệ/đã dùng |
| **UI-17** | Báo cáo doanh thu & CSV | [`frames/UI-17-bao-cao-to-chuc.html`](frames/UI-17-bao-cao-to-chuc.html) | Doanh thu, hoàn tiền, hoa hồng, thực nhận (Net Payable) & nút xuất CSV |
| **UI-18** | Tổng quan Quản trị viên | [`frames/UI-18-tong-quan-quan-tri.html`](frames/UI-18-tong-quan-quan-tri.html) | Bảng điều hành Admin nền tảng, triage hàng đợi phê duyệt |
| **UI-19** | Duyệt yêu cầu tổ chức | [`frames/UI-19-duyet-yeu-cau-to-chuc.html`](frames/UI-19-duyet-yeu-cau-to-chuc.html) | Duyệt tạo Organization, gán CommissionRule và cấp MANAGER trong 1 transaction |
| **UI-20** | Duyệt & Hủy sự kiện | [`frames/UI-20-duyet-huy-su-kien.html`](frames/UI-20-duyet-huy-su-kien.html) | Công khai sự kiện hoặc hủy khẩn cấp (kích hoạt tự động hoàn tiền toàn bộ) |
| **UI-21** | Xét duyệt hoàn tiền | [`frames/UI-21-duyet-hoan-ve.html`](frames/UI-21-duyet-hoan-ve.html) | Xét lý do hoàn của khách, duyệt hoàn tiền mô phỏng, cập nhật trả kho |
| **UI-22** | Hoa hồng & Quyết toán | [`frames/UI-22-chinh-sach-hoa-hong-doi-soat.html`](frames/UI-22-chinh-sach-hoa-hong-doi-soat.html) | Công thức chuẩn mục 6.11, đóng băng số liệu CONFIRMED & mô phỏng Payout |
| **UI-23** | Báo cáo hệ thống & Audit | [`frames/UI-23-bao-cao-he-thong-audit.html`](frames/UI-23-bao-cao-he-thong-audit.html) | Nhật ký AuditLog truy vết actor, action, target entity, timestamp |
| **UI-24** | Hồ sơ tài khoản người dùng | [`frames/UI-24-ho-so-tai-khoan.html`](frames/UI-24-ho-so-tai-khoan.html) | Thông tin cá nhân, trạng thái xác thực email và danh sách tổ chức tham gia |

---

## 3. Cách xem và trải nghiệm

1. **Xem trực tiếp trên trình duyệt**:
   Mở `/index.html` của WAR sau khi chạy ứng dụng bằng bất kỳ trình duyệt nào (Chrome, Edge, Firefox, Safari). Landing backend được giữ tại `/backend/`.
2. **Khám phá tự nhiên như người dùng thật**:
   - Nhấp vào thẻ sự kiện bất kỳ (ví dụ: *Bảo tàng Phụ nữ Việt Nam*) để vào màn hình **UI-02** (chọn ghế).
   - Chọn ghế VIP hoặc vé đứng -> bấm *"Tiến hành giữ vé & Thanh toán"* để sang màn hình **UI-04**.
   - Trải nghiệm đồng hồ đếm lùi 10 phút, áp mã giảm giá -> bấm *"Chuyển đến VNPAY Sandbox"* để sang màn hình kết quả **UI-05**.
   - Bấm *"Xem vé & Mã QR"* để mở thẻ vé điện tử **UI-07** có răng cưa perforation và mã QR vector.
3. **Thanh Drawer chuyển nhanh 24 màn hình**:
   Ở góc dưới bên phải màn hình có nút tròn đen: **"Xem 24 Màn Hình (SPEC.md)"**. Nhấp vào sẽ mở ra bảng danh mục đầy đủ 24 màn hình được phân loại rõ ràng theo từng nhóm vai trò (*Khách/Người mua, Tổ chức, Soát vé, Quản trị viên*), cho phép nhảy thẳng đến bất kỳ màn hình nào trong tích tắc!
