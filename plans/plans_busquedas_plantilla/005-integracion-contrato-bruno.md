# Plan backend 005 — Integración, contrato canónico y Bruno

**Estado:** TODO · **Prioridad:** P1 · **Depende de:** backend 001–004

## 01. Resultado observable

Los cuatro cambios funcionan juntos sobre PostgreSQL limpio, están descritos en el contrato canónico y tienen journeys reproducibles. Este plan integra y corrige incompatibilidades; no añade una tercera variante de búsqueda o creación.

## 02. Reconciliación obligatoria

Actualizar, con la autoridad correspondiente, `../thesis-docs/plan/architecture/07-api-contract.md` y trazabilidad funcional antes de declarar cierre:

- añadir filas contractuales para `GET /plantillas-apu/busqueda`, `POST /presupuestos/{id}/rubros/desde-plantillas` y `POST /presupuestos/{id}/apus/completo`;
- registrar query params repetibles, `Page<T>`, DTOs, detalle existente sin cambio, códigos HTTP y owner-scope;
- registrar lote atómico máximo 20, cantidad `1.000000`, destino implícito, alta manual sin totales cliente y errores estructurados opcionales;
- asignar trazabilidad P-xx con aprobación funcional en vez de inventar un número dentro del plan.

Si `thesis-docs` contradice estas decisiones, STOP y documentar; no cambiar backend silenciosamente.

## 03. Bruno

Extender colecciones existentes sin crear una colección duplicada:

- primera página vacía de `q`;
- búsqueda acento-insensible y ranking;
- filtro SISTEMA, PERSONAL y combinado;
- detalle de resultado;
- lote simple y mixto;
- rollback en segundo elemento con identificación;
- última hoja implícita;
- creación manual completa;
- negativos 400/404/409 y owner-to-404.

Los requests deben autenticarse y derivar ids de respuestas previas; no fijar BIGINT internos.

## 04. Integración y rendimiento

- PostgreSQL 18 limpio con V001–V012.
- Medir `EXPLAIN (ANALYZE, BUFFERS)` del FTS con fixture suficiente.
- Medir lote de 20 para detectar N consolidaciones o transacción desproporcionada; registrar tiempo observado, no inventar SLO nuevo.
- Confirmar que logs `apu.creado` se revierten con la transacción fallida.
- Confirmar que cronograma queda sincronizado una vez después de éxito.

## 05. Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' --tests 'ec.uce.propuestas.apu.*' --tests 'ec.uce.propuestas.presupuesto.*' --tests 'ec.uce.propuestas.recalculo.*'
./gradlew test
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

Reportar por separado los residuales GM aceptados; no atribuirlos a este paquete.

## 06. Done criteria

- Contrato canónico y recursos coinciden byte por byte en rutas, query params y DTOs.
- Bruno ejecuta todos los journeys con conteos medidos.
- No hay filtro en memoria del nuevo endpoint.
- No hay éxito parcial ni N recalculados completos.
- V001–V010 siguen intactas.
- El frontend puede iniciar su Plan 001 sin adivinar ninguna forma.

## 07. Rollback

La reversión funcional puede retirar endpoints nuevos manteniendo el detalle/listado legado. Las migraciones Flyway aplicadas no se borran en entornos existentes: una reversión posterior debe dejar columna/configuración sin consumidores o usar una migración compensatoria aprobada.
