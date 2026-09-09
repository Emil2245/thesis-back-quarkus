# 015 — Retirar `descuento` por APU (P-24 / S-24 withdrawn)

> **Plan del directorio `plans/` raíz.** Predecesor: `014-motor-precision-no-links.md` (**DONE 2026-08-28 / cierre parcial user-accepted**). Numeración monótona: este plan es `015`.
>
> **Estado (2026-09-01):** **DONE — verificación dirigida completa; suite completa / Spotless / build siguen pendientes (no se reclaman).** El seam activo de descuento por APU se retira conforme a este plan; Plan 020 (recalculo write-through) queda **DONE** al desbloquearse la incompatibilidad `porcentajeDescuento` global vs. `apu.porcentaje_descuento` por APU (ver §9 «Decisión de mayor autoridad»). Las cifras de evidencia se reportan desde los XML de `build/test-results/test/` (no se presuponen).
>
> **Estado previo (2026-09-01) — PLANNED / READY:** autorización con TDD estricto para la remoción del seam; no tocaba código de producción/tests hasta aprobación.
>
> **Decisión de mayor autoridad (N04 §A1 rectificada, 2026-09-01):**
>
> - **P-24 / S-24 withdrawn.** No existe descuento por APU. El único descuento
>   que sobrevive es **FORMA 1** (mutación de las columnas base de los
>   insumos elegibles copiados a la base PROYECTO del proyecto; **MO exenta**,
>   reversible desde la base) — implementado por el path de edición atómica de
>   `InsumoCrudService.editar` sobre la base PROYECTO. **FORMA 2** = edición
>   atómica de un insumo ya PROYECTO (mismo path; no requiere seam nuevo).
>   No se introduce “monto absoluto” de descuento.
> - **DB column `apu.porcentaje_descuento` queda INERT (compatibility
>   seam).** No se crea migración nueva (V001–V008 permanecen intactas).
>   JPA deja de mapeear la columna; el modelo la ignora; las inserciones del
>   seed V004 siguen aplicando su valor por defecto `NOT NULL DEFAULT 0`.
> - **Motor**: se conserva la precisión natural de `BigDecimal` (sin redondeo
>   intermedio); se conserva la regla **workbook-consistent** de la frontera
>   APU→Rubro (`precioUnitario DOWN 2dp`; `precioTotal = cantidad × PU_2dp`
>   retenido a escala 6 `HALF_UP`; capítulo y `totalGeneral` agregan esos
>   `precioTotal` a escala 6; display canónico a 2 dp `HALF_UP` solo en
>   presentación/assertion). Se **retira la etapa `costoDirectoAjustado = CD
>   × (1 − descuento)`** del motor (siempre identidad con `descuento = 0`).
>   `CI = CD × %CI` y `CT = CD + CI` sustituyen el encadenamiento previo.
> - **DTOs públicos**: `ApuResponse` pierde el campo `porcentajeDescuento`;
>   `ApuCalculoParametros` pierde `descuento`; `ApuCalculoResumen` pierde
>   `cdAjustado` + `operacionCdAjustado` (queda `{cd, ci, ct}`).
> - **REST**: `PATCH /api/v1/apus/{apuId}/porcentaje-descuento` se retira.
>   `PATCH .../porcentaje-indirecto` y `PATCH ...` (cabecera) sobreviven.
> - **`rango_descuento_min/max`** en `ParametrosSistema` **se conservan**
>   (regulan el slider / validación de FORMA 1 sobre los insumos base
>   PROYECTO editables).
>
> **Pre-requisito secuencial del DAG I-07 — RESUELTO 2026-09-01:** Plan 020
> (recalculo write-through) **queda desbloqueado al cierre de este Plan 015**
> (ver `plans/README.md` y `docs/modulos/05-presupuesto/00.md` §10 — el
> siguiente plan ejecutable es Plan 021, no Plan 020). La incompatibilidad
> `porcentajeDescuento` global vs. `apu.porcentaje_descuento` por APU ya
> desaparece con este plan: el contrato de `Motor.consolidar(VersionSnapshot)`
> deja de exigir `porcentajeDescuento` global; el modelo de datos ya no
> ofrece `apu.porcentaje_descuento` mutable; sólo sobrevive la columna inert.

---

## Quick path (TL;DR para el ejecutor)

> **Plantilla: el ejecutor copia y adapta, no improvisa.** Pasos RED /
> GREEN / REFACTOR exactos abajo.

1. **RED T1 — seam motor:** `DescuentoRetiradoMotorTest` con reflexión
   sobre `motor/ApuCalculado.java`, `motor/ParametrosCalculo.java` y
   aserción JSON-friendly sobre la ausencia de los campos. Capturar fallo
   textual.
2. **GREEN T1 — motor:** retirar `costoDirectoAjustado` de `ApuCalculado`;
   retirar `porcentajeDescuento` de `ParametrosCalculo`; reordenar `Motor`
   (`CI = CD × %CI`, `CT = CD + CI`); ajustar constructores en `Fixtures`,
   `MotorApuTest`, `MotorPropiedadesTest`, `MotorConsolidacionTest`. Re-correr
   suite motor: todos los GMs conservados (CMT workbook `descuento = 0`)
   idénticos; basuras intactas.
3. **TRIANGULATE T1 — invariantes:** `descuento_no_cambia_CD` se conserva
   como `cd_siempre_igual_a_ct_sin_ci` (o equivalente) con la semántica nueva
   (sin etapa de descuento). `consolidar_con_descuento_cero_es_identidad` se
   reescribe verificando `costoTotal = costoDirecto` cuando `%CI = 0`.
4. **RED T2 — contrato APU:** `DescuentoRetiradoContratoTest` enumera
   claves JSON de `ApuResponse` (sin `porcentajeDescuento`),
   `ApuCalculoParametros` (sin `descuento`),
   `ApuCalculoResumen` (sin `cdAjustado`/`operacionCdAjustado`).
5. **GREEN T2 — APU entity / DTOs / mapper:** retirar campo
   `porcentajeDescuento` de `Apu.java` (la columna queda *inert* sin mapping
   JPA), `ApuResponse.java`, `ApuCalculoParametros.java`,
   `ApuCalculoResumen.java`, `ApuCalculoService.java`,
   `ApuDuplicarService.java`, `ApuMapper.java`. JSON del contrato queda
   automático por la eliminación del campo.
6. **RED T3 — REST:** `DescuentoEndpointRetiradoTest` con `RestAssured`
   `PATCH .../porcentaje-descuento` espera 404.
7. **GREEN T3 — REST:** retirar endpoint de `ApuResource.java` y método de
   `ApuCrudService.java`. Re-correr `ApuResourceIT` con la mitad del
   `TC_P23_P24_*` ajustada: `%CI` sobrevive, `descuento` se elimina.
8. **REFACTOR:** baseline 24 GMs habilitados (22 verdes + 2 rojos residuales
   aceptados) + `ApuResourceIT` + `ApuCalculoServiceIT` + suite dirigida
   `apu` preservada; reportes XML reportados (no presupuestos).

### T2 (motor) — cambio de fórmula autorizado (sin aritmética nueva)

| Antes (con descuento) | Después (sin descuento) |
|---|---|
| `uno = 1 − descuento` | — (eliminado) |
| `costoDirectoAjustado = CD × uno` | — (eliminado) |
| `costoIndirecto = CD_ajustado × %CI` | `costoIndirecto = CD × %CI` |
| `costoTotal = CD_ajustado + CI` | `costoTotal = CD + CI` |
| `ApuCalculado.costoDirectoAjustado` | eliminado |

> **Equivalencia numérica:** con `descuento = 0` (único valor permitido en el
> modelo vigente y en todo el workbook IESS), `CD_ajustado ≡ CD`. La forma
> “después” es lo que el motor ya calculaba en la práctica — el plan sólo
> borra código muerto.

---

## 1. Context / evidence (línea base)

### 1.1 Justificación funcional

La entrevista N04 (corpus canónico en `../thesis-docs/DOCUMENTOS/entrevistas/04/`)
deja vigente la siguiente distribución del descuento:

- **FORMA 1 (N04 §A1):** descuento aplicado al mutar las columnas base de
  los **insumos elegibles** copiados a la base PROYECTO del proyecto; MO
  exenta; reversible desde la base; tope regulado por
  `parametros_sistema.rango_descuento_min/max`.
- **FORMA 2 (N04 §A2):** edición atómica de un insumo ya PROYECTO
  (mismo path; sin seam nuevo).

El atajo legacy **P-24** (`PATCH /apus/{id}/porcentaje-descuento`) sobrevive
**sólo de forma residual** en el código: el campo `apu.porcentaje_descuento`
se persiste por el seed V004 (con valor `0.0000`), el motor lo lee vía
`ParametrosCalculo.porcentajeDescuento`, el motor calcula
`costoDirectoAjustado = CD × (1 − descuento)` en
`Motor.calcularApu` (línea 106), y `ApuCalculado.costoDirectoAjustado`
propaga el resultado a `ApuCalculoResumen.cdAjustado` +
`operacionCdAjustado`. Es código que en producción siempre devuelve
identidad (`descuento = 0`) — está pidiendo ser retirado por rigor de
tesis y desbloquea Plan 020.

**Decisión del usuario (2026-09-01):** retirar P-24 / S-24 completo, **sin**
reintroducir un campo “monto absoluto”. El descuento vive únicamente en
FORMA 1 (mutación de insumos PROYECTO) con MO exenta. La columna
`apu.porcentaje_descuento` permanece en V001 como **compatibility seam
inert** (no se migra, no se borra, no se reasigna; JPA simplemente la
ignora).

### 1.2 Línea base del motor (2026-08-28; cierre parcial user-accepted)

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
```

| Suite | Tests | Rojos | Skipped | Comentario |
|---|---|---|---|---|
| `MotorApuTest` | 21 | 0 | 0 | verde |
| `MotorConsolidacionTest` | 5 | 2 (GM-19, GM-20) | 2 (GM-24 `@Disabled`, DIAG borrado) | residuales aceptados |
| `MotorPropiedadesTest` | 5 | 0 | 0 | verde (incluye `descuento_no_cambia_CD`) |
| `ConsolidadorFronteraTest` | 5 | 0 | 0 | verde (T1 Plan 014) |
| `SnapshotSinAuxiliaresTest` | 6 | 0 | 0 | verde (T2 Plan 014) |
| **Total motor** | **42** | **2** | **2** | ver desglose de GMs abajo |

**Conteo de Golden Masters en la línea base (exacto):**

| Métrica | Valor |
|---|---|
| GMs **habilitados** | **24** — 21 per-APU (GM-01…18, GM-22, GM-23, GM-25) + GM-19 + GM-20 + GM-21 |
| GMs habilitados **verdes** | **22** — los 21 per-APU + GM-21 (allowlist 11 ≤ 0.03) |
| GMs habilitados **rojos** | **2** — GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` (residuales aceptados) |
| GMs `@Disabled` | **1** — GM-24 (fixture EMELNORTE upstream) |

> ⚠️ **No escribir** “24 GMs habilitados verdes”. La línea base tiene 24
> habilitados, 22 verdes + 2 rojos. Cualquier doc que afirme 24 verdes
> pre-implementación de este plan es incorrecto. El cierre parcial 2026-08-28
> (Plan 014/Plan 02) acepta los deltas y **no** los reabre.

### 1.3 Línea base de `apu/` (verificación dirigida)

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.*' --console=plain
```

| Suite | Tests | Notas |
|---|---|---|
| `ApuCalculoServiceIT` | 2 | verde (P-27 desglose) |
| `ApuCalculoServiceNullableInsumoTest` | 1 | verde (fila pendiente plantilla) |
| `ApuCalculoServiceTest` | — | (si existe) |
| `ApuResourceIT` | 36 | **2 de los 36 tests** tocan el endpoint `PATCH /porcentaje-descuento` (`TC_P23_P24_*`) y `parametros.descuento`/`resumen.cdAjustado` — **deben ajustarse en este plan** |
| `ResolverInsumoProyectoTest` | 9 | verde |
| Subtotal apu (pre-ajuste) | 41 (+1 pendiente) | — |

### 1.4 Línea base de presupuesto (verificación dirigida)

```bash
./gradlew test --tests 'ec.uce.propuestas.presupuesto.entity.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.identifier.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.schema.*' --console=plain
```

Todos verdes pre-ajuste; este plan **no debe** regresionar ninguno de ellos.

### 1.5 Decisiones locked (no se reabren; este plan las respeta)

- `Motor.consolidar(VersionSnapshot)` ↔ `ParametrosCalculo` (sin etapa de
  descuento tras este plan).
- Regla **workbook-consistent** en `internal/Consolidador.java`
  (`PU DOWN 2dp`; `PT = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`;
  totales de capítulo y `totalGeneral` agregan esos `PT` a escala 6;
  display canónico a 2 dp `HALF_UP` solo en presentación/assertion).
- Precisión natural de `BigDecimal`: **sin** redondeo intermedio en el motor.
- GM-19 (`-$6.95`) y GM-20 cap. 1 (`-$0.84`) con residual aceptado (no se
  reabre el motor).
- `ApuSnapshot.sin esAuxiliar` y `ApuCalculado.sin esAuxiliar` (Plan 014 T2).
- `ApuSnapshot.porcentajeIndirecto` nullable (override semántico por APU).
- `FilaSnapshot.sin cdAuxiliar` (Plan 014 T2).
- Display global `app.display.precision=2` y
  `app.display.precision-porcentaje=4` (Plan 014 T3; T4 ya con `@Digits`
  cerrado).
- PK/FK internas `BIGINT`; `public_id UUID DEFAULT uuidv7()` (sin V009; los
  seam inerts no fuerzan migración nueva).

---

## 2. In scope (paths exactos)

### Producción — motor (3 archivos en `src/main/java/ec/uce/propuestas/motor/`)

| Archivo | Cambio autorizado |
|---|---|
| `ApuCalculado.java` | Quitar el record component `BigDecimal costoDirectoAjustado`. Renombrar la línea de javadoc adyacente. |
| `ParametrosCalculo.java` | Quitar el record component `BigDecimal porcentajeDescuento` y ajustar javadoc. Retener `porcentajeHerramientaMenor` y `porcentajeIndirectoDefault`. |
| `Motor.java` | **Edición estructural autorizada única:** eliminar `BigDecimal uno = ...subtract(p.porcentajeDescuento(), MC);` y la línea `BigDecimal costoDirectoAjustado = costoDirecto.multiply(uno, MC);`. Sustituir `costoIndirecto = costoDirectoAjustado.multiply(pctCi, MC);` por `costoIndirecto = costoDirecto.multiply(pctCi, MC);` y `costoTotal = costoDirectoAjustado.add(costoIndirecto);` por `costoTotal = costoDirecto.add(costoIndirecto);`. Eliminar el `costoDirectoAjustado` argumento del constructor `new ApuCalculado(...)`. Aritmética del resto **sin cambios**. |

#### Contrato exacto de `Motor.java`

**Autorizado únicamente:**

1. Borrar la etapa `uno`/`costoDirectoAjustado`.
2. Sustituir `CD × (1 − descuento)` por `CD` directo al calcular `CI` y
   `CT` (identidad con `descuento = 0`).
3. Actualizar el `new ApuCalculado(...)` para no pasar
   `costoDirectoAjustado`.

**Prohibido en `Motor.java`:**

- Cambiar el `static final MathContext MC = new MathContext(20,
  RoundingMode.HALF_UP)`.
- Cambiar cualquier fórmula (`subtotalN/M/O/P`, `costoHmExact`,
  `costoDirecto`, `uno`).
- Añadir cualquier `setScale`, `round`, `divide` con escala.
- Cambiar el orden de operaciones (N → M → O → P → CD → CI → CT).
- Cambiar el orden de presentación de las filas.

### Producción — `internal/Consolidador.java`

| Archivo | Cambio |
|---|---|
| `internal/Consolidador.java` | **No tocar.** La regla **workbook-consistent** (PU `DOWN` 2 dp; PT a escala 6 `HALF_UP`; totales agregados a escala 6) queda cerrada y no se reabre en este plan. Verificar sólo que la nueva firma de `Motor.consolidar` (via `ParametrosCalculo` sin descuento) compila sin cambios. |

### Producción — APU (`src/main/java/ec/uce/propuestas/apu/`)

| Archivo | Cambio autorizado |
|---|---|
| `entity/Apu.java` | Quitar `@Column(name = "porcentaje_descuento", ...) public BigDecimal porcentajeDescuento = BigDecimal.ZERO;`. La columna queda **inert** en BD (no se migra; `NOT NULL DEFAULT 0` en V001 §2.10). |
| `dto/ApuResponse.java` | Quitar el record component `BigDecimal porcentajeDescuento`. JSON resultante omite la clave por contrato estable. |
| `dto/ApuCalculoParametros.java` | Quitar el record component `BigDecimal descuento`. Queda `{hm, ciDefault, ciAplicado}`. |
| `dto/ApuCalculoResumen.java` | Quitar `BigDecimal cdAjustado` y `String operacionCdAjustado`. Queda `{cd, ci, ct}`. |
| `mapper/ApuMapper.java` | Quitar el argumento `e.porcentajeDescuento` en la tupla del `ApuResponse`. |
| `service/ApuCrudService.java` | Quitar el método `public ApuResponse actualizarPorcentajeDescuento(...)`. **Sin nuevos métodos.** |
| `service/ApuDuplicarService.java` | Quitar la línea `copia.porcentajeDescuento = origen.porcentajeDescuento == null ? BigDecimal.ZERO : origen.porcentajeDescuento;` y cualquier referencia posterior. Actualizar el javadoc. |
| `service/ApuCalculoService.java` | (1) `recalcular(Apu apu)`: al construir `new ParametrosCalculo(...)` usar `BigDecimal.ZERO` como tercer argumento o el constructor de 2 argumentos (`porcentajeHerramientaMenor`, `porcentajeIndirecto`). (2) `proyectar(Apu apu)`: idem + ajustar `buildParametros` (quitar el cálculo de `descuento`) y `buildResumen` (quitar `cdAjustado`, `factor`, `operacionCdAjustado`). |
| `resource/ApuResource.java` | Quitar el método `@PATCH @Path("/porcentaje-descuento") public ApuResponse actualizarPorcentajeDescuento(...)`. |

### Producción — call sites afectados para compilar

| Archivo | Cambio |
|---|---|
| `motor/ApuCalculado.java` (uso) | Eliminado (registro) |
| `motor/ParametrosCalculo.java` (uso) | Eliminado (registro) |
| `apu/service/ApuCalculoService.java` | Ajuste de constructores `new ApuCalculado(...)` y `new ParametrosCalculo(...)` |
| `apu/service/ApuCrudService.java` | Sin nuevos campos |
| `motor/Fixtures.java` | Ajustar `param(...)` y `apuStub(...)` para usar constructor de `ParametrosCalculo` sin descuento y `ApuCalculado` sin `costoDirectoAjustado`. No tocar valores esperados de los GMs. |
| `motor/MotorApuTest.java` | Ajuste de constructores (cambiar `new ApuCalculado(...costoDirectoAjustado...)` por omisión de argumento). |
| `motor/MotorPropiedadesTest.java` | Reescribir `descuento_no_cambia_CD` y `descuento_10_aplica_sobre_CD` como `costoIndirecto_aplica_sobre_CD_y_depende_de_pct_ci` (ver TRIANGULATE). Mantener 5 propiedades totales. |
| `motor/MotorConsolidacionTest.java` | Ajustar constructores; el resto intacto. |

### Config / DB / migraciones

| Archivo | Cambio |
|---|---|
| `src/main/resources/application.yml` | **No tocar.** |
| `src/main/resources/db/migration/V00X__*.sql` | **No tocar.** V001–V008 intactas. La columna `apu.porcentaje_descuento` queda inert (DEFAULT 0, CHECK constraint intacto en BD; JPA deja de mapearla). |
| `src/main/resources/META-INF/resources/.env.example` | **No tocar** para este plan. `DISPLAY_PRECISION*` ya documentados. |

### Pruebas — archivos nuevos (2) + modificados (5)

| Archivo | Propósito |
|---|---|
| `src/test/java/ec/uce/propuestas/motor/DescuentoRetiradoMotorTest.java` *(nuevo)* | T1 — reflexión sobre `ApuCalculado` (sin `costoDirectoAjustado`) y `ParametrosCalculo` (sin `porcentajeDescuento`). Invariantes aritméticas: `CI = CD × %CI`, `CT = CD + CI`. |
| `src/test/java/ec/uce/propuestas/apu/DescuentoRetiradoContratoTest.java` *(nuevo)* | T2 — reflexión + RestAssured sobre DTOs: `ApuResponse` sin `porcentajeDescuento`; `ApuCalculoParametros` sin `descuento`; `ApuCalculoResumen` sin `cdAjustado`/`operacionCdAjustado`. |
| `src/test/java/ec/uce/propuestas/apu/DescuentoEndpointRetiradoTest.java` *(nuevo)* | T3 — `RestAssured` `@QuarkusTest`: `PATCH /api/v1/apus/{apuId}/porcentaje-descuento` (UUIDv7) → 404. |
| `src/test/java/ec/uce/propuestas/motor/MotorPropiedadesTest.java` *(modificado)* | Sustituir `descuento_no_cambia_CD` y `descuento_10_aplica_sobre_CD` por `costoIndirecto_aplica_sobre_CD_y_depende_de_pct_ci`. Mantener 5 propiedades totales. |
| `src/test/java/ec/uce/propuestas/apu/resource/ApuResourceIT.java` *(modificado)* | (a) En `TC_P23_P24_porcentajes_actualizan_y_restauran`: eliminar el bloque de `descuento` (sólo sobrevive el de `%CI`). Renombrar el método a `TC_P23_porcentaje_indirecto_actualiza_y_restaurar`. (b) En `TC_P27_01_*`, `TC_P27_02_*` y aserciones de contrato del response: eliminar `.body("parametros.descuento", ...)` y `.body("resumen.cdAjustado", ...)` + `.body("resumen.operacionCdAjustado", ...)`. Actualizar el `Set.of("hm", "ciDefault", "ciAplicado")` y el `Set.of("cd", "ci", "ct")`. Re-numerar si quedaran duplicados (mantener 36 tests totales). |
| `src/test/java/ec/uce/propuestas/motor/Fixtures.java` *(modificado)* | Constructor de `ParametrosCalculo` sin descuento; `ApuCalculado` sin `costoDirectoAjustado`. **Sin tocar valores esperados de GMs.** |
| `src/test/java/ec/uce/propuestas/motor/MotorApuTest.java` *(modificado)* | Ajuste de constructores. Assertions intactas. |
| `src/test/java/ec/uce/propuestas/motor/MotorConsolidacionTest.java` *(modificado)* | Ajuste de constructores. Assertions intactas. GM-19/GM-20 conservan residual aceptado; GM-21 verde con su allowlist; GM-24 sigue `@Disabled`. |
| `src/test/java/ec/uce/propuestas/apu/service/ApuDuplicarServiceTest.java` *(modificado, si existe)* | Si el test actual asserta preservación de `porcentajeDescuento` en la copia, se sustituye por “no expone `porcentajeDescuento` en la copia del response”. |

> **Convenciones de test obligatorias:** JUnit 5 (`assertEquals`,
> `assertThrows`, `assertTrue`) + RestAssured. **AssertJ no es dependencia**
> del proyecto (ver `CLAUDE.md` §“What NOT to do”); no escribir
> `assertThat(...)`. Tests motor por la API pública `Motor.calcularApu` /
> `Motor.consolidar`; **nunca** invocar `motor/internal/*` directamente.

### Documentación (reconciliación activa)

| Archivo | Cambio |
|---|---|
| `plans/README.md` | Entrada Plan 015 con resumen de cierre + nota de que Plan 020 pasa a PLANNED / READY tras 015; eliminar la frase “discounts are per APU”; reemplazar el reclamo “no canonical docs were changed” por la cronología real (Plan 015 + Plan 020, en ese orden). |
| `CLAUDE.md` (backend) | Reemplazar el renglón “descuento / per-APU discount at CD level, %, reversible” por FORMA 1 + FORMA 2; añadir nota apuntando a `plans/015` para la historia retirada. Mantener el guard del motor (sólo cambios estructurales autorizados por el plan en ejecución). |
| `docs/modulos/05-presupuesto/00.md` | Reconciliar §1, §4, §8 (tabla de status) y §10: añadir Plan 015 como prerequisito explícito de Plan 020; cambiar el estado de Plan 020 a “READY once 015 closes” (manteniendo el “BLOCKED hoy” hasta que 015 implemente). Sin tocar las decisiones locked. |
| `docs/modulos/05-presupuesto/02-recalculo-write-through.md` | Reemplazar la mención “el modelo de descuentos es por APU (`apu.porcentaje_descuento`)” por “descuento FORMA 1 (mutación de insumos PROYECTO, MO exenta) + FORMA 2 (edición atómica); seam por APU retirado por Plan 015 (P-24 / S-24 withdrawn)”; añadir un bloque que apunte a Plan 015 como prerequisito. |
| `docs/modulos/03-apu.md` §1, §4 § nota al pie de P-24 | Marcar `PATCH /apus/{id}/descuento` (P-24) como **WITHDRAWN — Plan 015**; preservar el texto histórico como rastro de auditoría (no reescribir). |
| `docs/modulos/04-apu-avanzado.md` §0 + §2.6 + §9 tabla | Marcar P-24 (atajo legacy) como WITHDRAWN — Plan 015; preservar el bloque histórico como rastro de auditoría (no reescribir historia). |
| `docs/modulos/05-presupuesto/06-versionado-comparacion.md` §F deep copy | Quitar la mención de “preserva `porcentaje_descuento` en deep copy”; alinear con `Apu.porcentajeDescuento` retirado (la columna DB inert se ignora en la copia). |
| `docs/03-BASE-DATOS.md` §4/§6 (tabla `apu`) | Cambiar la fila de la columna `porcentaje_descuento` a “inert compatibility seam (DB); JPA ya no la mapea (Plan 015, 2026-09-01)”; conservar `rango_descuento_min/max` como activos (FORMA 1). |
| `docs/00-ESTADO-ACTUAL.md` §matriz de capacidades | Marcar P-24 descuento legacy por APU = **WITHDRAWN — Plan 015**; mantener DISPLAY/A6/A8/A9/ET/P-46 ya DONE. |

> **NO tocar:**
> - `docs/modulos/04-apu-avanzado.md` secciones de “bloque histórico” (N04
>   18-08-2026); sólo se agrega una nota transversal que dice “WITHDRAWN por
>   Plan 015 (2026-09-01); ver §0 + §2.6 + §9”. El cuerpo histórico se
>   conserva para auditoría.
> - `plans/006-motor-consolidacion-fix.md`, `plans/014-motor-precision-no-links.md`
>   (planes cerrados).
> - Los planes dentro de `docs/modulos/planes-para-estar-al-dia/0N-*.md`
>   que **no** mencionen el descuento por APU; los que sí lo mencionen se
>   editan en PR aparte (no en este plan).
> - `application.yml` fuera del bloque `app.display.*` (ya cerrado por
>   Plan 014).
> - Cualquier `V00X__*.sql`.
> - `build.gradle.kts` (sin cambios de dependencias).
> - Tolerancias o expected values de GMs existentes.

### Reconciliaciones permitidas (mínimas) en planes vigentes

> Los siguientes archivos mencionan el descuento por APU y deben
> **reconciliarse** (no reescribirse). Se autoriza una edición única por
> archivo, agregando una nota transversal “**WITHDRAWN por Plan 015
> (2026-09-01)**: `PATCH /apus/{id}/porcentaje-descuento` y
> `apu.porcentaje_descuento` ya no existen; sólo sobrevive FORMA 1
> (mutación de insumos PROYECTO, MO exenta) regulada por
> `rango_descuento_*`” sin reescribir la historia:

| Archivo | Acción |
|---|---|
| `docs/03-BASE-DATOS.md` §6 (tabla `apu`) | nota transversal |
| `docs/modulos/03-apu.md` §1 + §10 | nota transversal en cada mención P-24 |
| `docs/modulos/04-apu-avanzado.md` §0/§2.6/§9/§10 | nota transversal |
| `docs/modulos/05-presupuesto/06-versionado-comparacion.md` §F | nota transversal |
| `docs/00-ESTADO-ACTUAL.md` §4 matriz P-24 + §3 fila 013 | nota transversal |

---

## 3. Out of scope — baneado explícitamente

- Cambiar la aritmética de `Motor.java` más allá de la identidad
  `CD_ajustado ≡ CD` (fórmulas, `MathContext`, orden de operaciones).
- Cambiar tolerancias o expected values de los golden masters (los 22
  verdes siguen verdes; los 2 rojos mantienen residual sub-céntimo
  aceptado).
- Crear el módulo `recalculo` (DEFERRED — `estado-actual.md` §7.1).
- Migraciones Flyway nuevas (incluida V009).
- Añadir/quitar dependencias en `build.gradle.kts` (incluida AssertJ).
- Anotar `@Digits` en campos nuevos para “completar el catálogo”.
- Crear `application-test.yml`.
- Editar los bloques históricos de `04-apu-avanzado.md` (`Bloque histórico
  — no aplicar`).
- Crear un nuevo seam de “descuento global” (la decisión N04 rectificada lo
  prohíbe explícitamente).
- Tocar `display config`, `@Digits`, motor cálculos distintos al CD_ajustado
  (estos ya están cerrados por Plan 014).
- Reabrir `motor/internal/Consolidador.java` (regla workbook-consistent
  vigente).
- Importar `Migración`, `V00X__*.sql` o `pu.porcentaje_descuento` en nuevo
  código de aplicación.

---

## 4. Steps — TDD estricto

> **TDD estricto activo.** Cada paso RED debe observarse fallar antes
> del GREEN. Cada paso GREEN debe observarse pasar antes del siguiente
> paso. No se afirma evidencia que no se haya observado.

### Paso 1 — RED T1 (motor)

Crear `src/test/java/ec/uce/propuestas/motor/DescuentoRetiradoMotorTest.java`
con tres tests JUnit 5:

1. `ApuCalculado_sin_costoDirectoAjustado`: reflexión que asegura que el
   record no expone `costoDirectoAjustado` (`assertThrows(
   NoSuchFieldException.class, () -> ApuCalculado.class.getDeclaredField(
   "costoDirectoAjustado"))`).
2. `ParametrosCalculo_sin_porcentajeDescuento`: análogo para
   `porcentajeDescuento`.
3. `motor_calcula_CI_sobre_CD_y_CT_sobre_CDmasCI`: aritmético, con stub
   APU conocido (reutilizar `Fixtures.apuStub("A", BigDecimal.ONE)` o
   equivalente), `%CI = 0.1800`, `%HM = 0.0500`, sin descuento
   (constructor 2-arg `ParametrosCalculo`):
   - `CI = CD × %CI` esperado a 6 dp.
   - `CT = CD + CI` esperado a 6 dp.

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.DescuentoRetiradoMotorTest' --console=plain
```

**Resultado RED esperado:** los dos primeros fallan por campo aún
presente; el tercero falla por firma de `Motor.calcularApu` y/o
`ParametrosCalculo` con 3 argumentos. Capturar el fallo textual.

### Paso 2 — GREEN T1 (motor + tests colindantes)

1. `motor/ApuCalculado.java`: quitar `BigDecimal costoDirectoAjustado`
   del record; eliminar la línea de javadoc adyacente.
2. `motor/ParametrosCalculo.java`: quitar `BigDecimal porcentajeDescuento`;
   ajustar javadoc y contrato.
3. `motor/Motor.java`: aplicar la edición estructural autorizada en §2
   (“Contrato exacto de `Motor.java`”).
4. `motor/Fixtures.java`: actualizar el helper `param(...)` (constructor
   2-arg o con `BigDecimal.ZERO` para descuento) y limpiar
   `apuStub(...)` de la lectura de `porcentajeDescuento`.
5. `motor/MotorApuTest.java`: ajustar `new ApuCalculado(...)` /
   `new ParametrosCalculo(...)` quitando los argumentos.
6. `motor/MotorConsolidacionTest.java`: ajustar constructores.
7. `motor/MotorPropiedadesTest.java`: reescribir las dos propiedades de
   descuento (ver Paso 3).

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
```

**Resultado GREEN esperado:** los 21 GMs per-APU verdes (CMT workbook
`descuento = 0` y demás fixtures con `descuento = 0` ⇒ `CD ≡ CT − CI`).
GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` (residuales aceptados, sin
regresión vs línea base). GM-21 verde con su allowlist. GM-24
`@Disabled`. `ConsolidadorFronteraTest` 5/5 verde. `SnapshotSinAuxiliaresTest`
6/6 verde.

### Paso 3 — TRIANGULATE T1 (propiedades + invariantes)

Reescribir las dos propiedades de descuento en `MotorPropiedadesTest.java`:

- Eliminar `descuento_no_cambia_CD` y `descuento_10_aplica_sobre_CD`.
- Añadir `costoIndirecto_aplica_sobre_CD_y_depende_de_pct_ci` con la
  semántica:
  - `CI(aplicado 0.18) = CD × 0.18`.
  - `CI(aplicado 0.10) = CD × 0.10` (con %HM fijo, sin MO).
  - `CT = CD + CI` (sin CI residual cuando `pctCi = 0`).
- Mantener 5 propiedades totales (`@Property`, `@ForAll("apuSnapshots")`).
- Ajustar el javadoc del archivo para reflejar la ausencia de descuento.

Re-correr:

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.MotorPropiedadesTest' --console=plain
```

**Resultado esperado:** 5 propiedades verdes (mismas que la línea base en
número, contenido reescrito). La propiedad sustituta queda dentro del
contrato `[APU snapshot cualquiera] × [%CI ∈ [0, 1]] ⇒ CI = CD × pctCi`.

### Paso 4 — RED T2 (contrato APU — DTOs JSON)

Crear `src/test/java/ec/uce/propuestas/apu/DescuentoRetiradoContratoTest.java`
con dos grupos de tests:

**(a) Reflexión sobre DTOs:**

- `ApuResponse_sin_porcentajeDescuento`: `assertThrows(
  NoSuchFieldException.class, () -> ApuResponse.class.getDeclaredField(
  "porcentajeDescuento"))`.
- `ApuCalculoParametros_sin_descuento`: análogo.
- `ApuCalculoResumen_sin_cdAjustado_y_sin_operacionCdAjustado`:
  análogo para ambos campos.

**(b) RestAssured (`@QuarkusTest`) sobre `GET /api/v1/apus/{apuId}` y
`GET /api/v1/apus/{apuId}/calculo`:**

- `GET /api/v1/apus/{id}` → response body **NO** contiene la clave
  `porcentajeDescuento` (vía `bodyKeys`/`jsonPath` con `Map.keySet()`).
- `GET /api/v1/apus/{id}/calculo` → `parametros` tiene exactamente
  `Set.of("hm", "ciDefault", "ciAplicado")`; `resumen` tiene exactamente
  `Set.of("cd", "ci", "ct")`.

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.DescuentoRetiradoContratoTest' --console=plain
```

**Resultado RED esperado:** campos aún presentes (reflexión) y claves JSON
presentes (RestAssured) ⇒ todos los tests fallan. Capturar el fallo textual.

### Paso 5 — GREEN T2

1. `apu/entity/Apu.java`: eliminar campo `porcentajeDescuento`. La
   columna `apu.porcentaje_descuento` queda **inert** (V001 §2.10: `NOT
   NULL DEFAULT 0`; CHECK `BETWEEN 0 AND 0.5000`; JPA ya no la mapea).
2. `apu/dto/ApuResponse.java`: quitar `BigDecimal porcentajeDescuento` del
   record.
3. `apu/dto/ApuCalculoParametros.java`: quitar `BigDecimal descuento` del
   record.
4. `apu/dto/ApuCalculoResumen.java`: quitar `BigDecimal cdAjustado` y
   `String operacionCdAjustado` del record.
5. `apu/mapper/ApuMapper.java`: quitar el argumento en la tupla.
6. `apu/service/ApuDuplicarService.java`: eliminar la línea que copia
   `porcentajeDescuento` del origen; ajustar javadoc.
7. `apu/service/ApuCalculoService.java`:
   - En `recalcular(Apu apu)`: usar el constructor 2-arg de
     `ParametrosCalculo` (`porcentajeHerramientaMenor`,
     `porcentajeIndirecto`). Eliminar la asignación
     `apu.costoTotal = out.costoTotal();` indirectamente al tener el
     mismo costo; verificar que no cambia.
   - En `proyectar(Apu apu)`: idem para `buildParametros` (quitar
     cálculo de `descuento`) y `buildResumen` (quitar `cdAjustado`,
     `factor`, `operacionCdAjustado`).
8. Ajustar `apu/service/ApuDuplicarServiceTest.java` (si existe) para
   no assertar `porcentajeDescuento` en la copia.

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.DescuentoRetiradoContratoTest' --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.*' --console=plain
```

**Resultado esperado:** `DescuentoRetiradoContratoTest` 100% verde;
`ApuCalculoServiceIT`, `ApuCalculoServiceNullableInsumoTest`,
`ResolverInsumoProyectoTest` verde; suite apu parcial verde (con
`ApuResourceIT` ajustado en Paso 7).

### Paso 6 — RED T3 (endpoint REST)

Crear `src/test/java/ec/uce/propuestas/apu/DescuentoEndpointRetiradoTest.java`
(`@QuarkusTest`, fixture mínima):

- Resolver un APU propio del caller (`proyecto` + `presupuesto` + APU).
- `RestAssured` `PATCH /api/v1/apus/{apuId}/porcentaje-descuento`
  con body `0.10` (BigDecimal) → esperar **404**.

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.DescuentoEndpointRetiradoTest' --console=plain
```

**Resultado RED esperado:** status `404` antes del cambio (la ruta ya
devuelve 404 porque JAX-RS no encuentra el handler) **o** `200` si la ruta
todavía existe con descuento aplicado. Capturar el fallo textual: si
devuelve `200` con `porcentajeDescuento = 0.10`, el test falla por la
aserción `404`. (Si el endpoint ya devuelve 404 porque otro plan lo
retiró antes — comprobar `git log` — el RED se cumple trivialmente y se
documenta como “retirado preventivamente”.)

### Paso 7 — GREEN T3

1. `apu/resource/ApuResource.java`: eliminar el método
   `@PATCH @Path("/porcentaje-descuento") public ApuResponse
   actualizarPorcentajeDescuento(...)`.
2. `apu/service/ApuCrudService.java`: eliminar `public ApuResponse
   actualizarPorcentajeDescuento(...)`.
3. Ajustar `src/test/java/ec/uce/propuestas/apu/resource/ApuResourceIT.java`:
   - `TC_P23_P24_porcentajes_actualizan_y_restauran` → renombrar a
     `TC_P23_porcentaje_indirecto_actualiza_y_restaurar` y eliminar el
     bloque `descuento` (líneas ~567, ~585, ~1233).
   - Aserciones `parametros.descuento` (líneas ~1179, ~1245) y
     `resumen.cdAjustado`/`resumen.operacionCdAjustado` (líneas ~1198,
     ~1252): eliminar.
   - Aserciones de contrato del response (línea ~1306 y siguientes):
     cambiar `Set.of("hm", "ciDefault", "ciAplicado", "descuento")` →
     `Set.of("hm", "ciDefault", "ciAplicado")`; cambiar
     `Set.of("cd", "cdAjustado", "operacionCdAjustado", "ci", "ct")` →
     `Set.of("cd", "ci", "ct")`.
   - Mantener 36 tests en la clase.

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.DescuentoEndpointRetiradoTest' --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.*' --console=plain
```

**Resultado esperado:** `DescuentoEndpointRetiradoTest` 1/1 verde;
`ApuResourceIT` (con tests ajustados) verde; suite apu completa
reportada desde XML sin regresión vs línea base 41/41 (+ Ajuste).

### Paso 8 — REFACTOR (baselines + commit)

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.entity.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.identifier.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.schema.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.*' --console=plain
./gradlew spotlessCheck
./gradlew build -x test

grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.motor.*.xml \
    build/test-results/test/TEST-ec.uce.propuestas.apu.*.xml

# Restricciones preservadas
! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'porcentajeDescuento|costoDirectoAjustado|cambios autores no autorizados' \
    src/main/java/ec/uce/propuestas/motor src/main/java/ec/uce/propuestas/apu
! grep -RIn 'patch.*porcentaje-descuento' src/main/java/ec/uce/propuestas/apu \
    src/test/java/ec/uce/propuestas/apu

# Diff saneado
git diff --check
git status --short
```

**Commit único sugerido (Conventional Commit):**

```text
feat(motor,apu): retire per-APU discount seam (P-24/S-24 withdrawn)
```

**Rollback:** `git revert <commit>` revierte producción + tests + docs en
un solo paso. La columna DB `apu.porcentaje_descuento` queda inert en
ambas direcciones (V001 sin cambios).

---

## 5. Done criteria

> **Estado de cierre (2026-09-01) — verificación dirigida completa; suite completa / Spotless / build siguen pendientes (no se reclaman).** Cada item de abajo se reporta con la cifra observada en este pase (los conteos provienen de `build/test-results/test/` cuando aplica).

- [x] `motor/ApuCalculado.java` **sin** `costoDirectoAjustado` (javadoc conserva la nota histórica).
- [x] `motor/ParametrosCalculo.java` **sin** `porcentajeDescuento` (javadoc conserva la nota histórica).
- [x] `motor/Motor.java`: aritmética `CI = CD × %CI`, `CT = CD + CI`; etapa
  `costoDirectoAjustado` retirada; orden de operaciones intacto.
- [x] `apu/entity/Apu.java` **sin** campo `porcentajeDescuento` (la columna
  DB queda inert; no se migra).
- [x] `ApuResponse` **sin** `porcentajeDescuento`; `ApuCalculoParametros`
  **sin** `descuento`; `ApuCalculoResumen` **sin** `cdAjustado` **y sin**
  `operacionCdAjustado`.
- [x] `ApuResource` **sin** `PATCH /porcentaje-descuento`;
  `ApuCrudService` **sin** `actualizarPorcentajeDescuento`;
  `ApuDuplicarService` **sin** copia de `porcentajeDescuento`.
- [x] **Motor (medidos):** `./gradlew test --tests 'ec.uce.propuestas.motor.*'` → **45 tests** totales; **42 verdes** + **2 rojos** (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — residuales
  aceptados, sin cambio vs línea base) + **1 omitido** (GM-24 `@Disabled` por fixture upstream EMELNORTE). Desglose por suite: `MotorApuTest` 21/21, `MotorConsolidacionTest` 2/4 (GM-19, GM-20 rojos; GM-21 verde; GM-24 `@Disabled`), `MotorPropiedadesTest` 5/5 (re-nombrado: `costoIndirecto_aplica_sobre_CD_y_depende_de_pct_ci`), `ConsolidadorFronteraTest` 5/5, `SnapshotSinAuxiliaresTest` 6/6, `DescuentoRetiradoMotorTest` 4/4.
- [x] **APU (medidos):** `./gradlew test --tests 'ec.uce.propuestas.apu.*'` → **49/49 verdes**. Desglose: `DescuentoRetiradoContratoTest` 4/4, `DigitsValidationCatalogTest` 3/3, `ApuResourceIT` 36/36 (TC_P23_porcentaje_indirecto_actualiza_y_restauran ajustado), `DescuentoEndpointRetiradoTest` 4/4, `ApuCalculoServiceIT` 2/2.
- [x] **Contrato descuento (medidos):** 12/12 verdes — `DescuentoRetiradoMotorTest` 4 + `DescuentoRetiradoContratoTest` 4 + `DescuentoEndpointRetiradoTest` 4.
- [x] **Plan 020** implementado (`RecalculoServiceIT` 4/4 verdes; módulo `recalculo` activo con `Alcance = Version | Apu | Insumo`); marcado **DONE** en `plans/README.md` + `docs/modulos/05-presupuesto/00.md` + `02-recalculo-write-through.md`. DAG I-07 desbloqueado: planes 021–025 vuelven a **PLANNED / READY** (sin bloqueo transitivo); siguiente plan ejecutable = **Plan 021** (`03-ciclo-presupuesto.md`).
- [x] **Documentos reconciliados** (notas transversales “WITHDRAWN por Plan 015 — 2026-09-01” en este pase): `plans/README.md`, `CLAUDE.md`, `docs/modulos/05-presupuesto/00.md`, `docs/modulos/05-presupuesto/02-recalculo-write-through.md`, y `../thesis-docs/plan/architecture/08-codebase-design.md` §3 (marcado ACTIVADO en I-07 / Plan 020). Los docs restantes (`03-apu.md`, `04-apu-avanzado.md`, `06-versionado-comparacion.md`, `docs/03-BASE-DATOS.md`, `docs/00-ESTADO-ACTUAL.md`) conservan sus notas transversales existentes; los rastros históricos debajo de la nota no se reescriben.
- [ ] **Pendiente — no se reclama en este pase:** `git diff --check` limpio en todos los repos tocados, `spotlessCheck` global, `./gradlew build -x test`, y `./gradlew test` (suite completa). Los criterios de comandos `grep` específicos (`double|float`, `porcentajeDescuento|costoDirectoAjustado|...`, `assertThat(`) se reportan al cierre completo del pase.

---

## 6. Verification commands (autorizados para este plan)

```bash
# Línea base del motor (T1 / T1 REFACTOR)
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain

# Línea base de APU (T2 / T3 / REFACTOR)
./gradlew test --tests 'ec.uce.propuestas.apu.*' --console=plain

# Regresiones adyacentes
./gradlew test --tests 'ec.uce.propuestas.presupuesto.entity.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.identifier.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.schema.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.*' --console=plain

# Calidad y build sin tests
./gradlew spotlessCheck
./gradlew build -x test

# Conteo real desde XML (no presupuestos)
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-ec.uce.propuestas.motor.*.xml \
  build/test-results/test/TEST-ec.uce.propuestas.apu.*.xml

# Restricciones críticas (deben devolver 0)
! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'porcentajeDescuento|costoDirectoAjustado|porcentaje-descuento|
  cdAjustado|operacionCdAjustado' src/main src/test
! grep -RIn 'assertThat(' src/test

# Diff saneado
git diff --check
git status --short

# Suite completa (autorizada por Plan 018 cierre documental)
./gradlew test --console=plain
```

---

## 7. STOP conditions

Detener y reportar al autor si:

- **Cambia la aritmética del motor** más allá de la identidad
  `CD ≡ CD_ajustado`. Por ejemplo: tocar la frontera APU→Rubro de
  `internal/Consolidador.java` (la regla **workbook-consistent** está
  cerrada y este plan **no la reabre**).
- **GM-19 / GM-20 cambian de signo** respecto a la línea base (residuales
  aceptados `-$6.95` y `-$0.84` respectivamente; Plan 014 §STOP condition
  cerrada por residual aceptado).
- **Se introduce un seam nuevo** para descuento global (la decisión
  rectificada por N04 rectificación 2026-09-01 lo prohíbe).
- **Se crea V009 (o cualquier migración nueva)** para “limpiar”
  `apu.porcentaje_descuento`. Plan 015 deja la columna inert.
- **Se reabre `ParametrosSistema.rango_descuento_*`**. FORMA 1 los usa;
  este plan los conserva.
- **Se renombra `ParametrosCalculo.porcentajeDescuento` a otra cosa** (la
  eliminación es el único cambio autorizado en ese record).
- **Se reabre `ApuSnapshot.esAuxiliar` o `FilaSnapshot.cdAuxiliar`**
  (decisión no-links cerrada).
- **Se elimina el `@Column(name = "porcentaje_descuento", ...)` con
  efectos de esquema en JPA**: si Hibernate genera un `ALTER TABLE` para
  eliminar la columna, se reabre el contrato de migraciones y **STOP**.
  El plan sólo quita el campo Java; la anotación se elimina junto al
  campo.
- **Aparece nuevo archivo** en `motor/` distinto de los 3 listados
  (`ApuCalculado`, `ParametrosCalculo`, `Motor.java`). **STOP** — el
  motor no se reabre; este plan es exclusivamente *seam removal*.
- **Más de un conjunto de cambios aritméticos** requiere abrir
  `internal/Consolidador.java` o `internal/CalculadorFila.java`. **STOP**.
- **Cualquier plan posterior (020+)** quiere reintroducir
  `porcentajeDescuento` en `ParametrosCalculo` o `porcentaje_descuento`
  en `Apu`. **STOP** — abrir un `plans/NNN-redescuento-apu.md` con
  justificación funcional nueva + nota en ambos `CLAUDE.md`.

---

## 8. Maintenance note

- **Regla de modificación futura:** reintroducir el seam requeriría un
  plan nuevo (`plans/NNN-redescuento-apu.md`), nota transversal en
  `CLAUDE.md` (backend) + `thesis-docs/CLAUDE.md`, y ajuste del motor
  bajo las reglas de Plan 014 (workbook-consistent). Mientras tanto, el
  descuento vive **únicamente** en FORMA 1 (mutación de insumos PROYECTO,
  MO exenta, regulada por `rango_descuento_*`) y FORMA 2 (edición atómica
  de insumo ya PROYECTO, sin seam nuevo).
- **DB inert:** `apu.porcentaje_descuento` permanece en V001 §2.10 con
  `NOT NULL DEFAULT 0` y `CHECK (porcentaje_descuento BETWEEN 0 AND
  0.5000)`. JPA no la mapea. V004 (seeds) sigue insertando con valor `0`.
  No se migra.
- **Plan 020 desbloqueado:** al cierre de Plan 015, `02-recalculo-write-through.md`
  deja de mencionar la incompatibilidad `porcentajeDescuento` global vs.
  `apu.porcentaje_descuento` por APU y vuelve a PLANNED / READY.
- **Display y `@Digits`:** ya cerrados por Plan 014. Plan 015 no los
  reabre.
- **GM-19/GM-20:** residuales aceptados (`-$6.95` / `-$0.84`); no se
  reabre el motor. La retirada del descuento **no** cambia el delta vs
  workbook porque el workbook IESS tiene `descuento = 0` en todos los
  APUs del fixture.

---

## 9. Decisión de mayor autoridad (N04 §A1 rectificada, 2026-09-01)

> **No existe descuento por APU.** El descuento vive **únicamente** en
> FORMA 1 (mutación de las columnas base de los **insumos elegibles**
> copiados a la base PROYECTO del proyecto; **MO exenta**, reversible
> desde la base, regulada por `rango_descuento_*`) y FORMA 2 (edición
> atómica de un insumo ya PROYECTO; sin seam nuevo). El motor opera con
> la **precisión natural de `BigDecimal`**; la única rounding del motor
> es la frontera APU→Rubro en `internal/Consolidador.java` con la regla
> **workbook-consistent** (`PU DOWN 2dp`; `PT = cantidad × PU_2dp`
> retenido a escala 6 `HALF_UP`; totales de capítulo y `totalGeneral`
> agregados desde esos `PT` a escala 6; display canónico a 2 dp
> `HALF_UP` solo en presentación/assertion). El display se rige por la
> config global `app.display.precision=2` y
> `app.display.precision-porcentaje=4` (Plan 014 T3, ya cerrado). No se
> reintroduce “monto absoluto” de descuento. La columna
> `apu.porcentaje_descuento` queda como **compatibility seam inert** (no
> se migra; JPA la ignora). El guard del motor se mantiene: ediciones
> distintas a las autorizadas en este plan requieren un
> `plans/NNN-*.md` con justificación funcional + nota en ambos
> `CLAUDE.md`. Esta decisión **supersede** P-24 / S-24 del dossier N04
> original y rectifica §A1 a “mutación de insumos PROYECTO, MO exenta,
> reversible desde la base”.
