# `docs/modulos/` — Planes de implementación por módulo

Historia de decisiones y playbooks auto-contenidos para construir el backend
por módulo vertical. Cada módulo se construye **de punta a punta** (schema →
entidad → repositorio → servicio → REST → tests), siguiendo la convención de
empaquetado de `documentos/01-ARQUITECTURA.md §5`.

| Plan | Módulo | Procesos (P-xx) | Estado |
|---|---|---|---|
| [01-proyecto](01-proyecto.md) | `ec.uce.propuestas.proyecto` | P-05…P-11 | Implementado (crud núcleo) · TODO: logo, detalle |
| [02-insumo](02-insumo.md) | `ec.uce.propuestas.insumo` | P-13…P-18 | Implementado (crud, catálogo, selector, copia) · TODO: uso en APU (P-18) |
| [03-apu](03-apu.md) | `ec.uce.propuestas.apu` | P-19…P-22 | Implementado (lista/crea APUs, editor, filas M/N/O/P, fila HM protegida, override precio, write-through vía motor) · TODO: I-06 cubre P-23…P-27 |
| [04-apu-avanzado](04-apu-avanzado.md) | `apu` (ampliar) + `recalculo` (nuevo) + `plantilla` (nuevo) + `documento` (extender) | P-23…P-27, P-45, P-46 | Pendiente — plan completo escrito (N04 18-08-2026); cubre las decisiones N04 (A1 descuento CD, A2 auxiliares sin anidamiento, A3 HM reordenable, A6 rangos parametrizables, A9 bases SIEMPRE copia + nuevo tipo PERSONAL, D-12 archivar sin bloqueo, #7 decimales, ET nueva feature) |

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