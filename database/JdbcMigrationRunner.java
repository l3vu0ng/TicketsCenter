import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;

public final class JdbcMigrationRunner {

    private JdbcMigrationRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: JdbcMigrationRunner <migration-directory>");
        }

        List<Path> migrations;
        try (var paths = Files.list(Path.of(args[0]))) {
            migrations = paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
        if (migrations.isEmpty()) {
            throw new IllegalStateException("Không có migration .sql");
        }

        String url = "jdbc:sqlserver://" + required("TC_SQL_HOST") + ":" + value("TC_SQL_PORT", "1433")
                + ";databaseName=" + required("TC_SQL_TARGET_DB")
                + ";encrypt=" + value("TC_SQL_ENCRYPT", "true")
                + ";trustServerCertificate=" + value("TC_SQL_TRUST_SERVER_CERT", "false")
                + ";loginTimeout=" + value("TC_SQL_CONNECT_TIMEOUT_SEC", "30") + ";";

        try (Connection connection = DriverManager.getConnection(
                url, required("TC_SQL_MIGRATION_LOGIN"), required("TC_SQL_MIGRATION_PASSWORD"))) {
            try {
                execute(connection, "BEGIN TRANSACTION;");
                acquireLock(connection);
                ensureHistoryTable(connection);
                for (Path migration : migrations) {
                    applyMigration(connection, migration);
                }
                execute(connection, "COMMIT TRANSACTION;");
            } catch (Exception exception) {
                rollback(connection);
                throw exception;
            }
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static void rollback(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;");
        } catch (Exception ignored) {
            // Preserve the migration error; closing the connection is the final rollback guard.
        }
    }

    private static void acquireLock(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                     DECLARE @result int;
                     EXEC @result = sys.sp_getapplock
                         @Resource = N'TicketsCenterMigration',
                         @LockMode = 'Exclusive',
                         @LockOwner = 'Transaction',
                         @LockTimeout = 10000;
                     SELECT @result;
                     """)) {
            int lockResult = result.next() ? result.getInt(1) : Integer.MIN_VALUE;
            if (lockResult < 0) {
                throw new IllegalStateException("Cannot acquire migration lock");
            }
        }
    }

    private static void ensureHistoryTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    IF OBJECT_ID(N'dbo.tc_schema_migrations', N'U') IS NULL
                        CREATE TABLE dbo.tc_schema_migrations (
                            version varchar(100) NOT NULL PRIMARY KEY,
                            checksum_sha256 char(64) NOT NULL,
                            applied_at datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
                            applied_by sysname NOT NULL DEFAULT ORIGINAL_LOGIN()
                        );
                    """);
        }
    }

    private static void applyMigration(Connection connection, Path migration) throws Exception {
        String filename = migration.getFileName().toString();
        String version = filename.substring(0, filename.length() - 4);
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(migration)));

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT checksum_sha256 FROM dbo.tc_schema_migrations WHERE version = ?")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    if (!checksum.equals(result.getString(1))) {
                        throw new IllegalStateException("Migration checksum mismatch: " + version);
                    }
                    return;
                }
            }
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(migration, StandardCharsets.UTF_8));
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT dbo.tc_schema_migrations(version, checksum_sha256) VALUES (?, ?)")) {
            statement.setString(1, version);
            statement.setString(2, checksum);
            statement.executeUpdate();
        }
        System.out.println("Applied " + version);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Thiếu " + name);
        }
        return value.trim();
    }

    private static String value(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
