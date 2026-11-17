package br.com.resenha.Resenha.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MigrationUpgradeTest extends PostgresTestSupport {
    @Autowired DataSource dataSource;

    @Test void approvalMigrationPreservesExistingVenuesAndMarksThemPending() throws Exception {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("9").load().migrate();
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO " + schema + ".venues "
                        + "(name,description,opening_time,closing_time,court_count,street,neighborhood,zip_code,city,state) "
                        + "VALUES ('Arena antiga','Descrição','08:00','22:00',1,'Rua','Centro','12345678','Cidade','SP')");
                statement.executeUpdate("INSERT INTO " + schema + ".courts (name,type,venue_id) "
                        + "SELECT 'Antiga','CAMPO',id FROM " + schema + ".venues");
            }
            var migration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertEquals(3, migration.migrationsExecuted);
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT name, status FROM " + schema + ".venues")) {
                assertTrue(rows.next());
                assertEquals("Arena antiga", rows.getString("name"));
                assertEquals("PENDING", rows.getString("status"));
                assertFalse(rows.next());
            }
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT type,minimum_participants FROM " + schema + ".courts")) {
                assertTrue(rows.next());
                assertEquals("CAMPO", rows.getString("type"));
                assertNull(rows.getObject("minimum_participants"));
            }
        } finally {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
