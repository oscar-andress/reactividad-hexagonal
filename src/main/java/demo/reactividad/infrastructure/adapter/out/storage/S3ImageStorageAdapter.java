package demo.reactividad.infrastructure.adapter.out.storage;

import java.time.Duration;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.ImageStoragePort;
import demo.reactividad.domain.exception.ImageStorageException;
import demo.reactividad.infrastructure.config.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3ImageStorageAdapter implements ImageStoragePort {

    private final S3AsyncClient s3AsyncClient;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public Mono<Void> upload(String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(this.s3Properties.bucketName())
                .key(key)
                .contentType(contentType)
                .build();

        return Mono.fromFuture(() -> this.s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(content)))
                .doOnError(SdkException.class, ex -> log.error("S3 upload failed for key {}", key, ex))
                .onErrorMap(SdkException.class,
                        ex -> new ImageStorageException("Failed to upload image " + key + " to S3", ex))
                .then();
    }

    @Override
    public Mono<Void> delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(this.s3Properties.bucketName())
                .key(key)
                .build();

        return Mono.fromFuture(() -> this.s3AsyncClient.deleteObject(request))
                .doOnError(SdkException.class, ex -> log.error("S3 delete failed for key {}", key, ex))
                .onErrorMap(SdkException.class,
                        ex -> new ImageStorageException("Failed to delete image " + key + " from S3", ex))
                .then();
    }

    @Override
    public Mono<String> generatePresignedUrl(String key, Duration expiration) {
        return Mono.fromCallable(() -> {
                    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                            .bucket(this.s3Properties.bucketName())
                            .key(key)
                            .build();
                    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                            .signatureDuration(expiration)
                            .getObjectRequest(getObjectRequest)
                            .build();
                    return this.s3Presigner.presignGetObject(presignRequest).url().toString();
                })
                .doOnError(SdkException.class, ex -> log.error("Failed to presign URL for key {}", key, ex))
                .onErrorMap(SdkException.class,
                        ex -> new ImageStorageException("Failed to generate presigned URL for key " + key, ex));
    }
}
