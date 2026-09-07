# `api/bruno/` — Colecciones Bruno del backend

Colecciones HTTP ejecutables contra el backend en
`http://localhost:8080/api/v1` con la [CLI de Bruno](https://www.usebruno.com/).
Cada carpeta agrupa los casos de un módulo / iteración; los archivos
compartidos viven en `environments/dev.bru` (variables runtime + credenciales
de los titulares sembrados por V004).

## Estado de las colecciones

| Carpeta | Iteración | Módulo | Estado | Cómo correrla |
|---|---|---|---|---|
| [`01-auth-registro`](01-auth-registro/) | I-01/I-02 | `auth` | legacy | no se mantiene — ver [`04-perfil`](04-perfil/) y helpers de cada plan |
| [`02-auth-login`](02-auth-login/) | I-01/I-02 | `auth` | legacy | no se mantiene — los helpers de cada plan ya hacen login |
| [`03-auth-password-reset`](03-auth-password-reset/) | I-01/I-02 | `auth` | legacy | no se mantiene — los planes I-06 cubren reset |
| [`04-perfil`](04-perfil/) | I-01/I-02 | `auth` | legacy | no se mantiene — los planes I-06 cubren perfil |
| [`05-salud-checks`](05-salud-checks/) | I-01/I-02 | ops | legacy | smoke tests de `/q/health` |
| [`06-proyecto`](06-proyecto/) | I-03 | `proyecto` | activa | Plan 009 + Plan 07 — flujo CRUD de proyectos |
| [`07-insumo`](07-insumo/) | I-04 | `insumo` | activa | Plan 009 — CRUD + bases + CSV |
| [`08-apu`](08-apu/) | I-05 | `apu` | activa | Plan 011 — CRUD + filas M/N/O/P + HM |
| [`09-i02-i06`](09-i02-i06/) | I-06 | `apu`/`insumo`/`plantilla`/`documento` | activa | Plan 08 — nueve temas canónicos I-02…I-06 |
| **`10-presupuesto`** | **I-07** | **`presupuesto`** | **activa · verificada (Plan 025 — DONE 2026-09-01, 23/23 requests verde dinámico contra PostgreSQL 18 limpio + fast-jar)** | Esta carpeta: P-28/P-29/P-30/P-31/P-32 + validación |
| [`11-cronograma`](11-cronograma/) | I-10 | `cronograma` | activa (Plan 031 — export XLSX/PDF/MSPDI, P-37) | Esta carpeta: preflight + descarga por formato + UUIDv7 + owner-to-404 |

> **Nota sobre los archivos legacy 01–05:** no se borran para conservar
> auditoría; los planes I-06+I-07 (`06-proyecto/`, `07-insumo/`, `08-apu/`,
> `09-i02-i06/`, `10-presupuesto/`) son los que cubren los flujos vigentes.
> Los archivos `api/http/` (HTTP client JetBrains) son preexistencias
> externas al Plan 025 y no se reportan como parte de su evidencia de
> cierre; Bruno es la única herramienta vigente del backend.

## Cómo correr una colección

1. Levantar el backend:
   ```bash
   ./gradlew --console=plain quarkusDev
   ```
   Esperar a que arranque (mensaje `Listening on: http://localhost:8080`).
2. Abrir Bruno CLI apuntando a esta carpeta (`api/bruno/`) y seleccionar
   el environment `dev` (cargado desde `environments/dev.bru`).
3. Ejecutar los casos en **orden secuencial** — los casos `00a…00e` son
   *helpers* que capturan tokens/UUIDv7 en variables runtime que los casos
   posteriores consumen.

## `10-presupuesto/` — Plan 025 / I-07

Cubre los procesos canónicos **P-28 (capítulos), P-29 (rubros), P-30
(resumen), P-31 (versiones) y P-32 (validación de integridad)** del módulo
`presupuesto` contra una base Postgres local sembrada por Flyway
(`V001…V004`) y refrescada entre corridas.

### Orden de ejecución

```
TC-10-00a  Login titular John Doe                      (helper)
TC-10-00b  Login titular ajeno Ana de Armas            (helper)
TC-10-00c  Crear proyecto propio BORRADOR              (helper)
TC-10-00d  Capturar presupuesto v1 vigente auto-creado (helper)
TC-10-00e  Crear APU vacío sin plantilla               (helper)
─────────
TC-10-01   Leer árbol completo P-28/P-29 (read model)
TC-10-02a  Crear capítulo raíz P-28
TC-10-02b  Crear subcapítulo bajo el raíz
TC-10-02c  Mover subcapítulo a raíz (renumeración atómica)
TC-10-02d  Eliminar subcapítulo (cascade)
TC-10-03   Crear rubro P-29 (1:1 con APU)
TC-10-04   Intentar duplicar APU en otro rubro → 409 D-09
TC-10-05   Editar cantidad del rubro P-29
TC-10-06   Resumen por componente P-30
TC-10-07   Crear versión v2 vía deep copy P-31
TC-10-08   Marcar v2 como vigente P-31
TC-10-09   Comparar v1 vs v2 P-31 (lado a lado)
TC-10-10   Eliminar v1 (no vigente) → 204
TC-10-11   Intentar eliminar v2 (vigente) → 409
─────────
TC-10-12   Validación P-32 sobre v2 (PU=0 + sin actividad)
TC-10-13   UUIDv4 → 400 validacion
TC-10-14   UUIDv7 inexistente → 404
TC-10-15   Caller ajeno → 404 (RNF-05)
```

> **Conteo total: 23 requests** (5 helpers + 18 casos temáticos). El
> número es mayor que el "~6–8" del plan original porque HTTP no se
> puede comprimir sin saltarse contratos — cada mutación verifica su
> respuesta independientemente (P-28 se desglosa en 4 sub-casos:
> crear raíz / crear subcapítulo / mover / eliminar). La secuencia
> total tarda ~10–15 s contra el backend local.

### Pre-requisitos

- `john.doe@uce.edu.ec` y `ana.armas@gmail.com` deben existir en la BD
  con la password `Clave1234` (semilla V004). Si no, `00a`/`00b`
  devolverán 401 y los casos autenticados fallarán en cascada.
- Postgres limpio o cualquier BD con `V001…V004` aplicados (Flyway corre
  al arrancar Quarkus).
- **BD limpia (desechable) por corrida.** Los nombres y códigos de la
  colección son **deterministas** (`Plan025 demo`, `P-2026-P25`,
  `APU-P25-VACIO-01`): no hay sufijos aleatorios. Sobre una BD reutilizada,
  un segundo pase puede colisionar en `codigo`; el rerun limpio
  (reiniciar Postgres / Dev Services fresco) es un pre-requisito, no una
  propiedad de los casos.
- `environments/dev.bru` debe permanecer **sin comentarios**: el parser de
  environments de Bruno CLI 4.1 los rechaza. Documentar el propósito de
  cada variable en este README, no en el `.bru`.

### Variables runtime capturadas

Las variables `p25*` se declaran en `environments/dev.bru` (no en
`10-presupuesto/environments/` — Bruno carga el environment
compartido) y se capturan en runtime por los helpers:

| Variable | La captura | La consume |
|---|---|---|
| `p25ProyectoId` | TC-10-00c | TC-10-00d, TC-10-07 |
| `p25PresupuestoId` | TC-10-00d | TC-10-01, 02a, 02b, 02c, 02d, 03, 04, 05, 06, 07, 09, 10 |
| `p25CapituloRaizId` | TC-10-02a | TC-10-02c, 03, 04, 05 |
| `p25SubcapituloId` | TC-10-02b | TC-10-02c, 02d |
| `p25ApuVacioId` | TC-10-00e | TC-10-03, 04 |
| `p25RubroId` | TC-10-03 | TC-10-05 (v1 only; v2 tiene UUID fresco tras deep copy, ver TC-10-12 docs) |
| `p25V2Id` | TC-10-07 | TC-10-08, 09, 11, 12, 15 |
| `p25InexistenteV7` | fija en `dev.bru` | TC-10-14 |

> **Cómo se assertan las capturas:** Bruno interpola las plantillas
> `{{var}}` **antes** de ejecutar `script:post-response`, así que el
> propio request que captura una variable no puede verificarla vía
> `expect("{{var}}")` (vería el valor previo, p. ej. `empty`). Cada
> request que captura assertea contra `res.body` (o recomputa el valor
> desde el árbol de la respuesta) y deja `bru.setVar` sólo para
> propagar el valor a los requests posteriores; ahí sí `{{var}}` ya
> está resuelto.

### Rerun y limpieza

- La colección es **autocontenida**: crea su propio proyecto + árbol +
  versiones, sin depender de los proyectos de la semilla V004. No es
  **idempotente**: con valores deterministas, repetirla contra la misma
  BD reutiliza códigos ya insertados. Cada corrida parte de una BD
  limpia/desechable.
- **No hay limpieza explícita**: las versiones, capítulos, rubros y
  APUs creados viven hasta que se borre el proyecto. Si la BD es
  compartida con otras colecciones (`06`, `09`), esto contamina
  `GET /proyectos?q=...`. Para un rerun limpio, **reiniciar Postgres**
  o apuntar a un Dev Services fresco.
- TC-10-10 elimina la versión 1; TC-10-11 confirma que v2 sigue
  vigente. Tras toda la colección quedan: 1 proyecto + 1 presupuesto
  v2 vigente + 1 capítulo + 1 rubro + 1 APU + 0 cronograma.

### Limitaciones honestas

- TC-10-12 verifica PU=0 y sin actividad por las APIs públicas;
  `itemsCantidadCero` **no** se cubre por API porque REST P-29
  prohíbe `cantidad ≤ 0` en creación. La cobertura del caso canónico
  `cantidad=0` vive en el IT `PresupuestoValidacionResourceIT` (que
  siembra por SQL nativo). Esta separación se documenta en el doc
  `docs/modulos/05-presupuesto/07-validacion-y-cierre.md`.
- Los casos negativos (TC-10-13/14/15) usan UUIDs estáticos o
  capturados — no requieren mutación previa.
- TC-10-15 (caller ajeno) usa `p25V2Id` (presupuesto vigente de
  John); Ana no es owner → 404. Esta es la asimetría correcta con
  `09-i02-i06/TC-09-11b-recurso-ajeno-404.bru`.

### Referencias

- [`docs/modulos/05-presupuesto/07-validacion-y-cierre.md`](../../docs/modulos/05-presupuesto/07-validacion-y-cierre.md)
  — Plan 025 canónico (estado DONE 2026-09-01; única línea pendiente
      `graphify update .` final queda **DONE — 2.972 nodos / 9.064 aristas / 141 comunidades**).
- [`docs/modulos/05-presupuesto/`](../../docs/modulos/05-presupuesto/)
  — Índice del módulo.
- [`plans/README.md`](../../plans/README.md) — estado por plan.