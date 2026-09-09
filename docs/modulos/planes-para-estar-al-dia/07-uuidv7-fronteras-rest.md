# Plan 07 — Uniformar UUIDv7 en las APIs actuales

> **Estado:** **DONE (2026-08-30).**
> Implementación y verificación dirigida completadas. No se cambió DDL, PK/FK ni motor.
> La suite completa, Bruno y el cierre transversal permanecen en el Plan 08.

## Resultado esperado

Todas las fronteras REST de los módulos actuales reciben y devuelven UUIDv7
públicos bajo el nombre semántico `id`. Los `BIGINT` permanecen como detalle
interno de persistencia y nunca aparecen en paths ni en JSON. Reglas comunes:

- Paths públicos reciben UUIDv7 como `String` y se validan con
  `ec.uce.propuestas.common.UuidV7.parse` (regex v7 + `UUID.version() == 7` +
  variant `2`); entrada malformada o no-v7 → 400 `validacion` antes de tocar BD.
- Repositories resuelven `UUID + owner` → `BIGINT` una sola vez por frontera
  (`findByPublicIdAndOwnerScope` o equivalente); UUID ajeno o inexistente →
  404 `no-encontrado` (RNF-05; nunca 403).
- JSON usa el nombre semántico `id`, no `publicId` ni `public_id`.
- Los `BIGINT` se retienen debajo de los resources y de los services.

## Dependencias

Planes 03, 04, 05 y 06 ya están **DONE 2026-08-29** (ver
[`planes-para-estar-al-dia/00.md`](./00.md) y `docs/modulos/README.md`). Plan 07
los usa como base y no introduce nuevas migraciones.

## Alcance

### Incluye

- Auditar y migrar `proyecto`, firmantes, `parametros_proyecto`,
  `insumo`, `base_insumos` (admin central y bases personales), `presupuesto`
  (boundary en `PresupuestoApuResource` y en `DocumentoResource`), y la seam
  de `proyecto → guardar-plantilla`.
- Validar los `UUID` de los bodies (`plantillaId` en
  `ApuCrearRequest`; `baseId` / `proyectoId` en `CopiarBaseRequest`).
- Resolver UUID + owner a BIGINT en los repositories.
- Devolver 400 `validacion` o 404 `no-encontrado` coherentes.
- Cerrar referencias anidadas y `Location` headers para que no filtren BIGINT.

### No incluye

- Cambiar PK/FK internas de BIGINT a UUID.
- Modificar tablas que ya tienen `public_id` correcto (columna vigente desde
  V001 — ningún cambio DDL en este plan).
- Crear endpoints nuevos ajenos a la migración.
- Cambiar los IDs internos usados por el motor puro.

## Reglas (aplicadas)

1. Los paths públicos reciben UUIDv7 como `String` y validan con `UuidV7`.
2. Los repositories resuelven UUID + owner a BIGINT una sola vez por frontera
   de service.
3. JSON usa el nombre semántico `id`, no `publicId` ni `public_id`.
4. Recurso ajeno o inexistente devuelve 404 `no-encontrado` (RNF-05).
5. UUID malformado o que no es v7 devuelve 400 `validacion`.
6. Joins y claves foráneas permanecen BIGINT.

## Matriz de fronteras implementadas

Identidad externa = columna `public_id` (UUIDv7, generada por la BD; trigger de
inmutabilidad V001). `Id` interno = `BIGINT` (`PanacheEntityBase` +
`@GeneratedValue(IDENTITY)`). Las filas marcadas **alineado previo** ya
operaban con UUIDv7 antes de este plan; las marcadas **migrado en Plan 07**
fueron convertidas en este pase.

### Recurso: proyecto y firmantes

| Path / endpoint | ID externo | Id interno | Seam | Reglas |
|---|---|---|---|---|
| `GET /proyectos` | — | — | `proyectoService.listarDeUsuario` | owner-scoped |
| `POST /proyectos` | — | — | `proyectoService.crear` | `public_id` autogenerado V001 |
| `GET /proyectos/{proyectoId}` | UUIDv7 | `proyecto.id` | `ProyectoMapper` | `validarPropietario` → 404 si ajeno |
| `PUT /proyectos/{proyectoId}` | UUIDv7 | `proyecto.id` | `UuidV7.parse` | body `ProyectoEditarRequest` (sin IDs) |
| `DELETE /proyectos/{proyectoId}` | UUIDv7 | `proyecto.id` | `UuidV7.parse` | owner-to-404 |
| `GET /proyectos/{proyectoId}/firmantes` | UUIDv7 | `firmante.id` | `FirmanteService.listarDeProyecto` | owner-to-404 vía proyecto |
| `POST /proyectos/{proyectoId}/firmantes` | UUIDv7 (path) | `firmante.id` | `UuidV7.parse` | `FirmanteCrearRequest` (sin IDs) |
| `PUT /proyectos/{proyectoId}/firmantes/{firmanteId}` | UUIDv7×2 | `firmante.id` | `UuidV7.parse`×2 | ambos parseados; owner-to-404 |
| `DELETE /proyectos/{proyectoId}/firmantes/{firmanteId}` | UUIDv7×2 | `firmante.id` | `UuidV7.parse`×2 | owner-to-404 |

Recursos: `proyecto/resource/ProyectoResource.java`,
`proyecto/resource/FirmanteResource.java`.
DTOs: `ProyectoResponse(FirmanteResponse)` exponen `UUID id`;
`ProyectoCrearRequest`, `ProyectoEditarRequest`, `FirmanteCrearRequest` no
exponen ningún ID (lo fija el servidor).

### Recurso: parámetros del proyecto

| Path / endpoint | ID externo | Id interno | Seam | Reglas |
|---|---|---|---|---|
| `GET /proyectos/{proyectoId}/parametros` | UUIDv7 | `proyecto.id` | `UuidV7.parse` | owner-to-404 vía `validarPropietario` |
| `PUT /proyectos/{proyectoId}/parametros` | UUIDv7 | `proyecto.id` | `UuidV7.parse` | body `ParametrosProyectoEditarRequest` sin IDs; seam `ParametrosProyectoCambio` neutral |
| `GET /proyectos/parametros-sistema` | — | — | `parametrosService.leerSistema` | singleton (`Short id`) — sin exposición |
| `PUT /proyectos/parametros-sistema` | — | — | `parametrosService.actualizarSistema` | `SUPER_ADMIN`; sin IDs en body |

DTO: `ParametrosProyectoResponse.proyectoId` = UUIDv7 (migrado en este plan,
antes `Long` interno).

### Recurso: insumos y bases (proyecto + admin)

| Path / endpoint | ID externo | Id interno | Seam | Reglas |
|---|---|---|---|---|
| `GET /proyectos/{proyectoId}/insumos` | UUIDv7 | `proyecto.id` | `resolverProyectoYBase` | base PROYECTO vía `asegurarBaseProyecto` |
| `GET /proyectos/{proyectoId}/insumos/selector` | UUIDv7 | `proyecto.id` | `UuidV7.parse` | owner-to-404 |
| `POST /proyectos/{proyectoId}/insumos` | UUIDv7 (path) | `insumo.id` | `resolverProyectoYBase` | body `InsumoCrearRequest` sin IDs |
| `PUT /proyectos/{proyectoId}/insumos/{insumoId}` | UUIDv7×2 | `insumo.id` | `UuidV7.parse` | ambos parseados; owner-to-404 |
| `DELETE /proyectos/{proyectoId}/insumos/{insumoId}` | UUIDv7×2 | `insumo.id` | `UuidV7.parse` | owner-to-404 |
| `GET /proyectos/{proyectoId}/insumos/{insumoId}/usos` | UUIDv7×2 | `insumo.id` | `UuidV7.parse` | `InsumoUsoResponse.apuId` = UUIDv7 |
| `POST /proyectos/{proyectoId}/insumos/importar` | UUIDv7 | `base.id` | `resolverProyectoYBase` | multipart; sin IDs en body |
| `POST /proyectos/{proyectoId}/insumos/copiar` | UUIDv7 (path) + `UUID baseId?` + `UUID proyectoId?` | `base.id` | `UuidV7.parse` (path) + `CopiarBaseRequest` (body) | `CopiarBaseRequest` ya migrado a `UUID baseId`/`UUID proyectoId` |
| `GET /bases-centrales` | — | — | `BaseInsumosService.listarCentrales` | DTO `BaseInsumosResponse.id` = UUIDv7 |
| `GET /admin/bases-centrales?incluirArchivadas=` | — | — | `listarCentralesAdminEntidades` | admin; `AdminBaseCentralResponse.id` = UUIDv7 |
| `POST /admin/bases-centrales` | — | — | `crearCentral` | body `AdminBaseCentralCrearRequest` sin IDs |
| `PUT /admin/bases-centrales/{id}` | UUIDv7 | `base.id` | `UuidV7.parse` | `AdminBaseCentralEditarRequest` sin IDs |
| `POST /admin/bases-centrales/{id}/archivar` | UUIDv7 | `base.id` | `UuidV7.parse` | N04 §D-12; oculta del catálogo normal |
| `DELETE /admin/bases-centrales/{id}` | UUIDv7 | `base.id` | `UuidV7.parse` | solo archivadas; FK CASCADE limpia insumos |
| `POST /admin/bases-centrales/{id}/insumos` | UUIDv7 | `base.id` | `UuidV7.parse` | body `InsumoCrearRequest` sin IDs |
| `PUT /admin/bases-centrales/{id}/insumos/{iid}` | UUIDv7×2 | `insumo.id` | `UuidV7.parse`×2 | owner/admin scope |
| `DELETE /admin/bases-centrales/{id}/insumos/{iid}` | UUIDv7×2 | `insumo.id` | `UuidV7.parse`×2 | owner/admin scope |
| `POST /admin/bases-centrales/{id}/insumos/import` | UUIDv7 | `base.id` | `UuidV7.parse` | multipart; sin IDs en body |
| `GET /bases-personales` | — | — | `listar(usuarioId)` | `BasePersonalResponse.id` = UUIDv7 |
| `POST /bases-personales` | — | — | `crear(usuarioId, …)` | body `BasePersonalCrearRequest` sin IDs |
| `DELETE /bases-personales/{id}` | UUIDv7 | `base.id` | `UuidV7.parse` | owner-to-404 (Plan 05) |

DTOs migrados: `InsumoResponse.id`, `InsumoBusquedaResponse.id`,
`InsumoUsoResponse.apuId`, `BaseInsumosResponse.id`,
`AdminBaseCentralResponse.id`, `BasePersonalResponse.id`, `CopiarBaseRequest`
(`UUID baseId`, `UUID proyectoId`).

### Recurso: APU (boundary en `PresupuestoApuResource`)

| Path / endpoint | ID externo | Id interno | Seam | Reglas |
|---|---|---|---|---|
| `GET /presupuestos/{presupuestoId}/apus` | UUIDv7 (path) | `presupuesto.id` | `resolverPresupuestoInterno` | `presupuestoRepository.findByPublicIdAndOwnerScope` → 404 si ajeno |
| `POST /presupuestos/{presupuestoId}/apus` | UUIDv7 (path) + `UUID plantillaId?` (body) | `presupuesto.id`, `plantilla.id` | `UuidV7.parse` (path) + `normalizarPlantillaId` (body) | `plantillaId` se valida como v7 antes de delegar al service; respuesta 201 sin advertencias / 200 con `advertencias[]` |

`ApuCrearRequest.plantillaId` = `UUID` (introducido en Plan 04 y validado explícitamente como UUIDv7 en esta frontera por Plan 07). Paths APU y detalle (`/apus/{apuId}/...`,
`/apus/{apuId}/detalles/{detalleId}`) **alineado previo**: ya usaban UUIDv7
vía `UuidV7.parse` y `findByPublicIdAndOwnerScope`.

### Recurso: plantillas de proyecto (seam `guardar-plantilla`)

| Path / endpoint | ID externo | Id interno | Seam | Reglas |
|---|---|---|---|---|
| `GET /plantillas-proyecto` | — | — | `listar(usuarioId)` | `PlantillaProyectoResponse.id` = UUIDv7; owner-scoped |
| `GET /plantillas-proyecto/{id}` | UUIDv7 | `plantilla.id` | `UuidV7.parse` | owner-to-404 |
| `DELETE /plantillas-proyecto/{id}` | UUIDv7 | `plantilla.id` | `UuidV7.parse` | owner-to-404; FK `ON DELETE SET NULL` |
| `POST /proyectos/{proyectoId}/guardar-plantilla` | UUIDv7 (path) | `proyecto.id` | `UuidV7.parse` | backend-authored snapshot price-free; respuesta 201 con `PlantillaProyectoResponse.id` UUIDv7 |
| `POST /proyectos/desde-plantilla/{plantillaId}` | UUIDv7 (path) | `plantilla.id` | `UuidV7.parse` | 201 sin advertencias / 200 con `advertencias[]`; devuelve `ProyectoDesdePlantillaResponse` con `proyecto.id` UUIDv7 |

`/plantillas-apu[/...]` (P-26): **alineado previo** — ya usaban UUIDv7 desde
Plan 04; `PlantillaApuResumenResponse.id`, `PlantillaApuDetalleResponse.id` =
UUIDv7.

### Recurso: documento ET

| Path / endpoint | ID externo | Id interno | Seam | Reglas |
|---|---|---|---|---|
| `GET /documentos/especificaciones-tecnicas/{presupuestoId}?formato=docx&titulo1=&titulo2=` | UUIDv7 (path) | `presupuesto.id` | `UuidV7.parse` → `presupuestoRepository.findByPublicIdAndOwnerScope` → `proyectoService.validarPropietario` | 404 si ajeno; `Content-Disposition` con nombre sin BIGINT |

`DocumentoResource` (`documento/DocumentoResource.java`) migrado en este plan
para usar `UuidV7.parse` + owner-to-404 sobre el `presupuestoId` y devolver
attachment con cabecera consistente.

## Seams ya alineados (no modificados por Plan 07)

- APU (`apu/resource/ApuResource.java`):
  `GET/PATCH/PATCH %CI / PATCH %descuento / PUT ET / GET ET / DELETE / POST
  detalles / PATCH detalle / DELETE detalle / POST duplicar / GET calculo` —
  todos con `{apuId}` y `{detalleId}` UUIDv7 vía `UuidV7.parse` y
  `ApuRepository.findByPublicIdAndOwnerScope` /
  `ApuDetalleRepository.findByPublicIdAndOwnerScope` (verificado antes de
  este plan en I-06 / Plan 013).
- Plantillas APU (`plantilla/resource/PlantillaApuResource.java`,
  `PlantillaApuGuardarResource.java`): UUIDv7 en `{id}` y `{apuId}` vía
  `UuidV7.parse` (Plan 04 / P-26, DONE 2026-08-29).
- APU/detalle ya usaba UUIDv7 para sus IDs propios; Plan 07 migró la referencia anidada `insumoId` de entrada/salida a UUIDv7.

## DTOs migrados por Plan 07

Identidad externa siempre `UUID` y nombre JSON `id` (sin `publicId`/`public_id`
ni `Long` internos):

- `proyecto/dto/ProyectoResponse(UUID id, …)`
- `proyecto/dto/FirmanteResponse(UUID id, …)`
- `proyecto/dto/ParametrosProyectoResponse(UUID proyectoId, …)` — antes `Long`
- `insumo/dto/InsumoResponse(UUID id, …)`
- `insumo/dto/InsumoBusquedaResponse(UUID id, …, UUID fuente-equivalente, …)`
- `insumo/dto/InsumoUsoResponse(UUID apuId, …)`
- `insumo/dto/BaseInsumosResponse(UUID id, …)`
- `insumo/dto/AdminBaseCentralResponse(UUID id, …)`
- `insumo/dto/BasePersonalResponse(UUID id, …)`
- `insumo/dto/CopiarBaseRequest(String fuenteTipo, UUID baseId, UUID proyectoId)`
- `apu/dto/ApuCrearRequest(…, UUID plantillaId)` — `plantillaId` validado en
  frontera como UUIDv7 (`normalizarPlantillaId`)
- `apu/resource/PresupuestoApuResource` — `UuidV7.parse(presupuestoIdStr)` +
  `presupuestoRepository.findByPublicIdAndOwnerScope`
- `documento/DocumentoResource` — `UuidV7.parse(presupuestoIdStr)` +
  `presupuestoRepository.findByPublicIdAndOwnerScope`

## Cambios no realizados (a propósito)

- **Sin migraciones nuevas.** No se creó V008/V009 ni se editó V001/V007. La
  columna `public_id` (UUID con `DEFAULT uuidv7()`) ya existe en V001 para
  `usuario`, `proyecto`, `firmante`, `presupuesto`, `apu`, `apu_detalle`,
  `base_insumos`, `insumo`, `plantilla_apu`, `plantilla_proyecto`. La
  migración V005–V007 (filas pendientes con override 0; `cantidad >= 0` para
  plantillas de proyecto) no se reabre.
- **Sin cambios de PK/FK.** Las PK siguen siendo `BIGINT GENERATED ALWAYS AS
  IDENTITY` (V001 §1) y las FK son `BIGINT` (V001 §2). `public_id` es solo
  una columna adicional con trigger de inmutabilidad.
- **Sin tocar el motor.** `motor/` permanece con `BigDecimal` natural; no se
  modificaron firmas, fórmulas ni tolerancias.
- **Sin tocar módulos profundos.** `recalculo` sigue diferido.

## Criterios de terminado

- [x] La matriz de endpoints no contiene IDs públicos BIGINT.
- [x] Los responses usan `id` con UUIDv7 (ver matriz arriba).
- [x] UUID inválido/no-v7 produce 400 `validacion` (`UuidV7.parse`).
- [x] Recurso ajeno produce 404 `no-encontrado`
      (`findByPublicIdAndOwnerScope` + `validarPropietario`).
- [x] PK, FK y joins internos siguen usando BIGINT.
- [x] Las suites específicas disponibles de todos los módulos migrados pasan; `presupuesto.*` no contiene tests propios todavía.

## Verificación ejecutada (2026-08-30)

Pruebas dirigidas reales (tests / fallos / errores / omitidos):

| Patrón | Resultado |
|---|---:|
| `ec.uce.propuestas.proyecto.*` | 17 / 0 / 0 / 0 |
| `ec.uce.propuestas.insumo.*` | 57 / 0 / 0 / 0 |
| `ec.uce.propuestas.presupuesto.*` | sin tests encontrados |
| `ec.uce.propuestas.apu.*` | 41 / 0 / 0 / 0 |
| `ec.uce.propuestas.plantilla.*` | 75 / 0 / 0 / 0 |
| `ec.uce.propuestas.documento.*` | 7 / 0 / 0 / 0 |
| `ec.uce.propuestas.identifier.*` | 36 / 0 / 0 / 0 |

Total ejecutado con tests disponibles: **233/233 verdes**. La suite completa no se ejecutó.

Comprobaciones finales:

- `git diff --check`: **verde**.
- búsquedas acotadas: ningún `@PathParam` público `Long`/`UUID`, ningún ID público `Long` en los DTOs del alcance y ninguna propiedad JSON `publicId/public_id`.
- migraciones: V007 sigue siendo la última; PK/FK permanecen `BIGINT`, cubierto además por `PublicIdPersistenceTest`.
- `graphify update .`: **verde** (2 232 nodos, 6 579 aristas, 104 comunidades).
- `./gradlew spotlessCheck`: **rojo por 23 archivos preexistentes**, ninguno intersecta los Java modificados por Plan 07.
- `./gradlew build -x test`: **rojo únicamente por el gate global `spotlessJavaCheck`**; compilación y empaquetado independientes verdes con `-x spotlessCheck -x spotlessJavaCheck`.

La deuda Spotless no pertenece a Plan 07 y se conserva para el cierre transversal del Plan 08.

## Pasos ejecutados (resumen)

1. `common/UuidV7.parse` añadido como parser canónico de UUIDv7 (regex v7 +
   `UUID.version() == 7` + variant `2`).
2. `proyecto/resource/ProyectoResource` — `UuidV7.parse(proyectoId)` +
   `proyectoService.validarPropietario`.
3. `proyecto/resource/FirmanteResource` — `UuidV7.parse(proyectoId)` y
   `UuidV7.parse(firmanteId)`; service resuelve por owner.
4. `proyecto/resource/ParametrosProyectoResource` — `UuidV7.parse(proyectoId)`
   en GET/PUT; `ParametrosProyectoResponse.proyectoId` migrado a `UUID`.
5. `insumo/resource/InsumoResource` — `UuidV7.parse(proyectoId)` y
   `UuidV7.parse(insumoId)` en PUT/DELETE/selector/usos/copiar;
   `InsumoUsoResponse.apuId` migrado a `UUID`.
6. `insumo/resource/BasesPersonalesResource` — `UuidV7.parse(id)` en DELETE.
7. `insumo/resource/AdminBaseCentralResource` — `UuidV7.parse(id)` e
   `UuidV7.parse(insumoId)` en todos los sub-endpoints;
   `AdminBaseCentralResponse.id` y `BaseInsumosResponse.id` ya eran `UUID`.
8. `apu/resource/PresupuestoApuResource` — `UuidV7.parse(presupuestoId)` y
   validación de `ApuCrearRequest.plantillaId` como UUIDv7 en la frontera
   (`normalizarPlantillaId`).
9. `documento/DocumentoResource` — `UuidV7.parse(presupuestoId)` +
   owner-to-404 sobre presupuesto y proyecto.
10. `plantilla/resource/PlantillaProyectoGuardarResource` y
    `PlantillaProyectoAplicarResource` — `UuidV7.parse` en el path; seam
    `POST /proyectos/{proyectoId}/guardar-plantilla` migrada.

## Condiciones de parada

Mantener las del plan original. No se disparó ninguna en este pase
(`public_id` ya existía desde V001; ningún módulo nuevo creado; ningún ID
interno se expuso).

## Siguiente plan ejecutable

[`08-cierre-documental-y-verificacion.md`](./08-cierre-documental-y-verificacion.md)
— Cierre documental, Bruno y verificación transversal. Depende de 02–07; con Plan 07 DONE, el siguiente paso es actualizar Bruno, atender la deuda global de formato y ejecutar la suite completa dentro del Plan 08.