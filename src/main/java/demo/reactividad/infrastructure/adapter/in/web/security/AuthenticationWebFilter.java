package demo.reactividad.infrastructure.adapter.in.web.security;

import java.util.List;

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

    // Única excepción al "todo requiere auth-token": el spec OpenAPI en sí (JSON de qué
    // rutas existen) es información pública, no un dato sensible (decisión explícita, ver
    // docs/openapi.md) — distinto del resto de la API, que sigue sin ninguna excepción.
    // No incluye /swagger-ui: esa UI se descartó por incompatibilidad real con Spring
    // Framework 7 (ver docs/openapi.md), no se sirve nada bajo ese path.
    private static final List<String> PUBLIC_PATH_PREFIXES = List.of("/v3/api-docs");

    private final JwtTokenService jwtTokenService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isPublicPath(exchange)) {
            return chain.filter(exchange);
        }
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

    private boolean isPublicPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return PUBLIC_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
