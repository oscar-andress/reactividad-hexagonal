package demo.reactividad.infrastructure.adapter.out.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import demo.reactividad.application.port.out.ImageStoragePort;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

// Necesita un contexto real de Spring: @TimeLimiter solo actúa a través del proxy AOP
// (ver docs/resilience.md). Separado de S3ImageStorageAdapterTest para no pagar el
// costo del contexto en los tests rápidos que solo prueban el armado de la request.
@SpringBootTest
class S3ImageStorageAdapterResilienceTest {

    @MockitoBean
    private S3AsyncClient s3AsyncClient;

    @Autowired
    private ImageStoragePort imageStoragePort;

    @Test
    void upload_WhenS3NeverResponds_FailsWithTimeoutInsteadOfHangingForever() {
        // Un CompletableFuture que nunca se completa simula un S3 colgado.
        when(this.s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(new CompletableFuture<PutObjectResponse>());

        this.imageStoragePort.upload("menu.png", "image-bytes".getBytes(), "image/png")
                .as(StepVerifier::create)
                .expectError(TimeoutException.class)
                // resilience4j.timelimiter.instances.s3-image-storage.timeout-duration=2s
                .verify(Duration.ofSeconds(4));
    }
}
