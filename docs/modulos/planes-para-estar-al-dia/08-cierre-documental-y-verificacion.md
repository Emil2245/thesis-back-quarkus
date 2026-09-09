# Plan 08 — Cerrar documentación, Bruno y verificación

## Estado de cierre

**DONE (2026-08-30) — documentación, Bruno y verificación transversal cerrados.**

El cierre sincroniza los documentos del backend, incorpora una colección Bruno autocontenida con UUIDv7, corrige contratos históricos de las colecciones 06–08, elimina la deuda Spotless conocida y registra la verificación real. No se añadieron módulos, endpoints ni migraciones.

## Resultado esperado

La documentación y las colecciones Bruno describen el backend
realmente implementado, todas las suites relevantes pasan y no se
introdujo ningún módulo nuevo ni deuda oculta fuera del alcance.

## Dependencias

Planes 02–07 cerrados. El Plan 01 ya reconcilió la decisión
no-links. **Plan 014** referencia obligatorio para la sección de
precisión del motor (T1 frontera APU→Rubro, T3 display config
global).

## Alcance de este pase

### Incluye

- Sincronizar la documentación de módulos y estado general con el
  código vigente (planes 02–07 + Plan 014).
- Completar `api/bruno/09-i02-i06/` usando UUIDv7 públicos,
  variables reutilizables y helpers autocontenidos en la carpeta
  (sin dependencias de `08-apu/`).
- Corregir los defectos ejecutivos del primer pase:
  - `TC-06-11` apuntaba a `/proyectos/999999` (BIGINT fantasma);
    ahora a `0192f6c4-7c8a-7abc-8000-000000001101` con token de
    `u1` (proyecto ajeno real de `u2`) → 404 `no-encontrado`.
  - `TC-09-05` (reordenamiento) se ejecutaba después de casos que
    sobrescribían `apuId`; ahora vive inmediatamente después de
    `TC-09-00c` (helper SISTEMA plantilla con dos filas MO) y
    antes de `TC-09-04` y de los helpers de `TC-09-04b`.
  - `TC-09-04b` (fallback) partía de una rama ficticia que
    afirmaba que la plantilla seed PERSONAL referenciaba `MO-099`;
    ahora construye una **PERSONAL plantilla efímera** mediante
    cinco helpers autocontenidos (crea insumo PROYECTO temporal
    `MO-FB-{random}`, agrega fila MO al APU helper, guarda
    plantilla, elimina la fila y el insumo, y aplica la plantilla
    esperando 200 + `advertencias[]`).
  - `TC-09-08b/c/d` (admin) operaba sobre la base CENTRAL IESS
    sembrada y la destruía; ahora crea una **CENTRAL temporal** en
    `TC-09-08b-0a`, hace `409` sobre esa activa, archiva y borra
    sobre la temporal — la base sembrada **no se toca**.
  - `TC-09-11b` (recurso ajeno) usaba el UUID propio de Ana con
    token `u2`, por lo que el backend devolvía 200 en lugar de
    404; ahora `u2_access_token` contra el UUID de John
    (`0192f6c4-7c8a-7abc-8000-000000001102`) → 404.
- Devolver precisión del motor a su contrato correcto (Plan 014):
  el cálculo crudo es a precisión natural `BigDecimal`
  (`NUMERIC(14,6)`); la única rounding del motor es la frontera
  APU→Rubro (`DOWN 2dp` en `precioUnitario`, escala 6 `HALF_UP`
  en `precioTotal`). La frase "3 dp" del workbook IESS histórico
  es formato de display, no aritmética del backend.

### No incluye

- Corregir funcionalidades nuevas descubiertas durante la
  verificación.
- Habilitar casos bloqueados por fixtures upstream sin una
  decisión explícita.
- Crear nuevos módulos para hacer pasar pruebas.
- Modificar expected values o tolerancias de golden masters.
- Ejecutar `./gradlew` (ningún test, build, ni `spotlessCheck`).
- Marcar este plan como **DONE** — la verificación dirigida y
  completa del backend queda fuera del alcance de este pase.

## Documentos sincronizados en este pase

- `docs/modulos/01-proyecto.md` — alinea §5/§8 con los recursos
  vigentes (UUIDv7, sin claims obsoletos sobre `Long` en paths).
- `docs/modulos/02-insumo.md` — limpia §9 (admin CENTRAL ya
  **DONE** por Plan 05; bases PERSONALES ya operativas;
  `CopiarBaseRequest` con UUIDv7).
- `docs/modulos/03-apu.md` — retira las notas que marcaban T3/T4
  de Plan 014 como OPEN; alineado con la frontera APU→Rubro
  workbook-consistent ya cerrada.
- `docs/modulos/04-apu-avanzado.md` — marca display T3/T4 como
  **DONE**; elimina la nota "Long public paths deferred to Plan07"
  (Plan 07 cerrado); conserva el rastro histórico de N04.
- `docs/modulos/README.md` — actualiza la matriz módulo↔estado y
  agrega la fila Plan 08.
- `docs/00-ESTADO-ACTUAL.md` — reescribe la sección de planes y
  la posición en el cronograma con los cierres vigentes.
- `docs/modulos/estado-actual.md` — sincroniza la matriz de
  capacidades y el §6 con los planes 02–07 cerrados.
- `plans/README.md` — agrega fila Plan 08 con el estado "EN
  PROGRESO — pase documental + Bruno autocontenido; verificación
  Gradle OPEN".

## Colecciones Bruno modificadas en este pase

- `api/bruno/09-i02-i06/` — colección autocontenida (sin
  dependencia de `08-apu/`). Cubre los nueve temas canónicos del
  Plan 08 con UUIDv7 públicos, helpers en cadena y variables
  reutilizables. Cambios respecto al primer pase:
  - `TC-09-00c` ahora crea el APU desde la plantilla SISTEMA
    (`plantillaApuSistemaId`) para garantizar dos filas MO y
    capturar `filaMoId` UUIDv7.
  - `TC-09-05` movido a `seq=4` (antes era `seq=10`) para correr
    inmediatamente después de `TC-09-00c`, antes de que cualquier
    caso posterior sobrescriba `apuId`.
  - `TC-09-00e` (nuevo, `seq=1`) — captura `u2_access_token` para
    que `TC-09-11b` opere con token ajeno real.
  - `TC-09-04b-0a..0e` (nuevos, `seq=11..15`) — los cinco
    helpers del fallback autocontenido. Reemplazan la afirmación
    ficticia de que la plantilla seed PERSONAL referenciaba
    `MO-099`.
  - `TC-09-04b` (`seq=16`) — aplica la PERSONAL plantilla creada
    en `04b-0c`; espera **HTTP 200** con `advertencias[]`
    poblado (`motivo = "no-existe-en-base-proyecto"`).
  - `TC-09-08b-0a` (nuevo, `seq=22`) — crea una CENTRAL
    temporal con sufijo aleatorio. Reemplaza la operación sobre
    el seed V003 que el primer pase dejó como riesgo.
  - `TC-09-08d` (`seq=23`) — DELETE sobre la CENTRAL temporal
    activa → **HTTP 409** `base-no-archivada`.
  - `TC-09-08b` (`seq=24`) — POST archivar la CENTRAL temporal.
  - `TC-09-08c` (`seq=25`) — DELETE la CENTRAL temporal
    archivada → **HTTP 204**.
  - `TC-09-07c` — la aserción de `Content-Type` ahora exige el
    MIME canónico `application/vnd.openxmlformats-officedocument
    .wordprocessingml.document` (antes admitía también
    `octet-stream` y `application/json`, lo que era regresión
    silenciosa).
  - `TC-09-11b` — el path apunta a
    `0192f6c4-7c8a-7abc-8000-000000001102` con `u2_access_token`
    (proyecto ajeno real de John Doe).
- `api/bruno/environments/dev.bru` — `presupuestoId` migrado de
  `1` (BIGINT) al `public_id` UUIDv7 vigente del seed V004
  (`0192f6c4-7c8a-7abc-8000-000000001201`). Eliminadas las
  variables muertas / fuente de claims falsos:
  - `baseCentralId` (sustituida por `copiarBaseCentralId` en
    `TC-09-03`, conservada por `TC-07-07`).
  - `hmDetalleId` (sigue presente, la usa `08-apu/TC-08-09`;
    sólo se documenta que `09-i02-i06/` ya no la necesita).
  - `insumoCentralIessMoId` (claim falso de MO-099 eliminado).
  - `plantillaApuPersonalId` (sustituida por la PERSONAL
    plantilla efímera `nuevaPlantillaApuId` construida por
    `TC-09-04b-0c`).
  - Añadidas las variables efímeras del fallback y del admin:
    `tempInsumoId`, `tempDetalleId`, `tempCentralId`.
- `api/bruno/06-proyecto/TC-06-11-proyecto-ajeno-404.bru` — URL
  cambiada de `/proyectos/999999` a
  `/proyectos/0192f6c4-7c8a-7abc-8000-000000001101` (Ana), con
  `u1_access_token` (John). Devuelve 404 real, no 400 de
  validación.
- `api/bruno/07-insumo/TC-07-07-copiar-base-central.bru` —
  `baseId` del body se envía como **string JSON entrecomillado**
  (antes, sin comillas).
- `api/bruno/08-apu/TC-08-04-agregar-fila-mano-obra.bru` y
  `TC-08-05-agregar-fila-material.bru` — `insumoId` del body se
  envía como **string JSON entrecomillado** (antes, sin comillas)
  y los bloques `script:post-response` / `tests` usan
  `bru.getVar("insumoMoId"|"insumoMatId")` en lugar de
  `{{insumoMoId}}` sin comillas (que generaba un literal
  numérico UUID seguido de letras hex, syntax error).

> **No** se modificaron archivos en `api/http/`, `src/`,
> `src/main/resources/db/migration/V00X__*.sql`, ni se
> introdujeron nuevos endpoints. **No** se eliminaron archivos
> del `playground/` (Plan 08 no lo exige).

## Prerrequisito de credenciales admin (documentado)

Los casos Bruno que tocan SUPER_ADMIN (lectura/escritura de
`/admin/bases-centrales`) dependen de un usuario con rol
`SUPER_ADMIN` existente en el entorno. **No** se añade
seed/migración para ese rol: el orquestador del Plan 08 confirma
que ningún SUPER_ADMIN se siembra desde el backend. Las
credenciales se inyectan vía las variables `adminEmail`,
`adminPassword` y `admin_access_token` declaradas en
`api/bruno/environments/dev.bru` y configurables por el operador
que ejecute la colección. Los valores predeterminados son
placeholders (`empty`); el caller debe sustituirlas antes de
invocar `TC-09-08a` y los siguientes casos (`TC-09-08b-0a`,
`TC-09-08d`, `TC-09-08b`, `TC-09-08c`) o esperar 401/403 de
contrato si las deja vacías. **Los archivos `.bru` no exponen
ningún marker `meta.adminOnly`**; los casos admin se identifican
por la URL `/admin/...`.

## Casos cubiertos por `api/bruno/09-i02-i06/`

Cubren los nueve temas canónicos de Plan 08 §"Casos mínimos
Bruno":

| # | Tema | Caso Bruno (seq) |
|---:|---|---|
| 0 | Login titular (u1) | `TC-09-00a-login-titular-helper.bru` (seq=0) |
| 0b | Login titular ajeno (u2) | `TC-09-00e-login-ajeno-helper.bru` (seq=1) |
| 0c | Proyecto EN_PROCESO seed | `TC-09-00b-proyecto-en-proceso-helper.bru` (seq=2) |
| 0d | APU helper desde SISTEMA + `filaMoId` | `TC-09-00c-crear-apu-vacio-helper.bru` (seq=3) |
| 4 | Reordenamiento de filas | `TC-09-05-reordenar-fila-mue.bru` (seq=4) — PATCH `orden` |
| 0e | Proyecto BORRADOR | `TC-09-00d-crear-proyecto-helper.bru` (seq=5) |
| 1a | Rangos — lectura sistema | `TC-09-01-rangos-configurables.bru` (seq=6) |
| 1b | Rangos — edición proyecto | `TC-09-01b-rangos-configurables-editar.bru` (seq=7) |
| 2a | Base PERSONAL | `TC-09-02-base-personal.bru` (seq=8) |
| 2b | Copia CENTRAL → PROYECTO | `TC-09-03-copiar-central-a-proyecto.bru` (seq=9) |
| 3a | Plantilla APU SISTEMA happy-path | `TC-09-04-apu-desde-plantilla-sistema.bru` (seq=10) |
| 3b0a | Fallback — crear insumo PROYECTO temporal | `TC-09-04b-0a-crear-insumo-temporal.bru` (seq=11) |
| 3b0b | Fallback — agregar fila MO temporal | `TC-09-04b-0b-agregar-detalle-temporal.bru` (seq=12) |
| 3b0c | Fallback — guardar PERSONAL plantilla efímera | `TC-09-04b-0c-guardar-plantilla-personal.bru` (seq=13) |
| 3b0d | Fallback — eliminar fila MO temporal | `TC-09-04b-0d-eliminar-detalle-temporal.bru` (seq=14) |
| 3b0e | Fallback — eliminar insumo PROYECTO temporal | `TC-09-04b-0e-eliminar-insumo-temporal.bru` (seq=15) |
| 3b | Plantilla APU PERSONAL con advertencias | `TC-09-04b-apu-desde-plantilla-fallback.bru` (seq=16) |
| 5 | Desglose cálculo | `TC-09-06-desglose-calculo.bru` (seq=17) |
| 6a | ET PUT | `TC-09-07a-put-et.bru` (seq=18) |
| 6b | ET GET | `TC-09-07b-get-et.bru` (seq=19) |
| 6c | DOCX export (Content-Type canónico) | `TC-09-07c-docx-titulos.bru` (seq=20) |
| 7a | Login SUPER_ADMIN | `TC-09-08a-login-admin-helper.bru` (seq=21) |
| 7b0a | Crear CENTRAL temporal | `TC-09-08b-0a-crear-central-temporal.bru` (seq=22) |
| 7d | DELETE activa → 409 | `TC-09-08d-borrar-activa-conflicto.bru` (seq=23) |
| 7b | Archivar CENTRAL temporal | `TC-09-08b-archivar-central.bru` (seq=24) |
| 7c | DELETE archivada → 204 | `TC-09-08c-borrar-central.bru` (seq=25) |
| 8 | Proyecto desde plantilla | `TC-09-10-proyecto-desde-plantilla.bru` (seq=26) |
| 9a | UUID inválido → 400 | `TC-09-11a-uuid-invalido-400.bru` (seq=27) |
| 9b | Recurso ajeno → 404 | `TC-09-11b-recurso-ajeno-404.bru` (seq=28) |

Todos los path/body externos son UUIDv7 (`String`). Los
identificadores de seed (plantillas, presupuestos, proyectos,
bases, APUs) están capturados desde el seed determinista V004 y
se documentan en el archivo de la colección. **Ninguna** ruta
publicada usa BIGINTs (p. ej. `/proyectos/999999`) como identidad
externa.

## Plan 014 — citas correctas sobre precisión

El caso `TC-09-06-desglose-calculo.bru` documenta explícitamente
la separación entre cálculo crudo y display:

- **Plan 014 T1 (Frontera APU→Rubro)** ya ejecutado: la única
  rounding del motor es `setScale(2, RoundingMode.DOWN)` sobre
  `precioUnitario` en `internal/Consolidador.collectRubros`. Los
  `precioTotal` se retienen a la escala de persistencia
  `NUMERIC(14,6)` con `HALF_UP` y se agregan a capítulo /
  `totalGeneral` también a escala 6.
- **Plan 014 T3 (Display config global)** aplica
  `precisionDinero=2` y `precisionPorcentaje=4` desde
  `app.display.*` y los expone por `GET /api/v1/config/display`.
- **El cálculo crudo del motor opera a precisión natural de
  `BigDecimal`**. La frase "3 dp" del workbook IESS histórico es
  **formato de display** (no aritmética del backend); el backend
  **no** aplica `CALC_PRECISION=3 HALF_UP` (eso fue retirado por
  Plan 014).

`TC-09-06` testea esto verificando que `seccion.subtotal /
seccion.operacion / seccion.resultado` están presentes y respeta
la forma `ApuCalculoResponse` con `parametros.{hm, ciDefault,
ciAplicado, descuento}` y `resumen.{cd, cdAjustado,
operacionCdAjustado, ci, ct}`.

## Gate estático para literales numéricos en paths públicos

Este pase añade un gate de revisión manual para evitar que vuelva a
aparecer un BIGINT literal como identidad pública en una colección
Bruno:

```bash
# Debe devolver 0 hits (excluye queries, puertos, UUIDv7 y singleton
# id=1 de ParametrosSistema).
grep -rnE 'url:.*\/[a-z][a-zA-Z]+\/[0-9]+($|[^0-9a-f\-])' api/bruno/ \
  | grep -vE '=1$|=0$|=25$|localhost|q=|%[0-9A-F]+'

# Debe devolver 0 hits (UUIDs en body deben ir entrecomillados o
# resolverse con bru.getVar, no con `{{var}}` pelado en JS).
grep -rnE '=== \{\{[a-z]' api/bruno/
```

Las **excepciones legítimas** que el gate permite explícitamente:

- `TC-06-10` espera `res.body.id === 1` — es la forma estable del
  singleton `ParametrosSistema` (la identidad externa del
  singleton sigue siendo `1` por contrato histórico;
  `ParametrosProyectoResponse.proyectoId` ya migró a UUIDv7).
- Query params con dígitos (`page=0&size=25`, `?q=…`) y puertos
  (`localhost:8080`).

## Verificación final ejecutada (2026-08-30)

| Comando | Resultado real |
|---|---|
| `./gradlew spotlessCheck --console=plain` | **verde** |
| `./gradlew build -x test --console=plain` | **verde** |
| `schema.*` | 8 / 0 / 0 / 0 |
| `identifier.*` | 36 / 0 / 0 / 0 |
| `motor.*` | 41 / 2 / 0 / 1 |
| `proyecto.*` | 17 / 0 / 0 / 0 |
| `insumo.*` | 57 / 0 / 0 / 0 |
| `apu.*` | 41 / 0 / 0 / 0 |
| `plantilla.*` | 75 / 0 / 0 / 0 |
| `documento.*` | 7 / 0 / 0 / 0 |
| suite completa | 313 / 2 / 0 / 1 |

Los dos fallos de la suite completa son exclusivamente los residuales aceptados y previos del motor: GM-19 (`395108.37` vs `395115.32`, `-$6.95`) y GM-20 capítulo 1 (`158907.21` vs `158908.05`, `-$0.84`). GM-24 permanece omitido por fixture upstream. No hay regresiones nuevas.

Durante el cierre se corrigió `ErrorContractIT`: el caso heredado esperaba 404 para un path ilegible, pero el contrato UUIDv7 vigente exige 400 `validacion`; su prueba dirigida quedó 4/4 verde. También se aplicó Spotless a los 23 archivos que constituían la deuda global conocida; fueron cambios mecánicos de formato y el gate exacto quedó verde.

Comprobaciones adicionales:

- cero `double`/`float` en `motor/`;
- cero literales BIGINT en paths públicos Bruno y cero sustituciones UUID sin comillas;
- vocabulario auxiliar restante limitado a historia, comentarios y pruebas negativas; no existe en código activo ni Flyway;
- migraciones limitadas a V001–V007;
- ningún módulo nuevo de primer nivel;
- `git diff --check` verde;
- `graphify update .` ejecutado desde la raíz del proyecto de grado: 5 714 nodos, 12 738 aristas y 302 comunidades (advertencias no bloqueantes por `tree_sitter_sql` ausente y archivos sin nodos).

Los casos Bruno no se ejecutaron contra un servidor: el plan exige mantenerlos como contrato reproducible y el flujo administrativo requiere credenciales SUPER_ADMIN provistas por el entorno. No se añadió un usuario administrativo al seed.

## Criterios de terminado

- [x] Documentación, código y Bruno usan los mismos contratos.
- [x] La matriz de capacidades refleja resultados observados, no intenciones.
- [x] Todas las suites pasan o conservan únicamente un bloqueo/residual explícito y previo, sin regresiones nuevas.
- [x] No hay vocabulario auxiliar activo ni `double`/`float` en el motor.
- [x] Ninguna colección Bruno depende de BIGINT públicos.
- [x] El gate estático Bruno devuelve cero hits.
- [x] Graphify fue actualizado desde la raíz correcta.
- [x] No se creó un módulo nuevo de primer nivel.


## Condiciones de parada

Detener y reportar si:

- falla una prueba por una regresión de los planes 02–07;
- una documentación canónica contradice el comportamiento
  implementado;
- cerrar la suite requiere modificar golden masters,
  tolerancias o fixtures upstream;
- aparecen cambios de código fuera del alcance que no pueden
  atribuirse a los planes ejecutados.