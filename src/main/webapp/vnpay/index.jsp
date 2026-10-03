<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="description" content="TicketsCenter - Cổng thanh toán VNPAY Sandbox">
        <meta name="author" content="TicketsCenter">
        <title>TicketsCenter - Cổng thanh toán VNPAY Sandbox</title>
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
                        <li role="presentation"><a href="${pageContext.request.contextPath}/">Trang chủ TicketsCenter</a></li>
                    </ul>
                </nav>
                <h3 class="text-muted">TicketsCenter — VNPAY Sandbox</h3>
            </div>

            <div class="jumbotron">
                <h2>Cổng tích hợp thanh toán VNPAY</h2>
                <p class="lead">Giao diện kiểm thử thanh toán đơn hàng, truy vấn trạng thái giao dịch và xử lý hoàn tiền.</p>
                <p>
                    <a class="btn btn-lg btn-success" href="${pageContext.request.contextPath}/vnpay/vnpay_pay.jsp" role="button">Tạo giao dịch thanh toán</a>
                </p>
            </div>

            <div class="row marketing">
                <div class="col-lg-6">
                    <h4>Truy vấn giao dịch (QueryDR)</h4>
                    <p>Kiểm tra kết quả và đối soát giao dịch thanh toán đã gửi lên VNPAY Sandbox.</p>
                    <p><a class="btn btn-default" href="${pageContext.request.contextPath}/vnpay/vnpay_querydr.jsp" role="button">API truy vấn &raquo;</a></p>
                </div>

                <div class="col-lg-6">
                    <h4>Hoàn tiền giao dịch (Refund)</h4>
                    <p>Thực hiện hoàn tiền toàn phần hoặc một phần cho giao dịch thanh toán đã xác nhận.</p>
                    <p><a class="btn btn-default" href="${pageContext.request.contextPath}/vnpay/vnpay_refund.jsp" role="button">API hoàn tiền &raquo;</a></p>
                </div>
            </div>

            <footer class="footer">
                <p>&copy; 2026 TicketsCenter — VNPAY Integration Suite</p>
            </footer>
        </div>

        <script>
            function pay() {
                window.location.href = "${pageContext.request.contextPath}/vnpay/vnpay_pay.jsp";
            }
            function querydr() {
                window.location.href = "${pageContext.request.contextPath}/vnpay/vnpay_querydr.jsp";
            }
            function refund() {
                window.location.href = "${pageContext.request.contextPath}/vnpay/vnpay_refund.jsp";
            }
        </script>
    </body>
</html>
