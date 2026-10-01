package vn.ticketscenter.payment.integration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;

public final class SimulatedRefundGateway implements RefundGateway {
    private static final String PREFIX = "SIM-";

    private final Path stateDirectory;
    private final Status configuredStatus;
    private final boolean timeoutAfterSideEffect;

    public SimulatedRefundGateway(Path stateDirectory, Status configuredStatus, boolean timeoutAfterSideEffect) {
        this.stateDirectory = Objects.requireNonNull(stateDirectory).toAbsolutePath().normalize();
        this.configuredStatus = Objects.requireNonNull(configuredStatus);
        this.timeoutAfterSideEffect = timeoutAfterSideEffect;
    }

    @Override
    public Result submit(UUID refundId, BigDecimal amount) {
        Objects.requireNonNull(refundId, "refundId is required");
        if (amount == null || amount.signum() <= 0 || amount.scale() > 0) {
            throw new IllegalArgumentException("refund amount must be positive whole VND");
        }
        String reference = PREFIX + refundId;
        Path target = stateFile(refundId);
        if (Files.exists(target)) return requireSameObligation(read(target), refundId, amount);

        try {
            Files.createDirectories(stateDirectory);
            Path temporary = Files.createTempFile(stateDirectory, "refund-", ".tmp");
            try {
                write(temporary, new Result(refundId, amount, reference, configuredStatus));
                moveNew(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (FileAlreadyExistsException exception) {
            return requireSameObligation(read(target), refundId, amount);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot persist simulated refund state", exception);
        }
        if (timeoutAfterSideEffect) throw new Timeout(reference);
        return read(target);
    }

    @Override
    public Result query(String reference) {
        return read(stateFile(refundId(reference)));
    }

    private Path stateFile(UUID refundId) {
        return stateDirectory.resolve(refundId + ".properties");
    }

    private static UUID refundId(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            throw new IllegalArgumentException("invalid simulated refund reference");
        }
        try {
            return UUID.fromString(reference.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid simulated refund reference", exception);
        }
    }

    private static Result requireSameObligation(Result result, UUID refundId, BigDecimal amount) {
        if (!result.refundId().equals(refundId) || result.amount().compareTo(amount) != 0) {
            throw new IllegalStateException("refund replay payload mismatch");
        }
        return result;
    }

    private static void write(Path path, Result result) throws IOException {
        Properties values = new Properties();
        values.setProperty("refundId", result.refundId().toString());
        values.setProperty("amount", result.amount().toPlainString());
        values.setProperty("reference", result.reference());
        values.setProperty("status", result.status().name());
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE);
             var output = Channels.newOutputStream(channel)) {
            values.store(output, null);
            output.flush();
            channel.force(true);
        }
    }

    private static Result read(Path path) {
        Properties values = new Properties();
        try (var input = Files.newInputStream(path)) {
            values.load(input);
            return new Result(
                    UUID.fromString(values.getProperty("refundId")),
                    new BigDecimal(values.getProperty("amount")),
                    values.getProperty("reference"),
                    Status.valueOf(values.getProperty("status")));
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new NoSuchElementException("simulated refund reference not found");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("cannot read simulated refund state", exception);
        }
    }

    private static void moveNew(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }
}
