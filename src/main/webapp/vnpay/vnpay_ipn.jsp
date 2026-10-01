<%@page import="java.net.URLEncoder"%>
<%@page import="java.nio.charset.StandardCharsets"%>
<%@page import="vn.ticketscenter.payment.service.PaymentService"%>
<%@page contentType="application/json; charset=UTF-8"%>
<%@page import="java.util.Enumeration"%>
<%@page import="java.util.Map"%>
<%@page import="java.util.HashMap"%>
<%
    /*
     * VNPAY IPN (Instant Payment Notification)
     * Nhận và xử lý thông báo kết quả thanh toán từ server VNPAY.
     */
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
    if (signValue != null && signValue.equalsIgnoreCase(vnp_SecureHash)) {
        String txnRef = request.getParameter("vnp_TxnRef");
        String responseCode = request.getParameter("vnp_ResponseCode");

        boolean checkOrderId = txnRef != null && !txnRef.isBlank();
        boolean checkAmount = true;
        boolean checkOrderStatus = true;

        if (checkOrderId) {
            if (checkAmount) {
                if (checkOrderStatus) {
                    if ("00".equals(responseCode)) {
                        // Giao dịch thành công
                    } else {
                        // Giao dịch thất bại
                    }
                    out.print("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}");
                } else {
                    out.print("{\"RspCode\":\"02\",\"Message\":\"Order already confirmed\"}");
                }
            } else {
                out.print("{\"RspCode\":\"04\",\"Message\":\"Invalid Amount\"}");
            }
        } else {
            out.print("{\"RspCode\":\"01\",\"Message\":\"Order not Found\"}");
        }
    } else {
        out.print("{\"RspCode\":\"97\",\"Message\":\"Invalid Checksum\"}");
    }
%>
