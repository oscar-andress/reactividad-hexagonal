# JWT firmado reemplaza el mapa de tokens hardcodeado (Gap B, ejercicio 3)

Este doc cubre el ejercicio 3 del Gap B del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): cierra los hallazgos de **Spoofing** y **Elevation of Privilege** del threat model (`docs/security/threat-model.md`) — los tokens eran strings fijos escritos en el código fuente, que nunca expiraban ni se podían revocar.

## El problema concreto

```java
// AuthenticationWebFilter.java — antes
private static final Map<String, AuthenticationCategory> AUTH_CATEGORY_MAP = Map.of(
    "secret123", AuthenticationCategory.STANDARD,
    "secret456", AuthenticationCategory.PRIME
);
```

Quien viera el código (o el repo) tenía, para siempre, un token `PRIME` válido — sin expiración, sin forma de revocarlo sin recompilar y redesplegar.

## La librería elegida: Nimbus, no Spring Security

Este proyecto decidió, desde el inicio, no usar `spring-boot-starter-security` (toda su "capa de seguridad" son dos `WebFilter` a medida). Para JWT, en vez de arrastrar todo Spring Security solo para firmar/verificar tokens, se usó **Nimbus JOSE+JWT** — la misma librería que Spring Security usa internamente para JWT, pero sin necesitar el resto del framework.

## El servicio de tokens

```java
@Component
public class JwtTokenService {

    private final byte[] secretKeyBytes;
    private final Duration tokenDuration;

    public JwtTokenService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.expiration}") Duration tokenDuration) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.tokenDuration = tokenDuration;
    }

    public String generate(AuthenticationCategory category) {
        // firma un JWT (HS256) con el claim "category" y una expiración corta
    }

    public AuthenticationCategory parseCategory(String token) {
        // verifica firma + expiración; lanza InvalidAuthTokenException si algo falla
    }
}
```

```properties
# application.properties
security.jwt.secret=dev-only-secret-change-me-in-any-real-environment-00
security.jwt.expiration=15m
```

El secreto de desarrollo queda marcado explícitamente como tal — en cualquier entorno real tiene que sobreescribirse (variable de entorno / secret manager), igual que ya pasa (sin resolver, y fuera del alcance de este gap) con las credenciales de Postgres/S3 en el mismo archivo.

## Decisión de alcance: sin endpoint de emisión

El roadmap pide reemplazar la *verificación* de tokens — no agrega un endpoint `/login` ni ningún flujo de autenticación de usuarios (esta API no tiene concepto de "usuario", solo de categoría de cliente). `JwtTokenService.generate(...)` queda como el mecanismo que un proceso de administración llamaría para emitir un token nuevo — hoy, igual que con los tokens viejos, la emisión queda fuera de banda (no hay un endpoint HTTP que lo haga). Agregar uno sería una superficie de ataque nueva y una decisión de diseño propia, no pedida por este ejercicio.

## El filtro, actualizado

```java
// AuthenticationWebFilter.java — después
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
```

`AuthorizationWebFilter` no cambió — sigue leyendo el atributo `category` del exchange, sin importarle cómo se calculó.

## Efecto en cascada: los tokens viejos ya no son válidos, en ningún lado

Los strings `secret123`/`secret456` estaban hardcodeados también en **3 archivos de test** (`MenuWebIntegrationIT`, `OrderWebIntegrationIT`, `LoggingMenuBatchFailurePolicyMetricsEndpointTest`) — los tres dejaron de poder autenticarse apenas se reemplazó el mapa. Se actualizaron los tres para generar un token real en `@BeforeEach` vía `@Autowired JwtTokenService`, en vez de depender de un string fijo:

```java
@Autowired
private JwtTokenService jwtTokenService;

private String standardToken;
private String primeToken;

@BeforeEach
void setUp() {
    this.standardToken = this.jwtTokenService.generate(AuthenticationCategory.STANDARD);
    this.primeToken = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
    ...
}
```

## Verificación

1. **`JwtTokenServiceTest`** (nuevo, unitario puro — sin Spring): round-trip `generate`→`parseCategory`; token alterado (firma corrupta) rechazado; token firmado con otro secreto rechazado; token expirado rechazado; un string que ni siquiera es un JWT (el viejo `"secret123"`) rechazado.
2. **`AuthenticationWebFilterTest`** (nuevo, unitario — mock de `WebFilterChain`, sin Spring, igual razón que en Gap D: este filtro no tiene ninguna anotación que necesite un proxy AOP): confirma que un token válido setea el atributo de categoría correcto y continúa la cadena; que un token expirado/alterado/firmado con otro secreto/ausente se rechaza con 401 **sin** llegar a invocar la cadena (`verify(chain, never()).filter(any())`).

**Verificado rompiendo a propósito:** se comentó momentáneamente la verificación de firma en `JwtTokenService.parseCategory` (dejando que `signedJWT.verify(...)` se llamara pero sin chequear el resultado) — 4 tests fallaron exactamente donde se esperaba (los dos de firma alterada y los dos de secreto incorrecto, en ambos archivos de test). Restaurado el fix, vuelven a pasar los 10.

**Resultado final:** 85 tests verdes, PIT sin cambios (100%, 44/44 — la capa de seguridad está fuera del alcance de PIT).
