package vn.ticketscenter.payment.controller;

import vn.ticketscenter.payment.service.PaymentService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

@WebServlet(name = "vnpayRefund", urlPatterns = {"/vnpayrefund", "/vnpayrefund/*"})
public class vnpayRefund extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String vnp_RequestId = PaymentService.getRandomNumber(8);
        String vnp_Version = "2.1.0";
        String vnp_Command = "refund";
        String vnp_TmnCode = PaymentService.getTmnCode();
        String vnp_TransactionType = req.getParameter("trantype");
        if (vnp_TransactionType == null || vnp_TransactionType.isBlank()) {
            vnp_TransactionType = "02";
        }
        String vnp_TxnRef = req.getParameter("order_id");
        if (vnp_TxnRef == null) {
            vnp_TxnRef = "";
        }

        long amount = 1000000L;
        String amountParam = req.getParameter("amount");
        if (amountParam != null && !amountParam.isBlank()) {
            try {
                amount = Long.parseLong(amountParam.trim()) * 100L;
            } catch (NumberFormatException ignored) {
            }
        }
        String vnp_Amount = String.valueOf(amount);
        String vnp_OrderInfo = "Hoan tien GD OrderId:" + vnp_TxnRef;
        String vnp_TransactionNo = req.getParameter("transactionNo");
        if (vnp_TransactionNo == null) {
            vnp_TransactionNo = "";
        }
        String vnp_TransactionDate = req.getParameter("trans_date");
        if (vnp_TransactionDate == null) {
            vnp_TransactionDate = "";
        }
        String vnp_CreateBy = req.getParameter("user");
        if (vnp_CreateBy == null || vnp_CreateBy.isBlank()) {
            vnp_CreateBy = "TicketsCenter";
        }

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        String vnp_IpAddr = PaymentService.getIpAddress(req);

        String hashData = String.join("|", vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode,
                vnp_TransactionType, vnp_TxnRef, vnp_Amount, vnp_TransactionNo, vnp_TransactionDate,
                vnp_CreateBy, vnp_CreateDate, vnp_IpAddr, vnp_OrderInfo);

        String vnp_SecureHash = PaymentService.hmacSHA512(PaymentService.getSecretKey(), hashData);

        String jsonPayload = String.format(
                "{\"vnp_RequestId\":\"%s\",\"vnp_Version\":\"%s\",\"vnp_Command\":\"%s\",\"vnp_TmnCode\":\"%s\"," +
                "\"vnp_TransactionType\":\"%s\",\"vnp_TxnRef\":\"%s\",\"vnp_Amount\":\"%s\",\"vnp_OrderInfo\":\"%s\"," +
                "\"vnp_TransactionNo\":\"%s\",\"vnp_TransactionDate\":\"%s\",\"vnp_CreateBy\":\"%s\"," +
                "\"vnp_CreateDate\":\"%s\",\"vnp_IpAddr\":\"%s\",\"vnp_SecureHash\":\"%s\"}",
                vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode,
                vnp_TransactionType, vnp_TxnRef, vnp_Amount, vnp_OrderInfo,
                vnp_TransactionNo, vnp_TransactionDate, vnp_CreateBy,
                vnp_CreateDate, vnp_IpAddr, vnp_SecureHash
        );

        URL url = URI.create(PaymentService.getApiUrl()).toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);

        try (DataOutputStream wr = new DataOutputStream(con.getOutputStream())) {
            wr.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            wr.flush();
        }

        int responseCode = con.getResponseCode();
        StringBuilder response = new StringBuilder();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                (responseCode >= 200 && responseCode < 400) ? con.getInputStream() : con.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String output;
            while ((output = in.readLine()) != null) {
                response.append(output);
            }
        }

        resp.setContentType("application/json;charset=UTF-8");
        resp.getWriter().write(response.toString());
    }
}
