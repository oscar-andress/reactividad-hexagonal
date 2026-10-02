package demo.reactividad.orders.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.application.port.out.OrderEventPublisher;
import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.exception.MenuNotFoundForOrderException;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.MenuSnapshot;
import demo.reactividad.orders.domain.model.Order;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OrderUseCasesServiceTest {

    @Mock
    private OrderRepositoryPort orderRepositoryPort;

    @Mock
    private MenuLookupPort menuLookupPort;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private OrderUseCasesService orderUseCasesService;

    @BeforeEach
    void setUp() {
        this.orderUseCasesService = new OrderUseCasesService(
                this.orderRepositoryPort, this.menuLookupPort, this.orderEventPublisher);
    }

    @Test
    void createOrder_WhenMenuExists_SavesOrderAndPublishesEvent() {
        MenuId menuId = new MenuId(UUID.randomUUID());
        MenuSnapshot snapshot = new MenuSnapshot(menuId, "Menu del dia");
        when(this.menuLookupPort.findMenuSnapshot(menuId)).thenReturn(Mono.just(snapshot));
        when(this.orderRepositoryPort.save(any(Order.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        this.orderUseCasesService.createOrder(menuId, 3)
                .as(StepVerifier::create)
                .assertNext(order -> {
                    assertEquals(menuId, order.getMenuId());
                    assertEquals("Menu del dia", order.getMenuTitleSnapshot());
                    assertEquals(3, order.getQuantity());
                })
                .expectComplete()
                .verify();

        ArgumentCaptor<Order> savedOrderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(this.orderRepositoryPort).save(savedOrderCaptor.capture());
        assertEquals("Menu del dia", savedOrderCaptor.getValue().getMenuTitleSnapshot());
        verify(this.orderEventPublisher, times(1)).publish(any(Order.class));
    }

    @Test
    void createOrder_WhenMenuDoesNotExist_ThrowsMenuNotFoundForOrderExceptionAndNeverSavesOrPublishes() {
        MenuId menuId = new MenuId(UUID.randomUUID());
        when(this.menuLookupPort.findMenuSnapshot(menuId)).thenReturn(Mono.empty());

        this.orderUseCasesService.createOrder(menuId, 1)
                .as(StepVerifier::create)
                .expectError(MenuNotFoundForOrderException.class)
                .verify();

        verify(this.orderRepositoryPort, never()).save(any());
        verify(this.orderEventPublisher, never()).publish(any());
    }

    @Test
    void streamOrders_DelegatesToEventPublisher() {
        Order order = new Order(new MenuId(UUID.randomUUID()), "Menu del dia", 2);
        when(this.orderEventPublisher.subscribe()).thenReturn(Flux.just(order));

        this.orderUseCasesService.streamOrders()
                .as(StepVerifier::create)
                .expectNext(order)
                .expectComplete()
                .verify();
    }
}
