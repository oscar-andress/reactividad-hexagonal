package demo.reactividad.infrastructure.adapter.out.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import demo.reactividad.domain.exception.ImageStorageException;
import demo.reactividad.infrastructure.config.S3Properties;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageAdapterTest {

    private static final String BUCKET_NAME = "imagenes-menu";

    @Mock
    private S3AsyncClient s3AsyncClient;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private S3ImageStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        S3Properties s3Properties = new S3Properties(
                BUCKET_NAME, "http://localhost:4566", "us-east-1", "localstack", "localstack");
        this.adapter = new S3ImageStorageAdapter(this.s3AsyncClient, this.s3Presigner, s3Properties);
    }

    // Contract/regression pinning (Gap F, ejercicio 3): fija los ÚNICOS 3 campos que
    // upload() arma en el PutObjectRequest real antes de entregarlo al SDK de S3. Si
    // alguien agrega, quita o cambia un campo del builder en el adaptador, este test
    // lo detecta — ver docs/quality-gates.md.
    @Test
    void upload_Success_PinsTheExactPutObjectRequestShapeSentToS3() {
        when(this.s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()));

        this.adapter.upload("menu.png", "image-bytes".getBytes(), "image/png")
                .as(StepVerifier::create)
                .verifyComplete();

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(this.s3AsyncClient).putObject(requestCaptor.capture(), any(AsyncRequestBody.class));
        PutObjectRequest sentRequest = requestCaptor.getValue();
        assertEquals(BUCKET_NAME, sentRequest.bucket());
        assertEquals("menu.png", sentRequest.key());
        assertEquals("image/png", sentRequest.contentType());
        assertEquals(PutObjectRequest.builder().bucket(BUCKET_NAME).key("menu.png").contentType("image/png").build(),
                sentRequest, "El PutObjectRequest no debe tener ningún otro campo seteado además de bucket/key/contentType");
    }

    @Test
    void upload_WhenS3Fails_MapsToImageStorageException() {
        CompletableFuture<PutObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(S3Exception.builder().message("bucket not found").build());
        when(this.s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(failedFuture);

        this.adapter.upload("menu.png", "image-bytes".getBytes(), "image/png")
                .as(StepVerifier::create)
                .expectError(ImageStorageException.class)
                .verify();
    }

    @Test
    void delete_Success_SendsCorrectRequest() {
        when(this.s3AsyncClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()));

        this.adapter.delete("menu.png")
                .as(StepVerifier::create)
                .verifyComplete();

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(this.s3AsyncClient).deleteObject(requestCaptor.capture());
        assertEquals(BUCKET_NAME, requestCaptor.getValue().bucket());
        assertEquals("menu.png", requestCaptor.getValue().key());
    }

    @Test
    void delete_WhenS3Fails_MapsToImageStorageException() {
        CompletableFuture<DeleteObjectResponse> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(S3Exception.builder().message("bucket not found").build());
        when(this.s3AsyncClient.deleteObject(any(DeleteObjectRequest.class))).thenReturn(failedFuture);

        this.adapter.delete("menu.png")
                .as(StepVerifier::create)
                .expectError(ImageStorageException.class)
                .verify();
    }

    @Test
    void generatePresignedUrl_Success_ReturnsUrlFromPresigner() throws Exception {
        URL url = URI.create("http://localhost:4566/" + BUCKET_NAME + "/menu.png?X-Amz-Signature=abc").toURL();
        when(this.presignedGetObjectRequest.url()).thenReturn(url);
        when(this.s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(this.presignedGetObjectRequest);

        this.adapter.generatePresignedUrl("menu.png", Duration.ofMinutes(15))
                .as(StepVerifier::create)
                .expectNext(url.toString())
                .expectComplete()
                .verify();

        ArgumentCaptor<GetObjectPresignRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(this.s3Presigner).presignGetObject(requestCaptor.capture());
        assertEquals(Duration.ofMinutes(15), requestCaptor.getValue().signatureDuration());
        GetObjectRequest getObjectRequest = requestCaptor.getValue().getObjectRequest();
        assertEquals(BUCKET_NAME, getObjectRequest.bucket());
        assertEquals("menu.png", getObjectRequest.key());
    }
}
