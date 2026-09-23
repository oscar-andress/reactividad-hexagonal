package demo.reactividad.infrastructure.adapter.in.web.multipart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MultipartFilePartExtractorTest {

    private static final String PART_NAME = "image";

    private final MultipartFilePartExtractor extractor = new MultipartFilePartExtractor();

    @Mock
    private ServerRequest request;

    @Mock
    private FilePart filePart;

    @Test
    void extract_Success_ReturnsBytesAndContentType() {
        DataBuffer buffer = new DefaultDataBufferFactory().wrap("hello".getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        when(this.request.multipartData()).thenReturn(Mono.just(
                new LinkedMultiValueMap<>(Map.of(PART_NAME, List.of(this.filePart)))));
        when(this.filePart.content()).thenReturn(Flux.just(buffer));
        when(this.filePart.headers()).thenReturn(headers);

        this.extractor.extract(this.request, PART_NAME)
                .as(StepVerifier::create)
                .assertNext(uploaded -> {
                    assertEquals("hello", new String(uploaded.content(), StandardCharsets.UTF_8));
                    assertEquals(MediaType.IMAGE_PNG_VALUE, uploaded.contentType());
                })
                .expectComplete()
                .verify();
    }

    @Test
    void extract_WhenContentTypeMissing_DefaultsToOctetStream() {
        DataBuffer buffer = new DefaultDataBufferFactory().wrap("hello".getBytes(StandardCharsets.UTF_8));
        when(this.request.multipartData()).thenReturn(Mono.just(
                new LinkedMultiValueMap<>(Map.of(PART_NAME, List.of(this.filePart)))));
        when(this.filePart.content()).thenReturn(Flux.just(buffer));
        when(this.filePart.headers()).thenReturn(new HttpHeaders());

        this.extractor.extract(this.request, PART_NAME)
                .as(StepVerifier::create)
                .assertNext(uploaded -> assertEquals(MediaType.APPLICATION_OCTET_STREAM_VALUE, uploaded.contentType()))
                .expectComplete()
                .verify();
    }

    @Test
    void extract_WhenPartMissing_ThrowsServerWebInputException() {
        when(this.request.multipartData()).thenReturn(Mono.just(new LinkedMultiValueMap<>()));

        this.extractor.extract(this.request, PART_NAME)
                .as(StepVerifier::create)
                .expectError(ServerWebInputException.class)
                .verify();
    }
}
