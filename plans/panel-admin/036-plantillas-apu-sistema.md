# 036 — Plantillas APU de sistema (P-40)

**Estado:** TODO · I-11 · P-40 / US-37 / TC-P40-01.

> Reutiliza estrictamente `SnapshotApuMapper` (price-free) ya
> implementado por Plan 04 (DONE 2026-08-29). El snapshot lo
> construye el **backend** a partir de un APU existente
> (`desdeApuId` UUIDv7); nunca acepta JSONB del cliente. El
> servidor fija `tipo=SISTEMA` y `usuario_id=NULL`; el cliente
> no puede sobreescribirlos. Emite D-13 `admin.plantilla_editada`
> (solo operaciones **exitosas**) a través de 033.
>
> **Longitud de `descripcionRubro`:** la auditoría de 036 debe
> confirmar la longitud real de `plantilla_apu.descripcion_rubro`
> contra la columna de BD antes de fijar la validación Bean. **No
> se inventa tope arbitrario**; si la columna es `TEXT`, no se
> aplica tope en la capa de servicio más allá de la validación
> nativa. Si la columna es `VARCHAR(N)`, el tope Bean es N con
> un margen de tolerancia razonable.

## Proceso / historia / criterios

- **Proceso:** P-40.
- **Historia:** US-37.
- **Iteración:** I-11.
- **Criterio de aceptación (quality/02):**
  - **TC-P40-01:** admin crea plantilla SISTEMA desde un APU
    → visible para todos los usuarios con `tipo=SISTEMA`;
    snapshot igual a P-26.

## Objetivo medible

Una ejecución futura debe demostrar que:

1. `GET /admin/plantillas-apu` lista plantillas con
   `tipo=SISTEMA` (excluye PERSONAL por defecto); filtro `q`
   opcional; paginación canónica `Page<PlantillaApuResponse>`
   (`items, total, page, size, totalPaginas`);
2. `POST /admin/plantillas-apu` con
   `PlantillaSistemaCrearRequest{desdeApuId: <UUIDv7>, nombre:
   <text>, descripcionRubro?: <text>}`:
   - resuelve el APU por `desdeApuId` (UUIDv7 validado en
     frontera);
   - construye el snapshot reusando `SnapshotApuMapper` (writer
     price-free ya implementado);
   - persiste con `tipo=SISTEMA` y `usuario_id=NULL` fijados
     por el servidor (ignora cualquier valor del cliente;
     `@CHECK` V001 ya valida `SISTEMA ⇒ usuario_id NULL`);
   - 201 + `PlantillaApuResponse` con UUIDv7 nuevo;
3. `PUT /admin/plantillas-apu/{id}` con
   `PlantillaApuEditarRequest{nombre, descripcionRubro?}`
   permite renombrar y actualizar descripción; **no** permite
   cambiar `tipo`, `usuario_id`, `snapshot_secciones`,
   `desdeApuId` (campos no presentes en el DTO);
4. `DELETE /admin/plantillas-apu/{id}` → 204; las APU existentes
   basadas en la plantilla **no** se invalidan (el snapshot fue
   copiado al crear cada APU; APU ≠ plantilla);
5. la emisión D-13 `admin.plantilla_editada` se dispara, solo en
   operaciones **exitosas**, en `POST`, `PUT`, `DELETE` con la
   clave de operación correcta (decisión 41 abajo);
6. TC-P40-01: tras `POST`, un usuario regular autenticado ve
   la plantilla en su `GET /plantillas-apu` (mezclada con sus
   PERSONALES); el campo `tipo` viene `SISTEMA` y `usuarioId`
   es `null`.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada; decisiones 1–20 vigentes. | Habilita código. |
| G1 — 033 cerrado | `LogActividadService.emitir` operativo. | Habilita emisión D-13. |
| G2 — Plan 04 DONE | `SnapshotApuMapper` price-free (writer + reader tolerante V004); `PlantillaApuService` con `listarPorTipo(SISTEMA)`. | Habilita snapshot backend-authored. |
| G3 — `@CHECK` V001 | `CHECK ((tipo=SISTEMA AND usuario_id IS NULL) OR (tipo=PERSONAL AND usuario_id IS NOT NULL))`. | Habilita la invariante SISTEMA-sin-dueño. |
| G4 — UUIDv7 | `UuidV7.parse` en frontera; `PlantillaApu.publicId` ya existe (V001 §2.12 + V008). | Habilita `id` UUIDv7. |
| G5 — sin CAMICON / sin secretos | `plantilla_apu.snapshot_secciones` price-free; ningún campo PII; ningún token. | Habilita TC-P42-02 (sin PII). |
| G6 — cierre | Focales verdes; regresión `plantilla.*` verde; emisión D-13 verde. | Evidencia medible. |

`STOP-036-CROSS-OWNER` se activa si la auditoría detecta que 032
no cierra `admin.cross_owner_behavior` para 036 y el flujo lo
exige. Reabrir 032.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  (decisiones 1, 9, 13, 19).
- `plans/panel-admin/033-log-actividad-base.md` (API de emisión).
- [`docs/modulos/planes-para-estar-al-dia/04-plantillas-apu.md`](../../docs/modulos/planes-para-estar-al-dia/04-plantillas-apu.md)
  (Plan 04 DONE 2026-08-29; `SnapshotApuMapper`; `PlantillaApuService`,
  DTOs `PlantillaApuCrearRequest`, `PlantillaApuEditarRequest`,
  `PlantillaApuResumenResponse`, `PlantillaApuDetalleResponse`).
- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §H/P-40
  y §J/D-13 (D-13 incluye `admin.plantilla_editada`).
- `../../../thesis-docs/plan/architecture/07-api-contract.md` §9
  filas P-40 y §1 (errores, paginación
  `items,total,page,size,totalPaginas`).
- `../../../thesis-docs/plan/architecture/06-database-schema.md` §2.12
  (`plantilla_apu`).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P40-01.
- `src/main/java/ec/uce/propuestas/plantilla/service/PlantillaApuService.java`
  (ya implementa `listarPorTipo`).
- `src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java`
  (writer price-free + reader tolerante V004).
- `src/main/java/ec/uce/propuestas/plantilla/entity/PlantillaApu.java`
  (campos `tipo`, `usuario_id`, `snapshot_secciones`,
  `descripcion_rubro`, `publicId`).
- `src/main/java/ec/uce/propuestas/plantilla/repository/PlantillaApuRepository.java`.
- `src/main/java/ec/uce/propuestas/apu/repository/ApuRepository.java`
  (búsqueda por `publicId` UUIDv7).

## Estado inicial esperado

- `PlantillaApuService` implementa `listarPorTipo(SISTEMA)` y
  CRUD para plantillas PERSONALES.
- `SnapshotApuMapper` ya tiene `toJson(Apu)` (writer price-free)
  y `fromJson(JsonNode)` (reader tolerante V004).
- `PlantillaApuServiceTest` 16/16 + `PlantillaApuResourceIT` 12/12
  verdes (Plan 04).
- Sin servicio admin (`/admin/plantillas-apu`).
- Sin emisión D-13 para P-40 todavía.

## Decisiones locked adicionales (036)

Se suman a las anteriores; no las contradicen:

40. **Emisión D-13 (036) — solo operaciones exitosas:** consume
    verbatim la matriz canónica publicada por el acta 032 (decisión 2);
    036 **no** redefine, **no** agrega ni **no** amplía claves.
    `admin.plantilla_editada` con
    `detalle = { "operacion": "crear|editar|borrar", "tipo":
    "SISTEMA" }`; el UUIDv7 de la `PlantillaApu` vive en
    `entidadId` top-level (no en `detalle`). La clave `operacion`
    es una de 3 (`crear|editar|borrar`); el listado y la lectura
    individual no emiten. El `nombre` de la plantilla es texto
    libre capturado del formulario admin y **no** entra al detalle
    canónico (vive en `PlantillaApuResponse.nombre` para el admin;
    no es PII en el sentido RNF-08 pero la regla "no free-text en
    detalle" aplica por igual). Las operaciones rechazadas
    (400/404) no emiten. El emisor se invoca dentro de la
    `@Transactional` exterior del método admin (`MANDATORY`); un
    rollback del flujo borra la fila del log.
41. **Cross-owner para `desdeApuId`:** 036 **no** cruza owner
    para leer el APU origen. La regla canónica de 032 decisión 1
    (UUIDv7 sin cross-owner) **se aplica**: el admin crea la
    plantilla desde un APU que pertenezca a un proyecto que él
    puede ver (admin: todos los proyectos; RNF-05 permite
    lectura admin para soporte). El APU origen puede ser de
    cualquier owner; la restricción la aplica el filtro de
    `ApuRepository.findByPublicId(UUID)` sin filtrar por owner
    cuando el caller es SUPER_ADMIN (regla pre-existente en
    `ApuResource` ya validada por tests). Documentar este
    comportamiento en
    `docs/modulos/panel-admin/036-nota-cross-owner.md`.
42. **Snapshot backend-authored:** el writer de
    `SnapshotApuMapper` ya excluye precios efectivos, IDs y
    links al APU origen; el reader tolera campos V004 legacy.
    036 **no** modifica el mapper. El test de TC-P40-01 asserta
    que el JSONB guardado en `snapshot_secciones` **no**
    contiene `precioUnitario`, `precioOverride`, `tarifaJornal`
    ni `costoTotal` (verificación con regex sobre el campo
    crudo).
43. **`descripcionRubro` opcional** (decisión 9 de 032): la
    validación Bean (si la hay) usa la longitud real de la
    columna `plantilla_apu.descripcion_rubro` confirmada contra
    la BD; **no** se inventa tope arbitrario. Si la columna es
    `TEXT`, no se aplica tope en la capa de servicio más allá
    de la validación nativa.

## Alcance

### Incluye

- Recurso JAX-RS `PlantillaApuAdminResource` con
  `@Path("/admin/plantillas-apu")` y
  `@RolesAllowed("SUPER_ADMIN")`.
- DTO `PlantillaSistemaCrearRequest{desdeApuId, nombre,
  descripcionRubro?}`.
- DTO `PlantillaApuAdminResponse` que reusa el mismo cuerpo que
  `PlantillaApuResumenResponse` (mismas claves: `id`, `nombre`,
  `tipo`, `usuarioId`, `descripcionRubro`, `fechaCreacion`) pero
  con `usuarioId = null` siempre.
- Servicio `PlantillaApuAdminService` que coordina
  `ApuRepository.findByPublicId`, `SnapshotApuMapper.toJson`,
  `PlantillaApuRepository.persist`, y emite D-13.
- Emisión D-13 `admin.plantilla_editada` (solo operaciones
  exitosas) desde los 3 endpoints mutantes.
- Tests `@QuarkusTest`:
  - `PlantillaApuAdminResourceIT` (TC-P40-01 + 4 escenarios: crear,
    editar, borrar, listado).
  - `PlantillaApuSinJsonbClienteTest`: 400 si el cliente envía
    `snapshot_secciones` en el body (no existe en el DTO; pero
    se testea el comportamiento del reader).
  - `PlantillaApuSinPreciosTest`: regex sobre el campo crudo
    `snapshot_secciones` para TC-P40-01.

### No incluye

- Modificar `SnapshotApuMapper` (reusado tal cual).
- Modificar `PlantillaApuService` (reusado para listar
  PERSONALES; 036 solo agrega un recurso admin que lista
  SISTEMA).
- Crear un endpoint para "duplicar plantilla" (fuera del canon).
- Crear plantillas PERSONALES por admin (las PERSONALES son del
  usuario que las crea, canónico P-26).
- Re-seed de `V004` (la plantilla SISTEMA sembrada sigue; 036
  solo agrega el CRUD admin).
- Cambiar `descripcion_rubro` de longitud o tipo (la auditoría
  confirma contra la columna real).
- Tope arbitrario de caracteres en `descripcionRubro`.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/plantilla/admin/PlantillaApuAdminResource.java` | `@Path("/admin/plantillas-apu")` + `@RolesAllowed("SUPER_ADMIN")`. |
| Crear | `src/main/java/ec/uce/propuestas/plantilla/admin/PlantillaApuAdminService.java` | Orquesta `ApuRepository`, `SnapshotApuMapper`, `PlantillaApuRepository`, `LogActividadService`. |
| Crear | `src/main/java/ec/uce/propuestas/plantilla/admin/dto/PlantillaSistemaCrearRequest.java` | Record canónico. |
| Crear | `src/main/java/ec/uce/propuestas/plantilla/admin/dto/PlantillaApuAdminResponse.java` | Record canónico (reuso del shape de `PlantillaApuResumenResponse`). |
| Crear | `src/test/java/ec/uce/propuestas/plantilla/admin/PlantillaApuAdminResourceIT.java` | TC-P40-01 + 4 escenarios. |
| Crear | `src/test/java/ec/uce/propuestas/plantilla/admin/PlantillaApuSinJsonbClienteTest.java` | Reader no acepta JSONB del cliente. |
| Crear | `src/test/java/ec/uce/propuestas/plantilla/admin/PlantillaApuSinPreciosTest.java` | Regex sobre `snapshot_secciones` crudo. |
| Modificar | `src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java` | Cero cambios. |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marca 036 `DONE`. |
| Modificar (opcional) | `api/bruno/12-admin/TC-12-P40-01.bru` | En 040. |
| No previsto | `src/main/java/ec/uce/propuestas/plantilla/service/PlantillaApuService.java` | STOP — reusado. |
| No previsto | `src/main/java/ec/uce/propuestas/plantilla/resource/PlantillaApuResource.java` (no-admin) | STOP. |
| No previsto | `src/main/resources/db/migration/**` | STOP. |

## Contrato REST

```
GET    /api/v1/admin/plantillas-apu?q=&page=&size=
POST   /api/v1/admin/plantillas-apu
PUT    /api/v1/admin/plantillas-apu/{id}
DELETE /api/v1/admin/plantillas-apu/{id}
```

Todos requieren rol `SUPER_ADMIN` (USUARIO → 403).

### Errores

| Código | Tipo | Cuándo |
|---|---|---|
| 400 `validacion` | `uuid-invalido` | `desdeApuId` o `{id}` no es UUIDv7. |
| 400 `validacion` | `apu-origen-no-encontrado` | `desdeApuId` no resuelve a un APU. |
| 400 `validacion` | `nombre-requerido` | `nombre` blank o > 200. |
| 400 `validacion` | `tamano-pagina-invalido` | `size > 200` o `size < 1`. |
| 400 `validacion` | `descripcion-rubro-excedida` | `descripcionRubro` excede la longitud real de la columna (verificada contra BD). |
| 404 `no-encontrado` | — | `{id}` UUIDv7 válido pero sin fila. |
| 403 `forbidden` | — | caller `USUARIO`. |

## Secuencia TDD (estricta)

### RED

1. `PlantillaApuAdminResourceIT`:
   - `POST` con `desdeApuId` válido y `nombre` → 201 +
     `PlantillaApuAdminResponse` con `tipo=SISTEMA` y
     `usuarioId=null`; 1 fila `log_actividad` con
     `evento=admin.plantilla_editada` y
     `detalle.operacion=crear`.
   - `GET /plantillas-apu` como USUARIO autenticado
     (helper) → ve la plantilla con `tipo=SISTEMA` y
     `usuarioId=null` (TC-P40-01).
   - `PUT` con nuevo `nombre` → 200; 1 fila `operacion=editar`.
   - `PUT` que intenta cambiar `tipo` (no existe en el DTO) →
     imposible por diseño; el test verifica que el JSON
     enviado con `tipo:"PERSONAL"` es ignorado (Jackson lo
     descarta o el DTO lo rechaza con 400).
   - `DELETE` → 204; 1 fila `operacion=borrar`.
   - `DELETE` con id que no existe → 404.
   - `GET /admin/plantillas-apu` → paginación canónica
     (`items,total,page,size,totalPaginas`).
2. `PlantillaApuSinJsonbClienteTest`:
   - El cliente no puede enviar `snapshot_secciones` en el
     body (no existe en el DTO). Test focal: un body con
     `snapshot_secciones: {...}` se rechaza con 400 o se
     ignora silenciosamente (preferible: rechazado).
3. `PlantillaApuSinPreciosTest`:
   - Tras `POST`, leer el row de `plantilla_apu` y assertar
     que el JSONB `snapshot_secciones` no contiene ninguno de
     los patrones `precioUnitario|precioOverride|tarifaJornal
     |costoTotal` (regex sobre el `String` crudo).

### GREEN

Inyectar `LogActividadService`, `SnapshotApuMapper`,
`ApuRepository`, `PlantillaApuRepository`. Construir DTOs,
servicio, recurso.

### TRIANGULATE

- `POST` con `desdeApuId` que es de un APU de otro owner (admin
  sí ve; regla cross-owner decisión 41): debe funcionar (TC-P40-01
  admin-agnóstico).
- `POST` con `desdeApuId` UUIDv4 → 400.
- `POST` con `desdeApuId` UUIDv7 inexistente → 400
  `apu-origen-no-encontrado`.
- `POST` con `descripcionRubro` excediendo la longitud real de
  columna → 400.
- `PUT` con `{id}` UUIDv4 → 400.

### REFACTOR

- Centralizar la serialización del snapshot en un helper
  `SnapshotApuMapper.serializarApu(Apu)` si 036 lo requiere
  en más de un lugar (probablemente no).

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| TC-P40-01 crear SISTEMA desde APU | 201; visible para todos; `tipo=SISTEMA`, `usuarioId=null`. |
| TC-P40-01 USUARIO ve la plantilla | `GET /plantillas-apu` la lista con `tipo=SISTEMA`. |
| Editar nombre | 200; fila log `operacion=editar`. |
| Borrar | 204; fila log `operacion=borrar`. |
| Sin JSONB del cliente | 400 o rechazo silencioso verificable. |
| Snapshot sin precios | 0 matches regex. |
| `desdeApuId` UUIDv4 | 400. |
| `desdeApuId` UUIDv7 inexistente | 400 `apu-origen-no-encontrado`. |
| USUARIO 403 | cualquier endpoint admin responde 403. |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Focales.
./gradlew test --tests 'ec.uce.propuestas.plantilla.admin.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Regresión plantilla: corre `--tests 'ec.uce.propuestas.plantilla.*'` y comparar contra el XML agregado del comando; no predecir conteos.
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Sin migración nueva.
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío.

# SnapshotApuMapper intacto.
git diff --name-only -- 'src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java'
# esperado: vacío.

# Motor y recalculo intactos.
git diff --name-only -- 'src/main/java/ec/uce/propuestas/motor/**' \
  -- 'src/main/java/ec/uce/propuestas/recalculo/**'
# esperado: vacío.

# Forma canónica Page<T>.
grep -RInE 'items,|total,|page,|size,|totalPaginas' \
  src/main/java/ec/uce/propuestas/plantilla/admin/
```

No se predicen conteos de suite completa. El orquestador decide.

## Completion checklist (036)

- [ ] `SnapshotApuMapper` intacto (reuso estricto).
- [ ] `GET/POST/PUT/DELETE /admin/plantillas-apu` con
      `@RolesAllowed("SUPER_ADMIN")`.
- [ ] `POST` fija `tipo=SISTEMA` y `usuario_id=NULL`
      server-side; cliente no puede sobreescribirlos.
- [ ] Snapshot sin precios efectivos, IDs ni links al APU origen
      (TC-P40-01 + regex).
- [ ] USUARIO ve la SISTEMA en `GET /plantillas-apu` con
      `tipo=SISTEMA`.
- [ ] Emisión `admin.plantilla_editada` (solo operaciones
      exitosas) en POST/PUT/DELETE con `operacion` correcta.
- [ ] Longitud de `descripcionRubro` confirmada contra la columna
      real; sin tope arbitrario inventado.
- [ ] Ninguna migración nueva; motor intacto.
- [ ] `git diff --check` limpio.

## Handoff al siguiente plan

Cuando 036 cierre, el orquestador puede iniciar **037** (P-41
parámetros + valores de referencia) o cualquier otro de 035–037.
037 conserva `/proyectos/parametros-sistema` canónico y agrega
`/admin/valores-referencia`.