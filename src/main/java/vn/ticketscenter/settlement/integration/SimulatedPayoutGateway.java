package vn.ticketscenter.settlement.integration;

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

public final class SimulatedPayoutGateway implements PayoutGateway {
    private static final String PREFIX = "SIM-PAYOUT-";

    private final Path stateDirectory;
    private final Status configuredStatus;
    private final boolean timeoutAfterSideEffect;

    public SimulatedPayoutGateway(Path stateDirectory, Status configuredStatus, boolean timeoutAfterSideEffect) {
        this.stateDirectory = Objects.requireNonNull(stateDirectory).toAbsolutePath().normalize();
        this.configuredStatus = Objects.requireNonNull(configuredStatus);
        this.timeoutAfterSideEffect = timeoutAfterSideEffect;
    }

    @Override
    public String reference(UUID payoutId) {
        return PREFIX + Objects.requireNonNull(payoutId, "payoutId is required");
    }

    @Override
    public Result submit(UUID payoutId, BigDecimal amount) {
        validateAmount(amount);
        Path target = stateFile(payoutId);
        if (Files.exists(target)) return requireSamePayout(read(target), payoutId, amount);
        try {
            Files.createDirectories(stateDirectory);
            Path temporary = Files.createTempFile(stateDirectory, "payout-", ".tmp");
            try {
                write(temporary, new Result(payoutId, amount, reference(payoutId), configuredStatus));
                moveNew(temporary, target);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (FileAlreadyExistsException exception) {
            return requireSamePayout(read(target), payoutId, amount);
        } catch (IOException exception) {
            throw new IllegalStateException("cannot persist simulated payout state", exception);
        }
        if (timeoutAfterSideEffect) throw new Timeout(reference(payoutId));
        return read(target);
    }

    @Override
    public Result query(String reference) {
        if (reference == null || !reference.startsWith(PREFIX)) {
            throw new IllegalArgumentException("invalid simulated payout reference");
        }
        try {
            return read(stateFile(UUID.fromString(reference.substring(PREFIX.length()))));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("invalid simulated payout reference", exception);
        }
    }

    private Path stateFile(UUID payoutId) {
        return stateDirectory.resolve(Objects.requireNonNull(payoutId) + ".properties");
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 0) {
            throw new IllegalArgumentException("payout amount must be positive whole VND");
        }
    }

    private static Result requireSamePayout(Result result, UUID payoutId, BigDecimal amount) {
        if (!result.payoutId().equals(payoutId) || result.amount().compareTo(amount) != 0) {
            throw new IllegalStateException("payout replay payload mismatch");
        }
        return result;
    }

    private static void write(Path path, Result result) throws IOException {
        Properties values = new Properties();
        values.setProperty("payoutId", result.payoutId().toString());
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
            return new Result(UUID.fromString(values.getProperty("payoutId")),
                    new BigDecimal(values.getProperty("amount")), values.getProperty("reference"),
                    Status.valueOf(values.getProperty("status")));
        } catch (java.nio.file.NoSuchFileException exception) {
            throw new NoSuchElementException("simulated payout reference not found");
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("cannot read simulated payout state", exception);
        }
    }

    private static void moveNew(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    public static final class Timeout extends IllegalStateException {
        private final String reference;

        public Timeout(String reference) {
            super("simulated timeout after payout side effect");
            this.reference = reference;
        }

        public String reference() {
            return reference;
        }
    }
}
