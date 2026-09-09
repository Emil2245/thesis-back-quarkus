# `05-presupuesto/00-analisis-reevaluacion.md` — Plan 025

> **Re-evaluación de Plan 025 antes de implementar** (regla del
> usuario codificada en [`00.md` §0](00.md)). Resultado:
> **NEEDS ADJUSTMENT — cerrado en este mismo archivo antes de tocar
> código fuente**. La implementación efectiva ejecutada y verificada
> vive en [`07-validacion-y-cierre.md`](07-validacion-y-cierre.md),
> que cierra el plan en estado **DONE (2026-09-01)**.

## 1. Lo que el plan original proponía

El plan original proponía:

- Endpoint `GET /presupuestos/{id}/validacion` con
  `ValidacionPresupuestoResponse{exportable, itemsPuCero[],
  itemsCantidadCero[], itemsSinActividad[]}`.
- DTOs nuevos `ValidacionPresupuestoResponse`, `RubroRefResponse`.
- Crear `Actividad.java` JPA mínimo (sólo lectura) si no existía,
  como rastro del seed V004.
- Implementar `itemsSinActividad` devolviendo `[]` si no hay
  cronograma (alternativa descartada).
- Cobertura ~6–8 requests en Bruno `10-presupuesto/`.
- Suite completa con conteo real al cierre.

## 2. Lo que el código actual ya resolvió (Plan 024)

El Plan 024 (DONE 2026-09-01) ya implementó el deep copy de
versiones con **SQL nativo narrow** dentro de `VersionadoService`,
sin crear entidades JPA `Actividad`/`Cronograma`. Citas textuales:

- `VersionadoService.copiarVersion(...)` (Plan 024, archivos de
  `presupuesto/service/`) — copia estructural de `cronograma` y
  `actividad` con FK remapeada al nuevo `presupuesto_id` /
  `rubro_id`; misma transacción; sin seam.
- `PresupuestoRepository.findRubrosCubiertosPorCronograma(Long)` —
  se introduce en este plan como mirror del patrón Plan 024.

## 3. NEEDS ADJUSTMENT — correcciones locked antes de implementar

### 3.1 No crear `Actividad.java`/`Cronograma.java` JPA

**Decisión locked:** **No** se introduce `Actividad.java` ni
`Cronograma.java` en este plan. El deep copy P-31 ya está cerrado
por Plan 024 con SQL nativo; las filas existen en BD y se leen con
SQL nativo narrow (`PresupuestoRepository.findRubrosCubiertosPorCronograma`).
El CRUD de estas tablas queda diferido a I-08/I-09 (Plan 026+).

**Justificación:**

- Plan 024 ya validó que introducir la entidad JPA en `presupuesto/`
  requeriría seam nuevo (import de `cronograma`/`actividad` desde
  `presupuesto/`); esto contradice el STOP (D) del módulo
  (`No crear entidad nueva JPA sin cita canónica`).
- Mantener la simetría con el deep copy SQL nativo de Plan 024.
- I-08 introducirá el CRUD real con su propia entidad JPA, tests y
  suite; ese será el momento canónico.

### 3.2 `itemsSinActividad` poblado por defecto cuando no hay cronograma

**Decisión locked:** si el presupuesto NO tiene cronograma, el
conjunto `cubiertos` es vacío y **TODOS** los rubros del presupuesto
quedan en `itemsSinActividad` (`exportable=false`). Esta es la regla
de negocio: una versión sin cronograma no es presentable porque
carece de programación temporal.

**Justificación:**

- El plan original proponía devolver `[]` y dejar `exportable=true`
  en ese caso. Esta elección contradice la intención de P-32 (alertar
  al usuario sobre versiones incompletas antes de exportar a SERCOP).
- La regla locked se documenta como TC-P32-04 del IT
  `PresupuestoValidacionResourceIT` (test escrito primero).

### 3.3 Bruno: 23 requests, no ~6–8

**Decisión locked:** la colección Bruno `10-presupuesto/` entrega 23
requests (5 helpers + 18 casos temáticos (incluyendo 4 sub-casos para P-28 mover/eliminar + 3 negativos UUIDv7/owner-scope)). El número
del plan original era demasiado optimista porque cada mutación
HTTP requiere su propio caso para verificar respuestas
independientes (status, body, capturas).

**Justificación:**

- La práctica Bruno de los planes I-06 (`09-i02-i06/`, 30+ requests)
  confirma que la granularidad fina es la forma correcta.
- Comprimir el flujo completo en 6–8 requests requeriría asumir
  side-effects compartidos entre casos (capturas implícitas que no
  son verificables independientemente).

### 3.4 Sin CRUD de cronograma/actividad; sin export-bloqueado

**Decisión locked:** este plan sólo calcula `exportable` y lo expone.
**No** implementa el CRUD de `cronograma`/`actividad` (I-08) ni el
bloqueo de export `export-bloqueado` (P-37, I-10).

### 3.5 Estado actual: actualizar, no añadir filas

**Decisión locked:** los índices `00.md`, `plans/README.md` y
`docs/modulos/README.md` ya contienen filas para los planes 019–024
en estado DONE. Plan 025 los **actualiza** (no añade filas
duplicadas) y deja la entrada 025 en estado DONE con evidencia medida.

### 3.6 Sin commit unitario ni merge

**Decisión locked:** instrucción explícita del orquestador para esta
sesión — **no** se ejecuta commit unitario ni merge al cierre de
Plan 025. Los criterios de terminado reflejan esta retirada.

### 3.7 Sin código SQL nuevo

**Decisión locked:** el endpoint de validación no crea migración
nueva (V001–V008 intactas). El SQL nativo de
`findRubrosCubiertosPorCronograma` es **sólo de lectura** y vive en
el repositorio.

## 4. Citas textuales al código que motivan las correcciones

### 4.1 `VersionadoService.copiarVersion` — deep copy SQL nativo

```java
// src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java
// (Plan 024) — cita textual:
// "Las filas `cronograma` / `actividad` aún no son entidades JPA
//  (I-08/I-09) pero DM §3 / P-31 exige copiarlas, así que el deep
//  copy las copia con **SQL nativo** dentro de `VersionadoService`
//  (FK remapeada al nuevo `presupuesto_id` / `rubro_id`, misma
//  transacción); sin entidades nuevas, sin migraciones, sin seam."
```

### 4.2 `PresupuestoRepository.findRubrosCubiertosPorCronograma` — patrón mirroring

```java
// src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java
// (Plan 025) — método agregado al Plan 024 ya existente:
// SQL nativo narrow + JOIN a cronograma, aislamiento
// cross-presupuesto vía `c.presupuesto_id = ?1`. Devuelve
// Set<Long> de IDs internos cubiertos.
```

### 4.3 `PresupuestoValidacionResourceIT` — TC-P32-04 cubre la regla locked

```java
// src/test/java/ec/uce/propuestas/presupuesto/resource/PresupuestoValidacionResourceIT.java
// (Plan 025) — test TC-P32-04 escrito primero:
// "Presupuesto con rubros pero sin cronograma: cada rubro queda
//  forzado a aparecer en `itemsSinActividad` (no existe ninguna
//  actividad posible sin cronograma). `exportable=false`."
```

### 4.4 `PresupuestoValidacionResourceIT` — TC-P32-13 read-only

```java
// (Plan 025) — test TC-P32-13 escrito primero:
// "Read-only: N invocaciones consecutivas de GET no mutan BD."
```

## 5. STOP conditions adicionales generados por la re-evaluación

- **(E)** Si al ejecutar `PresupuestoValidacionResourceIT` se
  descubre que `PresupuestoRepository.findRubrosCubiertosPorCronograma`
  no aísla cross-presupuesto (TC-P32-12 falla), **STOP** — la causa
  es SQL nativo; corregir la query sin tocar la estructura JPA.
- **(F)** Si la auditoría revela que `itemsCantidadCero` debería
  poblarse vía API, **STOP** — eso requiere romper el contrato REST
  P-29; levantar al orquestador antes de cambiar el contrato.

## 6. Veredicto

`NEEDS ADJUSTMENT` cerrado. La implementación de Plan 025 puede
proceder con las decisiones locked en §3; los archivos a tocar son
exactamente los del allowed-edit-surfaces declarado por el
orquestador.

---

**Auditor:** ejecutor del pase Plan 025 (2026-09-01).
**Estado al cierre del análisis:** cierre de `NEEDS ADJUSTMENT`
consolidado en la implementación efectiva de Plan 025; las
verificaciones dirigida y completa de suite quedaron ejecutadas por
el escritor (conteo real desde XML: suite completa **438 = 435 pass +
2 aceptados (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84`) + 1 skipped
(GM-24 `@Disabled`) + 0 errors**; `./gradlew spotlessCheck` PASS;
`./gradlew build -x test` PASS; `git diff --check` limpio; Bruno
dinámico contra PostgreSQL 18 limpio + fast-jar 23/23 verde).
`graphify update .` también cerró correctamente con 2.972 nodos,
9.064 aristas y 141 comunidades. Plan 025 queda **DONE (2026-09-01)**.