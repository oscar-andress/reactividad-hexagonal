package demo.reactividad.infrastructure.adapter.out.batch;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import demo.reactividad.domain.model.Menu;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.test.StepVerifier;

class LoggingMenuBatchFailurePolicyTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final LoggingMenuBatchFailurePolicy policy = new LoggingMenuBatchFailurePolicy(this.meterRegistry);

    @Test
    void onBatchFailure_IncrementsFailedBatchCounterAndSwallowsTheBatch() {
        Menu menu = aMenu().build();

        this.policy.onBatchFailure(List.of(menu), new RuntimeException("db down"))
                .as(StepVerifier::create)
                .expectComplete()
                .verify();

        assertEquals(1, this.meterRegistry.get("menu.batch_save.failures").counter().count());
    }

    @Test
    void onBatchFailure_CalledTwice_AccumulatesTheCounter() {
        Menu menu = aMenu().build();

        this.policy.onBatchFailure(List.of(menu), new RuntimeException("db down")).blockLast();
        this.policy.onBatchFailure(List.of(menu), new RuntimeException("db down again")).blockLast();

        assertEquals(2, this.meterRegistry.get("menu.batch_save.failures").counter().count());
    }
}
