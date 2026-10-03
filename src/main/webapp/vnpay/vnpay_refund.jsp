<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="description" content="Hoàn tiền giao dịch VNPAY (Refund)">
        <meta name="author" content="TicketsCenter">
        <title>Hoàn tiền giao dịch (Refund API) — TicketsCenter</title>
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

            <h3>Yêu cầu hoàn tiền giao dịch (Refund API)</h3>
            <div class="table-responsive">
                <form action="${pageContext.request.contextPath}/vnpayrefund" id="frmRefund" method="post">
                    <div class="form-group">
                        <label for="order_id">Mã giao dịch cần hoàn (vnp_TxnRef)</label>
                        <input class="form-control" id="order_id" name="order_id" type="text" placeholder="Nhập mã đơn hàng / vnp_TxnRef" required />
                    </div>
                    <div class="form-group">
                        <label for="amount">Số tiền hoàn (VND)</label>
                        <input class="form-control" id="amount" max="100000000" min="1" name="amount" type="number" value="10000" required />
                    </div>
                    <div class="form-group">
                        <label for="trantype">Loại giao dịch hoàn</label>
                        <select name="trantype" id="trantype" class="form-control">
                            <option value="02">Hoàn tiền toàn phần (Giao dịch 02)</option>
                            <option value="03">Hoàn tiền một phần (Giao dịch 03)</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="trans_date">Thời gian khởi tạo giao dịch (vnp_CreateDate: yyyyMMddHHmmss)</label>
                        <input class="form-control" id="trans_date" name="trans_date" type="text" placeholder="Ví dụ: 20260927123000" required />
                    </div>
                    <div class="form-group">
                        <label for="user">Người thực hiện hoàn tiền</label>
                        <input class="form-control" id="user" name="user" type="text" value="admin" required />
                    </div>
                    <div class="form-group">
                        <button type="submit" class="btn btn-primary" id="btnRefund">Gửi yêu cầu hoàn tiền</button>
                    </div>
                </form>

                <div id="refundResult" style="display: none; margin-top: 20px;">
                    <h4>Kết quả phản hồi từ VNPAY:</h4>
                    <pre id="refundResultContent" style="background:#f8f9fa; border:1px solid #ddd; padding:15px;"></pre>
                </div>

                <footer class="footer">
                    <p>&copy; 2026 TicketsCenter — VNPAY Integration Suite</p>
                </footer>
            </div>
        </div>

        <script type="text/javascript">
            $("#frmRefund").submit(function () {
                var postData = $("#frmRefund").serialize();
                var submitUrl = $("#frmRefund").attr("action");
                $("#btnRefund").prop("disabled", true).text("Đang xử lý hoàn tiền...");
                $("#refundResult").hide();
                $.ajax({
                    type: "POST",
                    url: submitUrl,
                    data: postData,
                    success: function (data) {
                        var formatted = typeof data === 'object' ? JSON.stringify(data, null, 2) : data;
                        $("#refundResultContent").text(formatted);
                        $("#refundResult").show();
                    },
                    error: function (xhr, status, error) {
                        $("#refundResultContent").text("Lỗi kết nối API: " + error + "\n" + xhr.responseText);
                        $("#refundResult").show();
                    },
                    complete: function() {
                        $("#btnRefund").prop("disabled", false).text("Gửi yêu cầu hoàn tiền");
                    }
                });
                return false;
            });
        </script>
    </body>
</html>
