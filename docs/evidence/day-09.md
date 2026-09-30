# Bằng chứng Ngày 09

## TDD và unit test

- Red: `OrderServiceTest` không biên dịch trước khi tạo OrderRepository/OrderService seam.
- Green: `mvn -B -Dtest=OrderServiceTest test` đạt 3 test; kiểm actor từ session, breakdown server-side, gỡ mã blank, cap 30% và subtotal bằng 1.

## SQL integration

- `database/tests/day-09.sql` và `Day09IT` kiểm SP06 replay cùng order, snapshot unit price trước/sau đổi Zone price, SP03 fixed 200000 bị cap còn 150000 trên subtotal 500000, gỡ mã và owner khác bị từ chối.
- Lệnh nghiệm thu: `mvn -B -Psqlserver-it -Dtest=__none__ -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Day09IT verify`.
