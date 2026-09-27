package vn.ticketscenter.acceptance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.DatabaseConfig;

import java.util.Map;
import java.sql.DriverManager;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Day03IT {

    @Test
    void hibernateValidatesAllBusinessEntitiesAgainstSqlServer() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
        config.setUsername(DatabaseConfig.getUser());
        config.setPassword(DatabaseConfig.getPassword());
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        try (HikariDataSource dataSource = new HikariDataSource(config);
             var factory = Persistence.createEntityManagerFactory("ticketscenter", Map.of(
                     "jakarta.persistence.nonJtaDataSource", dataSource))) {
            assertEquals(23, factory.getMetamodel().getEntities().size());
        }
    }

    @Test
    void migrationCreatesRuntimeRolesWithoutBroadTableGrants() throws Exception {
        try (var connection = DriverManager.getConnection(
                DatabaseConfig.getJdbcUrl(), DatabaseConfig.getUser(), DatabaseConfig.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT name FROM sys.database_principals
                     WHERE type = 'R' AND name IN
                       ('tc_buyer','tc_manager','tc_checkin','tc_platform_admin','tc_auth','tc_worker')
                     """)) {
            Set<String> roles = new HashSet<>();
            while (result.next()) roles.add(result.getString(1));
            assertEquals(Set.of("tc_buyer", "tc_manager", "tc_checkin", "tc_platform_admin", "tc_auth", "tc_worker"), roles);
        }
        try (var connection = DriverManager.getConnection(
                DatabaseConfig.getJdbcUrl(), DatabaseConfig.getUser(), DatabaseConfig.getPassword());
             var statement = connection.createStatement();
             var result = statement.executeQuery("""
                     SELECT COUNT(*) FROM sys.database_permissions permission
                     JOIN sys.database_principals principal ON principal.principal_id = permission.grantee_principal_id
                     WHERE principal.name IN
                       ('tc_buyer','tc_manager','tc_checkin','tc_platform_admin','tc_auth','tc_worker')
                       AND permission.permission_name IN ('ALTER','CONTROL','DELETE','INSERT','UPDATE')
                     """)) {
            result.next();
            assertEquals(0, result.getInt(1));
        }
    }
}
