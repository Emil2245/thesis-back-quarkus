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
| [04-apu-avanzado](04-apu-avanzado.md) | `apu` (ampliar) + `plantilla` (nuevo) + `documento` (extender) | P-23, P-26, P-46 + cierre I-06 | **Siguiente plan ejecutable** — cubre plantillas APU (P-26), plantilla proyecto (P-46), descuento CD (P-12/P-24), y cierre del display global residual del Plan 014 |

> **Plan 03 cerrado 2026-08-28.** El siguiente plan ejecutable es el
> [Plan 04 — plantillas APU](04-apu-avanzado.md) (`P-26` + `P-46`), que
> sigue el orden vertical del módulo `apu` después de cerrar reordenamiento
> atómico + HM order-only. Los huecos residuales de P-23/P-27 ya quedaron
> cubiertos por este Plan 03 (reordenamiento, shape de `/calculo`,
> precisión natural de `BigDecimal` per Plan 014 T3).

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