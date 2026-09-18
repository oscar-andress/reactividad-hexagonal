package demo.reactividad.infrastructure.adapter.in.web.security;

import java.util.Map;
import java.util.Objects;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Order(1)
@Component
public class AuthenticationWebFilter implements WebFilter {

    private static final Map<String, AuthenticationCategory> AUTH_CATEGORY_MAP = Map.of(
        "secret123", AuthenticationCategory.STANDARD,
        "secret456", AuthenticationCategory.PRIME
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        if (headers.containsHeader(SecurityConstants.AUTH_TOKEN_HEADER)) {
            String token = headers.getFirst(SecurityConstants.AUTH_TOKEN_HEADER);
            if (Objects.nonNull(token) && AUTH_CATEGORY_MAP.containsKey(token)) {
                exchange.getAttributes().put(SecurityConstants.CATEGORY_ATTRIBUTE, AUTH_CATEGORY_MAP.get(token));
                return chain.filter(exchange);
            }
        }
        return Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED));
    }
}
