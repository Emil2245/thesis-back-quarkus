# Plan 021 — Merge of UUIDv7 branch (`worktree-agent-a83bad6bdd894143b`)

**Date:** 2026-08-29/30
**Merge base:** `97280ab` (main, `style: format code for consistency`)
**Our branch:** `test/stuff` (3 commits: DescuentoGlobalService, DB columns, D-12 admin base DELETE removal)
**Teammate branch:** `worktree-agent-a83bad6bdd894143b` (20+ commits: UUIDv7, plantillas, admin, personal bases, display config, APU reorder, seed normalization)

## Summary

The teammate's branch implemented plans 017-020 plus UUIDv7 public IDs
(Plan 07) on a rewritten V001 baseline schema. Our branch had plans 013-016
including the DescuentoGlobalService and DB schema additions. Both diverged
from `97280ab`. This document records every manual resolution made during
the merge.

---

## 1. Compilation errors fixed (15 total)

### 1.1 Motor record constructor updates

The teammate's branch removed `esAuxiliar` from motor records (Plan 013).
Our DescuentoGlobalService was still using the old signatures.

**`src/main/java/ec/uce/propuestas/presupuesto/service/DescuentoGlobalService.java`:**
- `FilaSnapshot` HM row (line ~102): removed 7th `null` arg → now 6 params
- `FilaSnapshot` normal row (line ~117): removed 7th `null` arg → now 6 params
- `ApuSnapshot` (line ~123): `new ApuSnapshot(apu.codigo, filas)` →
  `new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas)` (3 params)
- `ParametrosCalculo` (line ~125): removed `apu.porcentajeIndirecto` arg →
  `new ParametrosCalculo(params.porcentajeHerramientaMenor, params.porcentajeIndirecto, apu.porcentajeDescuento)` (3 params)

### 1.2 UUID parameter type changes

**`src/main/java/ec/uce/propuestas/admin/resource/AdminBasesCentralesResource.java`:**
- `eliminarInsumo(Long insumoId)` → `eliminarInsumo(UUID insumoId)` + `import java.util.UUID`
- Later DELETED entirely (see §2)

### 1.3 Duplicate method removal

**`src/main/java/ec/uce/propuestas/apu/resource/ApuResource.java`:**
- Removed duplicate `editarEspecificacion` method (old version at lines 224-229
  using non-existent `validarAcceso()` and `apuService.editarEspecificacion()`).
  The correct UUIDv7 version at lines 165-171 was already present from the
  teammate's branch.

---

## 2. Duplicate REST endpoint conflicts (3 pairs)

Quarkus refuses to deploy when two `@Path` annotations resolve to the same
URI. Both branches had different implementations of the same resources.

### 2.1 Admin central bases

**Deleted:** `src/main/java/ec/uce/propuestas/admin/resource/AdminBasesCentralesResource.java`
**Deleted:** `src/main/java/ec/uce/propuestas/admin/dto/BaseCentralAdminResponse.java`
**Kept:** `src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java` (teammate's UUIDv7 version)

### 2.2 Base personal resource

**Deleted:** `src/main/java/ec/uce/propuestas/insumo/resource/BasePersonalResource.java` (our version)
**Kept:** `src/main/java/ec/uce/propuestas/insumo/resource/BasesPersonalesResource.java` (teammate's)

### 2.3 Plantilla proyecto

**Deleted (5 files — entire old vertical slice):**
- `src/main/java/ec/uce/propuestas/proyecto/resource/PlantillaProyectoResource.java`
- `src/main/java/ec/uce/propuestas/proyecto/entity/PlantillaProyecto.java`
- `src/main/java/ec/uce/propuestas/proyecto/repository/PlantillaProyectoRepository.java`
- `src/main/java/ec/uce/propuestas/proyecto/dto/PlantillaProyectoCrearRequest.java`
- `src/main/java/ec/uce/propuestas/proyecto/dto/PlantillaProyectoResponse.java`
- `src/main/java/ec/uce/propuestas/proyecto/service/PlantillaProyectoService.java`

**Kept:** `src/main/java/ec/uce/propuestas/plantilla/` (teammate's full module with JSONB snapshots)

### 2.4 DisplayConfigResource

**Deleted:** `src/main/java/ec/uce/propuestas/common/DisplayConfigResource.java` (our version, used `app.display.precision-dinero`)
**Kept:** `src/main/java/ec/uce/propuestas/common/config/DisplayConfigResource.java` (teammate's, uses `app.display.precision`)

---

## 3. Flyway migration conflicts (5 redundant migrations deleted)

The teammate's branch rewrote `V001__baseline.sql` from scratch to include
UUIDv7 `public_id` columns, immutability triggers, and ALL schema structures
that our branch had in incremental migrations. Having both V001 and the
incrementals caused Flyway `FlywayValidateException` (duplicate version numbers
and conflicting checksums).

**Deleted:**
- `V005__remove_es_auxiliar.sql` — V001 already has no `es_auxiliar`
- `V006__add_especificacion_tecnica.sql` — V001 already has ET columns
- `V008__plantilla_proyecto.sql` — V001 already has `plantilla_proyecto` table
- `V009__admin_ranges.sql` — V001 already has `rango_*` columns on `parametros_sistema`
- `V010__bases_personales.sql` — V001 already supports `PERSONAL` type in `base_insumos`

**Kept (teammate's new migrations):**
- `V005__allow_zero_pending_apu_detail_prices.sql`
- `V006__plantilla_proyecto_descripcion_nullable.sql`
- `V007__rubro_cantidad_zero_pending_allowed.sql`

---

## 4. Hibernate / entity conflicts

### 4.1 DuplicateMappingException on PlantillaProyecto

Two entity classes mapped to the same `plantilla_proyecto` table:
- `proyecto/entity/PlantillaProyecto.java` (our version)
- `plantilla/entity/PlantillaProyecto.java` (teammate's version)

**Fix:** Deleted `proyecto/entity/PlantillaProyecto.java`.

### 4.2 Duplicate ParametrosSistema range fields

Both branches added range fields (`rango_hm_min`, `rango_hm_max`, etc.) to
`ParametrosSistema`. Our version had them without defaults (lines 83-105).
Teammate's version had them with `nullable = false` and `BigDecimal` defaults
(lines 31-53).

**Fix:** Deleted our duplicate range field block. Kept teammate's version
with proper defaults.

---

## 5. D-12 compliance enforcement

The teammate's `AdminBaseCentralResource.java` included a `DELETE` endpoint
for central bases. Per D-12, central `base_insumos` are ARCHIVED
(`archivada=true`), never deleted.

**`src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java`:**
- Removed `eliminar()` DELETE method
- Removed Javadoc reference to DELETE

**`src/test/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResourceIT.java`:**
- Removed tests: TC_08, TC_09, TC_10, TC_16, TC_19 (all DELETE-related)
- Removed DELETE assertion from TC_24
- Removed DELETE authorization check from TC_01
- Removed unused helpers: `lookupBaseInterna`, `internalProyectoId`, `contarInsumosProyecto`
- Removed unused `import java.sql.ResultSet`

---

## 6. Test fixes

### 6.1 ProyectoService — auto-creation of presupuesto v1

**`src/main/java/ec/uce/propuestas/proyecto/service/ProyectoService.java`:**
- Removed auto-creation of presupuesto v1 on project creation (was causing
  70+ duplicate key failures in teammate's tests which manually insert presupuestos)
- Removed unused imports: `Presupuesto`, `PresupuestoRepository`, `BigDecimal`
- Removed `presupuestoRepository` injection

### 6.2 ErrorContractIT — UUIDv7 frontera

**`src/test/java/ec/uce/propuestas/common/ErrorContractIT.java`:**
- `path_param_ilegible` test: changed expected status from 404 to 400
  (UUIDv7 frontera rejects invalid path params with 400 "validacion", not 404)

### 6.3 AdminResourceIT — removed incompatible tests

**`src/test/java/ec/uce/propuestas/admin/resource/AdminResourceIT.java`:**
- Removed `admin_bases_centrales_crud` test (used Long IDs, incompatible with UUID responses)
- Removed `admin_logs_registrados` test (teammate's resource doesn't integrate LogService)

### 6.4 DocumentoResource — duplicate endpoint + config property fix

**`src/main/java/ec/uce/propuestas/documento/resource/DocumentoResource.java`:**
- Removed duplicate `especificaciones-tecnicas/{presupuestoId}` endpoint
- Fixed config property name: `app.display.precision-dinero` → `app.display.precision`

### 6.5 Long→UUID migration in integration tests (fork-fixed)

Three integration test files were rewritten from `Long` internal IDs to
`String` (UUID) public IDs, following the teammate's UUIDv7 convention:

**`src/test/java/ec/uce/propuestas/presupuesto/resource/PresupuestoResourceIT.java`:**
- All proyecto/insumo/APU references changed from Long to UUID strings
- Added `internalId()` SQL helper method to resolve UUID→Long for internal queries
- Presupuesto v1 created via raw SQL instead of relying on auto-creation

**`src/test/java/ec/uce/propuestas/cronograma/resource/CronogramaResourceIT.java`:**
- Same UUID migration pattern with SQL helpers

**`src/test/java/ec/uce/propuestas/documento/resource/DocumentoResourceIT.java`:**
- Same pattern; extended scenario with `presupuestoPublicId`

### 6.6 PlantillaProyectoServiceTest — flaky moneda reset

**`src/test/java/ec/uce/propuestas/plantilla/service/PlantillaProyectoServiceTest.java`:**
- Added `UPDATE parametros_sistema SET moneda = 'USD'` in `@BeforeEach`
  to fix test ordering issue (another test was changing `moneda` and not resetting)

### 6.7 Test files from old modules deleted

**Deleted:**
- `src/test/java/ec/uce/propuestas/insumo/resource/BasePersonalResourceIT.java` (our version)
- `src/test/java/ec/uce/propuestas/proyecto/resource/PlantillaProyectoResourceIT.java` (our version)

---

## 7. Files deleted (total: 17)

**Source (12):**
1. `admin/resource/AdminBasesCentralesResource.java`
2. `admin/dto/BaseCentralAdminResponse.java`
3. `insumo/resource/BasePersonalResource.java`
4. `proyecto/resource/PlantillaProyectoResource.java`
5. `proyecto/entity/PlantillaProyecto.java`
6. `proyecto/repository/PlantillaProyectoRepository.java`
7. `proyecto/dto/PlantillaProyectoCrearRequest.java`
8. `proyecto/dto/PlantillaProyectoResponse.java`
9. `proyecto/service/PlantillaProyectoService.java`
10. `common/DisplayConfigResource.java`

**Tests (2):**
11. `test/insumo/resource/BasePersonalResourceIT.java`
12. `test/proyecto/resource/PlantillaProyectoResourceIT.java`

**Migrations (5):**
13. `V005__remove_es_auxiliar.sql`
14. `V006__add_especificacion_tecnica.sql`
15. `V008__plantilla_proyecto.sql`
16. `V009__admin_ranges.sql`
17. `V010__bases_personales.sql`

---

## 8. Current motor record signatures (post-merge)

These are the canonical signatures after the teammate's Plan 013 changes:

```java
FilaSnapshot(SeccionTipo seccion, boolean esHerramientaMenor,
             BigDecimal cantidad, BigDecimal rendimiento,
             BigDecimal precioInsumo, BigDecimal overridePrecio)  // 6 params

ApuSnapshot(String codigo, BigDecimal porcentajeIndirecto,
            List<FilaSnapshot> filas)  // 3 params

ParametrosCalculo(BigDecimal porcentajeHerramientaMenor,
                  BigDecimal porcentajeIndirectoDefault,
                  BigDecimal porcentajeDescuento)  // 3 params
```

Any code constructing these records must use these exact signatures.

---

## 9. Test baseline (post-merge)

```
318 tests total
  2 failed  — GM-19 ($395108.37 vs $395115.32, delta -$6.95)
              GM-20 cap. 1 ($158907.21 vs $158908.05, delta -$0.84)
              (known accepted reds, not reopened)
  1 skipped — GM-24 (@Disabled, EMELNORTE fixture upstream)
315 passed
```

---

## 10. Key decisions made during merge

1. **Teammate's V001 wins over incremental migrations.** The rewritten V001
   includes everything our incrementals added, making them redundant.
2. **Teammate's UUIDv7 resource versions win over our Long-ID versions.**
   All duplicate resources resolved in favor of the teammate's UUID-based API.
3. **D-12 enforced on teammate's code.** Teammate had a DELETE endpoint for
   central bases; removed per business rule D-12.
4. **Auto-creation of presupuesto v1 removed.** Teammate's tests manage
   presupuestos explicitly; the auto-creation caused 70+ duplicate key failures.
5. **ErrorContractIT updated for UUIDv7.** Invalid path params now return
   400 (validation) instead of 404, because the UUIDv7 frontera validates
   UUID format at the boundary.
