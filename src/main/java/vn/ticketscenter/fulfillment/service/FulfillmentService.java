package vn.ticketscenter.fulfillment.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.dto.FulfillmentDtos.PaymentConfirmation;
import vn.ticketscenter.fulfillment.dto.FulfillmentDtos.TicketView;
import vn.ticketscenter.fulfillment.repository.FulfillmentRepository;
import vn.ticketscenter.identity.service.AccountService.AuthenticatedAccount;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class FulfillmentService {
    private final TransactionManager transactions;
    private final FulfillmentRepository repository;
    public FulfillmentService(TransactionManager transactions, FulfillmentRepository repository) { this.transactions = Objects.requireNonNull(transactions); this.repository = Objects.requireNonNull(repository); }

    public PaymentConfirmation confirm(AuthenticatedAccount account, UUID orderId, UUID paymentId, String providerReference) {
        require(account); if (orderId == null) throw new IllegalArgumentException("orderId is required");
        String status = transactions.execute(DatabasePrincipal.WORKER, em -> repository.applyPayment(em, orderId, account.id(), paymentId, providerReference));
        List<TicketView> tickets = transactions.execute(DatabasePrincipal.BUYER, em -> repository.findMine(em, account.id()));
        String[] parts = status.split(":", 2);
        return new PaymentConfirmation(parts[0], parts[1], tickets);
    }
    public List<TicketView> mine(AuthenticatedAccount account) { require(account); return transactions.execute(DatabasePrincipal.BUYER, em -> repository.findMine(em, account.id())); }
    public byte[] qr(AuthenticatedAccount account, UUID ticketId) {
        require(account); if (ticketId == null) throw new IllegalArgumentException("ticketId is required");
        String code = transactions.execute(DatabasePrincipal.BUYER, em -> repository.ticketCode(em, account.id(), ticketId));
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BitMatrix matrix = new MultiFormatWriter().encode(code, BarcodeFormat.QR_CODE, 400, 400);
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (Exception exception) { throw new IllegalStateException("unable to generate QR", exception); }
    }
    private static void require(AuthenticatedAccount account) { if (account == null) throw new SecurityException("authenticated account required"); }
}
