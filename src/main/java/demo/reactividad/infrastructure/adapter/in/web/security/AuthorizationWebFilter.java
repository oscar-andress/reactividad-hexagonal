package demo.reactividad.infrastructure.adapter.in.web.security;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

// Hallazgo de la pasada STRIDE (ver docs/security/threat-model.md, Elevation of
// Privilege): el modelo era binario por verbo HTTP ("GET sí, todo lo demás no"), sin
// distinguir una escritura de cliente final (crear su propio pedido) de una escritura
// administrativa (crear/borrar un menú). STANDARD ahora también puede crear pedidos --
// la única escritura que un cliente final legítimamente necesita hacer. Cualquier otra
// escritura sigue siendo exclusiva de PRIME.
@Order(2)
@Component
public class AuthorizationWebFilter implements WebFilter {

    private static final String ORDER_CREATE_PATH = "/api/v1/order/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AuthenticationCategory category = exchange.getAttributeOrDefault(
                SecurityConstants.CATEGORY_ATTRIBUTE, AuthenticationCategory.STANDARD);
        return switch (category) {
            case STANDARD -> standard(exchange, chain);
            case PRIME -> prime(exchange, chain);
        };
    }

    private Mono<Void> prime(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange);
    }

    private Mono<Void> standard(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        boolean isGet = HttpMethod.GET.equals(request.getMethod());
        boolean isOrderCreation = HttpMethod.POST.equals(request.getMethod())
                && ORDER_CREATE_PATH.equals(request.getPath().value());
        if (isGet || isOrderCreation) {
            return chain.filter(exchange);
        }
        return Mono.fromRunnable(() -> exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN));
    }
}
