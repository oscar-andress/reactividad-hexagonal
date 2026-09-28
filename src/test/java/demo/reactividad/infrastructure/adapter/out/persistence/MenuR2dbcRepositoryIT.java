package demo.reactividad.infrastructure.adapter.out.persistence;

import static demo.reactividad.testsupport.fixtures.MenuEntityTestDataBuilder.aMenuEntity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest;

import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuR2dbcRepository;
import demo.reactividad.testsupport.containers.AbstractPostgresContainerTest;
import reactor.test.StepVerifier;

@DataR2dbcTest
class MenuR2dbcRepositoryIT extends AbstractPostgresContainerTest {

    private static final String UPDATED_DESCRIPTION = "Lorem Ipsum";
    private static final Logger log = LoggerFactory.getLogger(MenuR2dbcRepositoryIT.class);

    @Autowired
    private MenuR2dbcRepository menuR2dbcRepository;

    private MenuEntity menu;

    @BeforeEach
    void setUp() {
        this.menu = this.menuR2dbcRepository.deleteAll()
                .then(this.menuR2dbcRepository.save(aMenuEntity().build()))
                .block();
    }

    @Test
    void findAll_Success() {
        this.menuR2dbcRepository.findAll()
                .doOnNext(m -> log.info("{}", m))
                .as(StepVerifier::create)
                .assertNext(m -> {
                    Assertions.assertEquals(this.menu.getTitle(), m.getTitle());
                    Assertions.assertEquals(this.menu.getDescription(), m.getDescription());
                })
                .expectComplete()
                .verify();
    }

    @Test
    void findById_Success() {
        this.menuR2dbcRepository.findById(this.menu.getId())
                .doOnNext(m -> log.info("{}", m))
                .as(StepVerifier::create)
                .assertNext(m -> Assertions.assertEquals(this.menu.getTitle(), m.getTitle()))
                .expectComplete()
                .verify();
    }

    @Test
    void findById_WhenMenuDoesNotExist_ReturnsEmpty() {
        this.menuR2dbcRepository.findById(java.util.UUID.randomUUID())
                .as(StepVerifier::create)
                .expectComplete()
                .verify();
    }

    @Test
    void updateMenu_Success() {
        this.menuR2dbcRepository.findById(this.menu.getId())
                .doOnNext(m -> m.setDescription(UPDATED_DESCRIPTION))
                .flatMap(m -> this.menuR2dbcRepository.save(m))
                .doOnNext(m -> log.info("{}", m))
                .as(StepVerifier::create)
                .assertNext(c -> Assertions.assertEquals(UPDATED_DESCRIPTION, c.getDescription()))
                .expectComplete()
                .verify();
    }

    @Test
    void save_WhenTitleIsNull_Fails() {
        MenuEntity invalidMenu = aMenuEntity().withTitle(null).build();

        this.menuR2dbcRepository.save(invalidMenu)
                .as(StepVerifier::create)
                .expectError()
                .verify();
    }
}
