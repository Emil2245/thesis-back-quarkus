# Plan 04 — Módulo `apu-avanzado` (P-23…P-27, P-45, P-46 + decisiones N04) — I-06

> **Estado actual (reconciliado 2026-08-28 tras entrevista N04 temporal):**
> este documento **ya no describe trabajo activo**. Su contenido original
> queda aquí **solo como historial de decisiones**; las instrucciones activas
> están en [`docs/modulos/planes-para-estar-al-dia/`](planes-para-estar-al-dia/)
> (planes 01–08) y en [`docs/modulos/estado-actual.md`](estado-actual.md) §4.
>
> **Decisión no-links (N04 temporal):** NO existen enlaces entre APUs.
> P-25, `es_auxiliar`, `apu_auxiliar_id`, `apuAuxiliarId`, `cdAuxiliar`,
> `CAMBIO_AUXILIAR` y `ApuValidacionService` quedan **superados**. Un APU es
> siempre un análisis ordinario independiente; un supuesto "auxiliar" se
> modela como otro APU/rubro independiente. Detalle y rationale:
> [`docs/modulos/estado-actual.md`](estado-actual.md) §2.1 y
> `../thesis-docs/DOCUMENTOS/entrevistas/04/temporal/Respuesta_Entrevista_N04_TERMPORAL.md`
> §2.
>
> **Librería documental:** la exportación de Especificaciones Técnicas usa
> **Apache POI** (`poi-ooxml`), ya presente en dependencias; no se usa
> docx4j.
>
> **Módulo profundo `recalculo`:** **DEFERRED**. No existe ni se crea en
> esta etapa. Las decisiones de propagación global (%HM, %CI default,
> descuentos, write-through) requieren ese módulo, que queda fuera del
> alcance actual.
>
> **Capacidades actuales** (cruzadas con [`estado-actual.md`](estado-actual.md)
> §4): %CI y descuento legacy por APU → DONE; duplicar APU → DONE; ET
> (Apache POI) → DONE; rangos configurables → DONE; bases PERSONALES +
> copia al usar → DONE; desglose de cálculo → PARTIAL; plantillas APU →
> MISSING (Plan 04); plantilla de proyecto → MISSING (Plan 06);
> consolidación APU→Rubro **workbook-consistent** (regla aplicada) →
> **PARTIAL — CIERRE CON RESIDUO ACEPTADO (2026-08-28)** (Plan 02; GM-19
> `-$6.95` y GM-20 cap. 1 `-$0.84`); UUIDv7 en módulos actuales →
> PARTIAL (Plan 07); write-through global → DEFERRED.
>
> ---
>
> **Bloque histórico (no aplicar):** secciones siguientes reproducen el
> plan original N04 (18-08-2026, Ing. Carlosama) como rastro de auditoría.
> Las instrucciones activas son las de
> [`planes-para-estar-al-dia/`](planes-para-estar-al-dia/).

## 0. Alcance histórico (N04 18-08-2026, antes del ajuste temporal)

> **Bloque histórico — no aplicar.** Reproduce la tabla original del plan
> N04 antes de la decisión no-links. Conservar solo como auditoría.
> Para el alcance vigente, ver
> [`planes-para-estar-al-dia/`](planes-para-estar-al-dia/).

**Procesos P-xx que entran en I-06 (N04 original):**

| Proceso | Tema | Estado actual |
|---|---|---|
| P-23 | %CI override por rubro (heredando default) | **DONE** en `ApuCrudService` (Plan 03 contrato; propagación global = DEFERRED) |
| P-24 | Descuento CD por rubro (campo legacy `APU.porcentaje_descuento`) | **DONE** (`PATCH /apus/{id}/porcentaje-descuento`) |
| P-25 | Rubro auxiliar (`es_auxiliar`, `apu_auxiliar_id`) — **validación sin anidamiento** | **OBSOLETO/SUPERSEDED** — N04 temporal elimina los enlaces entre APUs |
| P-26 | Plantillas personales + carga con fallback | **MISSING** — Plan 04 ([planes-para-estar-al-dia/04](planes-para-estar-al-dia/04-plantillas-apu.md)) |
| P-27 | Desglose de cálculo (`ApuCalculoResponse`) | **PARTIAL** — Plan 03 |
| P-45 | **NUEVO** — Especificaciones Técnicas por APU | **DONE** (Apache POI) |
| P-46 | **NUEVO** — Plantilla de proyecto completo | **MISSING** — Plan 06 ([planes-para-estar-al-dia/06](planes-para-estar-al-dia/06-plantillas-proyecto.md)) |
| `POST /apus/{id}/duplicar` | Decisión dossier §B.7 (opción a) | **DONE** |
| Módulo `recalculo` | Decisión dossier §B.6 (write-through al cambiar parámetros) | **DEFERRED** — no se crea módulo nuevo en esta etapa |
| Rangos parametrizables | Decisión N04 §A6 | **DONE** |
| `CALC_PRECISION` / `DISPLAY_PRECISION` | Decisión N04 §#7 | **WITHDRAWN 2026-08-28** — Plan 014 retira `CALC_PRECISION` del motor (precisión natural `BigDecimal`); display global `precisionDinero=2` / `precisionPorcentaje=4` vía `app.display.*` + `GET /api/v1/config/display`. **Corrección workbook-consistent 2026-08-28:** frontera APU→Rubro aplica `RoundingMode.DOWN` 2 dp **solo** a `precioUnitario`; `precioTotal = cantidad × PU_2dp` se retiene a la escala de persistencia 6 (`NUMERIC(14,6)`) con `HALF_UP` (sin truncar cada PT a 2 dp); capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6. Plan 02 activo lo cubre ahora ([planes-para-estar-al-dia/02](planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)). |
| Base `PERSONAL` | Decisión N04 §A9 | **DONE** |
| Reordenamiento filas/secciones | Decisión N04 §A3 | **PARTIAL** — Plan 03 |

**Procesos que NO entran (quedan para iteraciones futuras):**

- P-39 admin edición/eliminación de bases CENTRALES (admin Super-Admin) →
  Plan 05 ([planes-para-estar-al-dia/05](planes-para-estar-al-dia/05-administracion-bases.md)).
- Reordenamiento con drag-and-drop visual (Gantt, presupuesto) → fuera del
  MVP.
- Selección múltiple y operaciones bulk en APU → fuera del MVP.
- **Cualquier plan 02–08** descrito en
  [`planes-para-estar-al-dia/`](planes-para-estar-al-dia/) **no está
  implementado**: este doc solo conserva su rastro histórico.

## 1. Arquitectura de empaquetado (N04 original — bloque histórico)

> **Bloque histórico — no aplicar.** Las decisiones de empaquetado de
> N04 original (módulos `recalculo`, DTOs auxiliares, etc.) **no se
> implementan** en esta etapa. El árbol real del backend se mantiene en
> los módulos existentes (`apu`, `plantilla`, `proyecto`, `insumo`,
> `documento`).

```
ec/uce/propuestas/
├── apu/                          (continúa desde I-05; ver 03-apu.md)
│   ├── dto/  (sin ApuDetalleAuxiliarRequest; sin ApuValidacionService)
│   ├── service/  (ApuCalculoService local + ApuDuplicarService + ET via documento)
│   └── resource/
├── plantilla/                    (extender sin crear submódulos profundos)
│   └── entity/PlantillaProyecto.java  (P-46 — MISSING, Plan 06)
└── documento/                    (extender para ET Word — Apache POI, no docx4j)
    └── service/
        └── EspecificacionesTecnicasService.java    (genera .docx por proyecto)
```

**Schema (N04 original — bloque histórico):**

- V005 ya **no se contempla** para esta etapa; el reader actual tolera
  campos extra del seed V004 (opción a) y los planes futuros deciden
  aisladamente.
- ET (`apu.especificacion_tecnica`) y PERSONAL
  (`base_insumos.usuario_id`, `tipo_base = PERSONAL`) ya están aplicados
  en V001–V004.
- Rangos parametrizables ya están aplicados en `parametros_sistema`/
  `parametros_proyecto` (Plan 01 cerrado).
- No se crea paquete nuevo `recalculo/` — el write-through global está
  **DEFERRED**. La propagación local por APU la realiza
  `apu.service.ApuCalculoService.recalcular(apu)` (instancia del propio
  APU), no un servicio global.

## 2. Servicios nuevos / ampliados (N04 original — bloque histórico)

> **Bloque histórico — no aplicar.** Se conserva para auditoría de las
> decisiones N04 que quedaron superadas o ya integradas. Las instrucciones
> activas viven en [`planes-para-estar-al-dia/`](planes-para-estar-al-dia/).

### 2.1 `ApuValidacionService` — OBSOLETO/SUPERSEDED

> La decisión no-links elimina cualquier validación de anidamiento
> auxiliar. **No crear** `ApuValidacionService.validarFilaAuxiliar`.
> No exponer `es_auxiliar`, `apu_auxiliar_id`, `apuAuxiliarId`,
> `cdAuxiliar` ni `CAMBIO_AUXILIAR`. Un APU no puede referenciar a otro
> APU desde sus filas; `apu_detalle.apu_auxiliar_id` no existe.

### 2.2 `ApuCalculoService` (P-27 — parcialmente activo)

Devuelve `ApuCalculoResponse` con el desglose (dossier §B.8):

```java
record ApuCalculoResponse(
  long apuId, String codigo,
  ParametrosEfectivos parametros,    // {hm, ciDefault, ciAplicado, descuento}
  List<SeccionDesglose> secciones,  // EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE
  ResumenResumen resumen            // {cd, cdAjustado, operacionCdAjustado, ci, ct}
)
```

Cada `SeccionDesglose` y cada `LineaDesglose` expone `operacion` (cadena
legible: `"0.500000 × 4.750000 × 0.500000"`) y `resultado` (BigDecimal a 6 dp).
El cálculo reutiliza `Motor.calcularApu` (puro, sin I/O) — el servicio
**no** recalcula; solo proyecta el resultado a la estructura del response.

**Precisión:** la aplicación de `precisionDinero=2` / `precisionPorcentaje=4`
sobre los `resultado` del response (display/export) se rige por la config
global `app.display.*` y el endpoint `GET /api/v1/config/display`.
El motor **no** aplica redondeo intermedio: usa la precisión natural de
`BigDecimal`. La frontera APU→Rubro (`internal/Consolidador.java`,
autorizada por [`plans/014`](../../plans/014-motor-precision-no-links.md)
2026-08-28; corrección workbook-consistent) aplica `RoundingMode.DOWN`
2 dp **solo** a `precioUnitario`; `precioTotal = cantidad × PU_2dp` se
retiene a la escala de persistencia 6 (`NUMERIC(14,6)`) con `HALF_UP`
en esa frontera de resultado (sin truncar cada PT a 2 dp); capítulo y
`totalGeneral` agregan esos `precioTotal` a escala 6; el display
canónico a 2 dp `HALF_UP` ocurre solo en la capa de presentación/
assertion.
El estado actual del desglose es **PARTIAL** (ver
[`estado-actual.md`](estado-actual.md) §4).

### 2.3 `ApuDuplicarService` (DONE — sección activa)

```
APU duplicar(long apuId, long duenoId, boolean copiarET):
  APU origen = repository.findOrThrow(apuId, duenoId)  // 404 si ajeno
  APU copia = origen.deepCopyExceptoIdYPrecios()
  copia.codigo = generarCodigoUnico(copia.presupuesto_id, "APU-")
  // preserva: porcentaje_indirecto (override o NULL), porcentaje_descuento
  // copia secciones + filas (4 secciones + n filas)
  // recalcula write-through via Motor.calcularApu (mismas filas → mismo resultado)
  // copia especificacion_tecnica si copiarET
  repository.persist(copia)
  return copia
```

> **No preserva** `es_auxiliar`/`apu_auxiliar_id`/`apuAuxiliarId` (no
> existen por la decisión no-links). `generarCodigoUnico` ya implementado
> en Plan 03 (I-05); ver [`03-apu.md`](03-apu.md) §1 y §4.

### 2.4 `EspecificacionTecnicaService` (DONE — sección activa)

```
void guardar(long apuId, long duenoId, String texto)        // PUT /apus/{id}/especificacion-tecnica
String obtener(long apuId, long duenoId)                    // GET (en el detalle del APU)
Resource descargarWord(long presupuestoId, long duenoId,
                       String titulo1Override, String titulo2Override)   // GET /documentos/especificaciones-tecnicas/{presupuestoId}?formato=docx
```

Validación: longitud ≤ 64 KB (RNF-09). Texto libre UTF-8 (sin validación
de markup — el backend trata el contenido como texto plano; el frontend
puede serializar desde TipTap/React-Quill a texto plano antes del PUT).

`descargarWord` delega al módulo `documento/EspecificacionesTecnicasService`
(ver [`docs/modulos/estado-actual.md`](estado-actual.md) §4 fila P-45).
El documento Word se genera con **Apache POI** (`poi-ooxml`, ya en
dependencias); **no** se usa docx4j. La cabecera incluye:

- **Título 1:** `Proyecto.titulo_et_1` o, si override no-null en la
  request, el valor pasado como `titulo1Override`. Default: la cadena
  `"ESPECIFICACIONES TÉCNICAS"`.
- **Título 2:** `Proyecto.titulo_et_2` o, si override no-null,
  `titulo2Override`. Default: `<nombre_proyecto>` del proyecto.

Los overrides son opcionales y por generación (no se guardan en BD; el
proyecto mantiene su default).

### 2.5 `RecalculoService` (N04 dossier §B.6) — DEFERRED, NO IMPLEMENTAR

> **Bloque histórico — no aplicar.** La N04 original preveía un módulo
> `recalculo` con `RecalculoService.recalcular(Alcance)` cubriendo los
> tipos de cambio `PORCENTAJE_INDIRECTO_DEFAULT`,
> `PORCENTAJE_HERRAMIENTA_MENOR`, `DESCUENTO_GLOBAL`,
> `EDICION_ATOMICA_INSUMO`, `CAMBIO_PLANTILLA` y `CAMBIO_AUXILIAR`.
> **Ese módulo queda DEFERRED** porque su introducción contradice el
> alcance actual de no crear módulos nuevos de primer nivel
> ([`estado-actual.md`](estado-actual.md) §1 y §7).
>
> **Lo que SÍ existe hoy (Plan 03 cerrado):** el write-through local por
> APU lo realiza `apu.service.ApuCalculoService.recalcular(apu)`. Solo
> persiste los derivados del propio APU (`costo_directo/indirecto/total`,
> subtotales por sección, costo/costo_hora por fila). **No** propaga al
> presupuesto ni dispara recálculo masivo. La enumeración histórica
> `CAMBIO_AUXILIAR` ya no es un caso posible (decisión no-links).

### 2.6 `ApuCrudService` (03-apu) — estado actual

- `actualizarPorcentajeIndirecto(apuId, duenoId, BigDecimal valor)` — **DONE**
  (Plan 03): null = hereda (limpieza); valor = override (rango configurable).
  La propagación cuando cambia el default del proyecto sigue **DEFERRED**
  (requeriría el módulo `recalculo` global; ver §2.5).
- `actualizarPorcentajeDescuento(apuId, duenoId, BigDecimal valor)` — **DONE**
  (Plan 03): null = 0; rango 0–50 % configurable.
- `agregarDetalle(...)` — **no invoca** `ApuValidacionService` (no existe;
  ver §2.1). El write-through es local por APU vía
  `ApuCalculoService.recalcular(apu)`.
- Al crear/eliminar fila: invalidar caché de `ApuCalculoResponse` (si se
  cachea).

### 2.7 `PlantillaProyectoService` (P-46) — MISSING, Plan 06

> **Bloque histórico — no implementar todavía.** Se conserva la firma
> prevista de N04 para auditoría. Implementación activa en
> [planes-para-estar-al-dia/06](planes-para-estar-al-dia/06-plantillas-proyecto.md)
> usando solo los paquetes existentes (`plantilla`, `proyecto`,
> `presupuesto`, `apu`, `insumo`).

```
PlantillaProyectoSnapshot snapshot(Proyecto origen)         // para guardar como plantilla
Proyecto cargarDesdePlantilla(long plantillaId, String nombreNuevo, long duenoId)
```

`cargarDesdePlantilla` aplica la misma lógica de `cargarPlantillaApu`
(Plan 04 P-26): deep copy de capítulos + rubros (sin cantidad) + APUs
(snapshot sin precios); insumos con fallback (CENTRAL/PERSONAL → PROYECTO);
APUs incompletos marcados con advertencia.

### 2.8 `InsumoCrudService` (02-insumo) — estado actual

- `editar(baseId, iid, req)` — **no invoca** `RecalculoService` global
  (módulo DEFERRED, §2.5). El cambio de precio en PROYECTO se persiste y
  la siguiente mutación del APU afectado reflejará el nuevo precio vía
  herencia COALESCE. La propagación automática al editar insumo
  (`EDICION_ATOMICA_INSUMO`) queda **DEFERRED**.
- `BaseInsumosService` con enum `TipoBase` y `PERSONAL` — **DONE**
  (ya implementado). CRUD de personales propias está cubierto por
  `BasesPersonalesService`; eliminar una base personal queda en
  [planes-para-estar-al-dia/05](planes-para-estar-al-dia/05-administracion-bases.md).

### 2.9 `ParametrosProyectoService` (01-proyecto) — estado actual

- `actualizar(usuarioId, proyectoId, req)` **DONE**: lee rangos desde
  `ParametrosSistema` (default 0–20, 0–100, 0–50, 0–30) — ya implementado
  y verificado por `ParametrosRangoDinamicoTest`.
- Tras actualizar `%HM` o `%CI`: **no** invoca `RecalculoService` global
  (módulo DEFERRED). El recurso devuelve solo
  `ParametrosProyectoCambio.parametros()` y expone la costura
  neutral para futura propagación; los flags internos
  (`porcentajeHerramientaMenorCambio`, `porcentajeIndirectoCambio`) y el
  `Long proyectoId` **no** se exponen por REST.
- La migración de los paths a UUIDv7 (incluido
  `ParametrosProyectoResponse`) está diferida a
  [planes-para-estar-al-dia/07](planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md).

## 3. Motor — cambio de precisión (N04 §#7) + frontera APU→Rubro (N04-bis)

> **Bloque histórico — implementación pendiente.** La decisión funcional
> de aplicar `RoundingMode.DOWN` 2 dp en la frontera APU→Rubro está
> **cerrada** ([`plans/006` está en DECISIÓN CERRADA](../../plans/README.md);
> [`plans/014` es READY FOR IMPLEMENTATION](../../plans/014-motor-precision-no-links.md)),
> pero el código del motor **no se ha modificado todavía**. La
> implementación real corresponde a
> [planes-para-estar-al-dia/02](planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)
> y exige el plan [`plans/014`](../../plans/014-motor-precision-no-links.md).
> Post-Plan 014: el motor opera con la precisión natural de `BigDecimal`;
> la única rounding del motor es la frontera APU→Rubro `DOWN` 2 dp; el
> display se rige por la config global `precisionDinero=2` /
> `precisionPorcentaje=4` vía `app.display.*` + `GET /api/v1/config/display`.
>
> Se conserva el rationale original (N04 §#7 + N04-bis) a continuación
> como auditoría; los apartados 3.1 (motor) y 3.2 (frontera) anotan el
> cambio de política 2026-08-28 al inicio.

### 3.1 Cálculo interno — WITHDRAWN, Plan 02 (supersede 2026-08-28)

> **Bloque histórico — no aplicar.** La decisión N04 §#7 de redondear cada
> operación a `CALC_PRECISION=3` con `HALF_UP` queda **retirada** por el
> [`plans/014`](../../plans/014-motor-precision-no-links.md) (2026-08-28):
> el workbook IESS no aplica redondeo intermedio al APU; las 3 dp
> visibles son formato de display, no cómputo. Los 21 GMs per-APU verdes
> y las 5 propiedades verdes prueban que la aritmética natural de
> `BigDecimal` ya es correcta.
>
> Por tanto: **no** se introduce helper `r(x)` en `Motor.calcularApu` ni
> en `internal/CalculadorFila.java`; **no** se redondea cada
> `multiply`/`add`/`subtract`. Plan 014 solo autoriza en esos dos archivos
> ediciones **estructurales** mínimas (borrar las ramas
> `esAuxiliar`/`cdAuxiliar`), nunca aritméticas. La BD persiste
> `NUMERIC(14,6)` sin pérdida porque no hay redondeo intermedio que altere
> la magnitud. El export aplica `precisionDinero` (default 2) en la capa de
> presentación; no recalcula.
>
> El test previsto `TC-DECIMALES-CALC3-DISP2` queda **redocumentado**:
> el motor devuelve la precisión completa (`0.333332…`); export xlsx da
> `0.33` (2 dp desde config global); BD persiste la magnitud completa.

### 3.2 Frontera APU→Rubro — `RoundingMode.DOWN` (N04-bis 2026-08-19) — MISSING, Plan 02

> **Bloque histórico — implementación pendiente.** La frontera
> APU→Rubro en `internal/Consolidador.java` es la **única rounding del
> motor** (post-Plan 014 ya no hay regla HALF_UP general a la que sea
> excepción). Autorizada por
> [`plans/014`](../../plans/014-motor-precision-no-links.md) y por
> `plans/006`.

Para **cerrar GM-19/GM-20** (delta vs workbook IESS = $2.50 sobre
presupuesto de 298 rubros en la línea base; corrección workbook-consistent
2026-08-28), `internal/Consolidador.java` aplica la regla
**workbook-consistent** al construir `RubroConPrecio` en la frontera
APU→Rubro. Es la **única** rounding del motor (post-Plan 014).

```java
// En Consolidador.java, al construir RubroConPrecio:
// precioUnitario: única aplicación de DOWN (reproduce workbook IESS).
rubro.precioUnitario = apu.costoTotal.setScale(2, RoundingMode.DOWN);
// precioTotal: cantidad × PU_2dp, retenido a la escala de persistencia
// 6 (`NUMERIC(14,6)`) con HALF_UP únicamente en esa frontera de resultado.
// NO se trunca cada precioTotal a 2 dp — el workbook IESS multiplica
// a precisión completa y suma antes de presentar a 2 dp HALF_UP.
rubro.precioTotal    = cantidad.multiply(rubro.precioUnitario)
                         .setScale(6, RoundingMode.HALF_UP);
// capítulo y totalGeneral agregan esos precioTotal a escala 6.
// El display canónico a 2 dp HALF_UP vive en la capa de presentación.
```

**Rationale (workbook-consistent 2026-08-28):** el workbook IESS usa
`ROUNDDOWN` (o tipea 2 dp directamente) para `precioUnitario` antes de
multiplicar por `cantidad`, pero **no** trunca el `precioTotal` resultante
a 2 dp — multiplica y suma a precisión completa antes de presentar a 2
dp. La versión previa con `setScale(2, DOWN)` simétrico en PU y PT
producía deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs
workbook IESS y queda retirada (STOP conditions de Plan 014).

**Regla relajada del CLAUDE.md backend:** "Do not touch Motor.java or
Consolidador.java" se levanta para esta decisión específica (cambio
de requerimientos funcionales N04-bis; corrección workbook-consistent
2026-08-28). Plan 014 (2026-08-28) precisa el
alcance: `internal/Consolidador.java` se modifica para la única rounding
del motor con la **regla workbook-consistent** (ver bloque de código
arriba), y `Motor.java` / `internal/CalculadorFila.java` reciben **solo
ediciones estructurales mínimas** (borrar ramas `esAuxiliar`/`cdAuxiliar`,
leer el `%CI` desde `ApuSnapshot.porcentajeIndirecto`) **sin cambio
aritmético**. Cambios futuros requieren
`plans/0NN-motor-fix.md` con justificación funcional + nota en ambos
`CLAUDE.md`.

**Estado real tras la implementación workbook-consistent (cierre parcial 2026-08-28):**
- `MotorConsolidacionTest.GM_19_total_general_tulcan` → actual `395108.37` vs workbook `395115.32` (**delta `-$6.95`**). **Residual aceptado** por el autor; el motor **no** se modifica más.
- `MotorConsolidacionTest.GM_20_totales_capitulos_raiz_tulcan` → 6/7 capítulos raíz a delta 0.00; cap. 1 actual `158907.21` vs `158908.05` (**delta `-$0.84`**). **Residual aceptado**.
- `MotorConsolidacionTest.GM_21_*` → verde con allowlist auditado de **11 entradas** ≤ 0.03 a nivel PU, atribuidas a artefactos de redondeo manual del workbook IESS (one example workbook, no exhaustive per-rubro audit — preferencia del usuario).
- Los 21 GMs per-APU siguen verdes (per-APU math intacta).
- `ConsolidadorFronteraTest` 5/5 verde (nuevo, T1 workbook-consistent).

## 4. REST resources (nuevos endpoints) — estado real

> Las rutas marcadas como **DONE** ya existen; las **MISSING/DEFERRED**
> siguen en los planes 02–08 de
> [`planes-para-estar-al-dia/`](planes-para-estar-al-dia/). **No** se
> exponen campos `es_auxiliar`, `apu_auxiliar_id`, `apuAuxiliarId`,
> `cdAuxiliar` ni `CAMBIO_AUXILIAR` en ningún DTO público.

**ApuResource** (ampliado en Plan 03):
- `PATCH /apus/{id}/porcentaje-indirecto` (P-23) — **DONE**
- `PATCH /apus/{id}/porcentaje-descuento` (P-24) — **DONE**
- `GET /apus/{id}/calculo` (P-27 — `ApuCalculoResponse`) — **PARTIAL**
  (falta aplicar `precisionDinero` / `precisionPorcentaje` al response desde `app.display.*` + `GET /api/v1/config/display`; Plan 03)
- `POST /apus/{id}/duplicar` (dossier §B.7) — **DONE**
- `PUT /apus/{id}/especificacion-tecnica` (P-45) — **DONE**
- `GET /apus/{id}/especificacion-tecnica` (P-45) — **DONE**

**DocumentoResource** (P-45):
- `GET /documentos/especificaciones-tecnicas/{presupuestoId}?formato=docx&titulo1=&titulo2=`
  (exporte Word único por proyecto; `titulo1`/`titulo2` opcionales
  overridean los defaults del proyecto — N04-bis) — **DONE** (Apache POI).

**PlantillaProyectoResource** (P-46) — **MISSING**, Plan 06
([planes-para-estar-al-dia/06](planes-para-estar-al-dia/06-plantillas-proyecto.md)):
- `GET /plantillas-proyecto` — lista del usuario
- `POST /plantillas-proyecto` — guardar snapshot desde proyecto actual
- `POST /proyectos/{proyectoId}/desde-plantilla/{plantillaId}` — P-46
- `DELETE /plantillas-proyecto/{id}` — eliminar (no afecta proyectos ya creados)

**ParametrosSistemaResource** (DONE):
- `GET /parametros-sistema` — lectura — **DONE**
- `PUT /parametros-sistema` — admin edita defaults (incluido rango de
  parámetros, N04 §A6) — **DONE**.

**InsumoResource / BasesPersonalesResource**:
- `GET /bases-personales` — lista del usuario — **DONE**
- `POST /bases-personales` — crear — **DONE**
- `DELETE /bases-personales/{id}` — **DONE**

> **UUIDv7 en paths:** `proyectoId`, `presupuestoId`, `apuId` siguen como
> `Long` en las rutas actuales; la migración a UUIDv7 público está
> diferida a
> [planes-para-estar-al-dia/07](planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md).

## 5. Tests nuevos (N04 original — bloque histórico)

> **Bloque histórico — no aplicar.** La siguiente tabla enumera tests
> previstos en el plan N04 original; varios están **obsoletos** (P-25) o
> **diferidos** (`RecalculoService*`). El set de pruebas activas vive en
> `plans/006`, en los planes 02–08 y en
> [`estado-actual.md`](estado-actual.md) §4.

## 6. Plantillas V004 — estado actual

> **Bloque histórico — la decisión V005 ya está tomada.** Plan 04 / N04
> §B.4.a eligió **opción a)** (reader tolera campos extra del seed V004);
> **no** se crea `V005__reseed_plantillas_canonico.sql` en esta etapa.
>
> Los apartados siguientes reproducen el rationale N04-bis como
> auditoría.

V004 siembra 2 plantillas con `snapshot_secciones` conteniendo precios
(`tarifaJornal`, `costo`). El reader actual no los usa (decisión N04
§B.4.a — recomendada). Si se quiere limpieza formal, migración
`V005__seed_plantillas_canonico.sql` reescribe las 2 plantillas al JSON
canónico.

**Recomendación vigente:** **opción a)** (reader tolera campos extra). Cero
migración; cero riesgo. Si en el futuro se quiere canonicalizar,
`V005__reseed_plantillas_canonico.sql` es limpieza sin impacto funcional.

**ET y títulos en los seeds (N04-bis 19-08-2026) — YA APLICADO en V001–V004:**
las columnas `plantilla_apu.especificacion_tecnica`, `proyecto.titulo_et_1`
y `proyecto.titulo_et_2` ya forman parte del esquema vigente. El texto
de referencia y los títulos sembrados siguen la guía N04-bis (orientativo,
sin hardcode de negocio):

- **Plantillas (`plantilla_apu`):** texto de referencia (orientativo,
  basado en `res/ESTANCIA-ACADEMICA/ESPECIFICACIONES TECNICAS TOTALES-signed.pdf`):
  - Plantilla 1 (SISTEMA «Hormigón f'c 210 kg/cm² (losa)»): descripción
    del proceso constructivo (dosificación, mezclado, vibrado, curado),
    calidad de materiales, equipo mínimo, normativa (NEC, ACI),
    garantías, mano de obra, medición y forma de pago (m³ ejecutado).
  - Plantilla 2 (PERSONAL del seed — id 2): texto equivalente
    simplificado.
- **Proyectos (texto sembrado histórico, orientativo):**
  - `titulo_et_1`: `"ESPECIFICACIONES TÉCNICAS"` (idéntico en los 3).
  - `titulo_et_2`: `"CONSTRUCCIÓN DE ESTANCIA ACADÉMICA…"` para el
    FINALIZADO (basado en el ejemplo real); placeholder para los otros 2.

## 7. Verificación (N04 original — bloque histórico)

> **Bloque histórico — no aplicar.** Las rutas a `ec.uce.propuestas.recalculo`
> **no existen** porque el módulo está DEFERRED. La verificación activa
> figura en [`estado-actual.md`](estado-actual.md) §9 y en cada plan
> 02–08 de [`planes-para-estar-al-dia/`](planes-para-estar-al-dia/).

## 8. Fuera de alcance (TODO) — vigente

- Edición/eliminación de bases CENTRALES por Super-Admin (P-39) →
  [planes-para-estar-al-dia/05](planes-para-estar-al-dia/05-administracion-bases.md).
- Gantt visual con drag de períodos → fuera del MVP.
- Selección múltiple bulk en APU → fuera del MVP.
- Plantillas SISTEMA de proyecto completo (sólo PERSONALES por ahora) →
  evaluar con uso real.
- Historial de cambios del APU (`log_actividad` con tipo `apu.editado`) →
  verificar si ya emite eventos correctos en P-21 (I-05).
- "Plantillas SISTEMA" para ET (texto precargado) → agenda A-ET.
- Reordenamiento con drag-and-drop visual (la API ya lo soporta vía
  `PATCH /apus/{id}/detalles/{detalleId} {orden: N}`) → fuera del MVP.

## 9. Decisiones operativas (N04 propagation) — estado actual

| Decisión | Home canónico | Implementación actual |
|---|---|---|
| Descuento FORMA 1 / FORMA 2 (A1) | DM §17 #11; procesos P-12 | **DEFERRED** — sin `RecalculoService` global; FORMA 2 persiste y hereda vía COALESCE en próxima mutación del APU |
| Auxiliares sin anidamiento (A2) | DM §17 #12; procesos P-25 | **OBSOLETO/SUPERSEDED** — sin enlaces entre APUs (N04 temporal) |
| HM primera + reordenable (A3) | DM §17 #9; procesos P-21 | **PARTIAL** — motor ordena por `orden`; PATCH de `orden` aún en Plan 03 |
| Rangos parametrizables (A6) | DM §11; procesos P-11 | **DONE** — `ParametrosSistema` con columnas de rango + `ParametrosRangoDinamicoTest` |
| Bases SIEMPRE copia + PERSONAL (A9) | DM §17 #16, §10; procesos P-17, P-39 | **DONE** — `ResolverInsumoProyectoService` + `BasesPersonalesService` |
| Archivar central sin bloqueo (D-12) | DM §10; procesos P-39 | **MISSING** — Plan 05 ([planes-para-estar-al-dia/05](planes-para-estar-al-dia/05-administracion-bases.md)) |
| Decimales: motor natural `BigDecimal`, display global 2/4, frontera APU→Rubro **workbook-consistent** (Plan 014 supersede #7; corrección 2026-08-28) | DM §0, §16, §17 #19 | **PARTIAL — CIERRE CON RESIDUO ACEPTADO (2026-08-28)** — Plan 02 ([planes-para-estar-al-dia/02](planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)) + Plan 014 ([plans/014](../../plans/014-motor-precision-no-links.md)). Regla workbook-consistent aplicada en `Consolidador`: `precioUnitario DOWN 2dp`; `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6. Display global `precisionDinero=2` / `precisionPorcentaje=4` vía `app.display.*` + `GET /api/v1/config/display` queda OPEN (T3 Plan 014). GM-19 (`-$6.95`) y GM-20 cap. 1 (`-$0.84`) con residual aceptado — **no** se reabre el motor. **No** reintroducir `setScale(2, DOWN)` por rubro total (causaba deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs workbook IESS — STOP conditions de Plan 014). |
| Especificaciones Técnicas (ET) | DM §17 #18; procesos P-45 | **DONE** — `EspecificacionesTecnicasService` con Apache POI |
| Plantilla de proyecto (A8) | DM §3, §10; procesos P-46 | **MISSING** — Plan 06 ([planes-para-estar-al-dia/06](planes-para-estar-al-dia/06-plantillas-proyecto.md)) |

## 10. Riesgos y deudas — vigente

> Riesgos heredados del N04 original con notas de vigencia:

- **R1:** el helper de redondeo `r(x)` en el motor puede acumular
  desviaciones si se aplica a operaciones que originalmente son enteras
  (ej. `cantidad × tarifa` donde ambos son "limpios"). Mitigación: GM-22,
  GM-23, GM-25 deben seguir verdes tras el cambio. Si fallan, ajustar
  helper. — **Aplica cuando se ejecute Plan 02.**
- **R2:** `EspecificacionesTecnicasService` (Word) usa **Apache POI**, no
  docx4j; curva de aprendizaje superada. — **Resuelto** (P-45 DONE).
- **R3:** `RecalculoService` global queda **DEFERRED**; el write-through
  local por APU lo realiza `ApuCalculoService.recalcular(apu)`. No
  introducenir stubs ni imports ocultos que simulen el global.
- **R4:** rangos parametrizables — si el Super-Admin edita los rangos
  en `ParametrosSistema`, los proyectos existentes **no se re-validan**
  (sus valores actuales quedan "fuera de rango" sin warning). Decisión:
  documentar; si se quiere re-validación, abrir plan 014. — **Vigente.**
