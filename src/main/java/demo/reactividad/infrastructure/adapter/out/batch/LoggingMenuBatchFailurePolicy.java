package demo.reactividad.infrastructure.adapter.out.batch;

import java.util.List;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.MenuBatchFailurePolicy;
import demo.reactividad.domain.model.Menu;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingMenuBatchFailurePolicy implements MenuBatchFailurePolicy {

    private static final String FAILED_BATCH_METRIC = "menu.batch_save.failures";

    private final MeterRegistry meterRegistry;

    @Override
    public Flux<Menu> onBatchFailure(List<Menu> batch, Throwable error) {
        this.meterRegistry.counter(FAILED_BATCH_METRIC).increment();
        log.error("Failed to save a batch of {} menus: {}", batch.size(), error.getMessage(), error);
        return Flux.empty();
    }
}
