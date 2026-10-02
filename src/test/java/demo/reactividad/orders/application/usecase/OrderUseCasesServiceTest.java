package demo.reactividad.orders.application.usecase;

import static demo.reactividad.orders.testsupport.fixtures.OrderTestDataBuilder.anOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.model.Order;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OrderUseCasesServiceTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    private OrderUseCasesService orderUseCasesService;

    @BeforeEach
    void setUp() {
        this.orderUseCasesService = new OrderUseCasesService(this.orderRepositoryPort);
    }

    @Test
    void createOrder_DelegatesToRepositoryAndReturnsSavedOrder() {
        Order order = anOrder().build();
        Order savedOrder = anOrder().withId(UUID.randomUUID()).build();
        when(this.orderRepositoryPort.save(order)).thenReturn(Mono.just(savedOrder));

        this.orderUseCasesService.createOrder(order)
                .as(StepVerifier::create)
                .expectNext(savedOrder)
                .expectComplete()
                .verify();

        verify(this.orderRepositoryPort).save(order);
    }
}
