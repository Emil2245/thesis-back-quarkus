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
| [04-apu-avanzado](04-apu-avanzado.md) | `apu` (ampliar) + `plantilla` (nuevo) + `documento` (extender) | P-23, P-26, P-46 + cierre I-06 | **P-26 DONE 2026-08-29 (Plan 04, 34/34 + regresión dirigida 47/47)**, P-46 pendiente (Plan 06). El módulo APU avanzado sigue PARTIAL en su conjunto (P-23 sin propagación global, P-46 MISSING, `recalculo` DEFERRED); sólo P-26 (plantillas APU) está cerrado. Cubre plantillas APU (P-26), plantilla proyecto (P-46), descuento CD (P-12/P-24), y cierre del display global residual del Plan 014 |

> **Plan 03 cerrado 2026-08-28 · Plan 04 cerrado 2026-08-29 (P-26).**
> El siguiente plan ejecutable es el
> [Plan 05 — administración de bases actuales](planes-para-estar-al-dia/05-administracion-bases.md)
> (`D-12` archivar/borrar central sin bloqueo + `A9` cerrar CRUD de bases
> PERSONALES), que sigue el orden vertical del módulo `insumo` después de
> cerrar plantillas APU. P-46 (plantilla de proyecto) queda en
> [Plan 06](planes-para-estar-al-dia/06-plantillas-proyecto.md) — **no** se
> cierra aquí y el módulo APU avanzado sigue PARTIAL en su conjunto
> (`P-23` sin propagación global, `recalculo` DEFERRED, etc.).

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