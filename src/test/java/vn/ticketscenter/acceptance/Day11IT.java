package vn.ticketscenter.acceptance;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.DatabaseConfig;
import java.nio.file.Files;
import java.nio.file.Path;

class Day11IT {
    @Test void paymentIssuesTicketsAndConsumesInventory() throws Exception {
        SQLServerDataSource ds=new SQLServerDataSource();
        ds.setServerName(DatabaseConfig.getHost()); ds.setPortNumber(DatabaseConfig.getPort()); ds.setDatabaseName(DatabaseConfig.getDatabaseName());
        ds.setUser(DatabaseConfig.getUser()); ds.setPassword(DatabaseConfig.getPassword()); ds.setEncrypt(String.valueOf(DatabaseConfig.isEncrypt()));
        ds.setTrustServerCertificate(DatabaseConfig.isTrustServerCertificate()); ds.setLoginTimeout(DatabaseConfig.getLoginTimeout());
        try(var c=ds.getConnection(); var s=c.createStatement()){ s.execute(Files.readString(Path.of("database/tests/day-11.sql"))); }
    }
}
