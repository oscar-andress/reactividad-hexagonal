package demo.reactividad.infrastructure.adapter.in.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuthenticationWebFilterTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long!!";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, Duration.ofMinutes(15));
    private final AuthenticationWebFilter filter = new AuthenticationWebFilter(this.jwtTokenService);

    @Test
    void filter_WithValidToken_SetsCategoryAttributeAndContinuesChain() {
        String token = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
        ServerWebExchange exchange = exchangeWithToken(token);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain)
                .as(StepVerifier::create)
                .verifyComplete();

        assertEquals(AuthenticationCategory.PRIME, exchange.getAttribute(SecurityConstants.CATEGORY_ATTRIBUTE));
        verify(chain).filter(exchange);
    }

    @Test
    void filter_WithExpiredToken_RejectsWithUnauthorizedAndNeverContinuesChain() throws InterruptedException {
        JwtTokenService shortLivedService = new JwtTokenService(SECRET, Duration.ofMillis(1));
        AuthenticationWebFilter shortLivedFilter = new AuthenticationWebFilter(shortLivedService);
        String token = shortLivedService.generate(AuthenticationCategory.STANDARD);
        Thread.sleep(20);
        ServerWebExchange exchange = exchangeWithToken(token);
        WebFilterChain chain = chainThatCompletes();

        shortLivedFilter.filter(exchange, chain)
                .as(StepVerifier::create)
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_WithTamperedToken_RejectsWithUnauthorized() {
        String token = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
        String tampered = token.substring(0, token.length() - 4) + "abcd";
        ServerWebExchange exchange = exchangeWithToken(tampered);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain)
                .as(StepVerifier::create)
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        assertNull(exchange.getAttribute(SecurityConstants.CATEGORY_ATTRIBUTE));
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_WithTokenSignedByADifferentSecret_RejectsWithUnauthorized() {
        JwtTokenService otherService = new JwtTokenService(
                "a-completely-different-secret-also-32-bytes!!", Duration.ofMinutes(15));
        String token = otherService.generate(AuthenticationCategory.PRIME);
        ServerWebExchange exchange = exchangeWithToken(token);
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain)
                .as(StepVerifier::create)
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    @Test
    void filter_WithNoAuthHeaderAtAll_RejectsWithUnauthorized() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/").build());
        WebFilterChain chain = chainThatCompletes();

        this.filter.filter(exchange, chain)
                .as(StepVerifier::create)
                .verifyComplete();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        verify(chain, never()).filter(any());
    }

    private ServerWebExchange exchangeWithToken(String token) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/").header(SecurityConstants.AUTH_TOKEN_HEADER, token).build());
    }

    private WebFilterChain chainThatCompletes() {
        WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }
}
