# Plan 02 — Motor de cálculo: precisión natural, no-links, display global

> **Estado (2026-08-28 — cierre parcial USER-DECIDED):**
> **PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL.**
>
> - **IMPLEMENTADO:** T1 workbook-consistent (PU `DOWN` 2 dp, `PT = cantidad × PU_2dp` a escala 6 `HALF_UP`, agregación consistente de capítulo y `totalGeneral` desde esos `precioTotal`). Fixtures stubs usan `presupuesto.precioUnitario` directamente (decisión de fidelidad de fixture, no de motor). `ConsolidadorFronteraTest` 5/5 verde; `MotorApuTest` 21/21 verde; `MotorPropiedadesTest` 5/5 verde.
> - **GM-19 (residuo):** `totalGeneral` actual `395108.37` vs esperado workbook `395115.32` → **delta `-$6.95`** (sub-céntimos arrastrados por 298 rubros).
> - **GM-20 cap. 1 (residuo):** actual `158907.21` vs esperado `158908.05` → **delta `-$0.84`**.
> - **GM-21:** allowlist con **11 entradas** ≤ 0.03 a nivel PU, atribuido a **artefactos de redondeo manual del workbook IESS** (un único example workbook; no se realizan auditorías exhaustivas por rubro). Ver §6.
> - **GM-24:** sigue `@Disabled` por rotura upstream del fixture EMELNORTE.
> - **Decisión del autor (user-accepted residual):** GM-19/GM-20 quedan **red** bajo la regla workbook-consistent vigente; el motor no se modifica más para cerrar este residual. **No** se cambian workbook, golden expected values, tolerancias ni fórmulas del motor. La regla `PU DOWN 2 dp` + `PT = cantidad × PU_2dp` a escala 6 `HALF_UP` es la implementación **cerrada** del motor.
>
> Ver [Plans 006](../006-motor-consolidacion-fix.md) (implementación workbook-consistent aplicada), [Plan 014](../../plans/014-motor-precision-no-links.md) (T2–T4 OPEN; consolidación T1 ejecutada) y el `CLAUDE.md` backend para el guard del motor.

## Política vigente y estado de implementación

| Tema | Regla | Estado al cierre parcial |
|---|---|---|
| Precisión dentro del APU | **Natural `BigDecimal`** — el motor **no** aplica redondeo intermedio. | **IMPLEMENTADO**; 21 GMs per-APU y 5 propiedades verdes. |
| Frontera APU → Rubro | `precioUnitario = costoTotal.setScale(2, DOWN)`; `precioTotal = cantidad × precioUnitario`, escala 6 `HALF_UP`; capítulos y `totalGeneral` agregan esos PT6. | **IMPLEMENTADO** con residual IESS documentado. |
| Display dinero/porcentaje | Config global `app.display.precision=2` y `app.display.precision-porcentaje=4`. | **IMPLEMENTADO** (Plan 014 T3, 2026-08-28). `DisplayConfig` (`@ConfigMapping("app.display")`) + placeholders `${DISPLAY_PRECISION:2}` / `${DISPLAY_PRECISION_PORCENTAJE:4}` en `application.yml`. |
| Endpoint display | `GET /api/v1/config/display` público con `{precisionDinero, precisionPorcentaje}`. | **IMPLEMENTADO** (Plan 014 T3, 2026-08-28). `DisplayConfigResource` (`@PermitAll`). Verificado vía `DisplayConfigResourceTest` 1/1 + `DisplayConfigResourceOverrideTest` 1/1. |
| Validación de entrada | `@Digits(integer=8, fraction=2)` solo en los tres campos monetarios del catálogo cerrado. | **IMPLEMENTADO** (Plan 014 T4, 2026-08-28). Anotación en `ApuDetallePatchRequest.precioOverride`, `InsumoCrearRequest.precioUnitario`, `InsumoEditarRequest.precioUnitario`. Verificado vía `DigitsValidationCatalogTest` 3/3. |
| No-links entre APUs | Retirar `esAuxiliar`, `cdAuxiliar` y `porcentajeIndirectoApu`; usar `ApuSnapshot.porcentajeIndirecto` nullable. | **IMPLEMENTADO** (Plan 014 T2, 2026-08-28). `Motor.java`/`CalculadorFila.java` edit estructural mínimo autorizado; aritmética intacta. Verificado vía `SnapshotSinAuxiliaresTest` 6/6. Stub fixtures conservan CI=0 vía `porcentajeIndirecto = BigDecimal.ZERO`. |
| Persistencia | `NUMERIC(14,6)` monetario y `NUMERIC(5,4)` porcentaje, sin cambios. | **IMPLEMENTADO/PRESERVADO**. |

## Resultado originalmente esperado

El alcance completo incluía consolidación, no-links, display global y `@Digits`. Tras Plan 014 (2026-08-28), la **implementación está completa** salvo GM-21 cleanup (DEFERRED por preferencia del usuario):

- **IMPLEMENTADO** (2026-08-28): la política matemática, la frontera workbook-consistent, T2 (no-links estructural), T3 (display config + endpoint), T4 (`@Digits`) y borrado de DIAG.
- **Residual aceptado** (cierre parcial user-decided): GM-19/GM-20 quedan red con delta sub-céntimo (`-$6.95` / `-$0.84`) atribuido al example workbook IESS único. **No** se reabre el motor.
- **DEFERRED**: GM-21 cleanup (no auditoría exhaustiva per-rubro); GM-24 sigue `@Disabled` (fixture upstream).

> **Resultado efectivo (cierre parcial 2026-08-28):** la regla workbook-consistent queda aplicada y verificada a nivel de unidad (`ConsolidadorFronteraTest` 5/5 verde). **No** logra `delta 0.00` en GM-19/GM-20 sobre el presupuesto Cetro Médico Tulcán (ver §6); el autor acepta este residual como artefacto sub-céntimo del example workbook único y cierra este plan como **PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL**.

## Dependencia

Completar primero el [Plan 01](./01-linea-base-y-decisiones.md) y crear el plan obligatorio [`plans/014-motor-precision-no-links.md`](../../../plans/014-motor-precision-no-links.md).

## Contexto / evidencia

### Línea base (pre-implementación)

`./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain`:

- 31 tests totales del motor.
- 2 rojos preexistentes: **GM-19** (`totalGeneral` esperado `395115.32`, actual `395112.82` sin redondeo intermedio; actual `395105.95` con `setScale(2, DOWN)` por rubro total — delta `-$9.37` que motivó la corrección workbook-consistent 2026-08-28) y **GM-20** (capítulo raíz `1` esperado `158908.05`, actual `158909.20` sin redondeo; actual `158904.96` con `setScale(2, DOWN)` por rubro total — delta `-$3.09`).
- 2 skipped: **GM-24** `@Disabled` (fixture EMELNORTE upstream con `secciones` vacías y `codigo` null) y **DIAG** (método diagnóstico temporal de `plans/006`).
- **24 GMs habilitados** = **22 verdes** (21 per-APU: GM-01…18 + GM-22 + GM-23 + GM-25; más GM-21 con allowlist de 11 entradas) + **2 rojos** (GM-19, GM-20). 5/5 propiedades jqwik verdes. *(No escribir "24 GMs habilitados verdes": en la línea base son 22 verdes y 2 rojos.)*
- Decisión del autor (2026-08-28, supersede N04/N04-bis): el workbook IESS no aplica redondeo intermedio al APU; las 3 dp visibles son formato de display. Por tanto **se retira `CALC_PRECISION=3 HALF_UP`** del plan activo y del código de motor.

### Estado post-implementación (cierre parcial 2026-08-28)

`./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain`:

- **Nuevos tests verdes:** `ConsolidadorFronteraTest` 5/5 (T1 workbook-consistent verificado).
- **Verdes preservados:** `MotorApuTest` 21/21 verde; `MotorPropiedadesTest` 5/5 verde.
- **GM-21 verde** con allowlist auditado de 11 entradas ≤ 0.03 a nivel PU; ver §6.
- **GM-19 / GM-20 — RESIDUO ACEPTADO:** ver §6.
- **GM-24** sigue `@Disabled` por la rotura upstream del fixture EMELNORTE (no se repara en backend).
- **DIAG** `DIAG_rubro_expected_vs_actual` aún presente en `MotorConsolidacionTest` como `@Disabled`; **borrado** queda diferido a Plan 014.

> **No** se reintroduce `setScale(2, DOWN)` por rubro total (causaba deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs workbook IESS). **No** se tocan tolerancias ni expected values de golden masters. **No** se reabre el workbook para auditorías per-rubro.

### §6 — Residual IESS documentado (user-accepted 2026-08-28)

| GM | Workbook esperado | Motor actual (workbook-consistent) | Delta | Naturaleza |
|---|---|---|---|---|
| **GM-19** `totalGeneral` | `395115.32` | `395108.37` | **`-$6.95`** | sub-céntimo acumulado en 298 rubros; residuo propio de la regla `PU DOWN 2 dp + PT escala 6` sobre el example workbook IESS |
| **GM-20 cap. 1** (`SISTEMA ARQUITECTONICO`) | `158908.05` | `158907.21` | **`-$0.84`** | sub-céntimo en el subárbol más pesado |
| **GM-21 allowlist** | delta `0.00` | 11 entradas con delta ≤ 0.03 a nivel PU | ≤ 0.03 cada una | artefactos de redondeo manual del workbook (one example workbook, no exhaustive per-rubro audit) |

**Decisión del autor (cierre parcial user-accepted):**

1. La regla `precioUnitario = costoTotal.setScale(2, DOWN)` + `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP` es la **implementación correcta y cerrada** del motor (workbook-consistent, reproduce la práctica del workbook IESS).
2. El residual sub-céntimo de GM-19/GM-20 se atribuye a decisiones de redondeo manual dispersas en el workbook fuente (no son un bug del motor). **No** se modifica el workbook, los goldens, las tolerancias, ni fórmulas adicionales del motor para cerrarlo.
3. **Preferencia del usuario (a registrar en Plan 014):** este es **un example workbook único**; no se realizan auditorías exhaustivas per-rubro ni se sobreajusta el motor para corregir cada delta sub-céntimo.
4. El guard del motor sigue vigente: futuros cambios requieren `plans/0NN-motor-fix.md` con justificación funcional + nota en ambos `CLAUDE.md`.

## Alcance

### Implementado (T1 + fixtures + auditoría; cierre parcial 2026-08-28)

- Regla workbook-consistent aplicada en `internal/Consolidador.java`: `precioUnitario DOWN 2dp` (única aplicación de `DOWN`); `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; agregación de totales de capítulo y `totalGeneral` desde esos `precioTotal` a escala 6.
- `ConsolidadorFronteraTest` 5/5 verde vía API pública `Motor.consolidar(VersionSnapshot)` — T1 cerrado.
- Fixtures stubs (`Fixtures.versionFromJson`) usan `presupuesto.precioUnitario` directamente para construir el stub APU (decisión de fidelidad de fixture, no de motor); **no** se reconstruye `stubCT = precioTotal / cantidad`.
- Auditoría GM-21: las 11 entradas del allowlist sobreviven (delta ≤ 0.03 a nivel PU); atribuidas a artefactos de redondeo manual del workbook (one example, no exhaustive per-rubro audit).
- Limpieza de `plans/006-motor-consolidacion-fix.md`: `./mvnw` → `./gradlew`; `HALF_DOWN` → `RoundingMode.DOWN`; nota de que el motor ya no aplica `CALC_PRECISION=3 HALF_UP`.

### Implementado (alcance compartido con Plan 014)

- Sin cambios de dependencias (`build.gradle.kts`), ni de `V00X__*.sql`.
- Sin cambios de tolerancias ni de expected values de los golden masters.
- Sin cambios aritméticos en `Motor.java` ni en `internal/CalculadorFila.java`.

### Diferido a Plan 014 (T2–T4 OPEN / READY FOR REMAINING IMPLEMENTATION)

- T2 — edición estructural mínima en `Motor.java`/`CalculadorFila.java`: quitar `esAuxiliar` de `ApuSnapshot`/`ApuCalculado`, `cdAuxiliar` de `FilaSnapshot`, `porcentajeIndirectoApu` de `ParametrosCalculo`; leer el `%CI` desde `ApuSnapshot.porcentajeIndirecto`; ajustar `apu/service/ApuCalculoService.java` y fixtures/tests del motor para compilar (sin tocar assertions). **Aritmética intacta.**
- T3 — display config global: `common/config/DisplayConfig.java` (`@ConfigMapping(prefix = "app.display")`), `common/config/DisplayConfigResponse.java`, `common/config/DisplayConfigResource.java` (`GET /api/v1/config/display`, `@PermitAll`); bloque `app.display.*` en `application.yml`; documentar `DISPLAY_PRECISION`/`DISPLAY_PRECISION_PORCENTAJE` en `.env.example`.
- T4 — `@Digits(integer=8, fraction=2)` en los campos monetarios de DTO del catálogo cerrado (`ApuDetallePatchRequest.precioOverride`, `InsumoCrearRequest.precioUnitario`, `InsumoEditarRequest.precioUnitario`).
- DIAG `MotorConsolidacionTest.DIAG_rubro_expected_vs_actual` — borrar.
- GM-24 sigue `@Disabled` por la rotura upstream del fixture EMELNORTE (no se repara en backend).

### No incluye (alcance cerrado)

- Redondear la persistencia a 2 dp.
- Aplicar `precisionDinero`/`precisionPorcentaje` dentro del motor.
- Crear el módulo `recalculo` (DEFERRED, [`estado-actual.md`](../estado-actual.md) §7.1).
- Recálculo transaccional de APUs persistidos.
- Cambios de fórmula no respaldados por requisitos vigentes (en particular: **no** se reabre el motor para cerrar el residual sub-céntimo de GM-19/GM-20 sobre el example workbook IESS).
- Reescribir `v1.1`/`v1.2` ni la respuesta N04 temporal — son histórico; la nueva política se anota en docs activos. *(`v1.3` sí es la versión vigente y ya fue reconciliada.)*
- Tocar el plan CI histórico `plans/002-ci-github-actions.md` (Plan 002 ≠ Plan 02 del motor; el enunciado del autor se refería al actual Plan 02).
- Crear `application-test.yml` (los overrides de test van por `QuarkusTestProfile`).
- Añadir dependencias nuevas (AssertJ **no** está en `build.gradle.kts`; usar JUnit 5 + RestAssured).
- Testear `motor/internal/*` directamente: los tests van por la API pública `Motor.calcularApu` / `Motor.consolidar`.
- Fallback de display en el frontend (`../thesis-front-react`) — backend-only.

## Pasos

### Ejecutados (cierre parcial 2026-08-28)

1. Baseline registrado: GM-19/GM-20 rojos preexistentes; 24 GMs habilitados = 22 verdes + 2 rojos; 5/5 propiedades verdes; GM-24 + DIAG `@Disabled`.
2. **RED T1** → **GREEN T1**: `ConsolidadorFronteraTest` con la regla **workbook-consistent** (`PU DOWN 2dp`, `PT = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`, agregación consistente de capítulo y `totalGeneral` desde esos `precioTotal` a escala 6) vía la API pública `Motor.consolidar(VersionSnapshot)`. 5/5 verde.
3. **TRIANGULATE** parcial:
   - GM-19 y GM-20 **siguen RED** bajo la regla workbook-consistent vigente; el residual documentado en §6 (`-$6.95` y `-$0.84` respectivamente) es aceptado por el autor; **no** se reabre el motor.
   - GM-21 allowlist auditado: las 11 entradas ≤ 0.03 sobreviven (atribuidas a artefactos de redondeo manual del workbook).
   - `MotorApuTest` 21/21 verde, `MotorPropiedadesTest` 5/5 verde (sin cambios en assertions).
   - GM-24 sigue `@Disabled` por la rotura upstream del fixture EMELNORTE.
4. Fixtures: `Fixtures.versionFromJson` usa `presupuesto.precioUnitario` directamente como `stubCT` (decisión de fidelidad de fixture; **no** se reconstruye `precioTotal / cantidad`).
5. Limpieza de `plans/006-motor-consolidacion-fix.md` (gradle + `RoundingMode.DOWN` + nota `CALC_PRECISION` retirada).

### Diferidos a [`plans/014`](../../../plans/014-motor-precision-no-links.md) (OPEN / READY FOR REMAINING IMPLEMENTATION)

- **T2** — `SnapshotSinAuxiliaresTest` (reflexión: sin `esAuxiliar`/`cdAuxiliar`/`porcentajeIndirectoApu`, con `ApuSnapshot.porcentajeIndirecto`); quitar las ramas obsoletas en `Motor.java`/`CalculadorFila.java`; ajustar call sites.
- **T3** — `DisplayConfigResourceTest` (defaults 2/4 + override por `QuarkusTestProfile`); crear `DisplayConfig`, `DisplayConfigResponse`, `DisplayConfigResource`; bloque `app.display.*` en `application.yml`; documentar env vars en `.env.example`.
- **T4** — `DigitsValidationCatalogTest` (catálogo cerrado de `@Digits(integer=8, fraction=2)`); anotar los 3 campos existentes en `HEAD`.
- **Borrar** `DIAG_rubro_expected_vs_actual`.
- Conteo final de tests reportado desde los XML de `build/test-results/`, no presupuesto.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.common.config.DisplayConfigResourceTest' --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.dto.DigitsValidationCatalogTest' --console=plain
./gradlew build -x test
./gradlew spotlessCheck

! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'esAuxiliar|cdAuxiliar|porcentajeIndirectoApu|apu_auxiliar_id|apuAuxiliarId|CAMBIO_AUXILIAR' \
    src/main/java/ec/uce/propuestas/motor src/test/java/ec/uce/propuestas/motor
! grep -RIn 'assertThat(' src/test

# Conteo real de tests (no presupuesto)
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-ec.uce.propuestas.motor.*.xml

git diff --check
git status --short
```

## Criterios de terminado

> **Estado real (cierre parcial user-accepted 2026-08-28):** los ítems marcados **[x]** están **cerrados**; los **[ ]** quedan **deferidos a Plan 014** (T2/T3/T4 OPEN); los **[~]** describen el **residual aceptado** sobre el example workbook IESS.

- [x] Existe `plans/014-motor-precision-no-links.md` y autoriza el cambio aplicado en T1 (workbook-consistent).
- [~] `./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain` — **`MotorApuTest` 21/21 verde, `MotorPropiedadesTest` 5/5 verde, `ConsolidadorFronteraTest` 5/5 verde, GM-21 verde con allowlist 11 ≤ 0.03.** GM-19 / GM-20 siguen RED con el residual documentado en §6 (`-$6.95`, `-$0.84`). GM-24 `@Disabled` por fixture upstream. `DIAG` aún en el archivo; **borrado** queda en Plan 014.
- [x] Regla workbook-consistent aplicada: `internal/Consolidador.java` aplica `setScale(2, RoundingMode.DOWN)` **solo** a `precioUnitario`; `precioTotal = cantidad × PU_2dp, setScale(6, RoundingMode.HALF_UP)`; capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6; display canónico a 2 dp `HALF_UP` solo en presentación/assertion. **No** se reintroduce `setScale(2, DOWN)` por rubro total.
- [x] `Motor.java` y `internal/CalculadorFila.java` **sin cambios** en este cierre parcial (la edición estructural mínima de T2 queda diferida a Plan 014).
- [x] `ApuSnapshot`/`ApuCalculado`/`FilaSnapshot`/`ParametrosCalculo` **sin cambios** en este cierre parcial (la limpieza no-links queda diferida a Plan 014).
- [ ] `GET /api/v1/config/display` retorna `{"precisionDinero": 2, "precisionPorcentaje": 4}` por default; el test con `QuarkusTestProfile` demuestra el override — **diferido a Plan 014 T3**.
- [ ] `@Digits(integer=8, fraction=2)` aparece solo en los campos monetarios de DTO del catálogo cerrado que existen en `HEAD` — **diferido a Plan 014 T4**.
- [x] Ningún test accede a `motor/internal/*` directamente (la frontera se testea vía `Motor.consolidar`).
- [x] La persistencia conserva `NUMERIC(14,6)` monetario y `NUMERIC(5,4)` porcentaje.
- [x] No hay `double` ni `float` en `motor/`.
- [x] Tolerancias y expected values de los golden masters sin cambios.
- [~] Conteo final de tests **se reporta** desde los XML de `build/test-results/`, no se presupone.

## Condiciones de parada

Detener y reportar al autor si:

- STOP — workbook-consistent 2026-08-28 (cerrado, registrado para auditoría): la versión previa de este plan con `setScale(2, DOWN)` simétrico en `precioUnitario` y `precioTotal` producía deltas sistemáticos `GM19 = -$9.37` (`totalGeneral = 395105.95` vs workbook `395115.32`) y `GM20 cap1 = -$3.09` (capítulo `1` = `158904.96` vs workbook `158908.05`). Los deltas son del mismo signo: truncar cada `precioTotal` a 2 dp `DOWN` arrastra sub-céntimos que el workbook IESS **no** trunca (su presentación es a 2 dp `HALF_UP` después de sumar a precisión completa). El autor corrigió la regla a workbook-consistent (escala 6 `HALF_UP` por rubro total); **no** reintroducir `setScale(2, DOWN)` por rubro total en futuros `plans/0NN-motor-fix.md` sin evidencia de que no reproduce estos deltas.
- STOP — residual IESS aceptado (cerrado 2026-08-28, user-decided): el residual sub-céntimo actual (`GM19 = -$6.95`, `GM20 cap1 = -$0.84`, `GM21` con 11 entradas ≤ 0.03) es **aceptado por el autor** y cierra este plan como **PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL**. No reabrir el motor para cerrar estos deltas; **no** cambiar el workbook, los golden expected values, las tolerancias ni introducir fórmulas adicionales. Cualquier plan futuro que proponga cerrar el residual debe aportar evidencia nueva y adjuntar STOP al autor antes de tocar código.
- La edición estructural en `Motor.java`/`CalculadorFila.java` exige tocar una fórmula, el `MathContext`, el orden de operaciones o añadir un `setScale`; **no autorizado**.
- `ApuDetalleCrearRequest.precioOverride` — el campo **no existe** en `HEAD`; no crearlo. Ver STOP condition en [`plans/014`](../../../plans/014-motor-precision-no-links.md).
- T2 exige eliminar más campos que `esAuxiliar`/`cdAuxiliar`/`porcentajeIndirectoApu`; pedir confirmación para ampliar el scope.
- T3 requiere habilitar un profile distinto a `%dev`/`%prod`, crear `application-test.yml`, o añadir secrets al `DisplayConfig`.
- T4 exige añadir `@Digits` en un DTO fuera del catálogo cerrado o crear un campo nuevo para anotarlo; STOP — pedir confirmación.
- La auditoría GM-21 **supera** las 11 entradas originales sin explicación plausible; reportar. *(Reducirla a 0 entradas **no** es condición de parada; el resultado actual — 11 entradas ≤ 0.03 — es el estado cerrado y aceptado.)*
- Cualquier paso quiere tocar expected values de GMs, tolerancias, `build.gradle.kts`, o un `V00X__*.sql`; no autorizado.
- `v1.1`/`v1.2` o las entrevistas requieren cambio para mantener coherencia; STOP — son histórico. *(`v1.3` es la versión vigente y ya fue reconciliada; no es un bloqueo.)*