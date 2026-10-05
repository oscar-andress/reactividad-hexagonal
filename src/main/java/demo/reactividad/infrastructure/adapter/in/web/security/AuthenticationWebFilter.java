package demo.reactividad.infrastructure.adapter.in.web.security;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Order(1)
@Component
@RequiredArgsConstructor
public class AuthenticationWebFilter implements WebFilter {

    private final JwtTokenService jwtTokenService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String token = exchange.getRequest().getHeaders().getFirst(SecurityConstants.AUTH_TOKEN_HEADER);
        if (token == null) {
            return unauthorized(exchange);
        }
        try {
            AuthenticationCategory category = this.jwtTokenService.parseCategory(token);
            exchange.getAttributes().put(SecurityConstants.CATEGORY_ATTRIBUTE, category);
            return chain.filter(exchange);
        } catch (InvalidAuthTokenException exception) {
            return unauthorized(exchange);
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED));
    }
}
