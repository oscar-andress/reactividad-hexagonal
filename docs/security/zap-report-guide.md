# Cómo leer un reporte HTML de OWASP ZAP

Esta guía no es específica de este proyecto — es conocimiento general sobre cómo interpretar el reporte que genera ZAP (`Report → Generate Report`), surgido de leer el reporte real de `docs/security/owasp-zap-baseline.md` sección por sección. Sigue la misma estructura que el resto de `docs/`.

## Estructura del reporte

El reporte tiene cinco bloques, en este orden:

| Sección | Qué contiene |
|---|---|
| **About This Report → Report Parameters** | El alcance del scan: qué sitios, qué niveles de riesgo/confianza se incluyeron. Contexto, no resultados. |
| **Summaries → Alert Counts by Risk and Confidence** | Tabla cruzada: filas = riesgo, columnas = confianza. El resumen numérico más rápido de leer. |
| **Summaries → Alert Counts by Site and Risk** | Lo mismo, desglosado por sitio (útil solo si escaneaste más de un host). |
| **Summaries → Alert Counts by Alert Type** | Agrupa por el tipo específico de hallazgo (ej. "Application Error Disclosure"), no solo por riesgo. |
| **Summaries → Insights** | **No son vulnerabilidades** — estadísticas operativas del scan y del comportamiento agregado del target (ver más abajo). |
| **Alerts** | Los hallazgos reales, en detalle, agrupados por Riesgo → Sitio → Tipo. |
| **Appendix → Alert Types** | Ficha de referencia genérica de cada tipo de alerta que apareció (qué es, CWE, WASC, links) — separado de "qué pasó en tu app específicamente", que vive en la sección Alerts. |

## Risk vs. Confidence: dos ejes independientes

No es una sola escala — son dos preguntas distintas:

| Eje | Pregunta que responde | Escala |
|---|---|---|
| **Risk** | Si esto fuera real y lo explotaran, ¿qué tan grave sería? | Informational < Low < Medium < High |
| **Confidence** | ¿Qué tan seguro está ZAP de que el hallazgo es real, no un falso positivo? | Low < Medium < High < User Confirmed (verificado a mano por un humano) |

Por ser independientes, se combinan para priorizar: Risk alto + Confidence alto es lo urgente de verdad. Risk alto + Confidence bajo necesita verificación manual antes de reaccionar. Un "Medium" de confianza en una regla que detecta por patrón de texto (como las de disclosure de errores) no significa necesariamente duda real — es la calibración propia de esa regla, no una medida de qué tan reproducible fue en tu caso puntual.

## "Insights": estadísticas, no hallazgos

Esta sección confunde porque aparece mezclada visualmente con las de riesgo, pero es otra cosa — mide cómo se comportó el target y el propio scan en agregado, con una columna "Reason" que indica si cruzó algún umbral interno de ZAP (`Informational`, `Warning`, `Exceeded Low`, `Exceeded High`):

- **Relevante:** un porcentaje alto de respuestas `5xx` marcado como `Exceeded High` suele ser la confirmación, a escala, de un bug que también aparece como Alert individual — no un hallazgo nuevo, pero sí evidencia de que el problema es más amplio de lo que un solo ejemplo sugiere (ej. el mismo error en varias rutas, no solo una).
- **Ruido operativo, ignorable:** conteos de warnings internos del propio ZAP (`ver zap.log`), desglose de content-type/método HTTP, cantidad total de endpoints "tocados" (suele ser mayor a tus rutas reales, porque cada variante de payload probada cuenta como una URL distinta), porcentaje de fallos de red o de respuestas lentas.

## Los "Alert tags" de cada hallazgo

Referencias cruzadas contra distintos estándares, para no tener que re-describir el mismo tipo de problema en cada marco:

| Tag | Qué es |
|---|---|
| `WSTG-vXX-YYYY-NN` | Ítem numerado del *OWASP Web Security Testing Guide* — metodología oficial de testing. |
| `OWASP_<año>_A<NN>` | Categoría del *OWASP Top 10*, repetida para varias ediciones (2017/2021/2025) porque la numeración cambia entre ediciones. |
| `CWE-<número>` | *Common Weakness Enumeration* — catálogo universal e independiente de herramientas, el identificador más neutral. |
| `POLICY_<nombre>` (sin link) | Interno de ZAP: a qué política de scan predefinida (conjunto de reglas) pertenece esta alerta. |

## Los campos de cada instancia de Alert

| Campo | Qué te dice |
|---|---|
| **Alert description** | Qué es este tipo de problema, en general — no específico a tu request. |
| **Request** | La request HTTP exacta que ZAP mandó y disparó el hallazgo — la receta para reproducirlo a mano. |
| **Response** | La respuesta HTTP exacta que devolvió el target — la prueba de que es real. |
| **Evidence** | Un extracto chico y puntual, sacado de dentro del Request/Response, que es el texto literal que hizo disparar la regla — más acotado que la Response completa. |
| **Parameter** (no siempre aparece) | El nombre del header/campo puntual al que se refiere el hallazgo, cuando aplica (ej. `x-content-type-options`). |
| **Solution** | Recomendación genérica de ZAP para ese tipo de problema — la misma para cualquiera con este alert type, no adaptada al código específico. |

## Ejemplo real de este proyecto

El primer Active Scan real contra este proyecto (ver `docs/security/owasp-zap-baseline.md`) encontró exactamente un hallazgo real (visto por dos reglas distintas de ZAP, mismo incidente): una excepción `IllegalArgumentException` sin capturar (`UUID.fromString` contra un path variable inválido) que se escapaba hasta el manejador default de Spring y devolvía un 500 con el stack trace completo en el body — Risk=Low, Confidence=Medium. El "Insights" del mismo reporte (53% de respuestas 5xx) confirmó que el problema probablemente se repite en varias rutas con `{menuId}`, no solo en la que se vio el detalle. Cierre y fix documentados en `docs/security/threat-model.md`.
