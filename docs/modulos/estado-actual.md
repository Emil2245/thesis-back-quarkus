# Estado actual y plan directo para cerrar `04-apu-avanzado`

> **Corte auditado:** `main` en `9ccb25c`.
> **Modo de ejecución acordado:** directo sobre `main`, sin SDD, sin worktrees y
> sin crear nuevos módulos de primer nivel. Cada bloque termina en un commit.
> Las comprobaciones acumuladas quedan para el cierre y serán ejecutadas por el
> usuario.

Este documento responde una sola pregunta: **qué falta exactamente para dejar
los módulos backend actuales alineados con `docs/modulos/04-apu-avanzado.md` y
con los planes globales vigentes de `../thesis-docs`**.

## 1. Resumen ejecutivo

El backend ya tiene implementada una parte importante de I-06:

- IDs internos `BIGINT` + `public_id` UUIDv7.
- Seeds deterministas sin compatibilidad temporal.
- `%CI` y descuento legacy por APU.
- Duplicación profunda de APU.
- Desglose de cálculo.
- Especificaciones técnicas y exportación DOCX con Apache POI.
- Rangos configurables de parámetros.
- Bases PERSONALES y copia al usar hacia una base PROYECTO.

Lo que todavía falta, limitado a los módulos existentes, se concentra en:

1. el seam de `ParametrosProyectoCambio` está **commiteado/completo** en `main` como `feat(proyecto): expose parameter change seam`; el write-through global de parámetros hacia el frontend queda **diferido** (la costura neutral ya está expuesta para futura propagación);
2. corregir documentación contradictoria sobre APUs auxiliares;
3. aplicar precisión `CALC_PRECISION=3` y frontera APU→Rubro a 2 dp `DOWN`;
4. completar reordenamiento y precisión de la respuesta de cálculo;
5. implementar plantillas de APU;
6. completar administración de bases centrales y bases personales;
7. completar plantillas de proyecto usando los paquetes existentes;
8. uniformar UUIDv7 en las fronteras REST de los módulos actuales;
9. completar Bruno/documentación y ejecutar la verificación final.

El write-through global que exige un módulo profundo `recalculo` queda
**diferido**, porque crear ese nuevo módulo contradice el alcance solicitado en
esta etapa.

---

## 2. Regla de precedencia de decisiones

Hay documentos de `thesis-docs` que todavía describen APUs auxiliares enlazados,
pero esa descripción quedó superada.

### 2.1 Decisión final: no existen enlaces entre APUs

Fuente más reciente y específica:

- `../thesis-docs/DOCUMENTOS/entrevistas/04/temporal/Respuesta_Entrevista_N04_TERMPORAL.md`
  §2: **“NO EXISTEN ENLACES EN APUS AUXILIALES”**. Si un caso necesita un
  supuesto “rubro auxiliar”, se crea otro rubro/APU ordinario independiente.
- Decisión explícita del autor durante la alineación backend: no implementar
  `es_auxiliar`, `apu_auxiliar_id`, `cdAuxiliar`, propagación entre APUs ni
  `CAMBIO_AUXILIAR`.

Por tanto:

- no agregar `apu.es_auxiliar`;
- no agregar `apu_detalle.apu_auxiliar_id`;
- no crear `ApuValidacionService` para anidamiento;
- no crear endpoints o DTOs con `apuAuxiliarId`;
- no mantener ramas auxiliares dentro del motor;
- un APU puede contener cualquier subconjunto de EQUIPO, MANO_OBRA, MATERIAL y
  TRANSPORTE, incluso una sola sección.

### 2.2 Documentos globales que siguen desactualizados

Deben reconciliarse con la decisión final de no-links:

- `../thesis-docs/README.md`;
- `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md`;
- `../thesis-docs/plan/architecture/06-database-schema.md`;
- `../thesis-docs/plan/architecture/07-api-contract.md`;
- `../thesis-docs/plan/architecture/08-codebase-design.md`;
- `../thesis-docs/plan/domain/02-data-model.md`;
- `../thesis-docs/plan/design/03-procesos-detalle.md`;
- `../thesis-docs/plan/design/07-decisiones-i06-pendientes.md`;
- `../thesis-docs/plan/quality/02-catalogo-pruebas.md`;
- `../thesis-docs/plan/quality/03-trazabilidad.md`;
- `../thesis-docs/plan/quality/04-poblamiento-bd.md`;
- `../thesis-docs/DOCUMENTOS/requerimientos/v1.1-functional-requirements.md`;
- `../thesis-docs/DOCUMENTOS/requerimientos/v1.2-functional-requirements.md`;
- `docs/modulos/04-apu-avanzado.md` de este backend.

Los casos `TC-P25-*` ligados a referencias auxiliares deben marcarse como
**obsoletos/superseded**, no implementarse.

### 2.3 Otras decisiones globales vigentes

| Tema | Decisión vigente | Fuente principal |
|---|---|---|
| Precisión de cálculo | `CALC_PRECISION=3`, `HALF_UP`, tras cada operación | `thesis-docs/CLAUDE.md`, `plan/domain/02-data-model.md` §0/§16/§17 #19 |
| Consolidación APU→Rubro | precio unitario y total a 2 dp con `DOWN` | `plan/design/07-decisiones-i06-pendientes.md` N04-bis; backend `plans/006` |
| Presentación | `DISPLAY_PRECISION=2`, solo UI/export | `plan/domain/02-data-model.md`; `plan/design/04-export-sercop-spec.md` |
| Persistencia | `NUMERIC(14,6)` | `plan/architecture/06-database-schema.md` |
| Bases | CENTRAL/PERSONAL siempre se copian a PROYECTO | `plan/design/07-decisiones-i06-pendientes.md` §A9 |
| D-12 | archivar central; después puede borrarse sin bloqueo | `plan/design/03-procesos-detalle.md` §J D-12 |
| ET | un DOCX por proyecto, títulos personalizables | `plan/design/03-procesos-detalle.md` P-45 |
| Librería documental | Apache POI en backend | `plan/README.md`, `plan/backend/01-quarkus-backend.md` |

---

## 3. Estado Git que debe resolverse primero

> **Actualización 2026-08-28:** el bloque descrito a continuación
> (`ParametrosProyectoCambio` + ajustes en `01-proyecto.md`, `Resource` y
> `Service`) ya está **commiteado en `main`** y constituye la costura
> neutral de WU-06. Este §3 se conserva como checklist histórico;
> mantenlo en este documento mientras siga siendo la lista de fuentes
> pendientes de alinear.

Histórico (corte auditado previo a la reconciliación):

En el corte auditado, `main` estaba limpio hasta `9ccb25c`, pero conservaba
un bloque sin commit:

```text
M  docs/modulos/01-proyecto.md
M  src/main/java/ec/uce/propuestas/proyecto/resource/ParametrosProyectoResource.java
M  src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoService.java
?? src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoCambio.java
?? src/test/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoCambioTest.java
```

Este bloque implementa la costura neutral de WU-06:

```java
ParametrosProyectoCambio(
    Long proyectoId,
    boolean porcentajeIndirectoCambio,
    boolean porcentajeHerramientaMenorCambio,
    ParametrosProyectoResponse parametros)
```

**Acción ejecutada:**

- Bloque commiteado como `feat(proyecto): expose parameter change seam`.
- `ParametrosProyectoResource` devuelve únicamente `cambio.parametros()`
  (`ParametrosProyectoResponse`); los flags internos y el `Long proyectoId`
  **no** se exponen por REST.
- La migración del `proyectoId` en path y de
  `ParametrosProyectoResponse.id` a UUIDv7 está diferida a
  [`planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md`](planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md).

Si en el futuro se decide revertir la costura, hay que revertir
exactamente esos cinco paths; no mezclar esa decisión con los bloques
siguientes.

---

## 4. Matriz de capacidades

### Leyenda

- **DONE:** comportamiento y prueba principal existentes.
- **PARTIAL:** existe una parte útil, pero el contrato no está cerrado.
- **MISSING:** no existe implementación funcional.
- **DEFERRED:** exige un módulo nuevo o una iteración fuera del alcance actual.

| Capacidad | Estado | Evidencia actual | Falta exacta |
|---|---|---|---|
| P-23 `%CI` por APU | **PARTIAL** | `ApuResource` y `ApuCrudService.actualizarPorcentajeIndirecto` | propagación cuando cambia el default del proyecto; depende de write-through global |
| P-24 descuento legacy por APU | **DONE** | `PATCH /apus/{id}/porcentaje-descuento` | nada dentro del atajo legacy |
| Descuento FORMA 1 global | **DEFERRED** | tablas estructurales de snapshot presentes | servicio de presupuesto + recálculo transaccional |
| Descuento FORMA 2 | **PARTIAL** | `PUT` de insumos PROYECTO existente | recalcular APUs que heredan el precio; debounce pertenece al frontend |
| P-25 enlaces auxiliares | **OBSOLETO** | schema/entities actuales correctamente no los tienen | eliminar referencias antiguas de docs y motor; no crear columnas/endpoints |
| P-26 plantillas de APU | **MISSING** | solo `PlantillaApu` + repository | servicio, resource, DTOs, guardar snapshot, cargar con fallback |
| P-27 desglose de cálculo | **PARTIAL** | DTOs, `ApuCalculoService.proyectar`, `GET /calculo` | aplicar CALC_PRECISION en response y cubrir shape/orden |
| Duplicar APU | **DONE** | `ApuDuplicarService`, `POST /duplicar` | comprobar que no reaparezca vocabulario auxiliar |
| P-45 ET por APU | **DONE** | GET/PUT ET, `DocumentoResource`, `EspecificacionesTecnicasService` | solo sincronizar docs: usa Apache POI, no docx4j |
| P-46 plantilla de proyecto | **MISSING** | `PlantillaProyecto` + repository | servicio/resource/carga usando paquetes `plantilla`, `proyecto`, `presupuesto` existentes |
| A3 reordenamiento | **PARTIAL** | entidad tiene `orden`; cálculo ordena | aceptar `orden` en PATCH y persistirlo; prueba TC-P27-02 |
| A6 rangos globales | **DONE** | columnas, GET/PUT admin, validación dinámica, `ParametrosRangoDinamicoTest`, `ParametrosProyectoCambio` + test commitados | nada (la costura neutral ya está expuesta para futura propagación) |
| A9 base PERSONAL | **DONE** | `BasesPersonalesService/Resource` | DELETE personal opcional indicado en Plan 04 |
| A9 copia al usar | **DONE** | `ResolverInsumoProyectoService`, integración en `ApuCrudService` | nada para creación de filas; reutilizarlo desde plantillas |
| D-12 central | **MISSING** | listado central activo solamente | CRUD admin, archivar y borrar sin bloqueo |
| Precisión del motor | **MISSING** | motor aún usa `MathContext`/precisión histórica | CALC=3 por operación, remover ramas auxiliares, config explícita |
| Consolidación GM-19/20 | **MISSING** | `Consolidador` todavía no aplica 2dp DOWN en frontera | implementar plan 006 realmente |
| UUIDv7 en APU | **DONE** | paths APU y detalle usan UUIDv7 | nada |
| UUIDv7 resto de módulos | **PARTIAL** | entidades/repositories tienen `publicId` | varios resources actuales aún usan `Long` en paths/responses |
| Write-through global | **DEFERRED** | costura `ParametrosProyectoCambio` ya commiteada y neutral | módulo profundo `recalculo`, excluido por alcance actual |

---

## 5. Archivos ya alineados que no deben rehacerse

### Seeds e identidad

- `V001__baseline.sql`: schema reconstruido con BIGINT interno y UUIDv7 público.
- `V002__seed.sql`, `V003__seed_insumos.sql`, `V004__seed_escenarios.sql`:
  IDs explícitos y sin compatibilidad temporal.
- `RepresentativeSeedsIT`: contrato de fuente y rebuild determinista.
- `PublicIdPersistenceTest`: persistencia/immutabilidad UUIDv7.

### APU

- `%CI` y descuento legacy.
- duplicación profunda;
- ET;
- cálculo/desglose;
- IDs públicos en recursos APU/detalle;
- copia al usar para insumos CENTRAL/PERSONAL.

### Proyecto/insumo/documento

- rangos dinámicos;
- bases PERSONALES;
- materialización PROYECTO;
- exportación DOCX de ET con Apache POI.

---

## 6. Plan de ejecución directa por bloques

No se usan fases SDD. Cada bloque se aplica directamente a `main` y termina en
un commit independiente. Las pruebas se acumulan y se ejecutan al final.

### Bloque 0 — línea base y fuente de verdad reconciliadas

**Estado:** aplicado en el árbol de trabajo; pendiente de revisión y commit por el
usuario.

Resultados:

1. `ParametrosProyectoCambio` ya estaba terminado y commiteado antes de este
   bloque; su seam permanece neutral y el write-through global está diferido;
2. `docs/modulos/04-apu-avanzado.md` quedó reconciliado:
   - P-25 auxiliar, `ApuValidacionService`, `CAMBIO_AUXILIAR`, `es_auxiliar`,
     `apu_auxiliar_id` y `cdAuxiliar` están marcados como obsoletos/prohibidos;
   - Apache POI sustituye cualquier instrucción previa de usar docx4j;
   - lo implementado está marcado DONE y `recalculo` queda diferido;
3. los documentos globales enumerados en §2.2 se sincronizaron con la nueva
   versión acumulativa `v1.3-functional-requirements.md`;
4. `plans/README.md` refleja:
   - Plan 013 en estado **PARTIAL**;
   - Plan 006 como “decisión cerrada, código aún pendiente” hasta aplicar DOWN.

Commit sugerido:

```text
feat(proyecto): expose parameter change seam
```

La reconciliación documental puede ir en un segundo commit:

```text
docs(i06): reconcile advanced APU plan with no-links decision
```

### Bloque 1 — motor: precisión, consolidación y limpieza no-links

**Objetivo:** cerrar la parte matemática de I-02/I-06 sin introducir un módulo
nuevo.

Antes de editar, crear el plan obligatorio:

```text
plans/014-motor-precision-no-links.md
```

Actualizar también:

- `CLAUDE.md` del backend;
- `../thesis-docs/CLAUDE.md`.

Cambios:

1. `common/config/PrecisionConfig.java`:
   - `calcPrecision` default 3;
   - `displayPrecision` default 2;
   - configuración vía MicroProfile/env;
2. `motor/ParametrosCalculo.java`:
   - agregar precisión inmutable;
   - conservar constructor compatible;
3. `motor/Motor.java`:
   - helper privado `r(BigDecimal, int)` con `HALF_UP`;
   - redondear productos, porcentajes, sumas, restas y acumulaciones;
   - eliminar `in.esAuxiliar()`;
4. `motor/ApuSnapshot.java` y `ApuCalculado.java`:
   - eliminar `esAuxiliar`;
   - representar solamente un APU ordinario;
   - si hace falta override de CI por APU, usar un campo semántico de
     `porcentajeIndirecto`, nunca un booleano auxiliar;
5. `motor/FilaSnapshot.java`:
   - eliminar `cdAuxiliar`;
6. `motor/internal/CalculadorFila.java`:
   - modificar únicamente lo autorizado por el cambio funcional explícito;
   - no agregar tolerancias ni aproximaciones;
7. `motor/internal/Consolidador.java`:
   - `precioUnitario = costoTotal.setScale(2, DOWN)`;
   - `precioTotal = cantidad × precioUnitario`, luego `setScale(2, DOWN)`;
8. adaptar fixtures/propiedades que todavía describen auxiliares, sin modificar
   tolerancias de golden masters;
9. aplicar `DISPLAY_PRECISION` solamente en response/export, no dentro del motor.

Commit sugerido:

```text
feat(motor): apply calculation and consolidation precision
```

### Bloque 2 — cerrar el contrato APU actual

**Objetivo:** completar P-23/P-27/A3 usando únicamente `apu`.

Cambios:

1. `ApuDetallePatchRequest`:
   - agregar `JsonNullable<Integer> orden`;
2. `ApuCrudService.editarDetalle`:
   - persistir el nuevo orden;
   - mantener la fila HM protegida contra borrado, pero permitir reordenarla;
3. `ApuCalculoService`:
   - resultados monetarios a CALC_PRECISION;
   - operandos de `operacion` a 6 dp;
   - respetar orden persistido;
4. revisar `ApuCalculoResponse`, `ApuCalculoLinea` y `ApuCalculoResumen` para no
   exponer BIGINT ni valores auxiliares;
5. añadir/ajustar pruebas:
   - TC-P23 set/clear;
   - TC-P27-01 shape;
   - TC-P27-02 reordenamiento;
   - TC-DECIMALES-CALC3-DISP2 API.

Commit sugerido:

```text
feat(apu): finish calculation response and row ordering
```

### Bloque 3 — plantillas de APU P-26

**Objetivo:** convertir `plantilla` de una costura estructural a una capacidad
usable, sin crear otro módulo.

Archivos principales:

- nuevos DTOs bajo `plantilla/dto/`;
- `plantilla/service/PlantillaApuService.java`;
- `plantilla/resource/PlantillaApuResource.java`;
- `ApuCrearRequest`;
- `PresupuestoApuResource`;
- `ApuCrudService`;
- `ResolverInsumoProyectoService`.

Comportamiento:

1. listar SISTEMA + PERSONALES propias;
2. obtener, renombrar y eliminar una PERSONAL propia;
3. guardar un APU como plantilla PERSONAL;
4. snapshot JSONB sin precios: códigos, cantidades, rendimientos, HM y override
   explícito cuando corresponda;
5. cargar plantilla al crear APU;
6. resolver cada código:
   - existe en PROYECTO → reutilizar;
   - existe en CENTRAL/PERSONAL visible → copiar a PROYECTO;
   - no existe → fila con precio 0 + `advertencias[]`;
7. tolerar los campos extra de las plantillas V004; no crear reseed V005.

Endpoints:

```text
GET    /plantillas-apu
GET    /plantillas-apu/{id}
PUT    /plantillas-apu/{id}
DELETE /plantillas-apu/{id}
POST   /apus/{id}/guardar-plantilla
POST   /presupuestos/{id}/apus  { plantillaId }
```

Commit sugerido:

```text
feat(plantilla): add reusable APU templates with fallback
```

### Bloque 4 — completar administración de bases actuales

**Objetivo:** cerrar A9/D-12 dentro de `insumo`.

Cambios:

1. bases personales:
   - `DELETE /bases-personales/{id}` con owner-to-404;
2. administración central bajo `/admin/bases-centrales`:
   - listar/crear/renombrar;
   - CRUD de insumos centrales;
   - importar CSV;
   - `PUT /admin/bases-centrales/{id}/archivar`;
   - `DELETE /admin/bases-centrales/{id}` solo después de archivar;
3. archivar oculta del catálogo;
4. borrar no comprueba referencias de proyectos, porque esos proyectos usan
   copias PROYECTO;
5. mantener dos roles únicamente: USUARIO y SUPER_ADMIN.

Commit sugerido:

```text
feat(insumo): complete personal and central base administration
```

### Bloque 5 — plantillas de proyecto P-46 en paquetes existentes

**Objetivo:** completar la decisión N04 más reciente sin crear un módulo nuevo.

Usar exclusivamente:

- `plantilla`;
- `proyecto`;
- `presupuesto`;
- `apu`;
- `insumo`.

Cambios:

1. `PlantillaProyectoService` y resource;
2. guardar snapshot estructural sin precios ni cantidades de obra;
3. recrear proyecto, parámetros, capítulos, rubros/APUs y estructura disponible;
4. resolver insumos con el mismo fallback de P-26;
5. advertir faltantes, nunca enlazar el nuevo proyecto con el original;
6. no copiar cronograma, historial, firmantes ni datos operativos fuera del
   snapshot aprobado.

Endpoints:

```text
GET    /plantillas-proyecto
POST   /plantillas-proyecto
DELETE /plantillas-proyecto/{id}
POST   /proyectos/{proyectoId}/desde-plantilla/{plantillaId}
```

> Este bloque permanece dentro de módulos ya existentes, pero debe ejecutarse
> después de P-26 porque comparte parser y fallback.

Commit sugerido:

```text
feat(plantilla): add project templates from existing aggregates
```

### Bloque 6 — alinear UUIDv7 en todas las fronteras actuales

**Objetivo:** que ningún resource actual exponga o acepte BIGINT internos.

Revisar:

- `proyecto` y firmantes;
- `insumo` y bases;
- `presupuesto`;
- `plantilla`;
- `documento`;
- referencias anidadas en DTOs.

Reglas:

1. paths públicos reciben UUIDv7 como `String` y validan mediante `UuidV7`;
2. repositories resuelven UUID + owner a BIGINT una sola vez;
3. joins y FKs permanecen BIGINT;
4. JSON usa el nombre semántico `id`, nunca `public_id`;
5. owner ajeno → 404;
6. UUID malformado/no-v7 → 400 `validacion`.

Commit sugerido:

```text
refactor(api): finish public UUID boundaries
```

### Bloque 7 — documentación, Bruno y cierre

Actualizar:

- `docs/modulos/01-proyecto.md`;
- `docs/modulos/02-insumo.md`;
- `docs/modulos/03-apu.md`;
- `docs/modulos/04-apu-avanzado.md`;
- `docs/modulos/README.md`;
- `docs/00-ESTADO-ACTUAL.md`;
- `plans/README.md`;
- colección `api/bruno/09-i02-i06/` con UUIDv7 públicos.

Casos mínimos Bruno:

- rangos configurables;
- PERSONAL y copia CENTRAL;
- plantillas APU y fallback;
- reordenamiento;
- cálculo a 3 dp;
- ET y títulos;
- archivar/borrar central;
- UUID inválido/ajeno.

Commit sugerido:

```text
docs(i06): reconcile backend modules and API examples
```

---

## 7. Trabajo explícitamente diferido

No debe introducirse indirectamente dentro de otros servicios.

### Nuevo módulo `recalculo`

Diferido por la restricción actual de no crear módulos nuevos. Mientras no
exista, permanecen incompletos:

- propagación de `%HM` a todos los APUs;
- propagación del `%CI` default solo a APUs sin override;
- descuento global FORMA 1;
- recálculo tras edición atómica FORMA 2;
- rollback transaccional de una recalculación masiva;
- actualización de presupuesto/cronograma posterior.

### Funcionalidad de iteraciones posteriores

- cronograma completo;
- export SERCOP xlsx/pdf completo;
- administración de usuarios;
- VAE;
- operaciones bulk de APU;
- frontend drag-and-drop/debounce visual.

---

## 8. Criterios de aceptación por bloque

| Bloque | Resultado observable |
|---|---|
| 0 | `main` sin cambios flotantes; docs ya no ordenan enlaces auxiliares |
| 1 | motor calcula a 3 dp; APU→Rubro usa DOWN 2 dp; no hay ramas auxiliares |
| 2 | PATCH orden funciona; cálculo respeta orden y precisión |
| 3 | plantilla PERSONAL se guarda/carga; fallback produce advertencias |
| 4 | central archivada desaparece; borrado no afecta copias PROYECTO |
| 5 | proyecto se crea desde snapshot sin enlazar datos originales |
| 6 | APIs actuales no filtran BIGINT internos |
| 7 | docs/Bruno describen exactamente el código final |

---

## 9. Verificación final para ejecutar al terminar

El usuario ejecutará estas comprobaciones al cierre:

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Formato/compilación
./gradlew spotlessCheck
./gradlew build -x test

# Schema/seeds/identidad
./gradlew test --tests 'ec.uce.propuestas.schema.*'
./gradlew test --tests 'ec.uce.propuestas.identifier.*'

# Motor
./gradlew test --tests 'ec.uce.propuestas.motor.*'

# Módulos actuales
./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
./gradlew test --tests 'ec.uce.propuestas.apu.*'
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew test --tests 'ec.uce.propuestas.documento.*'

# Suite completa
./gradlew test

# Restricciones críticas
! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'apu_auxiliar_id|apuAuxiliarId|CAMBIO_AUXILIAR|cdAuxiliar|esAuxiliar' \
  src/main src/test docs/modulos
! find src/main/resources/db/migration -maxdepth 1 \( -name 'V005*' -o -name 'V006*' \) -print | grep .

git diff --check
git status --short
```

Para validar el grafo después de todos los cambios:

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado
graphify update .
```

---

## 10. Definición de “backend actual al día”

Se considera alcanzada cuando:

- no quedan cambios sin commit en `main`;
- los documentos ya no contradicen la decisión no-links;
- las capacidades DONE/PARTIAL de esta matriz están cerradas dentro de módulos
  existentes;
- lo diferido está marcado explícitamente y no tiene imports o stubs ocultos;
- las APIs actuales usan UUIDv7 públicos;
- motor, seeds, schema y suite completa pasan;
- no se creó ningún módulo nuevo de primer nivel.
