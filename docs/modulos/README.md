# `docs/modulos/` — Planes de implementación por módulo

Historia de decisiones y playbooks auto-contenidos para construir el backend
por módulo vertical. Cada módulo se construye **de punta a punta** (schema →
entidad → repositorio → servicio → REST → tests), siguiendo la convención de
empaquetado de `documentos/01-ARQUITECTURA.md §5`.

| Plan | Módulo | Procesos (P-xx) | Estado |
|---|---|---|---|
| [01-proyecto](01-proyecto.md) | `ec.uce.propuestas.proyecto` | P-05…P-11 | **DONE** — CRUD núcleo, firmantes y parámetros; `proyectoId` UUIDv7 en path y JSON (Plan 07). |
| [02-insumo](02-insumo.md) | `ec.uce.propuestas.insumo` | P-13…P-18 | **DONE** — CRUD, catálogo, selector multi-fuente, copia al usar (N04 §A9) y CSV. `proyectoId`/`insumoId`/`baseId` UUIDv7 en path y JSON (Plan 07). |
| [03-apu](03-apu.md) | `ec.uce.propuestas.apu` | P-19…P-22, P-27 | **DONE 2026-08-28** — Plan 03 cierra reordenamiento atómico + HM order-only. `presupuestoId`/`apuId`/`detalleId` UUIDv7 (Plan 07). Ver [`planes-para-estar-al-dia/03-contrato-apu-actual.md`](planes-para-estar-al-dia/03-contrato-apu-actual.md). |
| [04-apu-avanzado](04-apu-avanzado.md) | `apu` (ampliar) + `plantilla` (nuevo) + `documento` (extender) | P-23, P-26, P-46 + cierre I-06 | **P-26 DONE 2026-08-29 (Plan 04)** y **P-46 DONE 2026-08-29 (Plan 06, verificación principal 83/83)**. El módulo APU avanzado sigue PARTIAL únicamente por P-23 sin propagación global y `recalculo` DEFERRED. Cubre plantillas APU (P-26), plantilla proyecto (P-46), descuento CD (P-12/P-24), y cierre del display global residual del Plan 014 |
| [05-presupuesto](05-presupuesto/00.md) | `presupuesto` (nuevo) + `recalculo` (nuevo módulo profundo) | P-28, P-29, P-30, P-31, P-32 | **PLANNED / READY (I-07 — 2026-08-31)** — 7 planes ejecutables 019–025: identidad pública UUIDv7 (V008), activación del módulo profundo `recalculo`, auto-create v1 vigente, CRUD capítulos con renumeración atómica, CRUD rubros 1:1 APU con write-through, versionado (deep copy, vigente, comparación) y validación de integridad. Cronograma (P-33…P-36) y export SERCOP (P-37) quedan para I-08/I-09/I-10. **No implementado todavía.** |

> **Planes 03, 04, 05 y 06 cerrados al 2026-08-29.** P-46 quedó
> implementado por el [Plan 06](planes-para-estar-al-dia/06-plantillas-proyecto.md)
> con verificación principal 83/83 verde.
>
> **[Plan 07 — UUIDv7](planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md) — DONE · VERIFICACIÓN DIRIGIDA COMPLETA (2026-08-30).**
> Recursos migrados: `proyecto/firmante/parametros_proyecto`,
> `insumo/base_insumos` (admin central y bases personales),
> `PresupuestoApuResource` (con validación de `ApuCrearRequest.plantillaId` en
> frontera), seam `POST /proyectos/{proyectoId}/guardar-plantilla`, y
> `DocumentoResource` (ET). APU/detalle y `plantillas-apu` ya estaban
> alineados. Sin migraciones nuevas (no V008/V009), sin cambios de PK/FK
> (siguen `BIGINT`), motor intacto. Su verificación dirigida quedó cerrada
> en Plan 07 y la suite completa/Bruno fueron cerrados posteriormente por
> [Plan 08](planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md).
>
> **[Plan 08 — Cierre documental y verificación](planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md) — DONE (2026-08-30).**
> Sincroniza la documentación canónica (este README,
> `docs/00-ESTADO-ACTUAL.md`, `docs/modulos/estado-actual.md`,
> `plans/README.md` y los playbooks 01–04), repara dependencias UUID
> rotas en `api/bruno/06-proyecto/`, `07-insumo/` y `08-apu/` que el
> Plan 07 dejó desalineadas, y completa `api/bruno/09-i02-i06/`
> con los nueve temas canónicos (rangos configurables, base PERSONAL
> + copia CENTRAL, plantilla APU + fallback con advertencias,
> reordenamiento, desglose a 6 dp, ET + títulos, archivar/borrar
> CENTRAL, proyecto desde plantilla, UUID inválido + recurso ajeno).
> Spotless y build verdes; suite completa con 313 tests y únicamente los dos residuales aceptados GM-19/GM-20 (GM-24 omitido upstream).
> El módulo APU avanzado sigue PARTIAL por P-23 sin propagación
> global y `recalculo` DEFERRED.

**Decisiones de diseño compartidas** (resuelven las preguntas de arquitectura):

| Tema | Decisión |
|---|---|
| DTOs | `record` Java + `@Valid`; mappers estáticos en `mapper/` (sin MapStruct/Lombok) |
| Respuesta REST | **`RestResponse<T>`** de RESTEasy Reactive (tipado + OpenAPI limpio) |
| Errores | `ProblemaException` (record `ErrorPayload`) → reusa `GlobalExceptionMapper` de `common/` |
| Capa BD | **`PanacheRepositoryBase` por entidad** (`repository/`) — los services no usan finders estáticos; todas las consultas viven en el repo para permitir métodos de consulta específicos sin refactor futuro |
| Paginación | `common/dto/Page<T>` + `io.quarkus.panache.common.Page` |
| CSV | Apache Commons CSV (`commons-csv`), `importacion` puro + `Transaccional` |

> **Nota de desvío (documentada):** los resources usan `Response` de JAX-RS y
> retornos tipados (no `RestResponse<T>`) para coincidir con la convención real
> de `usuario/auth/PerfilResource.java`. Migrar a `RestResponse<T>` es limpieza
> opcional posterior, no bloqueo.

**Regla de oro:** no se crean migraciones SQL nuevas (tablas ya en V001–V003);
solo entidades JPA que mapean el esquema.

**No implementado todavía:** `presupuesto` v1 y `cronograma` al crear proyecto se difieren
a los módulos `presupuesto/cronograma` (I-07/I-08) — la **planificación completa
de I-07 ya está redacta** en [`docs/modulos/05-presupuesto/`](05-presupuesto/00.md)
(7 planes ejecutables 019–025 PLANNED / READY al 2026-08-31); la ejecución efectiva de los
planes queda pendiente. El módulo `recalculo` (write-through profundo) es
una **creación nueva de I-07** que se introduce junto con el módulo `presupuesto`
(según `08-codebase-design.md` §3 — diferido por I-06). El `cronograma` y la
`actividad` viven como rastro del seed V004 (BIGINT interno, sin `public_id`
UUIDv7 — V009 diferido a I-08). La administración de bases CENTRALES (P-39) está
**DONE** desde Plan 05 (2026-08-29); el refactor de `usuario/` a la nueva
convención es limpieza sin bloqueo.
