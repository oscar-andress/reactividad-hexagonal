package demo.reactividad.orders.infrastructure.adapter.out.persistence;

import static demo.reactividad.orders.testsupport.fixtures.OrderEntityTestDataBuilder.anOrderEntity;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;
import org.springframework.dao.DuplicateKeyException;

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

    @Test
    void findByIdempotencyKey_WhenPresent_ReturnsTheMatchingOrder() {
        this.orderR2dbcRepository.save(anOrderEntity().withIdempotencyKey("idem-key-1").build()).block();

        this.orderR2dbcRepository.findByIdempotencyKey("idem-key-1")
                .as(StepVerifier::create)
                .assertNext(found -> Assertions.assertEquals("idem-key-1", found.getIdempotencyKey()))
                .expectComplete()
                .verify();
    }

    @Test
    void findByIdempotencyKey_WhenAbsent_ReturnsEmpty() {
        this.orderR2dbcRepository.findByIdempotencyKey("does-not-exist")
                .as(StepVerifier::create)
                .expectComplete()
                .verify();
    }

    // El índice único parcial de la migración V5 (ver docs/adr/0002-order-idempotency-
    // and-menu-lookup-resilience.md) es la fuente real de verdad de la deduplicación --
    // esto confirma, contra Postgres real, que SÍ rechaza una segunda fila con la misma
    // clave, y que null no cuenta como "la misma clave" entre sí.
    @Test
    void save_WhenIdempotencyKeyAlreadyExistsOnAnotherRow_FailsWithDuplicateKeyException() {
        this.orderR2dbcRepository.save(anOrderEntity().withIdempotencyKey("idem-key-2").build()).block();
        OrderEntity secondOrderWithSameKey = anOrderEntity().withIdempotencyKey("idem-key-2").build();

        this.orderR2dbcRepository.save(secondOrderWithSameKey)
                .as(StepVerifier::create)
                .expectError(DuplicateKeyException.class)
                .verify();
    }

    @Test
    void save_MultipleOrdersWithoutIdempotencyKey_AreAllAllowed() {
        this.orderR2dbcRepository.save(anOrderEntity().withIdempotencyKey(null).build())
                .as(StepVerifier::create)
                .expectNextCount(1)
                .expectComplete()
                .verify();
        this.orderR2dbcRepository.save(anOrderEntity().withIdempotencyKey(null).build())
                .as(StepVerifier::create)
                .expectNextCount(1)
                .expectComplete()
                .verify();
    }
}
