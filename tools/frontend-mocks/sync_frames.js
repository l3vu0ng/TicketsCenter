const fs = require('fs');
const path = require('path');

const webappDir = path.resolve(__dirname, '../../src/main/webapp');
const pagesDir = path.join(webappDir, 'pages');

const mappings = [
  { category: 'buyer', root: 'event-detail.html', frame: 'UI-02-chi-tiet-chon-ve.html' },
  { category: 'auth', root: 'auth.html', frame: 'UI-03-dang-nhap-otp.html' },
  { category: 'buyer', root: 'checkout.html', frame: 'UI-04-giu-ve-thanh-toan.html' },
  { category: 'buyer', root: 'payment-result.html', frame: 'UI-05-ket-qua-thanh-toan.html' },
  { category: 'buyer', root: 'my-orders.html', frame: 'UI-06-don-cua-toi.html' },
  { category: 'buyer', root: 'tickets.html', frame: 'UI-07-ve-ma-qr.html' },
  { category: 'buyer', root: 'refund-request.html', frame: 'UI-08-yeu-cau-hoan-ve.html' },
  { category: 'organizer', root: 'organizer-request.html', frame: 'UI-09-yeu-cau-tao-to-chuc.html' },
  { category: 'organizer', root: 'organizer-dashboard.html', frame: 'UI-10-tong-quan-to-chuc.html' },
  { category: 'organizer', root: 'organizer-members.html', frame: 'UI-11-thanh-vien-to-chuc.html' },
  { category: 'organizer', root: 'organizer-events.html', frame: 'UI-12-danh-sach-su-kien-to-chuc.html' },
  { category: 'organizer', root: 'organizer-event-editor.html', frame: 'UI-13-bien-tap-su-kien-ghe.html' },
  { category: 'organizer', root: 'organizer-coupons.html', frame: 'UI-14-ma-giam-gia.html' },
  { category: 'checkin', root: 'checkin-select.html', frame: 'UI-15-chon-su-kien-checkin.html' },
  { category: 'checkin', root: 'checkin-scanner.html', frame: 'UI-16-quet-ma-ve.html' },
  { category: 'organizer', root: 'organizer-reports.html', frame: 'UI-17-bao-cao-to-chuc.html' },
  { category: 'admin', root: 'admin-dashboard.html', frame: 'UI-18-tong-quan-quan-tri.html' },
  { category: 'admin', root: 'admin-org-approval.html', frame: 'UI-19-duyet-yeu-cau-to-chuc.html' },
  { category: 'admin', root: 'admin-event-approval.html', frame: 'UI-20-duyet-huy-su-kien.html' },
  { category: 'admin', root: 'admin-refund-approval.html', frame: 'UI-21-duyet-hoan-ve.html' },
  { category: 'admin', root: 'admin-settlements.html', frame: 'UI-22-chinh-sach-hoa-hong-doi-soat.html' },
  { category: 'admin', root: 'admin-audit-logs.html', frame: 'UI-23-bao-cao-he-thong-audit.html' },
  { category: 'buyer', root: 'profile.html', frame: 'UI-24-ho-so-tai-khoan.html' },
];

let synced = 0;
for (const m of mappings) {
  const rootPath = path.join(pagesDir, m.category, m.root);
  if (fs.existsSync(rootPath)) {
    let content = fs.readFileSync(rootPath, 'utf8');
    const framePath = path.join(pagesDir, m.category, m.frame);
    fs.writeFileSync(framePath, content, 'utf8');
    synced++;
  }
}
console.log(`Successfully synced ${synced} / ${mappings.length} frames.`);
