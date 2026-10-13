package demo.reactividad.orders.application.usecase;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import demo.reactividad.orders.application.port.in.OrderUseCases;
import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.application.port.out.OrderEventPublisher;
import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.exception.MenuLookupUnavailableException;
import demo.reactividad.orders.domain.exception.MenuNotFoundForOrderException;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderUseCasesService implements OrderUseCases {

    private final OrderRepositoryPort orderRepositoryPort;
    private final MenuLookupPort menuLookupPort;
    private final OrderEventPublisher orderEventPublisher;

    @Override
    public Mono<Order> createOrder(MenuId menuId, int quantity, String idempotencyKey) {
        if (idempotencyKey == null) {
            return createAndPublishNewOrder(menuId, quantity, null);
        }
        return this.orderRepositoryPort.findByIdempotencyKey(idempotencyKey)
                .switchIfEmpty(Mono.defer(() -> createAndPublishNewOrder(menuId, quantity, idempotencyKey)));
    }

    // Ver docs/adr/0002-order-idempotency-and-menu-lookup-resilience.md: MenuLookupPortAdapter
    // ya traduce "no existe" a Mono.empty() antes de que la llamada llegue a
    // @Retry/@TimeLimiter -- cualquier error que llegue hasta acá es un fallo real de
    // disponibilidad (timeout agotado, reintentos agotados, o el bulkhead lleno), nunca
    // un "no existe" disfrazado, así que se traduce entero sin distinguir tipos.
    private Mono<Order> createAndPublishNewOrder(MenuId menuId, int quantity, String idempotencyKey) {
        return this.menuLookupPort.findMenuSnapshot(menuId)
                .onErrorMap(error -> new MenuLookupUnavailableException(
                        "Menu lookup for " + menuId.value() + " is currently unavailable", error))
                .switchIfEmpty(Mono.error(() -> new MenuNotFoundForOrderException(
                        "Cannot order menu " + menuId.value() + ": it does not exist")))
                .map(snapshot -> new Order(menuId, snapshot.title(), quantity, idempotencyKey))
                .flatMap(order -> this.orderRepositoryPort.save(order)
                        .doOnNext(this.orderEventPublisher::publish)
                        // El ganador de la carrera ya publicó e insertó; el perdedor (misma
                        // Idempotency-Key, llegó una fracción de segundo después) solo necesita
                        // leer lo que el ganador ya guardó -- nunca reintenta el insert ni publica
                        // un segundo evento para el mismo pedido.
                        .onErrorResume(DuplicateKeyException.class,
                                error -> this.orderRepositoryPort.findByIdempotencyKey(idempotencyKey)));
    }

    @Override
    public Flux<Order> streamOrders() {
        return this.orderEventPublisher.subscribe();
    }
}
