package demo.reactividad.infrastructure.adapter.in.web.security;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

// Hallazgo real de OWASP ZAP (ver docs/security/owasp-zap-baseline.md): ninguna respuesta
// traía X-Content-Type-Options, lo que permite a navegadores legacy hacer MIME-sniffing
// del body y potencialmente interpretarlo como un content-type distinto al declarado
// (OWASP A05:2021, CWE-693). Corre primero (@Order(0), antes que Authentication/
// Authorization) para que el header quede seteado incluso en las respuestas 401/403 que
// esos filtros cortan antes de llegar al handler real.
@Order(0)
@Component
public class SecurityHeadersWebFilter implements WebFilter {

    private static final String NOSNIFF_HEADER = "X-Content-Type-Options";
    private static final String NOSNIFF_VALUE = "nosniff";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        exchange.getResponse().getHeaders().add(NOSNIFF_HEADER, NOSNIFF_VALUE);
        return chain.filter(exchange);
    }
}
