<%@page import="java.net.URLEncoder"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="vn.ticketscenter.payment.service.PaymentService"%>
<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.Iterator"%>
<%@page import="java.util.Collections"%>
<%@page import="java.util.List"%>
<%@page import="java.util.ArrayList"%>
<%@page import="java.util.Enumeration"%>
<%@page import="java.util.Map"%>
<%@page import="java.util.HashMap"%>

<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="utf-8">
        <meta http-equiv="X-UA-Compatible" content="IE=edge">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="description" content="Kết quả thanh toán VNPAY">
        <meta name="author" content="TicketsCenter">
        <title>Kết quả thanh toán — TicketsCenter</title>
        <!-- Bootstrap core CSS -->
        <link href="${pageContext.request.contextPath}/assets/css/bootstrap.min.css" rel="stylesheet"/>
        <!-- Custom styles for this template -->
        <link href="${pageContext.request.contextPath}/assets/css/jumbotron-narrow.css" rel="stylesheet">
        <script src="${pageContext.request.contextPath}/assets/js/jquery-1.11.3.min.js"></script>
    </head>
    <body>
        <%
            // Process return from VNPAY
            Map<String, String> fields = new HashMap<>();
            for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
                String rawName = params.nextElement();
                String rawValue = request.getParameter(rawName);
                if (rawValue != null && !rawValue.isEmpty()) {
                    String fieldName = URLEncoder.encode(rawName, StandardCharsets.US_ASCII.toString());
                    String fieldValue = URLEncoder.encode(rawValue, StandardCharsets.US_ASCII.toString());
                    fields.put(fieldName, fieldValue);
                }
            }

            String vnp_SecureHash = request.getParameter("vnp_SecureHash");
            fields.remove("vnp_SecureHashType");
            fields.remove("vnp_SecureHash");
            String signValue = PaymentService.hashAllFields(fields);

            boolean isSignatureValid = signValue != null && signValue.equalsIgnoreCase(vnp_SecureHash);
            String responseCode = request.getParameter("vnp_ResponseCode");
            String transactionStatus = request.getParameter("vnp_TransactionStatus");
            boolean isSuccess = isSignatureValid && "00".equals(responseCode) && "00".equals(transactionStatus);

            String rawAmount = request.getParameter("vnp_Amount");
            long displayAmount = 0L;
            if (rawAmount != null && !rawAmount.isBlank()) {
                try {
                    displayAmount = Long.parseLong(rawAmount) / 100L;
                } catch (NumberFormatException ignored) {}
            }
        %>
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

            <div class="page-header">
                <h2>Kết quả thanh toán đơn hàng</h2>
            </div>

            <% if (isSuccess) { %>
                <div class="alert alert-success" role="alert">
                    <h4 style="margin-top:0;"><strong>Giao dịch thành công!</strong></h4>
                    Cổng thanh toán VNPAY đã ghi nhận thanh toán hợp lệ.
                </div>
            <% } else if (!isSignatureValid) { %>
                <div class="alert alert-danger" role="alert">
                    <h4 style="margin-top:0;"><strong>Chữ ký không hợp lệ!</strong></h4>
                    Dữ liệu phản hồi từ cổng thanh toán không khớp với mã băm bảo mật.
                </div>
            <% } else { %>
                <div class="alert alert-warning" role="alert">
                    <h4 style="margin-top:0;"><strong>Giao dịch không thành công hoặc bị hủy!</strong></h4>
                    Mã lỗi VNPAY: <%= responseCode %>
                </div>
            <% } %>

            <div class="panel panel-default">
                <div class="panel-heading"><h3 class="panel-title">Chi tiết giao dịch</h3></div>
                <div class="panel-body">
                    <dl class="dl-horizontal">
                        <dt>Mã tham chiếu (TxnRef):</dt>
                        <dd><%= request.getParameter("vnp_TxnRef") != null ? request.getParameter("vnp_TxnRef") : "-" %></dd>

                        <dt>Số tiền:</dt>
                        <dd><strong><%= String.format("%,d", displayAmount) %> VND</strong></dd>

                        <dt>Nội dung thanh toán:</dt>
                        <dd><%= request.getParameter("vnp_OrderInfo") != null ? request.getParameter("vnp_OrderInfo") : "-" %></dd>

                        <dt>Mã giao dịch VNPAY:</dt>
                        <dd><%= request.getParameter("vnp_TransactionNo") != null ? request.getParameter("vnp_TransactionNo") : "-" %></dd>

                        <dt>Ngân hàng / Ví:</dt>
                        <dd><%= request.getParameter("vnp_BankCode") != null ? request.getParameter("vnp_BankCode") : "-" %></dd>

                        <dt>Thời gian thanh toán:</dt>
                        <dd><%= request.getParameter("vnp_PayDate") != null ? request.getParameter("vnp_PayDate") : "-" %></dd>

                        <dt>Trạng thái chữ ký:</dt>
                        <dd>
                            <% if (isSignatureValid) { %>
                                <span class="label label-success">Hợp lệ (Checksum OK)</span>
                            <% } else { %>
                                <span class="label label-danger">Không hợp lệ (Invalid Signature)</span>
                            <% } %>
                        </dd>

                        <dt>Trạng thái giao dịch:</dt>
                        <dd>
                            <% if (isSuccess) { %>
                                <span class="label label-success">Thành công (00)</span>
                            <% } else { %>
                                <span class="label label-danger">Thất bại / Hủy (<%= responseCode %>)</span>
                            <% } %>
                        </dd>
                    </dl>
                </div>
            </div>

            <p>
                <a class="btn btn-primary" href="${pageContext.request.contextPath}/vnpay/vnpay_pay.jsp">Tạo giao dịch khác</a>
                <a class="btn btn-default" href="${pageContext.request.contextPath}/">Về trang chủ TicketsCenter</a>
            </p>

            <footer class="footer">
                <p>&copy; 2026 TicketsCenter — VNPAY Integration Suite</p>
            </footer>
        </div>
    </body>
</html>
