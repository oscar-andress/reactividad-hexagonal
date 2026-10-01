package demo.reactividad.application.port.out;

import java.util.List;

import demo.reactividad.domain.model.Menu;
import reactor.core.publisher.Flux;

public interface MenuBatchFailurePolicy {
    Flux<Menu> onBatchFailure(List<Menu> batch, Throwable error);
}
