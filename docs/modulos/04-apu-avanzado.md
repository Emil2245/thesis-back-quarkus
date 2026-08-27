# Plan 04 — Módulo `apu-avanzado` (P-23…P-27, P-45, P-46 + decisiones N04) — I-06

> Playbook auto-contenido. Sigue `docs/modulos/README.md`. **Crea** migración
> `V005__*.sql` (única del I-06) si se opta por reseed de plantillas
> canónicas (`V004` ya siembra 2 plantillas con precios/costos — ver §6).
>
> **Decisiones congeladas (N04 18-08-2026 — Ing. Carlosama, dossier
> `plan/design/07-decisiones-i06-pendientes.md`):** A1 (descuento CD — dos
> formas, MO exenta, nunca monto absoluto), A2 (auxiliares sin
> anidamiento), A3 (HM primera por defecto + reordenable), A6 (rangos
> parametrizables globalmente), A9 (bases SIEMPRE copia al usar; nuevo
> tipo PERSONAL), A8/D-12 (plantilla de proyecto + archivar central sin
> bloqueo), #7 (CALC_PRECISION=3, DISPLAY_PRECISION=2), ET (nueva
> feature). Detalle y rationale en `thesis-docs/plan/domain/02-data-model.md`
> §17 #9, #11, #12, #16, #18, #19.

## 0. Alcance

**Procesos P-xx que entran en I-06:**

| Proceso | Tema | Decisión N04 que aplica |
|---|---|---|
| P-23 | %CI override por rubro (heredando default) | §17 #17 (sin cambios) |
| P-24 | Descuento CD por rubro (campo legacy `APU.porcentaje_descuento`) | §17 #11 (atajo simple) |
| P-25 | Rubro auxiliar (`es_auxiliar`, `apu_auxiliar_id`) — **validación sin anidamiento** | §17 #12 |
| P-26 | Plantillas personales + carga con fallback | §17 #16 + §B.4 |
| P-27 | Desglose de cálculo (`ApuCalculoResponse`) | §B.8 |
| P-45 | **NUEVO** — Especificaciones Técnicas por APU | §17 #18 (ET) |
| P-46 | **NUEVO** — Plantilla de proyecto completo | §A8 reactivado (N04) |
| `POST /apus/{id}/duplicar` | Decisión dossier §B.7 (opción a) | — |
| Módulo `recalculo` | Decisión dossier §B.6 (write-through al cambiar parámetros) | §17 #17 |
| Rangos parametrizables | Decisión N04 §A6 | DM §11 |
| `CALC_PRECISION` / `DISPLAY_PRECISION` | Decisión N04 §#7 | DM §0, §16, §17 #19 |
| Base `PERSONAL` | Decisión N04 §A9 | DM §10, §17 #16 |
| Reordenamiento filas/secciones | Decisión N04 §A3 | DM §9, §17 #9 |

**Procesos que NO entran (quedan para iteraciones futuras):**

- P-39 admin edición/eliminación de bases CENTRALES (admin Super-Admin) →
  **I-11**.
- Reordenamiento con drag-and-drop visual (Gantt, presupuesto) → **I-09**.
- Selección múltiple y operaciones bulk en APU → fuera del MVP.

## 1. Arquitectura de empaquetado (nuevo código I-06)

```
ec/uce/propuestas/
├── apu/                          (continúa desde I-05; ver 03-apu.md)
│   ├── dto/
│   │   ├── ApuPorcentajeIndirectoRequest.java       (PATCH override %CI)
│   │   ├── ApuPorcentajeDescuentoRequest.java       (PATCH legacy %desc)
│   │   ├── ApuDetalleAuxiliarRequest.java           (filas O con apu_auxiliar_id)
│   │   ├── ApuCalculoResponse.java                  (P-27 desglose)
│   │   ├── ApuDuplicarResponse.java                 (POST /apus/{id}/duplicar)
│   │   ├── EspecificacionTecnicaRequest.java        (P-45 PUT /apus/{id}/especificacion-tecnica)
│   │   └── EspecificacionTecnicaResponse.java
│   ├── service/
│   │   ├── ApuCalculoService.java          (existente; ampliar para P-27)
│   │   ├── ApuValidacionService.java       (nuevo; valida sin anidamiento A2)
│   │   ├── ApuDuplicarService.java         (nuevo; dossier §B.7 opción a)
│   │   └── EspecificacionTecnicaService.java (nuevo; P-45)
│   └── resource/
│       └── ApuResource.java               (ampliar: nuevos endpoints)
├── recalculo/                     (nuevo módulo deep, 08-codebase-design §1)
│   ├── RecalculoService.java              (API: `recalcular(Alcance)`)
│   ├── dto/AlcanceRecalculo.java          (versión, APUs subset, tipo de cambio)
│   └── internal/...                       (helpers package-private)
├── plantilla/                     (nuevo módulo; reusa lo de 03-apu para APU)
│   ├── entity/PlantillaProyecto.java
│   ├── service/PlantillaProyectoService.java
│   ├── dto/...
│   └── resource/PlantillaProyectoResource.java
└── documento/                     (extender para ET Word — ver 04-export-sercop-spec.md §7)
    └── service/
        └── EspecificacionTecnicaWriter.java    (genera .docx por proyecto)
```

**Schema (V005 opcional):**

- Si el reseed de plantillas canónicas se elige (alternativa a del dossier
  §B.4.b): `V005__seed_plantillas_canonico.sql` reescribe las 2 plantillas
  sembradas en V004 al JSON canónico sin precios.
- Si se elige la opción **a)** del dossier (reader tolera campos extra del
  seed V004): no se crea migración; el reader hace `.path("tarifaJornal")
  .ifPresent(...)` style. **Recomendado por defecto** (menos migración,
  menos riesgo).
- Para ET: agregar columna `apu.especificacion_tecnica TEXT` (nullable) en
  V005 (o ampliar V001 si V005 no se crea).
- Para PERSONAL: ampliar enum `tipo_base` con `PERSONAL` y agregar
  `base_insumos.usuario_id UUID FK → usuario` (nullable).
- Para parametrizabilidad de rangos: agregar columnas en `parametros_sistema`:
  `rango_hm_min NUMERIC(5,4)`, `rango_hm_max NUMERIC(5,4)`, etc.

## 2. Servicios nuevos / ampliados

### 2.1 `ApuValidacionService` (N04 §A2 — sin anidamiento)

```
boolean validarFilaAuxiliar(APU destino, APUDetalle detalle):
  if destino.es_auxiliar and detalle.apu_auxiliar_id is not null:
    throw ProblemaException("validacion",
      "Sin anidamiento: un rubro auxiliar no puede referenciar a otro auxiliar.")
  return true
```

Llamado desde `ApuCrudService.agregarDetalle` (03-apu) y
`actualizarDetalle`. Error HTTP 400 con `type: "validacion"` (consistente con
el `GlobalExceptionMapper` existente).

### 2.2 `ApuCalculoService` (ampliar para P-27)

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

**Precisión:** todos los `resultado` se redondean al motor a `CALC_PRECISION`
dp (default 3). Las cadenas `operacion` muestran los operandos a 6 dp
(persistencia) sin redondear (auditoría).

### 2.3 `ApuDuplicarService` (dossier §B.7 opción a)

```
APU duplicar(long apuId, long duenoId, boolean copiarET):
  APU origen = repository.findOrThrow(apuId, duenoId)  // 404 si ajeno
  APU copia = origen.deepCopyExceptoIdYPrecios()
  copia.codigo = generarCodigoUnico(copia.presupuesto_id, "APU-")
  // preserva: es_auxiliar, porcentaje_indirecto (override o NULL), porcentaje_descuento
  // preserva: apu_auxiliar_id en filas MATERIAL (mismo auxiliar referenciado)
  // copia secciones + filas (4 secciones + n filas)
  // recalcula write-through via Motor.calcularApu (mismas filas → mismo resultado)
  // copia especificacion_tecnica si copiarET
  repository.persist(copia)
  return copia
```

`generarCodigoUnico(presupuesto_id, "APU-")` cuenta APUs en esa versión y
genera `APU-{count+1}`; si colisiona (APUs borrados), incrementa hasta
encontrar uno libre (provisión de I-05 documentada en 03-apu.md §1).

### 2.4 `EspecificacionTecnicaService` (N04 §ESP — P-45 + N04-bis)

```
void guardar(long apuId, long duenoId, String texto)        // PUT /apus/{id}/especificacion-tecnica
String obtener(long apuId, long duenoId)                    // GET (en el detalle del APU)
Resource descargarWord(long presupuestoId, long duenoId,
                       String titulo1Override, String titulo2Override)   // GET /documentos/especificaciones-tecnicas/{presupuestoId}?formato=docx
```

Validación: longitud ≤ 64 KB (RNF-09). Texto libre UTF-8 (sin validación
de markup — el backend trata el contenido como texto plano; el frontend
puede serializar desde TipTap/React-Quill a texto plano antes del PUT).

`descargarWord` delega al módulo `documento/EspecificacionTecnicaWriter`
(ver 04-export-sercop-spec.md §7). El documento Word incluye en la
cabecera:

- **Título 1:** `Proyecto.titulo_et_1` o, si override no-null en la
  request, el valor pasado como `titulo1Override`. Default: la cadena
  `"ESPECIFICACIONES TÉCNICAS"`.
- **Título 2:** `Proyecto.titulo_et_2` o, si override no-null,
  `titulo2Override`. Default: `<nombre_proyecto>` del proyecto.

Los overrides son opcionales y por generación (no se guardan en BD; el
proyecto mantiene su default).

### 2.5 `RecalculoService` (N04 dossier §B.6 — write-through parámetros)

```
RecalculoResultado recalcular(AlcanceRecalculo alcance):
  // alcance = { presupuestoId, [apuIds...], tipoCambio }
  switch alcance.tipoCambio:
    case PORCENTAJE_INDIRECTO_DEFAULT:
      // Solo APUs con porcentaje_indirecto IS NULL (heredan)
      // Para cada uno: Motor.calcularApu → write-through costo_directo/indirecto/total
    case PORCENTAJE_HERRAMIENTA_MENOR:
      // Recalcular TODOS los APUs de la versión (HM no tiene override por APU)
    case DESCUENTO_GLOBAL:                    // N04 §A1 FORMA 1
      // Para cada insumo del tipo afectado en base PROYECTO:
      //   columna_reducida = columna_original * (1 - porcentaje)
      //   persistir (mutación de columnas — N04 §A1)
      // Luego recalcular todos los APUs de la versión que tengan filas
      // que heredan (override NULL) de esos insumos.
    case EDICION_ATOMICA_INSUMO:             // N04 §A1 FORMA 2 (N04-bis UX híbrida)
      // Recalcular APUs que tengan filas con insumo_id = insumoEditado
      // y override NULL. El backend responde con el nuevo costo total
      // del proyecto (preview client-side ya muestra el efecto en vivo;
      // el frontend hace debounced PUT cada ~500 ms tras la última edición;
      // ver §3.5 — UX preview).
    case CAMBIO_PLANTILLA:                   // N04 §B.4
      // Sin recálculo masivo (la carga es por APU con fallback).
    case CAMBIO_AUXILIAR:                    // N04 §A2 + P-25
      // Recalcular APUs que referencian el auxiliar modificado (bloque O).
  return RecalculoResultado(apusAfectados, totalGeneral, ...)
```

**Diseño:** `RecalculoService` es el mecanismo genérico "qué cambió →
derivados persistidos" (RNF-02). Reutilizable por todas las mutaciones que
afectan totales. Sin estado global. Sin I/O fuera de la BD (las mutaciones
de columna en FORMA 1 se hacen en una transacción).

**UX preview — FORMA 2 híbrida (N04-bis 19-08-2026):**
El endpoint `PUT /insumos/{id}` (FORMA 2) se invoca con **debounce** desde
el frontend:
- El frontend mantiene preview client-side del nuevo costo total del
  proyecto (conoce los APUs y filas que heredan del insumo editado).
- El frontend hace `PUT /insumos/{id}` con **debounce de 500 ms** tras
  la última edición (configurable; coalesce múltiples keystrokes en un
  solo PUT → no satura el backend).
- El backend persiste + recalcula en cada PUT y responde con el nuevo
  `costo_total` del proyecto.
- Indicador visual en UI: "Guardando..." (spinner/badge) durante el PUT
  + "Guardado" / "Error al guardar" tras la respuesta. Sin botón
  "Guardar" explícito.
- Si la edición es masiva (muchos PUTs seguidos), el backend procesa
  en orden; el frontend hace coalescing.

### 2.6 `ApuCrudService` (03-apu) — ampliar

- `actualizarPorcentajeIndirecto(apuId, duenoId, BigDecimal valor)` — null =
  hereda (limpieza); valor = override (0–100 %; rango configurable).
- `actualizarPorcentajeDescuento(apuId, duenoId, BigDecimal valor)` — null = 0;
  rango 0–50 % (configurable).
- `agregarDetalle(...)` — **invoca** `ApuValidacionService.validarFilaAuxiliar`
  (N04 §A2) **y** el `RecalculoService.recalcular` para el APU afectado
  (RNF-02 write-through).
- Al crear/eliminar fila: invalidar caché de `ApuCalculoResponse` (si se
  cachea).

### 2.7 `PlantillaProyectoService` (N04 §A8 — P-46)

```
PlantillaProyectoSnapshot snapshot(Proyecto origen)         // para guardar como plantilla
Proyecto cargarDesdePlantilla(long plantillaId, String nombreNuevo, long duenoId)
```

`cargarDesdePlantilla` aplica la misma lógica de `cargarPlantillaApu`
(03-apu + P-26): deep copy de capítulos + rubros (sin cantidad) + APUs
(snapshot sin precios); insumos con fallback (CENTRAL/PERSONAL → PROYECTO);
APUs incompletos marcados con advertencia.

### 2.8 `InsumoCrudService` (02-insumo) — ampliar para FORMA 2

- `editar(baseId, iid, req)` ahora invoca `RecalculoService` con
  `EDICION_ATOMICA_INSUMO` cuando cambia `precio_unitario` /
  `tarifa_jornal` / `precio_unitario_tarifa` (N04 §A1 FORMA 2).
- `BaseInsumosService` amplía enum `TipoBase` con `PERSONAL` (N04 §A9).
  Nuevos métodos: `crearBasePersonal(usuarioId, nombre)`, `listarPersonales(usuarioId)`,
  `compartirBasePersonal(baseId, destinoProyectoId)` (resuelve copia a base
  PROYECTO del proyecto destino; ya soportado por `CopiaBaseService` con
  `fuente = PERSONAL`).

### 2.9 `ParametrosProyectoService` (01-proyecto) — ampliar

- `actualizar(proyectoId, req)` lee rangos desde `ParametrosSistema`
  (default 0–20, 0–100, 0–50, 0–30) en vez de hardcoded (N04 §A6).
- Tras actualizar `%HM`: invoca `RecalculoService.recalcular(
  PORCENTAJE_HERRAMIENTA_MENOR, alcance=[versión])` — recalcula todos
  los APUs (HM no tiene override).
- Tras actualizar `%CI`: invoca `RecalculoService.recalcular(
  PORCENTAJE_INDIRECTO_DEFAULT, alcance=[versión])` — recalcula solo
  APUs con `porcentaje_indirecto IS NULL`.

## 3. Motor — cambio de precisión (N04 §#7) + frontera APU→Rubro (N04-bis)

### 3.1 Cálculo interno (N04 §#7 — HALF_UP a 3 dp)

- `Motor.calcularApu` redondea **cada operación aritmética** a
  `CALC_PRECISION` dp (default 3, configurable vía `CALC_PRECISION` env
  var / `ParametrosSistema`).
- El resultado persistido se redondea también a `CALC_PRECISION` dp.
- La BD persiste `NUMERIC(14,6)` sin pérdida (rendimiento total).
- El export aplica `DISPLAY_PRECISION` dp (default 2) en la capa de
  presentación; no recalcula.

**Implementación:** helper `private static BigDecimal r(BigDecimal x) { ... }`
que redondea con `HALF_UP` a la escala de `CALC_PRECISION`. Aplicado tras
cada `multiply`/`add`/`subtract`.

**Test:** nuevo `TC-DECIMALES-CALC3-DISP2`:
- APU con cálculo `1.0 × 0.333333 × 0.333333 × 3.0 = 0.333332…` →
  motor da `0.333` (3 dp).
- Export xlsx da `0.33` (2 dp).
- BD persiste `0.333000000` (6 dp).

### 3.2 Frontera APU→Rubro — `RoundingMode.DOWN` (N04-bis 2026-08-19)

Para **cerrar GM-19/GM-20** (delta vs workbook IESS = $2.50 sobre
presupuesto de 298 rubros), `internal/Consolidador.java` aplica
`RoundingMode.DOWN` al construir `RubroConPrecio` en la frontera
APU→Rubro. Esta es la **única excepción** a la regla HALF_UP del motor.

```java
// En Consolidador.java, al construir RubroConPrecio:
rubro.precioUnitario = apu.costoTotal.setScale(2, RoundingMode.DOWN);
rubro.precioTotal    = cantidad.multiply(rubro.precioUnitario)
                         .setScale(2, RoundingMode.DOWN);
```

**Rationale:** el workbook IESS usa `ROUNDDOWN` (o tipea 2 dp directamente)
para `precioUnitario` antes de multiplicar por `cantidad`. Para match
exact y cerrar el golden master, el motor debe usar `DOWN` en esta
frontera.

**Regla relajada del CLAUDE.md backend:** "Do not touch Motor.java or
Consolidador.java" se levanta para esta decisión específica (cambio
de requerimientos funcionales N04-bis). Cambios futuros requieren
`plans/0NN-motor-fix.md` con justificación funcional + nota en ambos
`CLAUDE.md`.

**Tests esperados verdes tras §3.2:**
- `MotorConsolidacionTest.GM_19_total_general_tulcan` → `totalGeneral == 395115.32`.
- `MotorConsolidacionTest.GM_20_totales_capitulos_raiz_tulcan` → 0.00 delta.
- `MotorConsolidacionTest.GM_21_*` → allowlist probablemente se cierra a 0
  (auditar).
- 21/25 GMs previos siguen verdes (per-APU math intacta).

## 4. REST resources (nuevos endpoints)

**ApuResource** (ampliar el existente):
- `PATCH /apus/{id}/porcentaje-indirecto` (P-23)
- `PATCH /apus/{id}/porcentaje-descuento` (P-24)
- `GET /apus/{id}/calculo` (P-27 — `ApuCalculoResponse`)
- `POST /apus/{id}/duplicar` (dossier §B.7)
- `PUT /apus/{id}/especificacion-tecnica` (P-45)
- `GET /apus/{id}/especificacion-tecnica` (P-45)

**DocumentoResource** (nuevo):
- `GET /documentos/especificaciones-tecnicas/{presupuestoId}?formato=docx&titulo1=&titulo2=`
  (P-45 — exporte Word único por proyecto; `titulo1`/`titulo2` opcionales
  overridean los defaults del proyecto — N04-bis)

**PlantillaProyectoResource** (nuevo):
- `GET /plantillas-proyecto` — lista del usuario
- `POST /plantillas-proyecto` — guardar snapshot desde proyecto actual
- `POST /proyectos/{proyectoId}/desde-plantilla/{plantillaId}` — P-46
- `DELETE /plantillas-proyecto/{id}` — eliminar (no afecta proyectos ya creados)

**ParametrosSistemaResource** (nuevo o ampliar el existente):
- `GET /parametros-sistema` — lectura (ya existe)
- `PUT /parametros-sistema` — admin I-11 edita defaults (incluido rango de
  parámetros, N04 §A6)

**InsumoResource** (ampliar):
- `GET /bases-personales` — lista del usuario
- `POST /bases-personales` — crear
- `DELETE /bases-personales/{id}`

## 5. Tests nuevos

| Test | Tipo | Verifica |
|---|---|---|
| `ApuCalculoServiceTest.porcentaje_indirecto_override_y_limpieza` | unit | P-23: set / limpiar override / recalcula solo este APU |
| `ApuValidacionServiceTest.sin_anidamiento` | unit | P-25: auxiliar con `apu_auxiliar_id` set → `validacion` |
| `ApuDuplicarServiceIT.duplicar_preserva_auxiliares_y_et` | @QuarkusTest | dossier §B.7 |
| `ApuCalculoResponseMapperTest.shape_desglose` | unit | P-27: shape exacto del response |
| `PlantillaProyectoServiceIT.cargar_con_fallback_advertencias` | @QuarkusTest | N04 §B.4: insumo inexistente → 200 con `advertencias[]` |
| `RecalculoServiceTest.porcentaje_indirecto_default_recalcula_sin_override` | unit | N04 §B.6: solo APUs con NULL se recalculan |
| `RecalculoServiceTest.porcentaje_HM_recalcula_todos` | unit | N04 §B.6 |
| `RecalculoServiceTest.descuento_global_FORMA1_reduce_columnas_y_recalcula` | unit | N04 §A1 FORMA 1 |
| `RecalculoServiceTest.edicion_atomica_FORMA2_recalcula_APUs_que_heredan` | unit | N04 §A1 FORMA 2 |
| `EspecificacionTecnicaServiceIT.guardar_y_leer` | @QuarkusTest | P-45 |
| `EspecificacionTecnicaWriterIT.exportarWord_estructura` | @QuarkusTest | N04 §7 export |
| `MotorApuTest.CALC_PRECISION_3_aplicado_en_cada_operacion` | unit | N04 §#7 |
| `MotorApuTest.DISPLAY_PRECISION_2_no_se_aplica_en_motor` | unit | N04 §#7 |
| `ParametrosProyectoServiceTest.rangos_desde_ParametrosSistema` | unit | N04 §A6 |
| `BaseInsumosServiceTest.PERSONAL_crud_y_compartir` | unit + IT | N04 §A9 |
| `BaseInsumosResourceIT.archivar_y_borrar_sin_bloqueo` | @QuarkusTest | N04 §D-12 |

## 6. Plantillas V004 — tolerancia o reseed (N04 §B.4)

V004 siembra 2 plantillas con `snapshot_secciones` conteniendo precios
(`tarifaJornal`, `costo`). El reader actual no los usa (decisión N04
§B.4.a — recomendada). Si se quiere limpieza formal, migración
`V005__seed_plantillas_canonico.sql` reescribe las 2 plantillas al JSON
canónico.

**Recomendación:** **opción a)** (reader tolera campos extra). Cero
migración; cero riesgo. Si en el futuro se quiere canonicalizar,
`V005__reseed_plantillas_canonico.sql` es limpieza sin impacto funcional.

**ET y títulos en los seeds (N04-bis 19-08-2026):** las nuevas
columnas/campos deben aparecer en V004 (o en una migración V005 si se
prefiere separar):
- **Plantillas (`plantilla_apu`):** añadir columna
  `especificacion_tecnica TEXT` (nullable) a las 2 plantillas sembradas.
  Texto de referencia (orientativo, basado en la estructura del ejemplo
  real `res/ESTANCIA-ACADEMICA/ESPECIFICACIONES TECNICAS TOTALES-signed.pdf`):
  - Plantilla 1 (SISTEMA «Hormigón f'c 210 kg/cm² (losa)»): descripción
    del proceso constructivo (dosificación, mezclado, vibrado, curado),
    calidad de materiales, equipo mínimo, normativa (NEC, ACI),
    garantías, mano de obra, medición y forma de pago (m³ ejecutado).
  - Plantilla 2 (PERSONAL del seed — id 2): texto equivalente
    simplificado.
- **Proyectos:** añadir `titulo_et_1` y `titulo_et_2` a los 3 proyectos
  del seed V004 (BORRADOR, EN_PROCESO, FINALIZADO):
  - `titulo_et_1`: `"ESPECIFICACIONES TÉCNICAS"` (idéntico en los 3).
  - `titulo_et_2`: `"CONSTRUCCIÓN DE ESTANCIA ACADÉMICA…"` para el
    FINALIZADO (basado en el ejemplo real); placeholder para los otros 2.

> Si el lector de la siembra V004 no tolera columnas nuevas, **migración
> V005**: `ALTER TABLE plantilla_apu ADD COLUMN especificacion_tecnica
> TEXT` + `ALTER TABLE proyecto ADD COLUMN titulo_et_1 TEXT` + `titulo_et_2
> TEXT` + `UPDATE` de las filas sembradas con los textos por defecto.
> **Sin drop + recreate** en este caso (los datos sembrados importan para
> los golden masters).

## 7. Verificación

```bash
# Unit tests del motor y servicios
./gradlew test --tests 'ec.uce.propuestas.motor.*'
./gradlew test --tests 'ec.uce.propuestas.apu.*'
./gradlew test --tests 'ec.uce.propuestas.recalculo.*'

# IT (tests de integración)
./gradlew test --tests 'ec.uce.propuestas.apu.*IT'
./gradlew test --tests 'ec.uce.propuestas.documento.*IT'

# Suite completa (sin regresión)
./gradlew test
```

**Esperado:** suite verde (excepto GM-19/20 preexistentes — no tocar el
motor). Total estimado: ~95 tests (77 actuales + ~18 nuevos).

## 8. Fuera de alcance (TODO)

- Edición/eliminación de bases CENTRALES por Super-Admin (P-39) → I-11.
- Gantt visual con drag de períodos → I-09.
- Selección múltiple bulk en APU → fuera del MVP.
- Plantillas SISTEMA de proyecto completo (sólo PERSONALES por ahora) →
  evaluar con uso real.
- Historial de cambios del APU (`log_actividad` con tipo `apu.editado`) →
  verificar si ya emite eventos correctos en P-21 (I-05).
- "Plantillas SISTEMA" para ET (texto precargado) → agenda A-ET.
- Reordenamiento con drag-and-drop visual (la API ya lo soporta vía
  `PATCH /apus/{id}/detalles/{detalleId} {orden: N}`) → I-09 si se quiere
  UX enriquecida.

## 9. Decisiones operativas (N04 propagation)

| Decisión | Home canónico | Implementación |
|---|---|---|
| Descuento FORMA 1 / FORMA 2 (A1) | DM §17 #11; procesos P-12 | `RecalculoService` + `InsumoCrudService.editar` |
| Auxiliares sin anidamiento (A2) | DM §17 #12; procesos P-25 | `ApuValidacionService` |
| HM primera + reordenable (A3) | DM §17 #9; procesos P-21 | motor ordena por `orden`; UI permite drag |
| Rangos parametrizables (A6) | DM §11; procesos P-11 | `ParametrosSistema` con columnas de rango |
| Bases SIEMPRE copia + PERSONAL (A9) | DM §17 #16, §10; procesos P-17, P-39 | `agregarDetalle` + `BaseInsumosService.PERSONAL` |
| Archivar central sin bloqueo (D-12) | DM §10; procesos P-39 | `BaseInsumosResource.archivar` + `eliminar` (sin check de referencias) |
| Decimales CALC=3, DISPLAY=2 (#7) | DM §0, §16, §17 #19 | motor: helper `r(x)`; export: capa de presentación |
| Especificaciones Técnicas (ET) | DM §17 #18; procesos P-45 | `EspecificacionTecnicaService` + `EspecificacionTecnicaWriter` |
| Plantilla de proyecto (A8) | DM §3, §10; procesos P-46 | `PlantillaProyectoService` |

## 10. Riesgos y deudas

- **R1:** el helper de redondeo `r(x)` en el motor puede acumular
  desviaciones si se aplica a operaciones que originalmente son enteras
  (ej. `cantidad × tarifa` donde ambos son "limpios"). Mitigación: GM-22,
  GM-23, GM-25 deben seguir verdes tras el cambio. Si fallan, ajustar
  helper.
- **R2:** `EspecificacionTecnicaWriter` (Word) — librería docx4j tiene
  curva de aprendizaje; riesgo de tiempo. Mitigación: implementar
  layout simple (texto + secciones); validar con un APU de prueba.
- **R3:** `RecalculoService` es un módulo nuevo y crítico — un bug
  podría dejar totales desincronizados. Mitigación: suite exhaustiva
  (5 tests dedicados) + verificación post-cambio que `Motor.calcularApu`
  sigue dando el mismo resultado que el write-through manual.
- **R4:** rangos parametrizables — si el Super-Admin edita los rangos
  en `ParametrosSistema`, los proyectos existentes **no se re-validan**
  (sus valores actuales quedan "fuera de rango" sin warning). Decisión:
  documentar; si se quiere re-validación, abrir plan 014.
