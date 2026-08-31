# Plan 023 — Rubros, totales write-through y resumen por componente (P-29, P-30)

> **Plan 023** del módulo [`05-presupuesto`](00.md). PLANNED / READY —
> 2026-08-31. **No implementado todavía.** Cubre P-29 (CRUD de
> rubros: vínculo 1:1 APU↔rubro, cantidad de obra, write-through) y
> P-30 (totales por capítulo recursivos + resumen por componente
> M/N/O/P + IVA referencial + total general).

## Resultado esperado

El usuario puede agregar ítems (rubros) a los capítulos, eligiendo un
APU de la versión (1:1 por D-09) e ingresando la cantidad de obra.
Cada mutación (crear/editar/eliminar rubro) actualiza el `precio_total`
del rubro, el `total` del capítulo y el `totalGeneral` del presupuesto
— todo en una sola transacción vía `recalculo` (Plan 020). El
`GET /presupuestos/{id}/resumen` devuelve los totales M/N/O/P, IVA
referencial y `totalConIva` para alimentar el chart del frontend. La
API REST exige `cantidad > 0` (V007 sólo relaja el CHECK en BD para
reconstrucción estructural desde plantilla — los DTOs P-29 siguen
estrictos).

## Dependencias

- [Plan 019 — Identidad y persistencia](01-identidad-y-persistencia.md).
- [Plan 020 — `recalculo` write-through](02-recalculo-write-through.md).
- [Plan 021 — Ciclo de presupuesto](03-ciclo-presupuesto.md).
- [Plan 022 — Capítulos](04-capitulos.md).
- Módulo `apu/` cerrado (DONE 2026-08-30) — `ApuRepository`,
  `ApuResponse` con `costoTotal`.

## Estado de cierre

**PLANNED / READY — 2026-08-31.** El ejecutor actualiza esta sección al
término. Resultado esperado: «DONE (YYYY-MM-DD). TC-P29-01/02/03 +
TC-P30-01 verdes; totales write-through verificados sobre el árbol
IESS sembrado por V004; `git diff --check` limpio; regresión
APU/capítulos/recalculo/motor verde.»

---

## Contexto / estado actual

### Lo que ya existe

1. **`Rubro` y `RubroRepository`** (Plan 019).
2. **`recalculo` write-through** (Plan 020) con `Alcance.Version`
   (no se introducen `Alcance.Rubro` ni `Alcance.Capitulo`; la
   propagación grano fino del agregado `Presupuesto → Capitulo →
   Rubro` se sirve recorriendo el árbol por alcance de versión).
3. **`CapituloService` + `CapituloResource`** (Plan 022).
4. **`PresupuestoResponse` / `CapituloResponse` / `RubroResponse`**
   (Plan 021) — el árbol se devuelve con totales.
5. **`ApuRepository.findByPublicIdAndOwnerScope`** (módulo `apu`) —
   resuelve APU por UUIDv7 con scope de owner.
6. **`Motor.calcularApu`** y la regla workbook-consistent (Plan 014)
   — `precio_unitario DOWN 2dp`, `precio_total = cantidad × PU_2dp`
   retenido a escala 6 `HALF_UP`.

### Decisiones de diseño locked

- **Vínculo 1:1 APU↔rubro por versión** (D-09): `Rubro.apu_id`
  `UNIQUE` dentro de la versión (porque `apu.codigo` es único por
  versión, el `rubro.codigo` espejo hereda la unicidad transitivamente
  sin desnormalizar `presupuesto_id`).
- **Cantidad de obra (DTO) `> 0`** (v1.1 §3, v1.1 §2.6). La columna
  `rubro.cantidad` admite `>= 0` desde V007 (sólo para reconstrucción
  estructural desde plantilla de proyecto, Plan 06); los DTOs P-29
  **siguen exigiendo > 0** vía `@DecimalMin("0.000001")` y la
  validación RNF-09.
- **No-links entre APUs** (N04 §2 / v1.3 §2.5.6): un rubro sólo
  referencia un APU **ordinario** (sin flag `es_auxiliar`, sin FK
  cruzada). El campo `apu_id` es BIGINT simple.
- **Write-through por `recalculo`** (Plan 020): tras crear/editar/
  eliminar un rubro, se invoca
  `RecalculoService.recalcular(Alcance.Version(presupuestoId))`. El
  orquestador de `Version` recorre el árbol del presupuesto, resuelve
  los APUs afectados (vía `Rubro.apu_id`) y propaga los totales a
  capítulo y presupuesto. **No** se recalcula la
  versión con granularidad de rubro (`Alcance.Rubro` no se introduce);
  el árbol real IESS con 298 rubros sigue sub-segundo
  (`08-codebase-design.md` §2.4).
- **Totales en M/N/O/P** se agregan a partir de los `APU.costo_*`
  que el motor ya cacheó write-through. Para
  `ResumenComponentesResponse` se requiere cargar cada APU de la
  versión y sumar sus `costo_directo * cantidad / Σ cantidades` por
  sección. Se reutiliza `Motor.calcularApu` para producir el
  `ApuCalculado` de cada APU y extraer `secciones[].subtotal`.
- **IVA referencial** sólo se muestra en el resumen (no se persiste
  en el rubro ni en el capítulo; DM §15 fija `iva` como
  `ParametrosProyecto.iva`). El resumen multiplica `totalGeneral ×
  parametros_proyecto.iva` con la precisión natural `BigDecimal` y
  redondeo HALF_UP sólo en presentación (`precisionDinero=2`).

---

## Alcance

### Incluye

- `RubroService` con métodos:
  - `crear(Long capituloId, Long callerUsuarioId,
    RubroCrearRequest)`.
  - `editar(Long rubroId, Long callerUsuarioId,
    RubroPatchRequest)`.
  - `eliminar(Long rubroId, Long callerUsuarioId)`.
- `RubroResource` con 3 endpoints:
  - `POST /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros`
    (P-29 crear).
  - `PATCH /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros/{rubroId}`
    (P-29 editar cantidad).
  - `DELETE /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros/{rubroId}`
    (P-29 eliminar — cascadea actividad, RNF-02).
- `ResumenComponentesResource` con 1 endpoint:
  - `GET /presupuestos/{presupuestoId}/resumen` (P-30).
- DTOs `RubroCrearRequest`, `RubroPatchRequest`,
  `ResumenComponentesResponse`, `ComponenteTotal` (interno al resumen).
- Mapper estático `RubroMapper` (Capitulo/Rubro/Presupuesto siguen
  reusando los mappers de Plan 021/022).
- `Motor.calcularApu` por APU para extraer `secciones[].subtotal`
  (M/N/O/P); agregación por componente.
- Validación `cantidad > 0` en DTO.
- Validación `apu_id` pertenece a la misma versión (no aceptar
  APUs de otra versión del mismo proyecto).
- Validación `capitulo_id` pertenece al presupuesto del path.

### No incluye

- **No** se introduce un endpoint para editar `descripcion`/`unidad`/
  `codigo` del rubro (esos vienen del APU; editar el APU propaga).
- **No** se permite cantidad = 0 vía REST (la columna lo permite en
  BD por V007, pero los DTOs lo rechazan).
- **No** se introduce un endpoint para mover un rubro entre
  capítulos sin recrearlo (PATCH sólo cambia `cantidad`; para
  cambiar `capitulo_id`, usar deep copy + delete o recrear).
- **No** se introduce soft-delete ni archivado de rubros.
- **No** se modifica la columna `rubro.cantidad` (V007 ya aplicado).
- **No** se introduce un endpoint de validación P-32 (vive en
  Plan 025).
- **No** se introduce `presupuesto_id` desnormalizado en `rubro` (la
  unicidad de código viene del `apu.codigo` único por versión).
- **No** se reabre el motor (regla workbook-consistent vigente).

---

## Contrato esperado

### Endpoints (P-29 + P-30)

```text
POST   /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros
       Body: { "apuId": "<UUIDv7>", "cantidad": "125.000000" }
       → 201 PresupuestoResponse (árbol recalculado)
       · 400 validacion (cantidad ≤ 0, APU no pertenece a la versión,
         capítulo no pertenece al presupuesto)
       · 404 no-encontrado  · 409 apu-referenciado (ya vinculado)

PATCH  /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros/{rubroId}
       Body: { "cantidad": "200.000000" }
       → 200 PresupuestoResponse (árbol recalculado)
       · 400 validacion (cantidad ≤ 0)

DELETE /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros/{rubroId}
       → 200 PresupuestoResponse (árbol recalculado)
       · 404 no-encontrado

GET    /presupuestos/{presupuestoId}/resumen
       → 200 ResumenComponentesResponse
       · 404 no-encontrado
```

### Shapes JSON

```jsonc
RubroCrearRequest  { "apuId": "<UUIDv7>", "cantidad": "125.000000" }
RubroPatchRequest  { "cantidad": "200.000000" }

ResumenComponentesResponse {
  "porComponente": {
    "EQUIPO":     "1234.560000",
    "MANO_OBRA":  "7890.120000",
    "MATERIAL":   "34567.890000",
    "TRANSPORTE": "456.780000"
  },
  "totalGeneral":   "44149.350000",
  "ivaReferencial": "6622.402500",
  "totalConIva":    "50771.752500"
}
```

> Los totales del resumen se serializan a `precisionDinero` dp sólo
> en presentación (display global 2 dp por defecto — Plan 014 T3).
> La persistencia es `NUMERIC(14,6)`.

### Reglas de error

| Caso | Código | `type` |
|---|---|---|
| `presupuestoId`/`capituloId`/`rubroId` UUIDv7 malformado | 400 | `validacion` |
| `apuId` (en crear) UUIDv7 malformado o no-v7 | 400 | `validacion` |
| `cantidad <= 0` (v1.1 §2.6) | 400 | `validacion` |
| APU ya vinculado a otro rubro de la versión (D-09) | 409 | `apu-referenciado` |
| APU pertenece a otra versión | 400 | `validacion` |
| Capítulo pertenece a otro presupuesto | 400 | `validacion` |
| Proyecto ajeno o inexistente | 404 | `no-encontrado` |
| Recurso ajeno o inexistente | 404 | `no-encontrado` |

Sin 403 en ningún caso (RNF-05).

---

## Pasos

1. **RED — `RubroRepositoryIT.vinculo_unico_por_version`**:
   - Crear 2 rubros que apunten al mismo `apu_id` en la misma
     versión → segundo INSERT falla por UNIQUE constraint.
   - Verificar el UNIQUE constraint con un test de integración
     (no mock).
2. **RED — `RubroServiceIT.crear_propagates_totales`**:
   - Sembrar proyecto + presupuesto v1 + capítulo + APU con
     `costo_total` conocido (ej. CT = 5.5352325).
   - `RubroService.crear(capituloId, caller,
     { apuId, cantidad: 100 })`.
   - Tras la operación, `rubro.precio_unitario = 5.53` (DOWN 2dp),
     `rubro.precio_total = 100 × 5.53 = 553.000000` (escala 6
     HALF_UP), `capitulo.total = 553.000000`,
     `presupuesto.total = 553.000000`. La regla workbook-consistent
     se respeta sin reintroducir `setScale(2, DOWN)` simétrico.
3. **GREEN — `RubroService.crear`**:
   - Validar `cantidad > 0`.
   - Validar `apuId` pertenece a la versión
     (`ApuRepository.findByPublicIdAndPresupuesto(...)` que devuelve
     vacío si la FK presupuestal del APU no coincide).
   - Validar `capituloId` pertenece al `presupuestoId` del path.
   - Verificar UNIQUE `rubro.apu_id` antes del INSERT (defensa
    defensa temprana, evita exception fea de BD).
   - Insertar rubro con `precio_unitario = APU.costoTotal.setScale(2,
     RoundingMode.DOWN)`, `precio_total = cantidad.setScale(6,
     RoundingMode.HALF_UP) × precioUnitario` (delegar al helper del
     motor / `internal/Consolidador` — **NO** se duplica la regla).
   - `RecalculoService.recalcular(Alcance.Version(presupuestoId))`
     (decisión recomendada: `Version` porque la mutación cambia el
     árbol y los totales ascendentes — `Alcance.Rubro` no se
     introduce; la propagación grano fino vive dentro del orquestador
     de `Version`).
4. **GREEN — `RubroService.editar`** con PATCH semántica: campo
   omitido = no-op; `cantidad: 0` o negativo = 400.
5. **GREEN — `RubroService.eliminar`**:
   - Borrar rubro.
   - Cascade `actividad.rubro_id` (RNF-02 sincronía presupuesto↔
     cronograma). El cronograma existe o no; si existe, la actividad
     desaparece (1:1).
   - `RecalculoService.recalcular(Alcance.Version(presupuestoId))`.
6. **GREEN — `RubroResource`** con 3 verbos + `@PathParam` UUIDv7
   validados en frontera. Las mutaciones devuelven el
   `PresupuestoResponse` completo (árbol recalculado).
7. **RED — `ResumenComponentesServiceIT.distribuye_por_seccion`**:
   - Sembrar proyecto + presupuesto + 2 capítulos con 4 rubros
     cada uno (cada APU con secciones M/N/O/P bien conocidas).
   - `GET /presupuestos/{id}/resumen` (vía servicio directo).
   - El `porComponente` suma correctamente: para cada rubro, calcular
     `ApuCalculado` con `Motor.calcularApu(...)`, extraer
     `secciones[].subtotal`, multiplicar por `cantidad`, agregar al
     bucket correspondiente.
   - El `totalGeneral` coincide con el `presupuesto.total` calculado
     por `RecalculoService` tras crear los rubros (test cruzado).
8. **GREEN — `ResumenComponentesService`**:
   - Carga todos los rubros + APUs de la versión.
   - Para cada rubro, calcula el APU con `Motor.calcularApu(...)` (o
     reutiliza `ApuCalculado` cacheado si I-08 lo introduce).
   - Agrega por sección y multiplica por `cantidad`.
   - Multiplica por `iva` del proyecto para `ivaReferencial`.
   - Devuelve `ResumenComponentesResponse` con totales a 6 dp.
9. **GREEN — `ResumenComponentesResource`** con `@GET
   /presupuestos/{presupuestoId}/resumen` (UUIDv7 validado).
10. **TRIANGULATE — `RubroResourceIT` + `ResumenComponentesResourceIT`**
    end-to-end:
    - `TC-P29-01` crear rubro y verificar propagación de totales.
    - `TC-P29-02` APU ya vinculado → 409.
    - `TC-P29-03` cantidad = 0 → 400.
    - `TC-P30-01` resumen con árbol IESS sembrado → totales
      coinciden con GM-19/GM-20 dentro del residual aceptado
      (`-$6.95` / `-$0.84`).
    - UUIDv7 v4 en path → 400.
    - Recurso ajeno → 404.
11. **Verificación dirigida**:
    - `./gradlew test --tests 'ec.uce.propuestas.presupuesto.*'
      -Dquarkus.http.test-port=0 --console=plain` → verde.
    - `./gradlew test --tests 'ec.uce.propuestas.apu.*'
      -Dquarkus.http.test-port=0 --console=plain` → regresión APU
      verde.
    - `./gradlew test --tests 'ec.uce.propuestas.recalculo.*'
      -Dquarkus.http.test-port=0 --console=plain` → regresión
      recalculo verde.
    - `./gradlew test --tests 'ec.uce.propuestas.motor.*'
      -Dquarkus.http.test-port=0 --console=plain` → regresión motor
      verde.
    - `./gradlew build -x test --console=plain` →
      BUILD SUCCESSFUL.
    - `git diff --check` → sin salida.
12. **Commit unitario** con mensaje
    `feat(presupuesto): Plan 023 rubros 1:1 APU, write-through y
    resumen M/N/O/P`.

---

## Pruebas y comprobaciones

```bash
# Verificación dirigida
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Regresiones obligatorias
./gradlew test --tests 'ec.uce.propuestas.apu.*' \
    -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' \
    -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.motor.*' \
    -Dquarkus.http.test-port=0 --console=plain

# Build sin tests
./gradlew build -x test

# Conteo real
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
    build/test-results/test/TEST-ec.uce.propuestas.presupuesto.*.xml

git diff --check
git status --short
```

**Resultado esperado** (cifras al cierre): TC-P29-01/02/03 +
TC-P30-01 verdes; totales write-through verificados sobre árbol
sembrado; regresión APU/capítulos/recalculo/motor verde; `git diff
--check` limpio.

---

## Criterios de terminado

- [ ] CRUD de rubros con UUIDv7, owner-scope, errores canónicos.
- [ ] Cantidad de obra > 0 validada en DTO.
- [ ] Vínculo 1:1 APU↔rubro verificado (UNIQUE activo).
- [ ] APU validado que pertenece a la misma versión.
- [ ] Write-through de `precio_total`/`capitulo.total`/
  `presupuesto.total` vía `recalculo` en cada mutación.
- [ ] Regla workbook-consistent respetada en la frontera
  APU→Rubro (delegada al helper del motor).
- [ ] Resumen por componente con IVA referencial.
- [ ] Tests TC-P29-01/02/03 + TC-P30-01 verdes.
- [ ] Regresión APU/capítulos/recalculo/motor verde.
- [ ] `git diff --check` limpio.
- [ ] Plan 024 (versionado) puede hacer deep copy del árbol
  completo.

---

## STOP conditions (específicas de este plan)

- **(A)** Si el resumen por componente requiere calcular el APU
  para 298 rubros y tarda > 1 segundo, **STOP** — I-08 introducirá
  cache de `ApuCalculado` por APU; mientras tanto, decidir si el
  resumen se construye on-demand o sólo cuando hay un cambio.
- **(B)** Si la diferencia entre el resumen calculado y el
  `presupuesto.total` calculado por `recalculo` excede el residual
  aceptado (`-$6.95` / `-$0.84`), **STOP** — el cálculo del
  resumen debe usar el mismo motor con la misma regla; documentar
  la diferencia si existe.
- **(C)** Si la validación `apuId pertenece a la versión` requiere
  consultar `Apu.presupuestoId` y ese campo no se expone en la
  entidad actual, **STOP** — añadir el campo al `ApuRepository`
  (no requiere migración; ya existe en la BD).
- **(D)** Si los totales del resumen no coinciden con
  `presupuesto.total` por redondeo, **STOP** — la respuesta del
  resumen debe usar la misma escala (`NUMERIC(14,6)`); el display
  aplica el redondeo a 2 dp. Documentar la invariante.

---

## Siguiente plan ejecutable

[`06-versionado-comparacion.md`](06-versionado-comparacion.md) (Plan
024) — deep copy de versión, marcar vigente, comparación de
versiones, eliminación con protección de la vigente. Depende de
019–023.
