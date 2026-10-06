package demo.reactividad.testsupport.containers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Singleton Container Pattern (recomendado por Testcontainers para este caso exacto):
// este campo static vive en la clase ABSTRACTA, así que Java lo comparte entre TODAS las
// subclases (MenuWebIntegrationIT, MenuR2dbcRepositoryIT, OrderWebIntegrationIT,
// OrderR2dbcRepositoryIT) — no es un campo nuevo por cada *IT. Por eso NO lleva @Testcontainers
// ni @Container: esas anotaciones le dirían a la extensión de JUnit 5 que pare el contenedor
// al terminar la PRIMERA clase que lo usa, dejando a las clases siguientes con la referencia
// a un contenedor ya muerto (mismo objeto, puerto viejo) — eso es lo que rompía
// OrderWebIntegrationIT/OrderR2dbcRepositoryIT con "Connection refused" en cuanto Docker
// estuvo disponible para correr mvn verify de verdad. Se arranca una sola vez, a propósito,
// y se deja vivo para todo el build — Ryuk lo limpia al terminar la JVM.
@SuppressWarnings("resource")
public abstract class AbstractPostgresContainerTest {

    @ServiceConnection // Spring autoconfigura la URL, usuario y password
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withInitScript("db/schema.sql");

    static {
        postgres.start();
    }
}
