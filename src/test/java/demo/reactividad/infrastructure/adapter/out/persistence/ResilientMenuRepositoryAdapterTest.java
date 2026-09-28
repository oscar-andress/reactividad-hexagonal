package demo.reactividad.infrastructure.adapter.out.persistence;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.domain.model.Menu;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientMenuRepositoryAdapterTest {

    private static final UUID MENU_ID = UUID.randomUUID();

    @Mock
    private MenuRepositoryAdapter delegate;

    private ResilientMenuRepositoryAdapter resilientAdapter;

    @BeforeEach
    void setUp() {
        this.resilientAdapter = new ResilientMenuRepositoryAdapter(this.delegate);
    }

    @Test
    void findById_DelegatesToWrappedAdapter() {
        Menu menu = aMenu().build();
        when(this.delegate.findById(MENU_ID)).thenReturn(Mono.just(menu));

        this.resilientAdapter.findById(MENU_ID)
                .as(StepVerifier::create)
                .expectNext(menu)
                .expectComplete()
                .verify();
    }

    @Test
    void fallbackFindById_ReturnsErrorInsteadOfExceptionAsValue() {
        this.resilientAdapter.fallbackFindById(MENU_ID, new RuntimeException("db down"))
                .as(StepVerifier::create)
                .expectError(MenuUnavailableException.class)
                .verify();
    }

    @Test
    void save_DelegatesToWrappedAdapter() {
        Menu menu = aMenu().build();
        when(this.delegate.save(menu)).thenReturn(Mono.just(menu));

        this.resilientAdapter.save(menu)
                .as(StepVerifier::create)
                .expectNext(menu)
                .expectComplete()
                .verify();
    }

    @Test
    void deleteById_DelegatesToWrappedAdapter() {
        when(this.delegate.deleteById(MENU_ID)).thenReturn(Mono.empty());

        this.resilientAdapter.deleteById(MENU_ID)
                .as(StepVerifier::create)
                .expectComplete()
                .verify();

        verify(this.delegate).deleteById(MENU_ID);
    }

    @Test
    void saveAll_DelegatesToWrappedAdapter() {
        Menu menu = aMenu().build();
        when(this.delegate.saveAll(List.of(menu))).thenReturn(Flux.just(menu));

        this.resilientAdapter.saveAll(List.of(menu))
                .as(StepVerifier::create)
                .expectNext(menu)
                .expectComplete()
                .verify();
    }
}
