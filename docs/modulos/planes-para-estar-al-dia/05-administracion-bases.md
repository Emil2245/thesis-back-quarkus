# Plan 05 — Completar administración de bases

## Resultado esperado

Los usuarios pueden eliminar sus bases PERSONALES con aislamiento por propietario, y SUPER_ADMIN puede administrar el ciclo completo de bases CENTRALES, incluido archivar y borrar sin afectar las copias de proyecto.

## Dependencia

Completar primero el [Plan 01](./01-linea-base-y-decisiones.md). Puede ejecutarse en paralelo conceptual con los planes 02–04, pero los cambios deben aplicarse en una sola línea de trabajo.

## Estado de cierre

**DONE (2026-08-29).** Implementación y regresión dirigida verificadas: `45/45`
tests de `ec.uce.propuestas.insumo.*` verdes con puerto de test dinámico, build
sin tests verde al excluir únicamente el gate Spotless global, y
`git diff --check` limpio. La suite completa no se ejecutó por instrucción del
autor. `spotlessCheck` global sigue rojo por archivos ajenos preexistentes de
APU/plantillas; todos los archivos Java modificados por este plan fueron
formateados aisladamente con el hook IDE de Spotless y ninguno aparece en el
reporte de violaciones.

**Referencia I-11:** Plan 035 agrega paginación canónica, protección defensiva `insumo-en-uso` y emisión D-13 `admin.base_editada`; ver [`035-auditoria-p39.md`](../panel-admin/035-auditoria-p39.md). Quedó **DONE** con evidencia final medida: focal fresca **41/41** (`AdminBaseCentralResourceIT` 29 + `AdminBaseCentralLogAuditoriaIT` 12), regresión dirigida `insumo.*` fresca **74/74**, `build -x test` y Spotless **PASS**, suite completa fresca **715 = 712 pass + GM-19/GM-20 aceptados + GM-24 skipped, 0 errors**, y `git diff --check` limpio. El primer `spotlessCheck` señaló únicamente cuatro archivos Java de Plan 035; `spotlessApply` los normalizó y el check posterior pasó dentro del build. Sin cambios en migraciones, `motor/` ni `recalculo/`; `graphify update .` finalizado con 4.453 nodos, 13.555 aristas y 182 comunidades.

### Implementación aplicada

1. **Borrado PERSONAL con owner-to-404** — `BasesPersonalesService.borrar(publicId, usuarioId)` reutiliza la seam existente `buscarPorPublicId(...)` (filtrada a `tipo = PERSONAL && usuarioId = caller`) y borra físicamente la fila. El recurso `BasesPersonalesResource.borrar(@PathParam("id") UUID)` devuelve 204 si la base es del caller; cualquier otro caso (base ajena, CENTRAL o PROYECTO) devuelve 404 (nunca 403, RNF-05). Los `insumo` asociados se eliminan por la FK CASCADE `insumo.base_id → base_insumos(id)` ya presente en V001. **No** se introduce ninguna migración: V001 ya soporta la operación (la FK CASCADE existía desde el baseline).
2. **Recurso administrativo `AdminBaseCentralResource`** — nueva clase en `ec.uce.propuestas.insumo.resource`, ruta canónica `/admin/bases-centrales`, `@RolesAllowed("SUPER_ADMIN")` a nivel de clase. Endpoints implementados (todos alineados con 07-api-contract.md §9, P-39):

   | Verbo | Path | Notas |
   |---|---|---|
   | `GET`    | `/admin/bases-centrales?incluirArchivadas=` | default `false`; admin las ve todas cuando `true` |
   | `POST`   | `/admin/bases-centrales` | crear (201 + UUIDv7) |
   | `PUT`    | `/admin/bases-centrales/{id}` | renombrar |
   | `POST`   | `/admin/bases-centrales/{id}/archivar` | **POST canónico D-12** (no PUT) |
   | `DELETE` | `/admin/bases-centrales/{id}` | solo tras archivar; 409 si no |
   | `POST`   | `/admin/bases-centrales/{id}/insumos` | CRUD de insumo |
   | `PUT`    | `/admin/bases-centrales/{id}/insumos/{iid}` | CRUD de insumo |
   | `DELETE` | `/admin/bases-centrales/{id}/insumos/{iid}` | CRUD de insumo |
   | `POST`   | `/admin/bases-centrales/{id}/insumos/import?soloValidar=` | importación CSV canónica (no `/importar`) |

   El CRUD/importación reusa `InsumoCrudService`, `ImportacionInsumoService`, `CsvInsumoParser`, los DTOs existentes (`InsumoCrearRequest`, `InsumoEditarRequest`, `ImportResultadoResponse`, `InsumoImportForm`) y el `InsumoRepository`. **No** se duplica parser, no se duplica mapper, no se duplica DTO.

3. **DTO admin UUIDv7** — `AdminBaseCentralResponse(UUID id, String nombre, String tipo, boolean archivada, long totalInsumos)` se añade para exponer la identidad externa inmutable (mismo patrón WU-03 que `BasePersonalResponse`). **No** se toca `BaseInsumosResponse` (DTO público de la ruta no-admin, archivo no relacionado con este plan).

4. **Visibilidad diferenciada** — la nueva ruta admin (`AdminBaseCentralResource.listar`) coexiste con la ruta pública `BaseInsumosResource.listar` ya existente; la primera sigue excluyendo archivadas por defecto (alineada con `/bases-centrales`), con `incluirArchivadas=true` las expone. Archivar fija `archivada = true` sin tocar las filas `insumo`; el listado de `/bases-centrales` las oculta automáticamente (V001 + filtro `archivada = false` existente en `listarCentralesActivas`).

5. **Borrado solo después de archivar** — `BaseInsumosService.eliminarCentralArchivada(publicId)` exige `archivada = true`; si la base sigue activa devuelve 409 con código `base-no-archivada` vía `ProblemaException.conflicto(...)` (helper nuevo en `common/ProblemaException`, 409 status, código parametrizable para futuros conflictos de estado).

6. **Preservación de copias PROYECTO** — el borrado físico de la CENTRAL elimina solo `base_insumos` (su fila) y, por la FK CASCADE de V001, los `insumo` con `base_id = base.id`. Las filas `apu_detalle.insumo_id` NUNCA referencian CENTRAL/PERSONAL: el resolver `ResolverInsumoProyectoService.materializarOReusar(...)` materializa una copia en la base PROYECTO del proyecto al usar el insumo (N04 §A9, ya implementado en planes anteriores). Por tanto la operación de borrado nunca es bloqueada por FK RESTRICT.

7. **Autorización** — `AdminBaseCentralResource` lleva `@RolesAllowed("SUPER_ADMIN")` a nivel de clase; no se introduce ningún rol nuevo, ningún middleware de roles, ningún módulo nuevo de primer nivel. La base de datos sigue garantizando `USUARIO`, `SUPER_ADMIN` (únicos dos roles por `usuario.rol`).

### No se aplica (perímetro)

- **No** se añade migración nueva. V001 ya contiene `base_insumos.usuario_id` con `ON DELETE CASCADE`, `tipo CHECK (CENTRAL|PERSONAL|PROYECTO)`, e `insumo.base_id ON DELETE CASCADE`. La operación D-12 funciona sin cambios de esquema.
- **No** se introduce un tercer rol ni un middleware roles.
- **No** se implementa recálculo de APUs tras cambiar precios centrales.
- **No** se reabre el parser CSV (sigue siendo `commons-csv`, reglas D-06 del módulo existente).
- **No** se modifica `BaseInsumosResponse` (DTO público no-admin) — preserva su forma actual para no romper la ruta `/bases-centrales` ni a los clientes.

### Verificación final

```text
./gradlew test --tests 'ec.uce.propuestas.insumo.*' \
  -Dquarkus.http.test-port=0 --console=plain
→ BUILD SUCCESSFUL; 45 tests, 45 passed, 0 skipped, 0 failures, 0 errors

./gradlew build -x test -x spotlessCheck --console=plain
→ BUILD SUCCESSFUL

git diff --check
→ sin salida
```

Conteo real desde `build/test-results/test/TEST-*.xml`:

- `AdminBaseCentralResourceIT`: 16/16.
- `BasesPersonalesResourceIT`: 8/8.
- `InsumoResourceIT`: 3/3.
- `BasesPersonalesServiceTest`: 9/9.
- `ResolverInsumoProyectoTest`: 9/9.

`./gradlew spotlessCheck` permanece rojo por 31 archivos ajenos preexistentes
(los primeros son `apu/dto/ApuCrearRequest.java`, `ApuResponse.java`,
`ApuResumenMapper.java`, `ApuResource.java` y `PresupuestoApuResource.java`).
No se tocaron para respetar el alcance. Ningún archivo Java de Plan 05 aparece
en ese reporte; se formatearon de forma dirigida con `spotlessIdeHook`.

### Cambios en este pase

| Archivo | Tipo |
|---|---|
| `src/main/java/ec/uce/propuestas/insumo/repository/BaseInsumosRepository.java` | +3 métodos admin (`listarCentralesAdmin`, `findCentralByPublicId`, `contarCentralPorNombre`) |
| `src/main/java/ec/uce/propuestas/insumo/service/BaseInsumosService.java` | +5 métodos admin (`listarCentralesAdminEntidades`, `crearCentral`, `renombrarCentral`, `archivarCentral`, `eliminarCentralArchivada`, `obtenerCentralPorPublicId`) |
| `src/main/java/ec/uce/propuestas/insumo/service/BasesPersonalesService.java` | +1 método `borrar(UUID, Long)` con owner-to-404 |
| `src/main/java/ec/uce/propuestas/insumo/resource/BasesPersonalesResource.java` | +endpoint `DELETE /{id}` |
| `src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java` | nuevo — ciclo de vida admin completo |
| `src/main/java/ec/uce/propuestas/insumo/dto/AdminBaseCentralResponse.java` | nuevo DTO UUIDv7 |
| `src/main/java/ec/uce/propuestas/insumo/dto/AdminBaseCentralCrearRequest.java` | nuevo DTO request (nombre) |
| `src/main/java/ec/uce/propuestas/insumo/dto/AdminBaseCentralEditarRequest.java` | nuevo DTO request rename (nombre) |
| `src/main/java/ec/uce/propuestas/common/ProblemaException.java` | +helper `conflicto(codigo, mensaje)` para 409 tipados |
| `src/test/java/ec/uce/propuestas/insumo/resource/BasesPersonalesResourceIT.java` | +5 tests de borrado PERSONAL |
| `src/test/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResourceIT.java` | nuevo — 16 tests admin |
| `docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md` | este archivo (estado de implementación) |
| `plans/README.md` | entrada del plan actualizada |

## Alcance (recordatorio)

### Incluye

- `DELETE /bases-personales/{id}`.
- Administración bajo `/admin/bases-centrales`.
- CRUD de bases e insumos centrales.
- Importación CSV central (ruta canónica `.../insumos/import`, no `/importar`).
- Archivar y borrar según D-12.
- Pruebas de roles, propiedad y preservación de copias PROYECTO.

### No incluye

- Nuevos roles.
- Borrar insumos ya materializados en bases PROYECTO.
- Recálculo de APUs tras cambiar precios.
- Cambiar el parser CSV salvo que una prueba del contrato vigente lo requiera.

## Contrato mínimo

```text
DELETE /bases-personales/{id}

GET    /admin/bases-centrales?incluirArchivadas=
POST   /admin/bases-centrales
PUT    /admin/bases-centrales/{id}
POST   /admin/bases-centrales/{id}/archivar   (POST canónico D-12, no PUT)
DELETE /admin/bases-centrales/{id}

# CRUD/importación de insumos bajo la base central correspondiente
POST   /admin/bases-centrales/{id}/insumos
PUT    /admin/bases-centrales/{id}/insumos/{iid}
DELETE /admin/bases-centrales/{id}/insumos/{iid}
POST   /admin/bases-centrales/{id}/insumos/import?soloValidar=
```

## Pasos

1. Auditar entities, repositories, services y resources de `insumo`, además del contrato REST canónico. ✓
2. Implementar borrado PERSONAL con owner-to-404 y una política explícita para contenido asociado. ✓
3. Crear o ampliar el resource administrativo con `@RolesAllowed("SUPER_ADMIN")`. ✓
4. Implementar listado, creación y renombrado de bases CENTRALES. ✓
5. Reutilizar servicios existentes para CRUD/importación de insumos centrales, evitando duplicar parser o reglas. ✓
6. Implementar archivado: archivada=true; ocultar del catálogo normal; conservarla para admin. ✓
7. Implementar borrado solo después de archivar. No bloquear por referencias históricas (copias PROYECTO). ✓
8. Verificar que solo existen los roles `USUARIO` y `SUPER_ADMIN`. ✓ (sin cambios de modelo de roles)
9. Ejecutar la suite dirigida y `git diff --check`. ✓ — 45/45 verdes; diff limpio.

## Pruebas y comprobaciones

(Ver evidencia real en la sección "Verificación final".)

## Criterios de terminado

- [x] Una base PERSONAL propia puede borrarse y una ajena responde 404.
- [x] USUARIO no accede a endpoints administrativos.
- [x] SUPER_ADMIN puede crear, renombrar, importar, archivar y borrar una central.
- [x] Una central archivada desaparece del catálogo normal.
- [x] Solo puede borrarse después de archivar.
- [x] Borrar una central no modifica copias PROYECTO existentes.

## Condiciones de parada

Detener y reportar si:

- el schema actual impide el borrado D-12 y exige una migración no contemplada; (no aplica: V001 ya soporta la operación)
- se descubre que algún proyecto referencia directamente insumos CENTRALES/PERSONALES; (no aplica: `ResolverInsumoProyectoService.materializarOReusar` garantiza copia-al-usar; APU siempre apunta a PROYECTO)
- el contrato propone un tercer rol o middleware roles; (no aplica: solo `USUARIO` y `SUPER_ADMIN`)
- completar el flujo exige implementar recálculo global. (no aplica: la copia al usar ya existe y los precios centrales son read-only para APUs)
