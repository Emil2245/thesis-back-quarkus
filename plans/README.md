# Planes de implementación — `thesis-back-quarkus`

Los planes de este directorio implementan decisiones ya tomadas en `thesis-docs`; no las
redefinen. Los planes ejecutados se retiraron: su evidencia vive en el código, las pruebas
(ITs, Bruno), `CLAUDE.md` y el historial git.

**Fuente de verdad (en `../../thesis-docs/plan/`):** `architecture/06-database-schema.md`
(DDL), `architecture/07-api-contract.md` (REST), `architecture/08-codebase-design.md`
(módulos), `domain/02-data-model.md` (fórmulas del motor), `roadmap/01-plan-iteraciones-xp.md`
(iteraciones).

## Qué sobrevive en `plans/`

| Archivo | Para qué se conserva |
|---|---|
| [`006-motor-consolidacion-fix.md`](006-motor-consolidacion-fix.md) | Plantilla de «plan de corrección del motor» (la cita `CLAUDE.md`). |
| [`014-motor-precision-no-links.md`](014-motor-precision-no-links.md) | Regla de redondeo workbook-consistent y sus condiciones de parada. |
| [`separar-en-microservicios/README.md`](separar-en-microservicios/README.md) | Decisiones y pendientes de la separación Core + Documentos. |

## Estado por ruta (todo DONE salvo lo indicado en «Pendientes»)

| Planes | Ruta | Nota |
|---|---|---|
| 001–006, 014, 015 | Bootstrap, auth, motor, retiro del descuento por APU | GM-19/GM-20 residuales aceptados, GM-24 omitido; la regla vive en `CLAUDE.md`. |
| 007–013 | Build Gradle, `proyecto`, `insumo`, `apu`, importación | Historial git. |
| 019–025 | Recálculo y módulo `presupuesto` (I-07) | |
| 026–031 | Cronograma y su exportación (I-08…I-10) | La exportación quedó en Documentos. |
| 032–040 | Panel Super-Admin y log D-13 (I-11) | Técnicamente DONE; piloto SUS pendiente (`docs/tesis/piloto-sus/04-estado.md`). |
| 041–044 | Usos de insumo, orden natural de `item`, bases personales y plantillas de proyecto | |
| búsquedas de plantilla 001–005 | FTS paginada, seed de plantillas, aplicación en lote, APU manual | Contrato en `07-api-contract.md`. |
| `separar-en-microservicios` 01–05 | Core + Documentos, POST + ticket | Local (FLoCI); los GET de bytes están retirados. |

## Pendientes

- I-12 aún no está planificada.
- Piloto SUS 1–2 sin ejecutar (sin participantes): `docs/tesis/piloto-sus/`.
- Pendientes de la separación en microservicios: ver `separar-en-microservicios/README.md`.
- `core/src/main/java/ec/uce/propuestas/plantilla/service/PlantillaProyectoService.java`
  (~l. 359 y ~l. 379) usa `order by c.item` / `order by r.item` en SQL nativo: orden
  lexicográfico (`"1.12" < "1.2"`). Resto del plan 042: ordenar en Java con el comparador
  natural. Para detectar otros casos usar `grep -rniE "order by [a-z]*\.?item" core/src/main/java`.
- Causa de los residuales GM-19/GM-20 sin diagnosticar (no se reabre el motor).

## Cómo añadir un plan

- El siguiente número libre es **045**; la numeración es monótona y no se reutiliza.
- Nombre `NNN-slug.md`. Empieza con «Contexto» (el porqué), lista explícitamente los
  archivos dentro y fuera de alcance y termina con «Criterios de terminado»: comandos con
  salida esperada, no prosa.
- Incluye salidas de escape: si hay una ambigüedad no cubierta por el plan ni por
  `thesis-docs`, detenerse y reportar. Las reglas de dominio están congeladas por
  entrevistas con los interesados.
- Usa el vocabulario de dominio en español de `thesis-docs/plan/domain/03-glosario.md`.
- Al terminar, actualiza la tabla de arriba (o elimina el plan si ya no aporta) y `CLAUDE.md`
  solo si cambia un invariante.
