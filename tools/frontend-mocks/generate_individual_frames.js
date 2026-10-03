const fs = require('fs');
const path = require('path');

const indexHtml = fs.readFileSync(path.resolve(__dirname, '../../src/main/webapp/index.html'), 'utf8');

const frameDefs = [
  { id: 'ui-01', name: 'UI-01-danh-sach-su-kien', title: 'UI-01: Danh sách và tìm kiếm sự kiện' },
  { id: 'ui-02', name: 'UI-02-chi-tiet-chon-ve', title: 'UI-02: Chi tiết sự kiện và chọn vé' },
  { id: 'ui-03', name: 'UI-03-dang-nhap-otp', title: 'UI-03: Đăng ký, đăng nhập, OTP và đặt lại mật khẩu' },
  { id: 'ui-04', name: 'UI-04-giu-ve-thanh-toan', title: 'UI-04: Lượt giữ và thanh toán' },
  { id: 'ui-05', name: 'UI-05-ket-qua-thanh-toan', title: 'UI-05: Kết quả thanh toán' },
  { id: 'ui-06', name: 'UI-06-don-cua-toi', title: 'UI-06: Đơn của tôi' },
  { id: 'ui-07', name: 'UI-07-ve-ma-qr', title: 'UI-07: Vé và mã QR' },
  { id: 'ui-08', name: 'UI-08-yeu-cau-hoan-ve', title: 'UI-08: Yêu cầu hoàn vé' },
  { id: 'ui-09', name: 'UI-09-yeu-cau-tao-to-chuc', title: 'UI-09: Yêu cầu tạo tổ chức' },
  { id: 'ui-10', name: 'UI-10-tong-quan-to-chuc', title: 'UI-10: Chọn tổ chức và tổng quan tổ chức' },
  { id: 'ui-11', name: 'UI-11-thanh-vien-to-chuc', title: 'UI-11: Thành viên tổ chức' },
  { id: 'ui-12', name: 'UI-12-danh-sach-su-kien-to-chuc', title: 'UI-12: Danh sách sự kiện của tổ chức' },
  { id: 'ui-13', name: 'UI-13-bien-tap-su-kien-ghe', title: 'UI-13: Biên tập sự kiện, khu và ghế' },
  { id: 'ui-14', name: 'UI-14-ma-giam-gia', title: 'UI-14: Mã giảm giá' },
  { id: 'ui-15', name: 'UI-15-chon-su-kien-checkin', title: 'UI-15: Chọn sự kiện check-in' },
  { id: 'ui-16', name: 'UI-16-quet-ma-ve', title: 'UI-16: Quét hoặc nhập mã vé' },
  { id: 'ui-17', name: 'UI-17-bao-cao-to-chuc', title: 'UI-17: Báo cáo tổ chức' },
  { id: 'ui-18', name: 'UI-18-tong-quan-quan-tri', title: 'UI-18: Tổng quan quản trị' },
  { id: 'ui-19', name: 'UI-19-duyet-yeu-cau-to-chuc', title: 'UI-19: Duyệt yêu cầu tổ chức' },
  { id: 'ui-20', name: 'UI-20-duyet-huy-su-kien', title: 'UI-20: Duyệt và hủy sự kiện' },
  { id: 'ui-21', name: 'UI-21-duyet-hoan-ve', title: 'UI-21: Duyệt yêu cầu hoàn vé' },
  { id: 'ui-22', name: 'UI-22-chinh-sach-hoa-hong-doi-soat', title: 'UI-22: Chính sách hoa hồng, đối soát và chi trả' },
  { id: 'ui-23', name: 'UI-23-bao-cao-he-thong-audit', title: 'UI-23: Báo cáo toàn hệ thống và nhật ký thao tác' },
  { id: 'ui-24', name: 'UI-24-ho-so-tai-khoan', title: 'UI-24: Hồ sơ tài khoản' },
];

const headMatch = indexHtml.match(/<!DOCTYPE html>[\s\S]*?<main class="flex-1 pb-16">/);
const footerMatch = indexHtml.match(/<\/main>[\s\S]*?<\/html>/);

if (!headMatch || !footerMatch) {
  console.error("Could not parse head/footer template.");
  process.exit(1);
}

const headHtml = headMatch[0];
const footerHtml = footerMatch[0];

const pagesDir = path.resolve(__dirname, '../../src/main/webapp/pages');
const categoryFor = number => {
  if (number <= 8 || number === 24) return 'buyer';
  if (number <= 14 || number === 17) return 'organizer';
  if (number <= 16) return 'checkin';
  return 'admin';
};

for (const def of frameDefs) {
  const regex = new RegExp(`<section id="${def.id}" class="page-frame(?: hidden)?">([\\s\\S]*?)<\\/section>`);
  const match = indexHtml.match(regex);
  if (match) {
    let sectionContent = match[1];
    // Create standalone page
    const pageHtml = `<!DOCTYPE html>
<html lang="vi">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>${def.title} — TicketsCenter</title>
  <script src="https://cdn.tailwindcss.com"></script>
  <link rel="stylesheet" href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/regular/style.css">
  <link rel="stylesheet" href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/fill/style.css">
  <link rel="stylesheet" href="https://unpkg.com/@phosphor-icons/web@2.1.1/src/bold/style.css">
  <link rel="stylesheet" href="../../assets/css/style.css">
</head>
<body class="bg-slate-50 text-slate-900 antialiased min-h-screen flex flex-col">
  <!-- Top Navigation -->
  <header class="bg-slate-900 text-white px-4 py-2 flex items-center justify-between text-xs">
    <div class="flex items-center gap-2">
      <a href="../../index.html" class="font-bold font-display text-sm text-blue-400 hover:text-white flex items-center gap-1">
        <i class="ph ph-arrow-left"></i> Quay lại Master Prototype
      </a>
      <span class="text-slate-500">|</span>
      <span class="font-mono text-slate-300 font-bold">${def.id.toUpperCase()}</span>
    </div>
    <span class="text-slate-400">SPEC.md Page Frame</span>
  </header>

  <main class="flex-1 pb-16">
    <section id="${def.id}" class="page-frame">
      ${sectionContent}
    </section>
  </main>

  <footer class="bg-white border-t border-slate-200 py-6 text-center text-xs text-slate-400">
    TicketsCenter • ${def.title}
  </footer>

  <script src="../../assets/js/api/data.js"></script>
  <script src="../../assets/js/main.js"></script>
</body>
</html>`;

    const categoryDir = path.join(pagesDir, categoryFor(Number(def.name.slice(3, 5))));
    fs.mkdirSync(categoryDir, { recursive: true });
    fs.writeFileSync(path.join(categoryDir, `${def.name}.html`), pageHtml, 'utf8');
  } else {
    console.warn(`Could not extract section for ${def.id}`);
  }
}

console.log(`Generated all ${frameDefs.length} standalone frame HTML files in src/main/webapp/pages/{buyer,organizer,checkin,admin}/.`);
