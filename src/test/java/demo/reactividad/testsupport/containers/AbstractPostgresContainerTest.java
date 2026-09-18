package demo.reactividad.testsupport.containers;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class AbstractPostgresContainerTest {

    @SuppressWarnings("resource")
    @Container // Define el contenedor con la imagen exacta de tu producción
    @ServiceConnection // Spring autoconfigura la URL, usuario y password
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withInitScript("db/schema.sql");
}
