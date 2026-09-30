# Evidence — Ngày 08

- `HoldServiceTest`: 3 unit tests cho xác thực buyer, giới hạn 1–8 vé và cancel.
- `Day08IT`: SQL Server kiểm tra giữ quota standing, cancel trả quota về 0, và hold hết hạn được thu hồi khi tạo hold mới.
- Command: `mvn -B -Psqlserver-it -Dtest=__none__ -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=Day08IT verify` — 1 test passed.
