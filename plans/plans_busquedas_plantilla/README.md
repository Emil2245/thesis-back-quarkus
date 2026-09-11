# Búsqueda FTS y creación de APUs desde plantillas — backend

Este paquete añade la infraestructura que necesita el nuevo diálogo `Agregar APU`: búsqueda PostgreSQL FTS paginada, catálogo de demostración suficiente, aplicación atómica de una o varias plantillas y creación manual completa en una sola transacción.

## Hallazgo de auditoría

El backend ya expone `GET /plantillas-apu?q=&tipo=` y `GET /plantillas-apu/{id}`, pero el listado trae todas las plantillas visibles y filtra `q` en memoria. No hay paginación, `tsvector`, índice GIN ni endpoint de lote. Solo existen dos seeds: una plantilla SISTEMA y una PERSONAL.

## Decisiones de producto cerradas

| Tema | Decisión |
| --- | --- |
| Búsqueda | PostgreSQL Full Text Search; nombre con peso A y descripción con peso B; tildes normalizadas; GIN. |
| Primera carga | Página 0, tamaño 20, ambas fuentes, aun con `q` vacío. |
| Fuentes | SISTEMA global + PERSONAL propia; owner-to-404 permanece. |
| Lote | Atómico, máximo acotado; cualquier fallo revierte todo y señala el índice/id visible problemático. |
| Destino omitido | Última hoja del árbol: último raíz por orden y descenso repetido por el último hijo hasta una hoja. |
| Cantidad | Cada rubro creado desde plantilla usa `1.000000`. |
| Manual | Cabecera + filas editables M/N/O/P en una única operación atómica; el cliente nunca envía totales. |
| Auditoría | Reusar `apu.creado` por cada APU dentro de la transacción; no ampliar el catálogo D-13. |

## Contratos nuevos propuestos

1. `GET /plantillas-apu/busqueda?q=&tipo=SISTEMA&tipo=PERSONAL&page=0&size=20` → `Page<PlantillaApuResumenResponse>`.
2. `POST /presupuestos/{presupuestoId}/rubros/desde-plantillas` con `{capituloId?, plantillaIds:[...]}`.
3. `POST /presupuestos/{presupuestoId}/apus/completo` con cabecera, `capituloId?`, `porcentajeIndirecto?` y detalles editables.

El listado legado `GET /plantillas-apu` y el detalle existente permanecen compatibles.

## DAG y worktrees

```text
Ola A — paralela
  001 FTS/paginación ─────────────┐
  002 seeds de catálogo ──────────┼─► 005 cierre integrado
  003 lote atómico ────────┐      │
                           └─► 004 creación manual completa ─┘
```

- 001, 002 y 003 pueden ejecutarse en worktrees separados. Solo 001 reserva V011; 002 reserva V012; 003 no crea migración.
- 004 depende de 003 para reutilizar la resolución de última hoja y primitivas transaccionales, evitando dos orquestadores.
- Ningún ejecutor individual modifica este README ni los planes hermanos.

## Planes

| # | Plan | Depende de |
| --- | --- | --- |
| 001 | [Búsqueda FTS paginada](001-busqueda-fts-paginada.md) | — |
| 002 | [Catálogo seed de plantillas](002-seed-catalogo-plantillas.md) | — |
| 003 | [Aplicación atómica por lote](003-aplicar-plantillas-en-lote.md) | — |
| 004 | [Creación manual completa](004-crear-apu-manual-completo.md) | 003 |
| 005 | [Integración, Bruno y contrato](005-integracion-contrato-bruno.md) | **DONE 2026-09-11** · 001–004 |

## Invariantes

- UUIDv7 en REST; BIGINT solo interno.
- SISTEMA visible a autenticados; PERSONAL solo de su dueño; ajena se oculta con 404.
- Snapshot price-free; los precios se resuelven contra PROYECTO → CENTRAL → PERSONAL al aplicar.
- Una sola consolidación `Alcance.Version` al final de cada operación agregada; no N recalculados completos.
- No tocar el motor ni los residuales GM aceptados.
- Migraciones aditivas; V001–V010 permanecen byte-for-byte.
- La documentación canónica de `../thesis-docs` debe reconciliar estos endpoints antes del cierre del plan 005; si contradice una decisión vigente, STOP.
