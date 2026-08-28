# 014 — Motor de cálculo: precisión natural, no-links, display global

- **Status (2026-08-28, cierre parcial USER-DECIDED):**
  **OPEN / READY FOR REMAINING IMPLEMENTATION** — T1 (workbook-consistent),
  fixtures ejecutados; T2 (no-links estructural), T3
  (display config global + endpoint), T4 (`@Digits`) y borrado de DIAG
  siguen **OPEN**. La política de redondeo del motor queda cerrada con
  residual aceptado (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`).
  **T1 EXECUTED (verificación):** `internal/Consolidador.java` aplica
  `precioUnitario DOWN 2dp` + `precioTotal = cantidad × PU_2dp` retenido
  a escala 6 `HALF_UP` + agregación consistente de capítulo y `totalGeneral`
  desde esos `precioTotal` a escala 6. `ConsolidadorFronteraTest` 5/5
  verde vía `Motor.consolidar(VersionSnapshot)`. `MotorApuTest` 21/21
  verde; `MotorPropiedadesTest` 5/5 verde. GM-21 conserva el allowlist
  preexistente de 11 entradas ≤ 0.03 a nivel PU; no se reaudita en este
  cierre parcial.
  **Residual aceptado (cierre parcial user-decided):** el residual
  sub-céntimo de GM-19/GM-20 se atribuye a artefactos de redondeo manual
  del example workbook IESS; **no** se reabre el motor; workbook, golden
  expected values, tolerancias y fórmulas del motor quedan cerradas. Ver
  [Plan 02](../docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)
  §6 y [Plan 006](./006-motor-consolidacion-fix.md) §1.1 / Done criteria.
  **Re-scope de trabajo restante:**
  - **T2** — no-links estructural: quitar `esAuxiliar`/`cdAuxiliar`/
    `porcentajeIndirectoApu`; ajustar call sites; edición estructural
    mínima (sin cambio aritmético) en `Motor.java` y `CalculadorFila.java`.
  - **T3** — display config global: crear `common/config/DisplayConfig`,
    `DisplayConfigResponse`, `DisplayConfigResource` (`GET
    /api/v1/config/display`, `@PermitAll`); añadir `app.display.*` a
    `application.yml`; documentar env vars en `.env.example`.
  - **T4** — `@Digits(integer=8, fraction=2)` en los 3 campos del catálogo
    cerrado que existen en `HEAD`.
  - **DIAG** — borrar `MotorConsolidacionTest.DIAG_rubro_expected_vs_actual`.
  - **GM-21** — limpieza/auditoría **OPEN**; el cierre parcial conserva
    las 11 excepciones existentes y evita una auditoría exhaustiva del
    único workbook de ejemplo.
  - **GM-24** — sigue `@Disabled` por rotura upstream del fixture EMELNORTE.
  **No** se considera "implementado" nada que no sea T1. Los criterios
  done que exigían GM-19/20 verdes o cierre completo de Plan 02 **se
  retiran** — Plan 02 cierra con residual aceptado (no green).
  **Preferencia del usuario (registrada para auditoría):** el workbook
  IESS es **un example workbook único**; no se realizan auditorías
  exhaustivas per-rubro para corregir cada delta sub-céntimo del
  workbook.
- **Iteration:** I-02 (cierre del motor) + I-06 (no-links)
- **Depends on:** ninguno (la línea base del motor está commiteada y probada).
- **Blocks:** el cierre del hito I-02 «GM api verdes» una vez implementado; el
  hito I-06 «documentación alineada con la decisión no-links».
- **CLAUDE.md override (autorización del autor, 2026-08-28):** la regla
  "Do not touch `Motor.java` or `internal/CalculadorFila.java` until the
  director decides" se levanta **de forma acotada y solo para este plan**:
  se autorizan **ediciones estructurales mínimas** en `Motor.java` y en
  `internal/CalculadorFila.java` **exclusivamente** para eliminar las ramas
  obsoletas `esAuxiliar`/`cdAuxiliar` y para consumir
  `ApuSnapshot.porcentajeIndirecto`. **No** se autoriza ningún cambio
  aritmético: fórmulas, `MathContext`, orden de operaciones y precisión
  natural de `BigDecimal` quedan semánticamente idénticos.

---

## Quick path (TL;DR para el ejecutor)

> **Estado 2026-08-28 (cierre parcial user-decided).** T1 EXECUTED; T2/T3/T4 OPEN.

### Trabajo restante (T2, T3, T4) — sin scope creep

1. **RED + GREEN T2** — no-links estructural (sin cambio aritmético):
   `SnapshotSinAuxiliaresTest` (reflexión sobre `ApuSnapshot`/`ApuCalculado`
   sin `esAuxiliar`, `FilaSnapshot` sin `cdAuxiliar`, `ParametrosCalculo`
   sin `porcentajeIndirectoApu`, `ApuSnapshot.porcentajeIndirecto`
   nullable); quitar las ramas obsoletas en `Motor.java` y
   `CalculadorFila.java`; ajustar call sites. **Aritmética intacta.**
2. **RED + GREEN T3** — display config global:
   `DisplayConfigResourceTest` (defaults 2/4) +
   `DisplayConfigResourceOverrideTest` (override por `QuarkusTestProfile`);
   crear `common/config/DisplayConfig` (`@ConfigMapping(prefix="app.display")`),
   `DisplayConfigResponse`, `DisplayConfigResource` (`@Path("/config/display")`,
   `@PermitAll`); añadir bloque `app.display.*` a `application.yml`;
   documentar `DISPLAY_PRECISION` / `DISPLAY_PRECISION_PORCENTAJE` en
   `.env.example`.
3. **RED + GREEN T4** — `@Digits` catálogo cerrado:
   `DigitsValidationCatalogTest` enumera los 3 campos existentes en `HEAD`
   (`ApuDetallePatchRequest.precioOverride`, `InsumoCrearRequest.precioUnitario`,
   `InsumoEditarRequest.precioUnitario`) con `@Digits(integer=8, fraction=2)`;
   verifica que ningún otro DTO/campo lo lleva.
4. **Borrar DIAG** — eliminar
   `MotorConsolidacionTest.DIAG_rubro_expected_vs_actual`.

### T1 EXECUTED (verificación — cierre parcial)

- `ConsolidadorFronteraTest` 5/5 verde vía `Motor.consolidar(VersionSnapshot)`.
- `MotorApuTest` 21/21 verde; `MotorPropiedadesTest` 5/5 verde.
- GM-21 verde con el allowlist **preexistente** de 11 entradas ≤ 0.03 a
  nivel PU; la limpieza sigue OPEN y no se ejecuta una auditoría exhaustiva
  per-rubro sobre el único workbook de ejemplo.
- **GM-19 / GM-20 rojos con residual aceptado** (GM-19 `-$6.95`;
  GM-20 cap. 1 `-$0.84`) — **no** se reabre el motor; Plan 02 cierra
  como PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL. El workbook IESS
  es **un example workbook único**; no se realizan auditorías exhaustivas
  per-rubro.

> **TDD estricto activo en T2/T3/T4.** Cada test rojo debe observarse
> fallar antes del cambio de implementación; cada test verde debe
> observarse pasar. No se afirma evidencia RED/GREEN que no se haya
> observado.

---

## Context / evidence (línea base)

Comando baseline registrado:

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
```

Resultado baseline (verificado):

| Suite | Tests | Rojos | Skipped | Comentario |
|---|---|---|---|---|
| `MotorApuTest` (GM-01…18, GM-22, GM-23, GM-25) | 21 | 0 | 0 | ✅ verde — per-APU math correcta |
| `MotorConsolidacionTest` (GM-19, GM-20, GM-21, GM-24, DIAG) | 5 | 2 (GM-19, GM-20) | 2 (GM-24 `@Disabled`, DIAG `@Disabled`) | 🔴 |
| `MotorPropiedadesTest` (jqwik) | 5 | 0 | 0 | ✅ verde |
| **Total motor** | **31** | **2** | **2** | ver conteo de GMs abajo |

**Conteo de Golden Masters en la línea base (exacto, no redondear el
enunciado):**

| Métrica | Valor |
|---|---|
| GMs **habilitados** (no `@Disabled`) | **24** — 21 per-APU (GM-01…18, GM-22, GM-23, GM-25) + GM-19 + GM-20 + GM-21 |
| GMs habilitados **verdes** | **22** — los 21 per-APU + GM-21 (con allowlist de 11 entradas) |
| GMs habilitados **rojos** | **2** — GM-19 y GM-20 |
| GMs `@Disabled` | **1** — GM-24 (fixture EMELNORTE upstream) |
| Tests no-GM | 5 propiedades jqwik verdes + 1 DIAG `@Disabled` |

> ⚠️ **No escribir "24 GMs habilitados verdes".** En la línea base hay
> **24 habilitados**, de los cuales **22 verdes y 2 rojos**. Cualquier doc
> que afirme 24 verdes pre-implementación es incorrecto.

**Conclusión de la línea base:** la aritmética per-APU es correcta a
precisión natural de `BigDecimal` (los 21 GMs per-APU verdes y las 5
propiedades verdes lo demuestran). Las dos fallas GM-19/20 son del
redondeo en la frontera APU→Rubro, **no** del cálculo interno del APU.

**Decisión del autor (2026-08-28, supersede N04/N04-bis):**

1. **El workbook IESS no aplica redondeo intermedio al APU.** Las 3 dp
   visibles son **formato de display**, no cómputo. Los 21 GMs per-APU
   verdes a `BigDecimal` natural prueban que la aritmética del motor ya
   es correcta.
2. **`CALC_PRECISION=3` con `HALF_UP` por operación queda RETIRADO** del
   plan activo. No se introduce redondeo intermedio en el motor.
3. La **única** rounding dentro del motor es la frontera APU→Rubro en
   `internal/Consolidador.java`. Regla workbook-consistent (corrección
   2026-08-28, opción B del autor — la versión previa de este plan con
   `setScale(2, DOWN)` simétrico en PU y PT quedaba retirada por
   deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs
   workbook IESS, documentados en STOP conditions más abajo):
   - `Rubro.precioUnitario = APU.costoTotal.setScale(2, RoundingMode.DOWN)`
     (única aplicación de `DOWN`; reproduce el workbook IESS que tipea
     precio unitario a 2 dp).
   - `Rubro.precioTotal = cantidad × Rubro.precioUnitario`,
     retenido a la escala de persistencia 6 (`NUMERIC(14,6)`) con
     `HALF_UP` aplicado **únicamente** en esa frontera de resultado.
     **No** se trunca cada `precioTotal` a 2 dp.
   - Totales de capítulo y `totalGeneral` agregan esos `precioTotal`
     a escala 6; la presentación/assertion canónica es a 2 dp
     (`HALF_UP` solo en display/assertion). Preserva `GM19 = 395115.32`
     y los totales de los 7 capítulos raíz de GM20.
4. **`Motor.java` y `internal/CalculadorFila.java` se tocan solo de forma
   estructural mínima** (borrar ramas `esAuxiliar`/`cdAuxiliar`, leer el
   `%CI` por APU desde `ApuSnapshot.porcentajeIndirecto`). **Aritmética
   sin cambios.**
5. `ApuSnapshot` pierde `esAuxiliar` y gana `porcentajeIndirecto`
   (`BigDecimal` nullable; null = hereda el default del proyecto).
   `ParametrosCalculo` retiene solo el default del proyecto.
6. **`FilaSnapshot` se queda sin `cdAuxiliar`** y `ApuCalculado` sin
   `esAuxiliar`.
7. Display config se vuelve global: dinero 2 dp, porcentaje 4 dp, con
   override vía `application.yml`/env bajo `app.display.*`. Endpoint
   público `GET /api/v1/config/display`. El fallback frontend-side queda
   **fuera del alcance** de este plan (backend-only).
8. `@Digits` solo en entradas monetarias del usuario que existan como
   campo de DTO. Nunca en cantidades, rendimientos, porcentajes ni
   resultados calculados, ni en campos de entidad.
9. GM-19/GM-20 siguen RED preexistentes al inicio de este plan; pasan a
   GREEN tras el cambio en `Consolidador` (T1). GM-24 sigue
   `@Disabled` por la rotura upstream del fixture EMELNORTE. DIAG
   (`DIAG_rubro_expected_vs_actual`) se borra (cumplió su propósito
   diagnóstico). GM-21: auditar y borrar entradas del allowlist donde
   la igualdad ya sea exacta.
10. Plan 006 cleanup: `./mvnw` → `./gradlew`; `HALF_DOWN` → `RoundingMode.DOWN`.

---

## In scope (archivos exactos)

> Solo Markdown y código de prueba/producción del scope. Ningún archivo
> fuera de esta lista. Ningún cambio de schema (`V00X__*.sql`) ni de
> dependencias (`build.gradle.kts`).

### Producción — motor (6 archivos en `src/main/java/ec/uce/propuestas/motor/`)

| Archivo | Cambio autorizado |
|---|---|
| `ApuSnapshot.java` | Quitar `boolean esAuxiliar`. Añadir `BigDecimal porcentajeIndirecto` **nullable** (override semántico por APU; null = hereda el default del proyecto). |
| `ApuCalculado.java` | Quitar `boolean esAuxiliar`. Sin otros cambios de campos. |
| `FilaSnapshot.java` | Quitar `BigDecimal cdAuxiliar` (y su línea de javadoc). Sin otras ramas. |
| `ParametrosCalculo.java` | Quitar `BigDecimal porcentajeIndirectoApu`. **Retener** `porcentajeIndirectoDefault` (default del proyecto), `porcentajeHerramientaMenor` y `porcentajeDescuento`. |
| `Motor.java` | **Solo edición estructural mínima** (ver contrato abajo). |
| `internal/Consolidador.java` | Única rounding del motor + agregación consistente de totales (ver contrato abajo). |

#### Contrato exacto de `Motor.java` (edición estructural mínima)

Autorizado **únicamente**:

1. Sustituir la lectura del override de `%CI`: donde hoy dice
   `if (p.porcentajeIndirectoApu() != null) pctCi = p.porcentajeIndirectoApu();`
   pasa a leerse desde `in.porcentajeIndirecto()`. El resto de la cascada
   (`porcentajeIndirectoDefault` → `BigDecimal.ZERO`) **no cambia**.
2. Eliminar la rama `if (in.esAuxiliar()) { costoIndirecto = ZERO; } else { … }`
   y dejar únicamente `costoIndirecto = costoDirectoAjustado.multiply(pctCi, MC);`.
3. Eliminar el argumento `in.esAuxiliar()` de la llamada
   `new ApuCalculado(...)`.

**Prohibido en `Motor.java`:** cambiar `static final MathContext MC =
new MathContext(20, RoundingMode.HALF_UP)`; cambiar cualquier fórmula
(`subtotalN/M/O/P`, `costoHmExact`, `costoDirecto`, `uno`,
`costoDirectoAjustado`, `costoTotal`); cambiar el orden de operaciones
(N → M → O → P → CD → CD_ajustado → CI → CT); añadir cualquier
`setScale`, `round`, `divide` con escala, `double` o `float`; cambiar el
orden de presentación de las filas.

#### Contrato exacto de `internal/CalculadorFila.java` (edición estructural mínima)

Autorizado **únicamente**: en `calcularMaterial`, sustituir
`BigDecimal precio = f.cdAuxiliar() != null ? f.cdAuxiliar() : effectivePrice(f);`
por `BigDecimal precio = effectivePrice(f);`, y actualizar la línea de
javadoc que menciona `cdAuxiliar`.

**Prohibido en `CalculadorFila.java`:** tocar `calcularEquipo`,
`calcularManoObra`, `calcularTransporte`, `effectivePrice`, el
`MathContext MC`, la constante `SCALE`, o cualquier `setScale` existente.

#### Contrato exacto de `internal/Consolidador.java` (workbook-consistent 2026-08-28)

1. Frontera APU→Rubro en `collectRubros`:
   - `precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN)`
     (única aplicación de `RoundingMode.DOWN` de la frontera; reproduce el
     precio unitario tipeado a 2 dp del workbook IESS).
   - `precioTotal = r.cantidad().multiply(precioUnitario, MC).setScale(6, RoundingMode.HALF_UP)`
     (**escala de persistencia `NUMERIC(14,6)`**; `HALF_UP` **únicamente**
     en esta frontera de resultado, **sin truncar a 2 dp** cada
     `precioTotal` — el workbook IESS multiplica `cantidad × PU_2dp` a
     precisión completa antes de redondear la presentación).
2. **Consistencia de agregación workbook-consistent:** `computeCapituloTotal`
   debe agregar **los mismos `precioTotal` a escala 6 `HALF_UP`** que
   produce la frontera, de modo que
   `totalGeneral = Σ precioTotal (escala 6) = Σ capituloTotal (escala 6)`.
   La presentación canónica vs workbook se hace a 2 dp (`setScale(2,
   HALF_UP)` en el punto de display/assertion), **no** en cada rubro.
   Esto preserva `GM19 = 395115.32` y los totales de los 7 capítulos
   raíz de GM20 (verificación a 2 dp).
3. No se añade ninguna otra rounding en `motor/`. La fórmula `precioTotal
   = cantidad × PU_2dp` ya estaba a `setScale(6, HALF_UP)` en el código
   anterior al `DOWN`2dp equivocado; el cambio es **retirar el segundo
   `setScale(2, DOWN)`** y dejar el resultado a 6 dp `HALF_UP`.

### Producción — call sites obligatorios para compilar

| Archivo | Cambio |
|---|---|
| `apu/service/ApuCalculoService.java` | Ajustar los 2 `new ApuSnapshot(codigo, false, filas)` → `new ApuSnapshot(codigo, apu.porcentajeIndirecto, filas)`; los 2 `new ParametrosCalculo(...)` pierden el argumento `apu.porcentajeIndirecto`; los 2 `new FilaSnapshot(...)` pierden el último argumento (`cdAuxiliar`). Sin cambio de comportamiento observable. |

### Producción — config/display (3 archivos nuevos, fuera de `motor/`)

> **Garantía de motor puro:** `DisplayConfig`, `DisplayConfigResponse` y
> `DisplayConfigResource` viven en `common/config/`. **Nada** de
> configuración, CDI o REST entra en `motor/`.

| Archivo | Cambio autorizado |
|---|---|
| `common/config/DisplayConfig.java` *(nuevo)* | `@ConfigMapping(prefix = "app.display")` con `int precision()` (← `app.display.precision`) y `int precisionPorcentaje()` (← `app.display.precision-porcentaje`). Nombre **`DisplayConfig`**, no `PrecisionConfig`. |
| `common/config/DisplayConfigResponse.java` *(nuevo)* | `record DisplayConfigResponse(int precisionDinero, int precisionPorcentaje)` — contrato JSON público. |
| `common/config/DisplayConfigResource.java` *(nuevo)* | JAX-RS `@Path("/config/display")` + `@PermitAll`, `GET` → `DisplayConfigResponse`. Ruta efectiva `GET /api/v1/config/display` (el prefijo `/api/v1` lo aporta `common/RestApplication.java`; **no** hardcodearlo). |

> **Mapeo nombre-config ↔ nombre-JSON:** la clave de configuración es
> `app.display.precision`; el campo JSON público es `precisionDinero`. El
> resource traduce `DisplayConfig.precision()` → `precisionDinero` y
> `DisplayConfig.precisionPorcentaje()` → `precisionPorcentaje`.

### DTOs de entrada con `@Digits` (catálogo cerrado)

| Archivo | Campo | Estado en `HEAD` |
|---|---|---|
| `apu/dto/ApuDetallePatchRequest.java` | `precioOverride` (`JsonNullable<BigDecimal>`) | ✅ existe |
| `insumo/dto/InsumoCrearRequest.java` | `precioUnitario` | ✅ existe (`@NotNull @DecimalMin("0.000001")`) |
| `insumo/dto/InsumoEditarRequest.java` | `precioUnitario` | ✅ existe (`@NotNull @DecimalMin("0.000001")`) |

**Anotación exacta:** `@Digits(integer = 8, fraction = 2)`.

> El default sugerido era `integer=12`, pero la restricción numérica
> existente lo reduce: las columnas monetarias son `NUMERIC(14,6)`
> (`V001`, líneas 174 / 262 / 266 / 282), es decir **8 dígitos enteros**
> como máximo. Usar `integer=12` aceptaría valores que la BD rechazaría.
> Se aplica la excepción explícita del enunciado ("unless existing
> numeric constraints require smaller integer").

> **Alcance cerrado:** ningún otro DTO recibe `@Digits`. Cantidades,
> rendimientos, porcentajes (`porcentajeIndirecto`, `porcentajeDescuento`,
> `porcentajeHerramientaMenor`) y resultados calculados NO llevan
> `@Digits`. **`tarifaJornal` y `precioUnitarioTarifa` NO se anotan:** son
> campos de la entidad `apu/entity/ApuDetalle.java`, no DTOs de entrada;
> llegan a la API como `ApuDetallePatchRequest.precioOverride`.

### Config (`application.yml` y `.env.example`)

Bloque nuevo bajo `app:` en `src/main/resources/application.yml` — claves
exactas:

```yaml
app:
  display:
    precision: ${DISPLAY_PRECISION:2}
    precision-porcentaje: ${DISPLAY_PRECISION_PORCENTAJE:4}
```

Añadir a `.env.example` (documentación de las dos variables, con sus
defaults):

```dotenv
DISPLAY_PRECISION=2
DISPLAY_PRECISION_PORCENTAJE=4
```

> No se toca ninguna otra clave de `application.yml` ni `.env.example`.
> No se crea `application-test.yml` (los overrides de test van por
> `QuarkusTestProfile`, ver T3).

### Pruebas (5 archivos nuevos + 4 archivos de test modificados)

| Archivo | Propósito |
|---|---|
| `src/test/java/ec/uce/propuestas/motor/ConsolidadorFronteraTest.java` *(nuevo)* | T1 — frontera APU→Rubro `DOWN` 2 dp **a través de `Motor.consolidar(VersionSnapshot)`**. Prohibido invocar `internal/Consolidador` directamente. |
| `src/test/java/ec/uce/propuestas/motor/SnapshotSinAuxiliaresTest.java` *(nuevo)* | T2 — reflexión sobre los records públicos: sin `esAuxiliar`/`cdAuxiliar`/`porcentajeIndirectoApu`; con `ApuSnapshot.porcentajeIndirecto`. |
| `src/test/java/ec/uce/propuestas/common/config/DisplayConfigResourceTest.java` *(nuevo)* | T3 — endpoint con defaults 2/4. |
| `src/test/java/ec/uce/propuestas/common/config/DisplayConfigResourceOverrideTest.java` *(nuevo)* | T3 — endpoint con `QuarkusTestProfile` que sobreescribe a 3/6. |
| `src/test/java/ec/uce/propuestas/apu/dto/DigitsValidationCatalogTest.java` *(nuevo)* | T4 — enumera el catálogo cerrado de `@Digits` y verifica que ningún otro DTO/campo lo lleva. |
| `src/test/java/ec/uce/propuestas/motor/MotorConsolidacionTest.java` *(modificado)* | Auditoría GM-21 (borrar entradas con delta exacto); **GM-19 y GM-20 mantienen residual sub-céntimo aceptado (no se exige verde)**; borrar `DIAG_rubro_expected_vs_actual`, mantener GM-24 `@Disabled`. |
| `src/test/java/ec/uce/propuestas/motor/Fixtures.java` *(modificado)* | Ajuste de constructores para compilar (quitar `esAuxiliar`/`cdAuxiliar`; `apuStub(...)` pasa `porcentajeIndirecto = BigDecimal.ZERO` para conservar CI = 0 del stub). **No** tocar valores esperados de los GMs. |
| `src/test/java/ec/uce/propuestas/motor/MotorApuTest.java` *(modificado)* | Solo ajuste de constructores `new FilaSnapshot(...)` / `new ApuSnapshot(...)` / `new ParametrosCalculo(...)`. Assertions intactas. |
| `src/test/java/ec/uce/propuestas/motor/MotorPropiedadesTest.java` *(modificado)* | Ajuste de constructores y renombrado semántico del generador/property de "auxiliar" a "override de CI cero". La aserción matemática permanece intacta. |

> **Convenciones de test obligatorias:** JUnit 5 (`assertEquals`,
> `assertThrows`, `assertTrue`) + RestAssured. **AssertJ no es una
> dependencia del proyecto** (`build.gradle.kts` solo declara
> `quarkus-junit5`, `rest-assured`, `jackson-databind`, `jqwik`); no
> escribir `assertThat(...)`. **Nunca** testear
> `motor/internal/*` directamente: la regla del proyecto exige la API
> pública `Motor.calcularApu` / `Motor.consolidar`.

### Documentación (reconciliación activa)

| Archivo | Cambio |
|---|---|
| `docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md` | Política final, `app.display.*`, endpoint, `@Digits` scope real, no-links, conteo GM correcto, edición estructural mínima autorizada. |
| `docs/modulos/03-apu.md` | Reconciliar §1 y §10: sin `CALC_PRECISION=3 HALF_UP`; display config global `app.display.*`; `@Digits` solo en entradas monetarias. |
| `docs/modulos/04-apu-avanzado.md` | Reconciliar §3.1 (motor) y §3.2 (frontera): sin `CALC_PRECISION`; display config; `@Digits` scope; no-links confirmado. |
| `docs/modulos/estado-actual.md` | Reconciliar §1.3, §2.3, §4 matriz P-27, §6 Plan 1: lista de archivos real y `app.display.*`. |
| `docs/00-ESTADO-ACTUAL.md` | Reconciliar §3 fila 006 y §4: conteo GM correcto (24 habilitados = 22 verdes + 2 rojos en baseline). |
| `plans/README.md` | Entrada de Plan 014 + matriz de decisiones + conteo GM correcto. |
| `plans/006-motor-consolidacion-fix.md` | `./mvnw` → `./gradlew`; `HALF_DOWN` → `RoundingMode.DOWN`; nota de que el motor no aplica `HALF_UP` a 3 dp. |
| `CLAUDE.md` (backend) | Guard del motor actualizado a "solo edición estructural mínima"; `app.display.*`. |
| `../thesis-docs/CLAUDE.md` | Misma actualización del guard + `app.display.*`. |
| `../thesis-docs/plan/domain/02-data-model.md` | `app.display.*`; display global; endpoint. |
| `../thesis-docs/plan/architecture/08-codebase-design.md` | `app.display.*`; display global 2/4; frontera APU→Rubro DOWN. |
| `../thesis-docs/plan/design/03-procesos-detalle.md` | `app.display.*`; sin redondeo intermedio del motor. |
| `../thesis-docs/plan/design/04-export-sercop-spec.md` | `app.display.*`; display 2/4 desde config global. |
| `../thesis-docs/plan/design/07-decisiones-i06-pendientes.md` | `app.display.*`; cerrar la contradicción "se mantiene 3 dp hasta respuesta". |
| `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` | `app.display.*` en la lista I-06. |
| `../thesis-docs/plan/quality/02-catalogo-pruebas.md` | `app.display.*` en GM-25 / TC-P27-01 / GM-19-20. |
| `../thesis-docs/plan/README.md` | `app.display.*` en el resumen de decisiones. |
| `../thesis-docs/README.md` | `app.display.*` en el resumen de requisitos. |

> **NO tocar:**
> - `DOCUMENTOS/requerimientos/v1.1-functional-requirements.md` y
>   `v1.2-functional-requirements.md`: **histórico**, no se reescriben.
>   Tampoco las entrevistas (`A02`,
>   `Respuesta_Entrevista_N04_TERMPORAL`).
> - `plans/002-ci-github-actions.md` (Plan CI histórico, no Plan 02 del
>   motor — el autor aclara que "Plan 02" del enunciado se refería al
>   plan activo del motor, no al plan CI histórico 002).
> - `plans/001-bootstrap-quarkus.md`, `003-…`, `004-…`, `005-…` (fuera
>   del alcance de este plan).
> - `application.yml` fuera del bloque `app.display.*` documentado.
> - Cualquier `V00X__*.sql`.
> - `build.gradle.kts` (sin cambios de dependencias).
> - Tolerancias o valores esperados de GMs existentes (regla del
>   proyecto, preservada por este plan).
>
> **SÍ es tocable (aclaración, supersede la nota previa):**
> `DOCUMENTOS/requerimientos/v1.3-functional-requirements.md` es la
> **versión vigente** y ya fue actualizada en la puerta de documentación
> de este plan. No existe ninguna regla de "nunca tocar v1.3".

---

## Out of scope — baneado explícitamente

- Cambiar la aritmética de `Motor.java` o `internal/CalculadorFila.java`
  (fórmulas, `MathContext`, orden de operaciones, precisión natural).
- Introducir `HALF_UP` a 3 dp en cualquier operación del motor.
- Cambiar tolerancias o expected values de los golden masters existentes.
- Crear el módulo `recalculo` (DEFERRED — `estado-actual.md` §7.1).
- Migraciones Flyway nuevas.
- Añadir/quitar dependencias en `build.gradle.kts` (incluida AssertJ).
- `precisionDinero` o `precisionPorcentaje` dentro de `motor/`.
- Crear `application-test.yml`.
- Añadir campos nuevos a DTOs para poder anotarlos con `@Digits`.
- `JsonNullable` para porcentajes o cantidades.
- **Fallback de display en el frontend** (`../thesis-front-react`): fuera
  del alcance; este plan es backend-only.
- Reescribir `v1.1`/`v1.2` ni las entrevistas históricas.
- Crear tests de tolerancia (>0.00) para los golden masters existentes.

---

## Steps — TDD estricto

> **TDD estricto activo.** Cada paso RED debe observarse fallar antes
> del GREEN. Cada paso GREEN debe observarse pasar antes del siguiente
> paso. No se afirma evidencia que no se haya observado.

### Paso 1 — RED T1 (Frontera APU→Rubro — regla workbook-consistent 2026-08-28)

Crear `src/test/java/ec/uce/propuestas/motor/ConsolidadorFronteraTest.java`.
El test construye un `VersionSnapshot` mínimo (1 capítulo, N rubros con
APUs stub) y llama **`Motor.consolidar(...)`**, no `Consolidador`.

**Política exacta a verificar (workbook-consistent, corrección
2026-08-28):**

> `precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN)`
> `precioTotal    = cantidad.multiply(precioUnitario, MC).setScale(6, RoundingMode.HALF_UP)`
>
> Capítulo `total` y `totalGeneral` agregan esos `precioTotal` a
> escala 6. La presentación/assertion canónica es a 2 dp
> (`setScale(2, HALF_UP)` en el punto de comparación).

Casos exactos a assertar, con `costoTotal` del APU stub y `cantidad` del rubro:

| `costoTotal` | `cantidad` | `precioUnitario` esperado | `precioTotal` esperado (escala 6 `HALF_UP`) |
|---|---|---|---|
| `5.5352325` | `10` | `5.53` (DOWN) | `55.300000` (10 × 5.53 = 55.30 → 6 dp `HALF_UP`) |
| `0.001` | `1000` | `0.00` (DOWN) | `0.000000` (1000 × 0.00 = 0.00 → 6 dp `HALF_UP`) |
| `1.999` | `1` | `1.99` (DOWN, no 2.00) | `1.990000` (1 × 1.99 = 1.99 → 6 dp `HALF_UP`) |
| `5.5352325` | `1` | `5.53` (DOWN) | `5.530000` (1 × 5.53 = 5.53 → 6 dp `HALF_UP`) |

**Notas sobre los casos:**

- Los precios unitarios son `DOWN 2dp` (workbook tipea 2 dp).
- Los precios totales son `cantidad × PU_2dp` retenidos a escala 6
  `HALF_UP` (**no** truncados a 2 dp; **no** `DOWN`).
- Las 3 cifras significativas mostradas en la columna "`precioTotal`
  esperado" coinciden con el workbook IESS; las 3 dp adicionales (a 6)
  son la escala de persistencia `NUMERIC(14,6)`.

**Caso de consistencia de agregación (reemplaza el viejo caso "suma de
`precioTotal` truncados a 2 dp"):**

Construir 3 rubros con `costoTotal` stubs:

- A: PU 5.5352325 × cant 10 → PT `55.300000`
- B: PU 1.999 × cant 1 → PT `1.990000`
- C: PU 0.001 × cant 1000 → PT `0.000000`

`capituloTotal = 55.300000 + 1.990000 + 0.000000 = 57.290000` (escala 6).
`totalGeneral = setScale(2, HALF_UP) de 57.290000 = 57.29`.

**El test asserta** `result.totalGeneral().setScale(2, HALF_UP) == 57.29`
(presentación canónica a 2 dp), **no** que el `totalGeneral` interno
esté truncado a 2 dp — el agregado es a escala 6.

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.ConsolidadorFronteraTest' --console=plain
```

**Resultado RED esperado (workbook-consistent):** la versión previa del
test (descartada; mantiene `precioTotal = ...setScale(2, DOWN)`) pasa
con `Consolidador` en su estado actual equivocado. Al corregir el test
a la regla workbook-consistent (escala 6 `HALF_UP` por rubro total), la
versión actual de `Consolidador` (con `setScale(2, DOWN)` por rubro total)
falla con `delta` ≠ 0 (esperado `55.300000`, actual `55.30` — distinto
tipo `BigDecimal` por la diferencia de escala). Capturar el fallo
textual literal.

### Paso 2 — GREEN T1 (corrección workbook-consistent)

Editar `motor/internal/Consolidador.java` según el contrato:

- `precioUnitario = apu.costoTotal().setScale(2, RoundingMode.DOWN)`
  (única aplicación de `DOWN` de la frontera).
- `precioTotal = r.cantidad().multiply(precioUnitario, MC).setScale(6, RoundingMode.HALF_UP)`
  (**escala 6 `HALF_UP`**, NO `setScale(2, DOWN)` por rubro total — esa
  variante previa fue retirada por deltas sistemáticos `GM19 = -$9.37` y
  `GM20 capítulo1 = -$3.09` vs workbook IESS).
- `computeCapituloTotal` agrega los `precioTotal` a escala 6
  (`BigDecimal.add` sin reescalado intermedio); `totalGeneral` se
  redondea a `precisionDinero` (default 2 dp, vía `app.display.*` o
  equivalente) en la presentación canónica, **no** en el agregado.

Re-ejecutar T1.

**Resultado GREEN esperado:** todos los casos del Paso 1 pasan a delta
0.00 contra `precioUnitario`/`precioTotal` workbook-consistent; el
assertion de agregación cierra a `57.29` (presentación 2 dp) desde
`Σ = 57.290000` (escala 6).

### Paso 3 — RED T2 (Snapshots sin ramas auxiliares)

Crear `SnapshotSinAuxiliaresTest.java` con reflexión y JUnit puro:

```java
@Test
void ApuSnapshot_no_expone_esAuxiliar() {
    assertThrows(NoSuchFieldException.class, () -> ApuSnapshot.class.getDeclaredField("esAuxiliar"));
}

@Test
void ApuSnapshot_expone_porcentajeIndirecto_nullable() throws Exception {
    assertEquals(BigDecimal.class, ApuSnapshot.class.getDeclaredField("porcentajeIndirecto").getType());
}

@Test
void FilaSnapshot_no_expone_cdAuxiliar() {
    assertThrows(NoSuchFieldException.class, () -> FilaSnapshot.class.getDeclaredField("cdAuxiliar"));
}

@Test
void ApuCalculado_no_expone_esAuxiliar() {
    assertThrows(NoSuchFieldException.class, () -> ApuCalculado.class.getDeclaredField("esAuxiliar"));
}

@Test
void ParametrosCalculo_no_expone_porcentajeIndirectoApu() {
    assertThrows(NoSuchFieldException.class,
        () -> ParametrosCalculo.class.getDeclaredField("porcentajeIndirectoApu"));
}
```

**Resultado RED esperado:** mientras los campos existan, los
`assertThrows` fallan (y `porcentajeIndirecto` lanza
`NoSuchFieldException`). Capturar el fallo textual.

### Paso 4 — GREEN T2

En este orden, para mantener el árbol compilable:

1. `motor/ApuSnapshot.java`: `esAuxiliar` → `BigDecimal porcentajeIndirecto`.
2. `motor/ApuCalculado.java`: quitar `esAuxiliar`.
3. `motor/FilaSnapshot.java`: quitar `cdAuxiliar`.
4. `motor/ParametrosCalculo.java`: quitar `porcentajeIndirectoApu`.
5. `motor/Motor.java`: **solo** los 3 cambios estructurales del contrato.
6. `motor/internal/CalculadorFila.java`: **solo** quitar el fallback
   `cdAuxiliar` en `calcularMaterial`.
7. `apu/service/ApuCalculoService.java`: ajustar los call sites.
8. `Fixtures.java`, `MotorApuTest.java`, `MotorPropiedadesTest.java`,
   `MotorConsolidacionTest.java`: ajustar constructores. **Sin tocar
   assertions ni expected values.**

Re-ejecutar T2 y luego la suite del motor para confirmar que los 21 GMs
per-APU y las 5 propiedades siguen verdes.

### Paso 5 — RED T3 (Display config global)

Crear dos clases, porque `@TestProfile` aplica a nivel de clase Quarkus:

1. `DisplayConfigResourceTest` — **defaults** (`@QuarkusTest`, sin profile):
   `GET /api/v1/config/display` → `200` con
   `{"precisionDinero": 2, "precisionPorcentaje": 4}`.
2. `DisplayConfigResourceOverrideTest` — **override** (`@QuarkusTest` +
   `@TestProfile(DisplayOverrideProfile.class)`): su profile implementa
   `QuarkusTestProfile` y devuelve
   `Map.of("app.display.precision", "3",
   "app.display.precision-porcentaje", "6")`. El endpoint responde
   `{"precisionDinero": 3, "precisionPorcentaje": 6}`.

> Patrón ya usado en el repo: `schema/SchemaBaselineIT.V001OnlyProfile` y
> `schema/RepresentativeSeedsIT.SeedsProfile`. **No** crear
> `application-test.yml`.

```bash
./gradlew test --tests 'ec.uce.propuestas.common.config.DisplayConfigResource*Test' --console=plain
```

**Resultado RED esperado:** `404` (endpoint inexistente). Capturar el
fallo textual.

### Paso 6 — GREEN T3

1. Añadir el bloque `app.display.*` a `application.yml` (claves exactas
   del enunciado, con placeholders de env).
2. Documentar `DISPLAY_PRECISION` y `DISPLAY_PRECISION_PORCENTAJE` en
   `.env.example`.
3. Crear `common/config/DisplayConfig.java` (`@ConfigMapping(prefix = "app.display")`).
4. Crear `common/config/DisplayConfigResponse.java`.
5. Crear `common/config/DisplayConfigResource.java` (`@Path("/config/display")`,
   `@PermitAll`).

Re-ejecutar T3: GREEN en ambos casos.

### Paso 7 — RED T4 (`@Digits` scope cerrado)

Crear `src/test/java/ec/uce/propuestas/apu/dto/DigitsValidationCatalogTest.java`.
El test:

- Verifica que cada campo del catálogo cerrado (tabla de DTOs) lleva
  `@Digits(integer = 8, fraction = 2)`.
- Verifica que un conjunto explícito de campos no monetarios
  (`ApuDetalleCrearRequest.cantidad`, `.rendimiento`,
  `ApuDetallePatchRequest.cantidad`, `.rendimiento`,
  `ApuCrearRequest`/`ApuPatchRequest` porcentajes) **no** lleva `@Digits`.

**Resultado RED esperado:** ningún DTO tiene `@Digits` hoy (`grep -RIn
'@Digits' src/` → 0 hits), así que el primer bloque falla. Capturar el
fallo.

### Paso 8 — GREEN T4

Añadir `@Digits(integer = 8, fraction = 2)` **solo** en los campos del
catálogo que existen en `HEAD`. No añadir en ningún otro DTO ni campo, y
**no crear** campos nuevos para poder anotarlos.

### Paso 9 — TRIANGULATE (GM-19, GM-20, GM-21 audit, DIAG delete)

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
```

**Esperado tras GREEN completo (cualitativo — el conteo se reporta, no se
presupone).** Estado real tras T1 ejecutado (cierre parcial 2026-08-28)
marcado con **[actual]**:

| Suite | Resultado esperado | Estado real (T1 ejecutado) |
|---|---|---|
| `MotorApuTest` | 21/21 verde (sin cambios en assertions) | ✅ **[actual]** 21/21 verde |
| `MotorConsolidacionTest` (con DIAG) | Cierre residual documentado; GM-21 pendiente de limpieza; GM-24 sigue `@Disabled` | 🔴 **[actual]** GM-19 `-$6.95` / GM-20 cap. 1 `-$0.84` (**residual aceptado**); GM-21 verde con allowlist preexistente 11 ≤ 0.03; GM-24 `@Disabled` |
| `MotorPropiedadesTest` | 5/5 verde | ✅ **[actual]** 5/5 verde |
| `ConsolidadorFronteraTest` (nuevo) | verde | ✅ **[actual]** 5/5 verde |
| `SnapshotSinAuxiliaresTest` (nuevo) | verde | ⚪ OPEN (T2) |
| `DisplayConfigResourceTest` (nuevo) | verde | ⚪ OPEN (T3) |
| `DigitsValidationCatalogTest` (nuevo) | verde | ⚪ OPEN (T4) |
| GMs habilitados | 24 habilitados → 24 verdes | 🔴 **[actual]** 24 habilitados = 22 verdes + 2 rojos (GM-19/GM-20 con residual aceptado) |

> **El plan NO exige GM-19/GM-20 verdes para considerar T1 ejecutado.** El
> cierre parcial user-accepted 2026-08-28 acepta el residual sub-céntimo
> como artefacto del example workbook IESS. T2–T4 + borrado de DIAG siguen
> OPEN y no se reclaman como aplicados hasta que se ejecuten.

> ⚠️ **No hardcodear un total de tests futuro.** El conteo final se
> **reporta** leyendo los XML de resultados tras la ejecución:
>
> ```bash
> grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
>   build/test-results/test/TEST-ec.uce.propuestas.motor.*.xml
> ```

**Auditoría GM-21:** las 11 entradas actuales del allowlist
(`501BM6`, `501D1V`, `501DQR`, `501D00`, `502897`, `500ASU`, `502ARV`,
`503B30`, `501DH5`, `505APQ`, `500C2S`) se reejecutan tras la frontera
`DOWN`. Cada entrada cuya igualdad sea ahora exacta (`delta == 0.00`) se
**borra**. **Cero entradas restantes es un resultado válido y exitoso**
si la igualdad exacta queda probada por el test verde; no es una
condición de parada. Documentar el conteo final observado y comentar el
workbook row de origen de las que sobrevivan.

**Borrar DIAG:** eliminar `DIAG_rubro_expected_vs_actual` de
`MotorConsolidacionTest.java`. Mantener `GM-24` `@Disabled` con la razón
del fixture upstream.

### Paso 10 — REFACTOR (limpieza del diff)

- Diff limpio: solo los archivos de producción/config/test del scope +
  las docs conciliadas.
- Mensaje Conventional Commit sugerido:
  `feat(motor): natural bigdecimal + display global + no-links`.
- Rollback: revertir el commit; las docs conciliadas pueden mantenerse
  porque son consistentes con la decisión.

---

## Done criteria

> **Re-scope 2026-08-28 (cierre parcial user-decided).** Los ítems marcados
> **[x]** están **cerrados** (T1 ejecutado). Los **[ ]** quedan **OPEN** y
> corresponden al trabajo restante (T2, T3, T4, borrar DIAG). Los
> criterios que exigían GM-19/GM-20 verdes o el cierre total de Plan 02
> **se retiran** — Plan 02 cierra con residual aceptado por el autor.

- [x] `internal/Consolidador.java` aplica `setScale(2, RoundingMode.DOWN)`
  **solo** a `precioUnitario`; `precioTotal = cantidad.multiply(precioUnitario,
  MC).setScale(6, RoundingMode.HALF_UP)` (regla workbook-consistent).
  Capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6; la
  presentación canónica a 2 dp ocurre solo en display/assertion.
- [x] `ConsolidadorFronteraTest` 5/5 verde vía `Motor.consolidar(VersionSnapshot)`.
- [x] `MotorApuTest` 21/21 verde; `MotorPropiedadesTest` 5/5 verde
  (sin cambios en assertions).
- [ ] Limpieza GM-21 **OPEN**. El test permanece verde con el allowlist
  preexistente de 11 entradas ≤ 0.03; no se ejecutó auditoría exhaustiva
  per-rubro en este cierre parcial.
- [x] Tolerancias y expected values de los golden masters sin cambios.
- [x] Ningún test toca `motor/internal/*` directamente.
- [~] **GM-19 / GM-20 — RESIDUAL ACEPTADO** (no se reabre el motor):
  GM-19 actual `395108.37` vs esperado `395115.32` (`-$6.95`);
  GM-20 cap. 1 actual `158907.21` vs esperado `158908.05` (`-$0.84`).
  Referencia: [Plan 02 §6](../docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md) y
  [Plan 006 §1.1](./006-motor-consolidacion-fix.md).
- [ ] `ApuSnapshot`/`ApuCalculado` sin `esAuxiliar`; `FilaSnapshot` sin
  `cdAuxiliar`; `ParametrosCalculo` sin `porcentajeIndirectoApu`;
  `ApuSnapshot.porcentajeIndirecto` nullable presente — **OPEN (T2)**.
- [ ] `Motor.java` y `internal/CalculadorFila.java`: el diff contra `HEAD`
  contendrá **únicamente** los cambios estructurales del contrato (sin
  aritmética). Ninguna fórmula, `MathContext`, orden de operaciones ni
  `setScale` alterado — **OPEN (T2)**.
- [ ] `GET /api/v1/config/display` retorna
  `{"precisionDinero": 2, "precisionPorcentaje": 4}` por default; el test
  de `QuarkusTestProfile` demuestra el override — **OPEN (T3)**.
- [ ] `application.yml` contiene exactamente el bloque `app.display.*`
  acordado; `.env.example` documenta `DISPLAY_PRECISION` y
  `DISPLAY_PRECISION_PORCENTAJE` — **OPEN (T3)**.
- [ ] `@Digits(integer = 8, fraction = 2)` aparece **solo** en los campos
  del catálogo cerrado que existen en `HEAD`; el test catálogo lo verifica
  — **OPEN (T4)**.
- [ ] Borrar `MotorConsolidacionTest.DIAG_rubro_expected_vs_actual` —
  **OPEN**.
- [ ] `./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain`
  sigue verde para `ConsolidadorFronteraTest` 5/5 + `MotorApuTest` 21/21
  + `MotorPropiedadesTest` 5/5 + GM-21; GM-19/GM-20 mantienen el residual
  aceptado; GM-24 `@Disabled` por fixture upstream.
- [ ] Conteo final de tests **reportado desde los XML**, no presupuesto.
- [ ] `grep -RInE 'CALC_PRECISION' src/main/java docs/modulos` → cero hits
  en producción; en docs, solo referencias históricas/superseded.
- [ ] `grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/`
  → cero hits.
- [ ] `grep -RIn 'assertThat(' src/test` → cero hits (AssertJ no es
  dependencia del proyecto).
- [ ] `git diff --check` limpio en ambos repos.
- [ ] `git status --short` solo lista los archivos del scope.

---

## Verification commands (autorizados para este plan)

```bash
# Baseline + GREEN de cada test nuevo + suite completa
./gradlew test --tests 'ec.uce.propuestas.motor.ConsolidadorFronteraTest' --console=plain
./gradlew test --tests 'ec.uce.propuestas.motor.SnapshotSinAuxiliaresTest' --console=plain
./gradlew test --tests 'ec.uce.propuestas.common.config.DisplayConfigResource*Test' --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.dto.DigitsValidationCatalogTest' --console=plain
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
./gradlew build -x test
./gradlew spotlessCheck

# Conteo real (no presupuesto) tras la ejecución
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-ec.uce.propuestas.motor.*.xml

# Reglas del proyecto preservadas
! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'esAuxiliar|cdAuxiliar|porcentajeIndirectoApu|CAMBIO_AUXILIAR|apu_auxiliar_id|apuAuxiliarId' \
    src/main/java/ec/uce/propuestas/motor src/test/java/ec/uce/propuestas/motor
! grep -RIn 'assertThat(' src/test

# El diff del motor no cambia aritmética
git diff -- src/main/java/ec/uce/propuestas/motor/Motor.java \
             src/main/java/ec/uce/propuestas/motor/internal/CalculadorFila.java

# Diff saneado
git diff --check
git status --short
```

---

## STOP conditions

Detener y reportar al autor si:

- **GM-19 o GM-20 sigue RED tras T1 con residual sub-céntimo:** **STOP
  cerrado** (2026-08-28). El residual actual (`-$6.95` y `-$0.84`) es
  aceptado por el autor; **no** se reabre el motor para cerrarlo. Si
  futuros planes propongan cerrar este residual, deben adjuntar evidencia
  nueva y abrir STOP al autor antes de tocar código.

### STOP — evidencia workbook-consistent 2026-08-28 (cerrado)

> **Caso cerrado — corrección workbook-consistent ya aplicada en este
> plan.** STOP conditions registrada para auditoría.
>
> **Observado (2026-08-28, RED preexistente):** aplicar la versión
> previa de este plan — `precioTotal = ...setScale(2, DOWN)` simétrico
> con `precioUnitario` — producía deltas sistemáticos:
>
> - `GM-19`: `totalGeneral (2dp)` calculado = `395105.95` vs workbook
>   esperado `395115.32` → **delta `-$9.37`**.
> - `GM-20`: capítulo raíz `1` (SISTEMA ARQUITECTONICO) calculado =
>   `158904.96` vs workbook esperado `158908.05` → **delta `-$3.09`**
>   (los 6 capítulos restantes también presentaban sub-deltas
>   consistentes con truncado simétrico por rubro).
>
> Los deltas son sistemáticos y **del mismo signo**: truncar cada
> `precioTotal = cantidad × PU_2dp` a 2 dp `DOWN` arrastra los
> sub-céntimos del workbook (que **no** los trunca) y los suma sobre
> 298 rubros. **`setScale(2, DOWN)` en cada PT es el antipatrón**:
> el workbook IESS multiplica `cantidad × PU_2dp` a precisión completa
> y redondea **solo en la presentación**.
>
> **Decisión del autor (2026-08-28, opción B):** corregir la regla de
> este plan a `precioTotal = cantidad × PU_2dp` retenido a la escala de
> persistencia 6 (`NUMERIC(14,6)`) con `HALF_UP` **únicamente** en esa
> frontera de resultado; el display canónico a 2 dp vive en la capa
> de presentación/assertion, no en cada rubro. Esta corrección está
> incorporada en la "Decisión de mayor autoridad" de este plan y en
> el contrato exacto de `Consolidador.java`. La regla retirada
> (`setScale(2, DOWN)` por rubro total) **no debe reaparecer** en
> futuros `plans/0NN-motor-fix.md`: reproduciría los deltas
> sistemáticos observados.
>
> Si tras aplicar la regla corregida GM-19/GM-20 siguen RED, STOP —
> no reintroducir `setScale(2, DOWN)` por rubro total.
- El cambio estructural en `Motor.java`/`CalculadorFila.java` exige tocar
  una fórmula, el `MathContext`, el orden de operaciones o añadir un
  `setScale`: **no autorizado**, pedir confirmación.
- T2 exige eliminar más campos que
  `esAuxiliar`/`cdAuxiliar`/`porcentajeIndirectoApu`: pedir confirmación
  antes de ampliar el scope.
- T3 requiere habilitar un profile distinto a `%dev`/`%prod`, crear
  `application-test.yml`, o añadir secrets/keys al `DisplayConfig`: no
  autorizado.
- T4 exige añadir `@Digits` en un DTO fuera del catálogo cerrado, o crear
  un campo nuevo para anotarlo: STOP.
- La auditoría GM-21 **supera** las 11 entradas originales sin
  explicación plausible (entrada huérfana o bug nuevo del motor):
  reportar. *(Reducir a 0 entradas **no** es una condición de parada.)*
- Cualquier paso quiere tocar los expected values de los GMs, las
  tolerancias, `build.gradle.kts`, o un `V00X__*.sql`: no autorizado.
- `v1.1`/`v1.2` o las entrevistas requieren cambio: STOP — son histórico.
  *(`v1.3` sí es la versión vigente y ya fue actualizada; no es un
  bloqueo.)*

---

## Maintenance note

- **Regresión CI permanente:** la suite del motor debe pasar en CI
  con `BUILD SUCCESSFUL` y **0 rojos**. Plan 002 (CI GitHub Actions)
  sigue como deuda y **no se toca en este plan**; cuando se monte CI,
  debe correr `./gradlew test` y romper el build si GM-19/20 vuelven a
  fallar.
- **Display config y frontend:** el endpoint es la fuente de verdad para
  que el frontend sepa cuántos dp usar. El fallback frontend-side está
  **fuera del alcance** de este plan (backend-only).
- **Motor puro:** `DisplayConfig`, `DisplayConfigResponse` y
  `DisplayConfigResource` viven en `common/config/`. `motor/` sigue sin
  framework, sin config y sin I/O.
- **Futuros overrides monetarios en DTOs:** añadir al catálogo cerrado de
  T4 (no usar `@Digits` ad-hoc).
- **Cambios futuros al motor:** siguen exigiendo `plans/0NN-motor-fix.md`
  con justificación por cambio de requerimientos funcionales + nota en
  ambos `CLAUDE.md`. La autorización de este plan para tocar
  `Motor.java`/`CalculadorFila.java` es **acotada, estructural y de un
  solo uso**; fuera de ella el guard sigue vigente.
- **Requerimientos:** `v1.3` es la versión vigente y ya está reconciliada.
  `v1.1`/`v1.2` y las entrevistas se conservan como histórico.

---

## Decisión de mayor autoridad (2026-08-28; corrección workbook-consistent 2026-08-28)

> **El motor de cálculo opera con la precisión natural de `BigDecimal`.**
> No aplica redondeo intermedio (`CALC_PRECISION=3 HALF_UP`) sobre sus
> operaciones internas.
>
> **Frontera APU → Rubro — regla workbook-consistent (corrección
> 2026-08-28, opción B del autor):**
>
> - `Rubro.precioUnitario = APU.costoTotal.setScale(2, RoundingMode.DOWN)`
>   (único redondeo `DOWN` de la frontera; sigue el workbook IESS donde el
>   precio unitario tipeado se trunca a 2 dp).
> - `Rubro.precioTotal = cantidad × Rubro.precioUnitario`,
>   retenido a la **escala de persistencia 6** (`NUMERIC(14,6)`) con
>   `HALF_UP` aplicado **únicamente** en esa frontera de resultado, **sin
>   truncar a 2 dp cada `precioTotal`** individual. Esto reproduce el
>   workbook IESS, que multiplica `cantidad × PU_2dp` a precisión completa
>   y suma a escala completa antes de presentar a 2 dp.
> - **Totales de capítulo y `totalGeneral`** se agregan **desde esos
>   `precioTotal` a escala 6 `HALF_UP`** (no desde PT truncados a 2 dp).
>   Su **comparación/display canónico vs workbook es a 2 dp** (preserva
>   `GM19 = 395115.32` y los totales de los 7 capítulos raíz de GM20).
>
> `Motor.java` y `internal/CalculadorFila.java` reciben **solo ediciones
> estructurales mínimas** para eliminar las ramas auxiliares obsoletas y
> consumir `ApuSnapshot.porcentajeIndirecto`; su aritmética queda intacta.
> El display se rige por **dos parámetros globales**
> (`precisionDinero=2`, `precisionPorcentaje=4`) configurables vía
> `app.display.*` (`application.yml`/env) y expuestos por `GET
> /api/v1/config/display`. Los APUs son análisis **ordinarios e
> independientes** (no-links, N04 §2). La validación de entrada `@Digits`
> se aplica **solo** a los campos monetarios de DTO del catálogo cerrado.
>
> **Corrección 2026-08-28 — superseded el `DOWN` 2 dp por rubro total.**
> La versión previa de este plan (2026-08-28, antes de la corrección)
> proponía `Rubro.precioTotal = cantidad × PU_2dp, setScale(2, DOWN)`
> simétrico con `precioUnitario`. Esa regla producía deltas sistemáticos
> `GM19 = -$9.37` y `GM20 capítulo1 = -$3.09` vs el workbook IESS (los
> 2 dp truncados en cada PT arrastraban sub-céntimos que el workbook no
> trunca). La regla actual (escala 6 `HALF_UP` por rubro total) reproduce
> el workbook y queda **retirada** la versión previa del propio plan.
> Esta política **supersede** las decisiones N04 §#7 y N04-bis sobre
> redondeo intermedio y `CALC_PRECISION=3 HALF_UP`.
