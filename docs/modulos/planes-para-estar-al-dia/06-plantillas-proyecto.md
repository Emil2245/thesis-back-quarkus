# Plan 06 — Implementar plantillas de proyecto

## Resultado esperado

Un proyecto puede crearse desde una plantilla estructural usando exclusivamente los paquetes existentes. El nuevo agregado no mantiene enlaces con el proyecto original y resuelve sus insumos con el mismo fallback de las plantillas APU.

## Dependencias

Completar primero:

- [Plan 04 — Plantillas de APU](./04-plantillas-apu.md).
- [Plan 05 — Administración de bases](./05-administracion-bases.md).

## Estado de cierre

**DONE (2026-08-29).** Plan 06 implementado y verificado con las rutas
`/proyectos/desde-plantilla/{plantillaId}` y
`/proyectos/{proyectoId}/guardar-plantilla`. Regresión principal:
`ec.uce.propuestas.plantilla.*` + `ec.uce.propuestas.schema.*` **83/83 verde**;
build sin tests verde excluyendo únicamente el gate Spotless global; y
`git diff --check` limpio. Regresiones adyacentes verificadas: APU **41/41**,
insumo **45/45** e identifier **10/10**. La suite completa no se ejecutó por
instrucción del autor. `spotlessCheck` global mantiene 31 violaciones
preexistentes ajenas; ningún Java de Plan 06 aparece en el reporte.

**Cabecera reutilizable y defaults (refinamiento post-implementación).** El
snapshot incluye ahora un bloque `cabecera` con los campos del proyecto que
son seguros de replicar al aplicar — `codigo`, `descripcion`, `anio`,
`fechaInicio`, `plazoEjecucion`, `plazoUnidad`,
`direccionInstitucional`, `subdireccionInstitucional`, `tituloEt1` y
`tituloEt2`. Se excluyen explícitamente: IDs (base/proyecto/rubro/APU/
cronograma/firmantes), `usuarioId` (owner), `estado` (siempre BORRADOR al
aplicar), `logo` (binario), `nombreProyecto` (autoritativo del request),
`plantilla_proyecto_origen_id` (lineage), `created_at`/`updated_at`. Cuando
el snapshot no trae `cabecera` (caso backward-compatible del seed V004
mínimo), el servicio usa los defaults editables mínimos — `anio` = año
actual, `plazoEjecucion` = 4, `plazoUnidad` = MES,
`direccionInstitucional` = "Pendiente de editar". Cuando el snapshot no
trae `parametros`, los defaults del singleton `ParametrosSistema` (id=1)
pueblan todos los campos no-nullables de `ParametrosProyecto` antes de
aplicar el overlay del snapshot si está presente — sin esto, la fila creada
durante `aplicar` violaría los CHECK/NOT NULL de la tabla para V004 mínimo.

## Decisiones funcionales locked (alineadas con thesis-docs)

1. **Aplicar plantilla crea un proyecto NUEVO distinto**, no edita el existente.
   Endpoint canónico: `POST /proyectos/desde-plantilla/{plantillaId}` (P-46, N04
   §A8). El cliente sólo envía `nombreProyecto`; el resto lo construye el
   backend. No existe variante "aplicar sobre un proyecto existente" — la
   decisión P-09 (duplicar destructivo) sigue excluida por v1.1 §2.4 WARNING.

2. **Guardar es explícito sobre el proyecto origen**: `POST
   /proyectos/{proyectoId}/guardar-plantilla` con `{nombre, descripcion?}`.
   El snapshot lo construye el backend — no se acepta JSONB del cliente (evita
   snapshot adulterado). Owner mismatch → 404 (RNF-05).

3. **`POST /proyectos` sigue creando un proyecto completamente en blanco.**
   No se redirige a través de lógica de plantilla; los tests de regresión
   (`ProyectoResourceIT` + `TC_PPR_15_blank_post_proyectos_no_pasa_por_plantilla`)
   cubren el camino.

4. **Rubros reconstruidos con `cantidad = 0.000000`** como "pendiente" — el
   usuario completa las cantidades de obra después. La columna sigue `NOT NULL`
   (no se hace nullable); V007 relaja el `CHECK` de `cantidad > 0` a `>= 0`
   (one-way, sin afectar el camino de la API existente que sigue exigiendo
   `> 0`).

5. **Migración V006** añade `plantilla_proyecto.descripcion TEXT NULL` (V006
   estructural, no reseed). El entity `PlantillaProyecto` y el DTO
   `PlantillaProyectoResponse` lo exponen opcionalmente. Nunca se modifica
   V001/V005.

6. **Snapshot estructural y backend-authored**, análogo a P-26:
   - Incluye: cabecera reutilizable (`cabecera` con codigo, descripcion, anio,
     fechaInicio, plazoEjecucion, plazoUnidad, direccionInstitucional,
     subdireccionInstitucional, tituloEt1, tituloEt2), parámetros del
     proyecto, títulos ET (legacy, conservados por compatibilidad),
     árbol recursivo de capítulos, rubros/APUs con sus filas
     (cantidad/rendimiento de fila APU = coeficientes estructurales;
     **no** cantidad de obra del rubro).
   - Excluye: precios efectivos, overrides, IDs de base/proyecto/rubro/APU/
     cronograma/firmantes/log/history, cantidades de obra (rubros.cantidad),
     cronograma, actividades, firmantes, log, source links, template lineage,
     owner (`usuarioId`), estado, logo, `nombreProyecto` (autoritativo del
     request `POST /proyectos/desde-plantilla/{id}`), timestamps.

7. **Fallback de insumos reusa el seam P-26**
   (`ResolverInsumoPlantillaService`):
   `PROYECTO destino → CENTRAL → PERSONAL del dueño → pendiente con
   override 0 + advertencia`. No se duplica lógica. Códigos faltantes
   generan `AdvertenciaPlantillaResponse{motivo:"no-existe-en-base-proyecto"}`.

8. **Una transacción, rollback total.** Toda la operación `aplicar` vive
   bajo un solo `@Transactional`. Un fallo intermedio deshace TODO el
   agregado (Proyecto, parámetros, base PROYECTO, Presupuesto, capítulos,
   rubros, APUs, filas). El nuevo proyecto fija
   `plantilla_proyecto_origen_id` para lineage; borrar la plantilla
   después dispara `ON DELETE SET NULL` y deja el proyecto intacto.

9. **NO se crea cronograma, ni firmantes, ni actividades, ni log** —
   Plan 06 §1 "operational data". Sin recálculo global.

## Contrato implementado

```text
GET    /plantillas-proyecto
GET    /plantillas-proyecto/{id}                          # UUIDv7; 404 ajena
DELETE /plantillas-proyecto/{id}                          # UUIDv7; 404 ajena

POST   /proyectos/{proyectoId}/guardar-plantilla          # 201; 404 ajena; 400 validación
       Body: { "nombre": "", "descripcion?": "" }

POST   /proyectos/desde-plantilla/{plantillaId}           # 201 sin advertencias; 200 con advertencias[]
       Body: { "nombre": "" }
```

Códigos y errores:

- `200 OK` con `advertencias[]` no vacío si hay códigos no resueltos
  (PROYECTO/CENTRAL/PERSONAL del caller).
- `201 Created` sin advertencias cuando todo se resuelve.
- `400 validacion` para UUIDv7 malformado o nombre ausente/vacío/excede 200.
- `404 no-encontrado` para plantilla/proyecto ajeno o inexistente (RNF-05 —
  nunca 403, no se filtra existencia).

## Alcance

### Incluye

- `PlantillaProyectoService` y resource dentro de `plantilla` (sin módulo
  nuevo de primer nivel).
- `SnapshotProyectoMapper` writer/reader JSONB (price-free + estructural).
- Guardar snapshot desde proyecto (estructural, sin precios ni IDs).
- Recrear proyecto, parámetros, base PROYECTO, Presupuesto v1 vigente,
  capítulos recursivos, rubros/APUs y estructura aprobada.
- Reutilizar parser y fallback de P-26 sin duplicar reglas.
- Advertencias por insumos faltantes.
- Eliminación de plantilla (owner-to-404) sin afectar proyectos ya creados.

### No incluye

- Crear un módulo `plantilla-proyecto` nuevo.
- Copiar cronograma, historial, firmantes o datos operativos no aprobados.
- Duplicar el proyecto mediante referencias al agregado original.
- Recálculo global.
- Variante "aplicar sobre proyecto existente" (P-09 sigue excluido).
- Endpoint para SISTEMA (P-46, N04 §A8 — solo plantillas PERSONALES del
  caller; SUPER_ADMIN no expone catálogo en el MVP).

## Cambios estructurales aplicados

| Archivo | Tipo |
|---|---|
| `src/main/resources/db/migration/V006__plantilla_proyecto_descripcion_nullable.sql` | nueva migración — añade `plantilla_proyecto.descripcion TEXT NULL` |
| `src/main/resources/db/migration/V007__rubro_cantidad_zero_pending_allowed.sql` | nueva migración — relaja `rubro.cantidad` CHECK a `>= 0` (Plan 06 §4; sin tocar la API) |
| `src/main/java/ec/uce/propuestas/plantilla/entity/PlantillaProyecto.java` | +campo `descripcion` |
| `src/main/java/ec/uce/propuestas/proyecto/entity/Proyecto.java` | +campo `plantillaProyectoOrigenId` (V001 ya tiene la FK con `ON DELETE SET NULL`) |
| `src/main/java/ec/uce/propuestas/plantilla/repository/PlantillaProyectoRepository.java` | +método `listarDeOwner(callerUsuarioId)` |
| `src/main/java/ec/uce/propuestas/plantilla/service/SnapshotProyectoMapper.java` | +record `SnapshotCabecera` (P-46 §1); reader tolera el bloque legacy `titulos` y tolera cabecera ausente |
| `src/main/java/ec/uce/propuestas/plantilla/service/PlantillaProyectoService.java` | cabecera del proyecto se captura al guardar; al aplicar replica cabecera del snapshot (o defaults editables si falta) y los defaults de `ParametrosSistema` pueblan `ParametrosProyecto` antes del overlay del snapshot |
| `src/main/java/ec/uce/propuestas/plantilla/dto/PlantillaProyectoResponse.java` | nuevo DTO de respuesta (UUIDv7 + snapshot opaco + `descripcion?`) |
| `src/main/java/ec/uce/propuestas/plantilla/dto/ProyectoDesdePlantillaResponse.java` | nuevo DTO de respuesta del apply (`ProyectoResponse + advertencias[]`) |
| `src/main/java/ec/uce/propuestas/plantilla/service/SnapshotProyectoMapper.java` | nuevo mapper JSONB (writer price-free + reader tolerante al seed V004) |
| `src/main/java/ec/uce/propuestas/plantilla/service/PlantillaProyectoService.java` | nuevo service (listar, detalle, eliminar, guardar desde proyecto, aplicar) |
| `src/main/java/ec/uce/propuestas/plantilla/resource/PlantillaProyectoResource.java` | nuevo resource `GET/DELETE /plantillas-proyecto[/{id}]` |
| `src/main/java/ec/uce/propuestas/plantilla/resource/PlantillaProyectoGuardarResource.java` | nuevo resource `POST /proyectos/{proyectoId}/guardar-plantilla` |
| `src/main/java/ec/uce/propuestas/plantilla/resource/PlantillaProyectoAplicarResource.java` | nuevo resource `POST /proyectos/desde-plantilla/{plantillaId}` |
| `src/test/java/ec/uce/propuestas/plantilla/dto/SnapshotProyectoMapperTest.java` | 10 tests focalizados del writer/reader |
| `src/test/java/ec/uce/propuestas/plantilla/service/PlantillaProyectoServiceTest.java` | 17 tests focalizados del service |
| `src/test/java/ec/uce/propuestas/plantilla/resource/PlantillaProyectoResourceIT.java` | 14 tests REST del resource completo |
| `docs/modulos/planes-para-estar-al-dia/06-plantillas-proyecto.md` | este archivo (estado de implementación) |
| `plans/README.md` | entrada del plan actualizada |

## Verificación final

```text
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' \
  --tests 'ec.uce.propuestas.schema.*' \
  -Dquarkus.http.test-port=0 --console=plain
→ BUILD SUCCESSFUL; 83 tests, 83 passed, 0 skipped/failures/errors

./gradlew build -x test -x spotlessCheck --console=plain
→ BUILD SUCCESSFUL

git diff --check
→ sin salida
```

Conteo principal: `SnapshotApuMapperTest` 5/5,
`SnapshotProyectoMapperTest` 10/10, `PlantillaApuResourceIT` 12/12,
`PlantillaProyectoResourceIT` 14/14,
`ApuCalculoServiceNullableInsumoTest` 1/1,
`PlantillaApuServiceTest` 16/16, `PlantillaProyectoServiceTest` 17/17,
`RepresentativeSeedsIT` 2/2 y `SchemaBaselineIT` 6/6.

Regresión adyacente ejecutada en un pase separado: APU 41/41, insumo 45/45,
identifier 10/10. Diez pruebas de proyecto también pasaron; cuatro casos de
`ParametrosProyectoCambioTest` siguen rojos por un fixture preexistente ajeno
que trunca `usuario` y luego asume `usuarioId=1`, por lo que no constituyen una
regresión de Plan 06 y no se modificaron para respetar el alcance.

`./gradlew spotlessCheck` global continúa rojo por 31 archivos preexistentes.
Todos los Java modificados por Plan 06 fueron formateados aisladamente mediante
`spotlessIdeHook` y ninguno aparece en el reporte. La suite completa no se
ejecutó.

## Contrato mínimo

```text
GET    /plantillas-proyecto
GET    /plantillas-proyecto/{id}
DELETE /plantillas-proyecto/{id}
POST   /proyectos/{proyectoId}/guardar-plantilla
POST   /proyectos/desde-plantilla/{plantillaId}
```

## Pasos

1. Auditar `PlantillaProyecto`, su repository, el JSONB V004 y P-46 en las
   fuentes canónicas. ✓
2. Definir el snapshot mínimo: cabecera reutilizable (`cabecera` con
   codigo, descripcion, anio, fechaInicio, plazoEjecucion, plazoUnidad,
   direccionInstitucional, subdireccionInstitucional, tituloEt1, tituloEt2)
   + parámetros + títulos ET (legacy) + árbol de capítulos + rubros/APUs y
   sus filas estructurales; sin precios, sin IDs, sin cantidades de obra,
   sin owner/estado/logo/nombre/lineage/timestamps. ✓
3. Implementar propiedad y visibilidad de plantillas sin ampliar roles. ✓
4. Reutilizar el parser/fallback de P-26; sin duplicar reglas de resolución
   de insumos. ✓
5. Crear el agregado destino en una transacción: Proyecto (BORRADOR) +
   parámetros + base PROYECTO + Presupuesto v1 vigente + capítulos
   recursivos + rubros/APUs y filas con fallback de insumos. ✓
6. Generar advertencias para códigos faltantes y usar precio 0 según P-26 +
   V005 (override 0 en la columna de la sección). ✓
7. Confirmar que NO se copian cronograma, historial, firmantes ni relaciones
   con IDs del origen; lineage sólo via `plantilla_proyecto_origen_id` con
   `ON DELETE SET NULL`. ✓
8. Probar rollback ante error intermedio y aislamiento entre propietarios
   (RNF-05 → 404). ✓ — verificado en la regresión 83/83.

## Pruebas y comprobaciones

- `SnapshotProyectoMapperTest` — 10 tests focalizados del writer/reader,
  incluyendo round-trip de cabecera, fallback legacy `titulos`, exclusión de
  owner/estado/logo/nombre/lineage/timestamps.
- `PlantillaProyectoServiceTest` — 17 tests focalizados del service,
  incluyendo captura de cabecera al guardar, réplica al aplicar, defaults
  editables para cabecera ausente, defaults de `ParametrosSistema` cuando
  el snapshot carece de parámetros (V004 mínimo aplica sin violar
  CHECK/NOT NULL), cabecera parcial.
- `PlantillaProyectoResourceIT` — 14 tests REST del resource completo.

(Ejecutadas; ver evidencia en "Verificación final".)

## Criterios de terminado

- [x] El snapshot excluye precios, cantidades de obra y datos operativos
      no autorizados.
- [x] La creación reconstruye el árbol y sus APUs sin enlazar IDs del
      origen (lineage sólo via FK opcional `plantilla_proyecto_origen_id`).
- [x] Los insumos quedan materializados en una base PROYECTO (vacía al
      aplicar; las copias CENTRAL/PERSONAL llegan vía fallback).
- [x] Los faltantes producen advertencias y override 0 (V005 estructural).
- [x] Un fallo intermedio no deja un agregado parcial (toda la operación
      vive bajo un solo `@Transactional`).
- [x] No se creó ningún módulo nuevo de primer nivel.
- [x] Verificación dirigida ejecutada: 83/83 verde, build verde y
      `git diff --check` limpio. Suite completa no ejecutada; Spotless global
      bloqueado únicamente por deuda preexistente ajena al plan.

## Condiciones de parada

- Las fuentes canónicas no determinan si el endpoint crea o completa el
  proyecto destino. **Resuelto por el autor**: el endpoint crea un proyecto
  NUEVO distinto (P-46, N04 §A8; decision confirmado en el presente pase
  y reflejado en `plans/README.md` y en
  `thesis-docs/plan/architecture/07-api-contract.md` §5).
- El snapshot requerido incluye cronograma, firmantes o historial.
  **No aplica**: P-46 sólo conserva cabecera reutilizable + árbol de
  capítulos + rubros/APUs estructurales (D-08 D-09 D-13 ya cerrados).
- La implementación exige duplicar el fallback de P-26 en vez de
  reutilizarlo. **No aplica**: el seam `ResolverInsumoPlantillaService`
  es invocado directamente desde `PlantillaProyectoService.aplicar()`
  (inyección CDI; sin duplicación).
- Aparece una dependencia con un módulo todavía inexistente. **No
  aplica**: el alcance se mantiene dentro de los paquetes existentes
  (`plantilla`, `proyecto`, `presupuesto`, `apu`, `insumo`, `common`).