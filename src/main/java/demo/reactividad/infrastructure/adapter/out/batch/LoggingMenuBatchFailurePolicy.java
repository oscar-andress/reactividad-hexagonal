package demo.reactividad.infrastructure.adapter.out.batch;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.MenuBatchFailurePolicy;
import demo.reactividad.domain.model.Menu;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class LoggingMenuBatchFailurePolicy implements MenuBatchFailurePolicy {

    private final AtomicLong failedBatchCount = new AtomicLong();

    @Override
    public Flux<Menu> onBatchFailure(List<Menu> batch, Throwable error) {
        this.failedBatchCount.incrementAndGet();
        log.error("Failed to save a batch of {} menus: {}", batch.size(), error.getMessage(), error);
        return Flux.empty();
    }

    long getFailedBatchCount() {
        return this.failedBatchCount.get();
    }
}
