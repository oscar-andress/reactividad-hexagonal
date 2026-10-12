# Qué es un threat model

Este doc no es específico de este proyecto — es conocimiento general de seguridad, portable a cualquier sistema. La instanciación concreta para `reactividad-hexagonal` vive en `docs/security/threat-model.md`.

## La idea central

Un threat model es un ejercicio que se hace **antes** de que algo salga mal, no después. Responde: *"dado este sistema real, ¿qué podría atacarse o usarse mal, y qué tan grave sería cada cosa?"*.

Sin esto, la seguridad es una sensación vaga ("creo que está bien protegido"). Con un threat model, se convierte en una lista concreta y priorizada de riesgos reales, atados a componentes reales del sistema — y de ahí, cada uno se arregla con un fix verificado, o se documenta explícitamente como "lo sé, decido no arreglarlo ahora" (nunca queda en silencio).

## El patrón general (independiente de la metodología)

1. **Mirar los componentes reales del sistema** — no una lista genérica de "mejores prácticas", sino el código/la arquitectura puntual que existe.
2. **Para cada componente, preguntarse qué podría salir mal** — usando alguna metodología que estructure la pregunta (ver abajo).
3. **Clasificar cada hallazgo por severidad** (y, si se usa una herramienta como ZAP más adelante, también por confianza — ver `docs/security/zap-report-guide.md`).
4. **Decidir, por cada hallazgo:** arreglarlo (con un fix + un test que falle si el problema sigue ahí), o documentarlo explícitamente como fuera de alcance por ahora. Nunca queda callado.

## STRIDE: la metodología usada en este proyecto

Un mnemónico (de Microsoft) para estructurar el paso 2 — da 6 categorías de amenaza a revisar sistemáticamente, en vez de confiar en acordarse de todo:

| Letra | Amenaza | Pregunta que responde |
|---|---|---|
| **S** | Spoofing | ¿Alguien puede hacerse pasar por quien no es? |
| **T** | Tampering | ¿Alguien puede modificar datos que no debería poder tocar? |
| **R** | Repudiation | ¿Alguien puede negar haber hecho algo que sí hizo? |
| **I** | Information Disclosure | ¿Se expone información que no debería verse? |
| **D** | Denial of Service | ¿Alguien puede tirar abajo o saturar el sistema? |
| **E** | Elevation of Privilege | ¿Alguien puede conseguir más permisos de los que le dieron? |

No es una auditoría exhaustiva ni una checklist para tildar — es un punto de partida para no dejar un ángulo entero sin mirar.

## No es un documento de una sola vez

Un threat model real no se escribe una vez y se archiva — es un documento vivo. Cada vez que algo nuevo lo pone a prueba (una herramienta real como ZAP, una revisión de código, un incidente), ese hallazgo nuevo se agrega como su propia entrada — con el mismo ciclo: hallazgo → decisión → qué cambió → cómo se verificó. Así el documento termina siendo el historial real de seguridad del sistema, no una foto de un día puntual que envejece mal.
