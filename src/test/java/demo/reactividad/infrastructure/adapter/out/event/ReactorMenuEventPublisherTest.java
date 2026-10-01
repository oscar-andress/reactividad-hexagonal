package demo.reactividad.infrastructure.adapter.out.event;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import demo.reactividad.domain.model.Menu;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

class ReactorMenuEventPublisherTest {

    private final ReactorMenuEventPublisher publisher = new ReactorMenuEventPublisher();

    @Test
    void publish_ThenSubscribe_EmitsThePublishedMenu() {
        Menu menu = aMenu().build();

        StepVerifier.create(this.publisher.subscribe())
                .then(() -> this.publisher.publish(menu))
                .expectNext(menu)
                .thenCancel()
                .verify();
    }

    @Test
    void publish_FromManyThreadsConcurrently_NoEventIsSilentlyLost() throws InterruptedException {
        int totalMenus = 200;
        List<Menu> menus = IntStream.range(0, totalMenus)
                .mapToObj(i -> aMenu().withTitle("menu-" + i).build())
                .toList();

        List<Menu> received = new CopyOnWriteArrayList<>();
        CountDownLatch allReceived = new CountDownLatch(totalMenus);
        Disposable subscription = this.publisher.subscribe()
                .subscribe(menu -> {
                    received.add(menu);
                    allReceived.countDown();
                });

        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            List<CompletableFuture<Void>> publications = menus.stream()
                    .map(menu -> CompletableFuture.runAsync(() -> this.publisher.publish(menu), executor))
                    .toList();
            publications.forEach(CompletableFuture::join);

            boolean allArrivedInTime = allReceived.await(5, TimeUnit.SECONDS);

            assertTrue(allArrivedInTime,
                    "Esperábamos recibir los " + totalMenus + " menús, llegaron " + received.size());
            assertEquals(totalMenus, received.size());
            assertEquals(0, this.publisher.getFailedEmissionCount(),
                    "Ninguna emisión debería haber agotado el presupuesto de reintentos");
        } finally {
            subscription.dispose();
            executor.shutdown();
        }
    }
}
