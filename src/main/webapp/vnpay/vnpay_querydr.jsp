<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="description" content="Truy vấn kết quả giao dịch VNPAY (QueryDR)">
        <meta name="author" content="TicketsCenter">
        <title>Truy vấn kết quả giao dịch (QueryDR) — TicketsCenter</title>
        <!-- Bootstrap core CSS -->
        <link href="${pageContext.request.contextPath}/assets/bootstrap.min.css" rel="stylesheet"/>
        <!-- Custom styles for this template -->   
        <link href="${pageContext.request.contextPath}/assets/jumbotron-narrow.css" rel="stylesheet"> 
        <script src="${pageContext.request.contextPath}/assets/jquery-1.11.3.min.js"></script>
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

            <h3>Truy vấn trạng thái giao dịch (QueryDR API)</h3>
            <div class="table-responsive">
                <form action="${pageContext.request.contextPath}/vnpayquery" id="frmQuerydr" method="post">
                    <div class="form-group">
                        <label for="order_id">Mã giao dịch cần truy vấn (vnp_TxnRef)</label>
                        <input class="form-control" id="order_id" name="order_id" type="text" placeholder="Nhập mã đơn hàng / vnp_TxnRef" required />
                    </div>
                    <div class="form-group">
                        <label for="trans_date">Thời gian khởi tạo giao dịch (vnp_CreateDate: yyyyMMddHHmmss)</label>
                        <input class="form-control" id="trans_date" name="trans_date" type="text" placeholder="Ví dụ: 20260927123000" required />
                    </div>
                    <div class="form-group">
                        <button type="submit" class="btn btn-primary" id="btnQuery">Gửi truy vấn QueryDR</button>
                    </div>
                </form>   

                <div id="queryResult" style="display: none; margin-top: 20px;">
                    <h4>Kết quả phản hồi từ VNPAY:</h4>
                    <pre id="queryResultContent" style="background:#f8f9fa; border:1px solid #ddd; padding:15px;"></pre>
                </div>

                <footer class="footer">
                    <p>&copy; 2026 TicketsCenter — VNPAY Integration Suite</p>
                </footer>
            </div> 
        </div>

        <script type="text/javascript">
            $("#frmQuerydr").submit(function () {
                var postData = $("#frmQuerydr").serialize();
                var submitUrl = $("#frmQuerydr").attr("action");
                $("#btnQuery").prop("disabled", true).text("Đang truy vấn...");
                $("#queryResult").hide();
                $.ajax({
                    type: "POST",
                    url: submitUrl,
                    data: postData,
                    success: function (data) {
                        var formatted = typeof data === 'object' ? JSON.stringify(data, null, 2) : data;
                        $("#queryResultContent").text(formatted);
                        $("#queryResult").show();
                    },
                    error: function (xhr, status, error) {
                        $("#queryResultContent").text("Lỗi kết nối API: " + error + "\n" + xhr.responseText);
                        $("#queryResult").show();
                    },
                    complete: function() {
                        $("#btnQuery").prop("disabled", false).text("Gửi truy vấn QueryDR");
                    }
                });
                return false;
            });
        </script>
    </body>
</html>
