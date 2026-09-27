package vn.ticketscenter.payment.controller;

import vn.ticketscenter.payment.service.Config;
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

@WebServlet(name = "vnpayQuery", urlPatterns = {"/vnpayquery", "/vnpayquery/*"})
public class vnpayQuery extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String vnp_RequestId = Config.getRandomNumber(8);
        String vnp_Version = "2.1.0";
        String vnp_Command = "querydr";
        String vnp_TmnCode = Config.getTmnCode();
        String vnp_TxnRef = req.getParameter("order_id");
        if (vnp_TxnRef == null) {
            vnp_TxnRef = "";
        }
        String vnp_OrderInfo = "Kiem tra ket qua GD OrderId:" + vnp_TxnRef;
        String vnp_TransDate = req.getParameter("trans_date");
        if (vnp_TransDate == null) {
            vnp_TransDate = "";
        }

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        String vnp_IpAddr = Config.getIpAddress(req);

        String hashData = String.join("|", vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode,
                vnp_TxnRef, vnp_TransDate, vnp_CreateDate, vnp_IpAddr, vnp_OrderInfo);
        String vnp_SecureHash = Config.hmacSHA512(Config.getSecretKey(), hashData);

        String jsonPayload = String.format(
                "{\"vnp_RequestId\":\"%s\",\"vnp_Version\":\"%s\",\"vnp_Command\":\"%s\",\"vnp_TmnCode\":\"%s\"," +
                "\"vnp_TxnRef\":\"%s\",\"vnp_OrderInfo\":\"%s\",\"vnp_TransactionDate\":\"%s\"," +
                "\"vnp_CreateDate\":\"%s\",\"vnp_IpAddr\":\"%s\",\"vnp_SecureHash\":\"%s\"}",
                vnp_RequestId, vnp_Version, vnp_Command, vnp_TmnCode,
                vnp_TxnRef, vnp_OrderInfo, vnp_TransDate,
                vnp_CreateDate, vnp_IpAddr, vnp_SecureHash
        );

        URL url = URI.create(Config.getApiUrl()).toURL();
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
