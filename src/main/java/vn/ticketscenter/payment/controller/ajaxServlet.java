package vn.ticketscenter.payment.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;

import java.io.IOException;

/** Legacy endpoint intentionally retired: it accepted client-controlled money and return URLs. */
@WebServlet(name = "ajaxServlet", urlPatterns = {"/vnpayajax", "/vnpayajax/*"})
public final class ajaxServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpResponses.error(response, HttpServletResponse.SC_GONE, "PAYMENT_ENDPOINT_RETIRED",
                "Use the order payment API");
    }
}
