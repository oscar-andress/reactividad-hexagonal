package demo.reactividad.infrastructure.adapter.in.web.multipart;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;

import reactor.core.publisher.Mono;

@Component
public class MultipartFilePartExtractor {

    public Mono<UploadedFile> extract(ServerRequest request, String partName) {
        return request.multipartData()
                .flatMap(parts -> toFilePart(parts.getFirst(partName), partName))
                .flatMap(this::toUploadedFile);
    }

    private Mono<FilePart> toFilePart(Object part, String partName) {
        if (!(part instanceof FilePart filePart)) {
            return Mono.error(new ServerWebInputException("Missing '" + partName + "' file part"));
        }
        return Mono.just(filePart);
    }

    private Mono<UploadedFile> toUploadedFile(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> new UploadedFile(readAndReleaseBytes(dataBuffer), contentTypeOf(filePart)));
    }

    private byte[] readAndReleaseBytes(DataBuffer dataBuffer) {
        byte[] bytes = new byte[dataBuffer.readableByteCount()];
        dataBuffer.read(bytes);
        DataBufferUtils.release(dataBuffer);
        return bytes;
    }

    private String contentTypeOf(FilePart filePart) {
        MediaType mediaType = filePart.headers().getContentType();
        return mediaType != null ? mediaType.toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
