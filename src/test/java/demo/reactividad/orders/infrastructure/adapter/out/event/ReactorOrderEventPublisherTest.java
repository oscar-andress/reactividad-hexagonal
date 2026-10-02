package demo.reactividad.orders.infrastructure.adapter.out.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

class ReactorOrderEventPublisherTest {

    private final ReactorOrderEventPublisher publisher = new ReactorOrderEventPublisher();

    @Test
    void publish_ThenSubscribe_EmitsThePublishedOrder() {
        Order order = new Order(new MenuId(UUID.randomUUID()), "Menu del dia", 2);

        StepVerifier.create(this.publisher.subscribe())
                .then(() -> this.publisher.publish(order))
                .expectNext(order)
                .thenCancel()
                .verify();
    }

    @Test
    void publish_FromManyThreadsConcurrently_NoEventIsSilentlyLost() throws InterruptedException {
        int totalOrders = 200;
        List<Order> orders = IntStream.range(0, totalOrders)
                .mapToObj(i -> new Order(new MenuId(UUID.randomUUID()), "Menu " + i, 1))
                .toList();

        List<Order> received = new CopyOnWriteArrayList<>();
        CountDownLatch allReceived = new CountDownLatch(totalOrders);
        Disposable subscription = this.publisher.subscribe()
                .subscribe(order -> {
                    received.add(order);
                    allReceived.countDown();
                });

        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<CompletableFuture<Void>> publications = orders.stream()
                    .map(order -> CompletableFuture.runAsync(() -> this.publisher.publish(order), executor))
                    .toList();
            publications.forEach(CompletableFuture::join);

            boolean allArrivedInTime = allReceived.await(5, TimeUnit.SECONDS);

            assertTrue(allArrivedInTime,
                    "Esperábamos recibir los " + totalOrders + " pedidos, llegaron " + received.size());
            assertEquals(totalOrders, received.size());
            assertEquals(0, this.publisher.getFailedEmissionCount(),
                    "Ninguna emisión debería haber agotado el presupuesto de reintentos");
        } finally {
            subscription.dispose();
            executor.shutdown();
        }
    }
}
