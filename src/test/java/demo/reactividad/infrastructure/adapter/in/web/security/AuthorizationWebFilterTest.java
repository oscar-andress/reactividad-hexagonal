package demo.reactividad.infrastructure.adapter.in.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthorizationWebFilterTest {

    private final AuthorizationWebFilter filter = new AuthorizationWebFilter();

    @Test
    void filter_StandardCategoryWithGet_ContinuesChain() {
        ServerWebExchange exchange = exchangeWithCategory(
                MockServerHttpRequest.get("/api/v1/menu/stream").build(), AuthenticationCategory.STANDARD);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_StandardCategoryCreatingAnOrder_ContinuesChain() {
        ServerWebExchange exchange = exchangeWithCategory(
                MockServerHttpRequest.post("/api/v1/order/").build(), AuthenticationCategory.STANDARD);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_StandardCategoryCreatingAMenu_RejectsWithForbidden() {
        ServerWebExchange exchange = exchangeWithCategory(
                MockServerHttpRequest.post("/api/v1/menu/").build(), AuthenticationCategory.STANDARD);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_StandardCategoryDeletingAMenu_RejectsWithForbidden() {
        ServerWebExchange exchange = exchangeWithCategory(
                MockServerHttpRequest.delete("/api/v1/menu/{menuId}", "any-id").build(),
                AuthenticationCategory.STANDARD);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_PrimeCategoryDeletingAMenu_ContinuesChain() {
        ServerWebExchange exchange = exchangeWithCategory(
                MockServerHttpRequest.delete("/api/v1/menu/{menuId}", "any-id").build(),
                AuthenticationCategory.PRIME);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain).as(StepVerifier::create).verifyComplete();

        verify(chain).filter(exchange);
    }

    private ServerWebExchange exchangeWithCategory(MockServerHttpRequest request, AuthenticationCategory category) {
        ServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(SecurityConstants.CATEGORY_ATTRIBUTE, category);
        return exchange;
    }

    private WebFilterChain chainThatCompletes() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }
}
