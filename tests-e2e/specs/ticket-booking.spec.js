const { test, expect } = require('@playwright/test');

test.describe('Luồng Đặt Vé và Thanh Toán (Buyer Journey)', () => {

  test('1. Xem danh sách sự kiện và tìm kiếm', async ({ page }) => {
    await page.goto('pages/buyer/UI-01-danh-sach-su-kien.html');

    // Kiểm tra tiêu đề trang và thanh navbar
    await expect(page).toHaveTitle(/TicketsCenter/);
    await expect(page.locator('.navbar-brand-custom')).toContainText('TicketsCenter');

    // Kiểm tra ô tìm kiếm và bộ lọc địa điểm
    const searchInput = page.locator('.search-input');
    await expect(searchInput).toBeVisible();
    await searchInput.fill('Hòa nhạc');

    // Click vào thẻ sự kiện đầu tiên
    const eventCard = page.locator('.event-card').first();
    await expect(eventCard).toBeVisible();
    const eventLink = eventCard.locator('a[href*="UI-02"]').first();
    await eventLink.click();

    // Chuyển hướng sang trang chi tiết chọn vé
    await expect(page).toHaveURL(/UI-02-chi-tiet-chon-ve\.html/);
  });

  test('2. Chọn ghế, điều chỉnh vé tự do và tính toán giá vé', async ({ page }) => {
    await page.goto('pages/buyer/UI-02-chi-tiet-chon-ve.html');

    // Kiểm tra sơ đồ ghế hiển thị
    const seatGrid = page.locator('.seat-grid');
    await expect(seatGrid).toBeVisible();

    // Chọn ghế chưa bán (ví dụ C1 hoặc D1)
    const availableSeat = page.locator('.seat-btn:not(.occupied)').first();
    await expect(availableSeat).toBeVisible();
    await availableSeat.click();
    await expect(availableSeat).toHaveClass(/selected/);

    // Tăng số lượng vé tham quan tự do (GA count)
    const plusButton = page.locator('button:has-text("+")');
    if (await plusButton.isVisible()) {
      await plusButton.click();
    }

    // Kiểm tra tổng tiền tạm tính hiển thị
    const totalPrice = page.locator('#totalPriceText');
    await expect(totalPrice).toBeVisible();
    await expect(totalPrice).not.toHaveText('0 đ');

    // Click nút Tiếp tục thanh toán
    const checkoutButton = page.locator('a:has-text("Tiếp tục thanh toán")');
    await expect(checkoutButton).toBeVisible();
    await checkoutButton.click();

    // Kiểm tra chuyển đến trang giữ vé và thanh toán
    await expect(page).toHaveURL(/UI-04-giu-ve-thanh-toan\.html/);
  });

  test('3. Giữ vé tạm thời, kiểm tra thông tin và hoàn tất thanh toán', async ({ page }) => {
    await page.goto('pages/buyer/UI-04-giu-ve-thanh-toan.html');

    // Kiểm tra bộ đếm thời gian giữ vé
    const timerDisplay = page.locator('#holdCountdown');
    await expect(timerDisplay).toBeVisible();

    // Kiểm tra trường thông tin người nhận vé
    const nameInput = page.locator('input[value="Nguyễn Hoàng Nam"]');
    await expect(nameInput).toBeVisible();

    // Kiểm tra phương thức thanh toán VNPAY được chọn
    const vnpayRadio = page.locator('input[value="VNPAY"]');
    await expect(vnpayRadio).toBeChecked();

    // Kiểm tra tóm tắt tài chính và tổng thanh toán
    const finalTotal = page.locator('#finalTotal');
    await expect(finalTotal).toBeVisible();

    // Nhấn nút Thanh toán ngay
    const payButton = page.locator('a:has-text("Thanh toán an toàn ngay")');
    await expect(payButton).toBeVisible();
    await payButton.click();

    // Chuyển hướng sang trang kết quả thanh toán
    await expect(page).toHaveURL(/UI-05-ket-qua-thanh-toan\.html/);
  });

  test('4. Xác nhận kết quả thanh toán và xem vé QR điện tử', async ({ page }) => {
    await page.goto('pages/buyer/UI-05-ket-qua-thanh-toan.html');

    // Kiểm tra trạng thái giao dịch thành công
    const statusPill = page.locator('.status-pill');
    await expect(statusPill).toContainText('Đã xác nhận thanh toán');

    // Click xem vé điện tử mã QR
    const qrButton = page.locator('a:has-text("Xem vé điện tử & Mã QR")');
    await expect(qrButton).toBeVisible();
    await qrButton.click();

    // Chuyển sang trang mã QR
    await expect(page).toHaveURL(/UI-07-ve-ma-qr\.html/);
    await expect(page.locator('body')).toBeVisible();
  });

});
