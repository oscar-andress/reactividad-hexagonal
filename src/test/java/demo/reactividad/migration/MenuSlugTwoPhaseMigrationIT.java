package demo.reactividad.migration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Ejercicio de práctica del patrón expand/backfill/contract (ver
 * docs/data/two-phase-migration.md) — prueba las migraciones V2-V4 contra una fila
 * "legacy" insertada a mano con solo V1 aplicado, simulando datos que ya existían
 * antes de que menu_slug se inventara.
 *
 * No usa Spring/el contenedor compartido de la app: necesita controlar manualmente
 * HASTA QUÉ VERSIÓN corre Flyway en cada paso, algo que el auto-configure de Spring
 * Boot no permite (siempre migra a la última). Por eso tiene su propio contenedor,
 * con @Testcontainers/@Container de JUnit 5 (su ciclo de vida por clase es correcto
 * acá, a diferencia de AbstractPostgresContainerTest — ver docs/rca/0001-...).
 */
@Testcontainers
class MenuSlugTwoPhaseMigrationIT {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

    @Test
    void expandBackfillContract_BackfillsPreExistingRowsAndEnforcesConstraintAfterward() throws SQLException {
        String jdbcUrl = postgres.getJdbcUrl();
        String username = postgres.getUsername();
        String password = postgres.getPassword();

        // Fase 0: solo V1 — como si Flyway nunca hubiera oído hablar de menu_slug.
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        UUID legacyMenuId = insertLegacyMenuWithoutSlug(jdbcUrl, username, password, "Ensalada César");

        // Fases 1-3: V2 (expand) + V3 (backfill) + V4 (contract), sobre la fila legacy
        // de arriba, que a esta altura no tiene ningún valor en menu_slug.
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .load()
                .migrate();

        String backfilledSlug = readSlug(jdbcUrl, username, password, legacyMenuId);
        assertNotNull(backfilledSlug, "La fila legacy debe haber quedado con un slug backfilleado por V3");
        assertTrue(backfilledSlug.startsWith("ensalada-cesar-"),
                "El slug debe derivarse del título (con tildes removidas) + sufijo de unicidad: " + backfilledSlug);

        assertConstraintIsEnforced(jdbcUrl, username, password, legacyMenuId);
    }

    private UUID insertLegacyMenuWithoutSlug(String jdbcUrl, String username, String password, String title)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO tbl_menu (menu_title, menu_description) VALUES (?, ?) RETURNING menu_id")) {
            insert.setString(1, title);
            insert.setString(2, "Descripción de prueba");
            try (ResultSet resultSet = insert.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("menu_id");
            }
        }
    }

    private String readSlug(String jdbcUrl, String username, String password, UUID menuId) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                PreparedStatement select = connection.prepareStatement(
                        "SELECT menu_slug FROM tbl_menu WHERE menu_id = ?")) {
            select.setObject(1, menuId);
            try (ResultSet resultSet = select.executeQuery()) {
                resultSet.next();
                return resultSet.getString("menu_slug");
            }
        }
    }

    // El NOT NULL de la fase de contract debe estar activo de verdad — no alcanza con
    // que el backfill haya puesto un valor; hay que confirmar que un intento posterior
    // de dejarlo en NULL es rechazado por la base, no solo que "parece" funcionar.
    private void assertConstraintIsEnforced(String jdbcUrl, String username, String password, UUID menuId)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE tbl_menu SET menu_slug = ? WHERE menu_id = ?")) {
            update.setNull(1, Types.VARCHAR);
            update.setObject(2, menuId);
            SQLException thrown = assertThrows(SQLException.class, update::executeUpdate);
            assertTrue(thrown.getMessage().toLowerCase().contains("null"),
                    "El NOT NULL debe estar activo tras la fase de contract: " + thrown.getMessage());
        }
    }
}
