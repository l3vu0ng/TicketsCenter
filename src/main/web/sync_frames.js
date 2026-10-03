const fs = require('fs');
const path = require('path');

const mappings = [
  { root: 'index.html', frame: 'UI-01-danh-sach-su-kien.html' },
  { root: 'event-detail.html', frame: 'UI-02-chi-tiet-chon-ve.html' },
  { root: 'auth.html', frame: 'UI-03-dang-nhap-otp.html' },
  { root: 'checkout.html', frame: 'UI-04-giu-ve-thanh-toan.html' },
  { root: 'payment-result.html', frame: 'UI-05-ket-qua-thanh-toan.html' },
  { root: 'my-orders.html', frame: 'UI-06-don-cua-toi.html' },
  { root: 'tickets.html', frame: 'UI-07-ve-ma-qr.html' },
  { root: 'refund-request.html', frame: 'UI-08-yeu-cau-hoan-ve.html' },
  { root: 'organizer-request.html', frame: 'UI-09-yeu-cau-tao-to-chuc.html' },
  { root: 'organizer-dashboard.html', frame: 'UI-10-tong-quan-to-chuc.html' },
  { root: 'organizer-members.html', frame: 'UI-11-thanh-vien-to-chuc.html' },
  { root: 'organizer-events.html', frame: 'UI-12-danh-sach-su-kien-to-chuc.html' },
  { root: 'organizer-event-editor.html', frame: 'UI-13-bien-tap-su-kien-ghe.html' },
  { root: 'organizer-coupons.html', frame: 'UI-14-ma-giam-gia.html' },
  { root: 'checkin-select.html', frame: 'UI-15-chon-su-kien-checkin.html' },
  { root: 'checkin-scanner.html', frame: 'UI-16-quet-ma-ve.html' },
  { root: 'organizer-reports.html', frame: 'UI-17-bao-cao-to-chuc.html' },
  { root: 'admin-dashboard.html', frame: 'UI-18-tong-quan-quan-tri.html' },
  { root: 'admin-org-approval.html', frame: 'UI-19-duyet-yeu-cau-to-chuc.html' },
  { root: 'admin-event-approval.html', frame: 'UI-20-duyet-huy-su-kien.html' },
  { root: 'admin-refund-approval.html', frame: 'UI-21-duyet-hoan-ve.html' },
  { root: 'admin-settlements.html', frame: 'UI-22-chinh-sach-hoa-hong-doi-soat.html' },
  { root: 'admin-audit-logs.html', frame: 'UI-23-bao-cao-he-thong-audit.html' },
  { root: 'profile.html', frame: 'UI-24-ho-so-tai-khoan.html' },
];

let synced = 0;
for (const m of mappings) {
  const rootPath = path.join(__dirname, m.root);
  if (fs.existsSync(rootPath)) {
    let content = fs.readFileSync(rootPath, 'utf8');
    // adjust relative css path for frames subdir
    content = content.split('href="css/style.css"').join('href="../css/style.css"');
    // adjust links in frames so they navigate between frames
    for (const linkMap of mappings) {
      content = content.split(`href="${linkMap.root}"`).join(`href="${linkMap.frame}"`);
    }
    const framePath = path.join(__dirname, 'frames', m.frame);
    fs.writeFileSync(framePath, content, 'utf8');
    synced++;
  }
}
console.log(`Successfully synced ${synced} / ${mappings.length} frames.`);
