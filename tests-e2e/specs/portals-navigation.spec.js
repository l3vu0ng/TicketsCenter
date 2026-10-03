const { test, expect } = require('@playwright/test');

test.describe('Kiểm thử Giao diện các Phân hệ Quản trị & Nghiệp vụ', () => {

  test('1. Phân hệ Admin: Tổng quan quản trị', async ({ page }) => {
    await page.goto('pages/admin/UI-18-tong-quan-quan-tri.html');

    // Kiểm tra trang tải thành công
    await expect(page).toHaveTitle(/TicketsCenter/);
    await expect(page.locator('body')).toBeVisible();

    // Kiểm tra các thành phần chỉ số hoặc navigation
    const mainContent = page.locator('main');
    await expect(mainContent).toBeVisible();
  });

  test('2. Phân hệ Ban tổ chức: Tổng quan tổ chức', async ({ page }) => {
    await page.goto('pages/organizer/UI-10-tong-quan-to-chuc.html');

    await expect(page).toHaveTitle(/TicketsCenter/);
    await expect(page.locator('body')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
  });

  test('3. Phân hệ Soát vé: Chọn sự kiện check-in', async ({ page }) => {
    await page.goto('pages/checkin/UI-15-chon-su-kien-checkin.html');

    await expect(page).toHaveTitle(/TicketsCenter/);
    await expect(page.locator('body')).toBeVisible();
    await expect(page.locator('main')).toBeVisible();
  });

});
