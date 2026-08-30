# `docs/modulos/` — Planes de implementación por módulo

Historia de decisiones y playbooks auto-contenidos para construir el backend
por módulo vertical. Cada módulo se construye **de punta a punta** (schema →
entidad → repositorio → servicio → REST → tests), siguiendo la convención de
empaquetado de `documentos/01-ARQUITECTURA.md §5`.

| Plan | Módulo | Procesos (P-xx) | Estado |
|---|---|---|---|
| [01-proyecto](01-proyecto.md) | `ec.uce.propuestas.proyecto` | P-05…P-11 | Implementado (crud núcleo) · TODO: logo, detalle |
| [02-insumo](02-insumo.md) | `ec.uce.propuestas.insumo` | P-13…P-18 | Implementado (crud, catálogo, selector, copia) · TODO: uso en APU (P-18) |
| [03-apu](03-apu.md) | `ec.uce.propuestas.apu` | P-19…P-22, P-27 | **Implementado (DONE 2026-08-28)** — Plan 03 cierra reordenamiento atómico + HM order-only; ver `planes-para-estar-al-dia/03-contrato-apu-actual.md` |
| [04-apu-avanzado](04-apu-avanzado.md) | `apu` (ampliar) + `plantilla` (nuevo) + `documento` (extender) | P-23, P-26, P-46 + cierre I-06 | **P-26 DONE 2026-08-29 (Plan 04)** y **P-46 DONE 2026-08-29 (Plan 06, verificación principal 83/83)**. El módulo APU avanzado sigue PARTIAL únicamente por P-23 sin propagación global y `recalculo` DEFERRED. Cubre plantillas APU (P-26), plantilla proyecto (P-46), descuento CD (P-12/P-24), y cierre del display global residual del Plan 014 |

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
> (siguen `BIGINT`), motor intacto. Las suites Gradle y la verificación
> completa quedan reservadas para el cierre del
> [Plan 08](planes-para-estar-al-dia/08-cierre-documental-y-verificacion.md);
> este pase documental no ejecuta Gradle. El módulo APU avanzado sigue
> PARTIAL por P-23 sin propagación global y `recalculo` DEFERRED.

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

**No en sonancias:** `presupuesto` v1 y `cronograma` al crear proyecto se difieren
a los módulos presupuesto/cronograma (I-07/I-08); administración de bases
CENTRALES a I-11; refactor de `usuario/` a la nueva convención es limpieza.