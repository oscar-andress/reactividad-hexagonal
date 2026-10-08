# Estrategias de merge en GitHub: merge commit, squash y rebase

Este doc no es específico de este proyecto — es conocimiento general de Git/GitHub que surgió revisando por qué un PR mostraba solo "Squash and merge" como botón. Sigue la misma estructura que el resto de `docs/`.

## Las tres estrategias

| Estrategia | Qué hace | Cuándo conviene |
|---|---|---|
| **Merge commit** | Crea un commit nuevo que une las dos historias, conservando cada commit individual de la rama | Cuando cada commit de la rama cuenta una parte útil de la historia por separado |
| **Squash and merge** | Junta *todos* los commits de la rama en uno solo sobre la rama destino | Cuando la rama tiene commits de "wip", "fix typo", etc. que no vale la pena conservar por separado |
| **Rebase and merge** | Reaplica cada commit de la rama sobre la rama destino, uno por uno, sin ningún commit de merge | Cuando querés historia lineal *y* conservar cada commit individual |

Ninguna es "la correcta" de forma universal — depende de si te importa conservar la granularidad de commits intermedios de una rama de feature, o si preferís que `main` tenga un commit limpio por feature.

## Por qué un PR puede mostrar solo una opción como botón principal

Encontramos esto en este mismo proyecto: un PR mostraba "Squash and merge" como único botón visible, cuando antes parecía haber solo "Merge". Se verificó (no se asumió) con la API de GitHub:

```bash
gh repo view <owner>/<repo> --json mergeCommitAllowed,squashMergeAllowed,rebaseMergeAllowed
gh api repos/<owner>/<repo>/branches/main/protection
```

Resultado real: las tres estrategias seguían habilitadas a nivel de repo, y `main` no tenía ninguna regla de protección que forzara una en particular. La explicación correcta: GitHub recuerda la **última estrategia usada** en el repo y la muestra como botón principal — en este caso, los PRs anteriores se habían mergeado con squash (vía `gh pr merge --squash`), así que GitHub mostró squash por defecto la vez siguiente. Las otras dos opciones no desaparecieron: siguen disponibles en el dropdown (▾) al lado del botón verde.

**La lección general:** si el botón de merge de un PR parece distinto a lo que recordabas, no asumas que cambió una configuración — revisá primero los settings reales del repo y la protección de la rama antes de concluir que algo se desactivó.
