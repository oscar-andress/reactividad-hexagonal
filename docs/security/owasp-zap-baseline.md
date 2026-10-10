# OWASP ZAP: primer ejercicio de DAST contra la app real

Este doc cubre el primer ejercicio de DAST (Dynamic Application Security Testing) del proyecto — distinto de todo lo anterior en `docs/security/threat-model.md`, que fue análisis estático (leer código, nunca atacar nada corriendo). Acá se ataca la app real, levantada y corriendo, con tráfico HTTP real.

## ¿Qué es ZAP y por qué Docker primero, manual, antes que nada automatizado?

[OWASP ZAP](https://www.zaproxy.org/) (Zed Attack Proxy) es un proxy interceptor + scanner de seguridad. Se usó la imagen oficial (`ghcr.io/zaproxy/zaproxy:stable`) corrida a mano, en modo gráfico, para entender el mecanismo real antes de automatizar nada en CI — decisión explícita: entender primero cómo funciona de verdad (qué ataca, qué encuentra, cómo se lee un resultado), recién después evaluar MCP u otra automatización.

## Topología real: Docker corre dentro de WSL2, la app corre en Windows

El daemon de Docker de esta máquina vive dentro de la VM de WSL2 (Ubuntu), no en Windows directamente — verificado con `wsl -d Ubuntu -- docker images`, no con suposiciones. Esto generó dos problemas de red reales, resueltos en esta sesión:

### Windows → WSL: funciona vía `localhost`

Por el *port-forwarding* automático de WSL2 (activado por defecto), un servicio escuchando dentro de la VM es alcanzable desde Windows como `localhost:<puerto>`. Así es como `DOCKER_HOST=tcp://localhost:2375` siempre funcionó para Testcontainers.

### WSL/contenedor → Windows: NO funciona vía `localhost`

La dirección contraria **no** está mirrorada — probado empíricamente (`curl http://localhost:8080` desde dentro de WSL y desde un contenedor con `--network host`, ambos con `HTTP 000`, conexión rechazada). Esta máquina corre WSL2 en modo NAT estándar, no en modo "mirrored networking" (se había asumido mirrored en una sesión anterior; quedó corregido acá tras verificarlo).

**La solución real:** desde WSL, el host Windows es alcanzable por la IP del gateway que la propia VM le asigna, no por `localhost`:

```bash
ip route | grep default   # → default via 172.21.32.1 dev eth0
```

Esa IP (`172.21.32.1` en esta máquina, la interfaz `vEthernet (WSL (Hyper-V firewall))` del lado Windows) es la que hay que usar como target en ZAP — nunca `localhost:8080`. Confirmado con `curl` tanto desde WSL puro como desde un contenedor Docker normal (sin necesidad de `--network host`, porque el bridge default de Docker ya enruta a través del host WSL).

## Cómo se levantó la GUI de ZAP

El contenedor "pelado" (`docker run ... zap.sh`) falla con `ZAP GUI is not supported on a headless environment` — necesita un servidor gráfico al que mandar la ventana, y el contenedor no tiene ninguno por sí solo.

La opción elegida: **Webswing** (incluido en la imagen oficial), que sirve la misma GUI de escritorio dentro de una pestaña de navegador, sin necesitar X11:

```bash
docker run --rm -u zap -p 8090:8080 -v $(pwd):/zap/wrk/:rw ghcr.io/zaproxy/zaproxy:stable zap-webswing.sh
```

Notas sobre este comando:
- Mapeo `8090:8080` (no `8080:8080` como sugiere la doc oficial) porque el puerto 8080 de Windows ya estaba ocupado por la app real.
- El puerto interno 8090 (el proxy/API propio de ZAP) no quedó expuesto al host — no hizo falta para uso manual vía GUI.
- Se abre en `http://localhost:8090/zap/` desde cualquier navegador de Windows (dirección Windows→WSL, la que sí funciona vía `localhost`).
- El volumen `-v $(pwd):/zap/wrk/:rw` le da a ZAP permiso de escritura sobre esa carpeta, pero **no vuelca nada ahí automáticamente** — un reporte generado con la ruta default de ZAP (`/home/zap/...`) queda dentro del filesystem del contenedor, no en el host. Para que aparezca en la carpeta montada hay que apuntar explícitamente la ruta de salida a `/zap/wrk/...` al generar el reporte (`Report → Generate Report...`).

## Autenticación: generar un JWT a mano

Este proyecto no tiene endpoint de login — los JWT se generan solo internamente (`JwtTokenService.generate(...)`, ver `docs/security/jwt-authentication.md`), nunca vía HTTP. Para que ZAP pudiera explorar rutas protegidas (todo lo es — `AuthenticationWebFilter` exige el header `auth-token` en el 100% de las requests, incluido `/actuator/health`), se firmó un JWT fuera de la app, con el mismo secreto y algoritmo (HS256) que usa `application.properties` en desarrollo, vía `jshell` con el jar de `nimbus-jose-jwt` ya presente en el repo local de Maven — sin tocar el repo ni escribir ningún script nuevo versionado.

Ese token se cargó en ZAP vía **Replacer** (`Options → Replacer`, regla con Match Type "Request Header (will add if not present)", Match String `auth-token`, Replacement String el JWT) para que se agregue automáticamente a cada request que ZAP genere.

**Detalle real encontrado:** el botón "Attack" de la pestaña "Automated Scan"/Quick Start hace una verificación previa de la URL que *no* respeta el Replacer — falla con `Failed to attack the URL: received a 401 response code` aunque el Replacer esté bien configurado (confirmado contra la FAQ oficial de ZAP, que recomienda explícitamente evitar ese botón cuando hay auth de por medio). La alternativa que sí funciona: mandar una request manual con el header a mano (`Manual Request Editor`), lo que agrega el sitio al árbol de **Sites**, y desde ahí click derecho → **Attack → Active Scan**, que sí pasa por el pipeline normal del proxy (y por lo tanto sí respeta el Replacer para las requests que ZAP genera durante el escaneo).

## El hallazgo

Ver el cierre documentado en `docs/security/threat-model.md` ("Cierre posterior: header `X-Content-Type-Options` faltante"). Un solo hallazgo real: `X-Content-Type-Options Header Missing` (Low/Medium, OWASP A05:2021, CWE-693) en toda respuesta de la API.

## Próximo paso: pasar a CI

Antes de automatizar esto en un pipeline, dos reglas no negociables (ver conversación): un **Active Scan** manda payloads de ataque reales, así que **nunca corre contra algo persistente o compartido** — solo contra un entorno efímero (igual que ya hacen los `*IT` con Testcontainers); para CI de rutina conviene el *baseline scan* (`zap-baseline.py`, solo pasivo, no ataca) y dejar el Active Scan completo para un job aparte, menos frecuente.
