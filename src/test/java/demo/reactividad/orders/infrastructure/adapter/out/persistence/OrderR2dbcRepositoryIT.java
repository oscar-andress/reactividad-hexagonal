package demo.reactividad.orders.infrastructure.adapter.out.persistence;

import static demo.reactividad.orders.testsupport.fixtures.OrderEntityTestDataBuilder.anOrderEntity;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;

import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.repository.OrderR2dbcRepository;
import demo.reactividad.testsupport.containers.AbstractPostgresContainerTest;
import reactor.test.StepVerifier;

@DataR2dbcTest
class OrderR2dbcRepositoryIT extends AbstractPostgresContainerTest {

    @Autowired
    private OrderR2dbcRepository orderR2dbcRepository;

    private OrderEntity order;

    @BeforeEach
    void setUp() {
        this.order = this.orderR2dbcRepository.deleteAll()
                .then(this.orderR2dbcRepository.save(anOrderEntity().build()))
                .block();
    }

    @Test
    void findById_Success() {
        this.orderR2dbcRepository.findById(this.order.getId())
                .as(StepVerifier::create)
                .assertNext(found -> {
                    Assertions.assertEquals(this.order.getMenuId(), found.getMenuId());
                    Assertions.assertEquals(this.order.getQuantity(), found.getQuantity());
                })
                .expectComplete()
                .verify();
    }

    @Test
    void findById_WhenOrderDoesNotExist_ReturnsEmpty() {
        this.orderR2dbcRepository.findById(UUID.randomUUID())
                .as(StepVerifier::create)
                .expectComplete()
                .verify();
    }

    @Test
    void save_WhenMenuIdIsNull_Fails() {
        OrderEntity invalidOrder = anOrderEntity().withMenuId(null).build();

        this.orderR2dbcRepository.save(invalidOrder)
                .as(StepVerifier::create)
                .expectError()
                .verify();
    }
}
