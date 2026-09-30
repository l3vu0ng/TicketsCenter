package vn.ticketscenter.acceptance;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.DatabaseConfig;

import java.nio.file.Files;
import java.nio.file.Path;

class Day08IT {
    @Test
    void holdAndReleaseRunAtomicallyOnSqlServer() throws Exception {
        SQLServerDataSource dataSource = new SQLServerDataSource();
        dataSource.setServerName(DatabaseConfig.getHost());
        dataSource.setPortNumber(DatabaseConfig.getPort());
        dataSource.setDatabaseName(DatabaseConfig.getDatabaseName());
        dataSource.setUser(DatabaseConfig.getUser());
        dataSource.setPassword(DatabaseConfig.getPassword());
        dataSource.setEncrypt(String.valueOf(DatabaseConfig.isEncrypt()));
        dataSource.setTrustServerCertificate(DatabaseConfig.isTrustServerCertificate());
        dataSource.setLoginTimeout(DatabaseConfig.getLoginTimeout());
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("database/tests/day-08.sql")));
        }
    }
}
