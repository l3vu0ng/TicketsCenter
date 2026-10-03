<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="description" content="Tạo mới đơn hàng thanh toán VNPAY">
        <meta name="author" content="TicketsCenter">
        <title>Tạo mới đơn hàng — TicketsCenter VNPAY</title>
        <!-- Bootstrap core CSS -->
        <link href="${pageContext.request.contextPath}/assets/css/bootstrap.min.css" rel="stylesheet"/>
        <!-- Custom styles for this template -->
        <link href="${pageContext.request.contextPath}/assets/css/jumbotron-narrow.css" rel="stylesheet">
        <script src="${pageContext.request.contextPath}/assets/js/jquery-1.11.3.min.js"></script>
    </head>

    <body>
        <div class="container">
            <div class="header clearfix">
                <nav>
                    <ul class="nav nav-pills pull-right">
                        <li role="presentation"><a href="${pageContext.request.contextPath}/vnpay/index.jsp">Trang demo VNPAY</a></li>
                        <li role="presentation"><a href="${pageContext.request.contextPath}/">TicketsCenter</a></li>
                    </ul>
                </nav>
                <h3 class="text-muted">TicketsCenter — VNPAY Sandbox</h3>
            </div>

            <h3>Khởi tạo giao dịch thanh toán</h3>
            <div class="table-responsive">
                <form action="${pageContext.request.contextPath}/vnpayajax" id="frmCreateOrder" method="post">
                    <input type="hidden" name="returnUrl" value="${pageContext.request.scheme}://${pageContext.request.serverName}:${pageContext.request.serverPort}${pageContext.request.contextPath}/vnpay/vnpay_return.jsp" />
                    <div class="form-group">
                        <label for="amount">Số tiền thanh toán (VND)</label>
                        <input class="form-control" id="amount" max="100000000" min="1" name="amount" type="number" value="50000" required />
                        <span class="help-block">Số tiền thực tế sẽ được nhân 100 theo quy định VNPAY.</span>
                    </div>

                    <h4>Chọn phương thức thanh toán</h4>
                    <div class="form-group">
                        <div class="radio">
                            <label>
                                <input type="radio" checked="checked" id="bankCodeDefault" name="bankCode" value="">
                                <strong>Cổng thanh toán VNPAYQR</strong> (Người dùng chọn phương thức tại cổng VNPAY)
                            </label>
                        </div>
                        <div class="radio">
                            <label>
                                <input type="radio" id="bankCodeQR" name="bankCode" value="VNPAYQR">
                                Thanh toán bằng ứng dụng hỗ trợ VNPAYQR
                            </label>
                        </div>
                        <div class="radio">
                            <label>
                                <input type="radio" id="bankCodeVnBank" name="bankCode" value="VNBANK">
                                Thẻ ATM / Tài khoản ngân hàng nội địa
                            </label>
                        </div>
                        <div class="radio">
                            <label>
                                <input type="radio" id="bankCodeIntCard" name="bankCode" value="INTCARD">
                                Thẻ thanh toán quốc tế (Visa, MasterCard, JCB)
                            </label>
                        </div>
                    </div>

                    <div class="form-group">
                        <h4>Ngôn ngữ giao diện thanh toán:</h4>
                        <label class="radio-inline">
                            <input type="radio" id="langVn" checked="checked" name="language" value="vn"> Tiếng Việt
                        </label>
                        <label class="radio-inline">
                            <input type="radio" id="langEn" name="language" value="en"> Tiếng Anh
                        </label>
                    </div>

                    <button type="submit" class="btn btn-primary btn-lg" id="btnSubmit">Thanh toán với VNPAY</button>
                </form>
            </div>

            <footer class="footer">
                <p>&copy; 2026 TicketsCenter — VNPAY Integration Suite</p>
            </footer>
        </div>

        <link href="https://pay.vnpay.vn/lib/vnpay/vnpay.css" rel="stylesheet" />
        <script src="https://pay.vnpay.vn/lib/vnpay/vnpay.min.js"></script>
        <script type="text/javascript">
            $("#frmCreateOrder").submit(function () {
                var postData = $("#frmCreateOrder").serialize();
                var submitUrl = $("#frmCreateOrder").attr("action");
                $("#btnSubmit").prop("disabled", true).text("Đang kết nối VNPAY...");
                $.ajax({
                    type: "POST",
                    url: submitUrl,
                    data: postData,
                    dataType: 'json',
                    success: function (x) {
                        if (x.code === '00') {
                            if (window.vnpay) {
                                vnpay.open({width: 768, height: 600, url: x.data});
                            } else {
                                location.href = x.data;
                            }
                        } else {
                            alert(x.message || "Lỗi tạo URL thanh toán: " + JSON.stringify(x));
                        }
                    },
                    error: function (xhr, status, error) {
                        alert("Không thể kết nối đến máy chủ: " + error);
                    },
                    complete: function() {
                        $("#btnSubmit").prop("disabled", false).text("Thanh toán với VNPAY");
                    }
                });
                return false;
            });
        </script>
    </body>
</html>