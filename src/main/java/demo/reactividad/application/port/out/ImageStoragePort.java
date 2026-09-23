package demo.reactividad.application.port.out;

import java.time.Duration;

import reactor.core.publisher.Mono;

public interface ImageStoragePort {
    Mono<Void> upload(String key, byte[] content, String contentType);
    Mono<Void> delete(String key);
    Mono<String> generatePresignedUrl(String key, Duration expiration);
}
