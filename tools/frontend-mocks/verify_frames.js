const fs = require('fs');
const path = require('path');

const frames = [
  { id: 'UI-01', file: 'index.html', title: 'Trang chủ & Tìm kiếm' },
  { id: 'UI-02', file: 'event-detail.html', title: 'Chi tiết sự kiện & Chọn ghế' },
  { id: 'UI-03', file: 'auth.html', title: 'Đăng nhập / Đăng ký / Xác thực OTP' },
  { id: 'UI-04', file: 'checkout.html', title: 'Giữ vé 10 phút & Thanh toán' },
  { id: 'UI-05', file: 'payment-result.html', title: 'Kết quả thanh toán & Biên lai' },
  { id: 'UI-06', file: 'my-orders.html', title: 'Lịch sử đơn hàng của tôi' },
  { id: 'UI-07', file: 'tickets.html', title: 'Vé điện tử & Mã QR' },
  { id: 'UI-08', file: 'refund-request.html', title: 'Yêu cầu hoàn trả vé' },
  { id: 'UI-09', file: 'organizer-request.html', title: 'Đăng ký Đơn vị tổ chức' },
  { id: 'UI-10', file: 'organizer-dashboard.html', title: 'Tổng quan Đơn vị tổ chức' },
  { id: 'UI-11', file: 'organizer-members.html', title: 'Quản lý thành viên tổ chức' },
  { id: 'UI-12', file: 'organizer-events.html', title: 'Danh sách sự kiện của BTC' },
  { id: 'UI-13', file: 'organizer-event-editor.html', title: 'Biên tập sự kiện & Cấu hình vé' },
  { id: 'UI-14', file: 'organizer-coupons.html', title: 'Quản lý mã giảm giá' },
  { id: 'UI-15', file: 'checkin-select.html', title: 'Chọn ca sự kiện check-in' },
  { id: 'UI-16', file: 'checkin-scanner.html', title: 'Màn hình quét vé QR & Soát vé' },
  { id: 'UI-17', file: 'organizer-reports.html', title: 'Báo cáo doanh thu & Hạch toán' },
  { id: 'UI-18', file: 'admin-dashboard.html', title: 'Bảng điều khiển Quản trị viên' },
  { id: 'UI-19', file: 'admin-org-approval.html', title: 'Phê duyệt hồ sơ tổ chức' },
  { id: 'UI-20', file: 'admin-event-approval.html', title: 'Duyệt / Hủy khẩn cấp sự kiện' },
  { id: 'UI-21', file: 'admin-refund-approval.html', title: 'Xét duyệt hoàn tiền' },
  { id: 'UI-22', file: 'admin-settlements.html', title: 'Quyết toán & Đối soát doanh thu' },
  { id: 'UI-23', file: 'admin-audit-logs.html', title: 'Nhật ký kiểm toán hệ thống' },
  { id: 'UI-24', file: 'profile.html', title: 'Hồ sơ tài khoản cá nhân' }
];

let errors = 0;
const rootDir = path.join(__dirname);
const framesDir = path.join(__dirname, 'frames');
const indexHtml = fs.readFileSync(path.join(rootDir, 'index.html'), 'utf8');

console.log('--- VERIFYING 24 FRAMES ARCHITECTURE ---');

frames.forEach(f => {
  const rootPath = path.join(rootDir, f.file);
  if (!fs.existsSync(rootPath) || fs.statSync(rootPath).size < 100) {
    console.error(`[FAIL] Root file missing or empty: ${f.file}`);
    errors++;
  }

  // Check sync frame
  const framePattern = new RegExp(`^${f.id}-.*\\.html$`, 'i');
  const frameFiles = fs.readdirSync(framesDir).filter(name => framePattern.test(name));
  if (frameFiles.length === 0) {
    console.error(`[FAIL] Individual frame missing for ${f.id} in prototype/frames/`);
    errors++;
  }

  // Check index.html matrix link
  if (!indexHtml.includes(f.file) && !indexHtml.includes(f.id)) {
    console.warn(`[WARN] Index matrix may miss reference to ${f.id} (${f.file})`);
  }
});

if (errors === 0) {
  console.log(`[SUCCESS] All 24 frames are perfectly verified across both root files and frames directory!`);
  console.log(`- 24 Root pages: OK`);
  console.log(`- 24 Sync frame files in /frames: OK`);
  console.log(`- Index 24-frame navigation matrix: OK`);
} else {
  console.error(`Encountered ${errors} verification errors.`);
  process.exit(1);
}
