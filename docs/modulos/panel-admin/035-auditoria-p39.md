# Auditoría P-39 — cierre de bases centrales

## Resultado

Plan 035 cierra P-39 con paridad focal demostrada para los nueve endpoints del
contrato administrativo. La implementación previa de Plan 015bis se conservó y
se corrigieron únicamente tres brechas comprobadas por RED: forma paginada del
listado, validación de parámetros de paginación y protección del borrado de un
insumo referenciado. También se incorporó la emisión D-13
`admin.base_editada` para las ocho mutaciones exitosas.

## Evidencia TDD medida

### RED contra la línea base

La corrida focal inicial ejecutó **41 tests**: **18 failures**, **0 errors** y
**0 skips**. Los fallos se dividieron en:

- **9 fallos de `AdminBaseCentralLogAuditoriaIT`**: la línea base no emitía
  `admin.base_editada`.
- **9 fallos de `AdminBaseCentralResourceIT`**: forma antigua `List`, ausencia
  de paginación y validación de parámetros, y borrado de insumo referenciado
  sin el rechazo canónico.

### GREEN fresco

```bash
./gradlew test --tests 'ec.uce.propuestas.insumo.resource.AdminBaseCentral*IT' \
  -Dquarkus.http.test-port=0 --console=plain --rerun-tasks
```

Resultado: **41/41 pass**, **0 failures**, **0 errors**, **0 skips**.

- `AdminBaseCentralResourceIT`: **29/29**.
- `AdminBaseCentralLogAuditoriaIT`: **12/12**.

### Verificación final amplia

- Regresión dirigida fresca `ec.uce.propuestas.insumo.*`: **74 tests = 74 pass**, **0 failures**, **0 errors**, **0 skips**.
- `./gradlew spotlessCheck` inicialmente detectó únicamente violaciones de formato en cuatro archivos Java de Plan 035. `spotlessApply` los normalizó; el `spotlessCheck` posterior, ejecutado como parte de `./gradlew build -x test`, pasó.
- `./gradlew build -x test`: **PASS**.
- Suite completa fresca: **715 tests = 712 pass + 2 failures aceptados** (GM-19 y GM-20) **+ 1 skipped** (GM-24), **0 errors**.
- `git diff --check`: **limpio**.
- Sin cambios en migraciones, `motor/` ni `recalculo/`.

Plan 035 queda **DONE** con verificación focal y amplia medida. La siguiente
tarea es Plan 036. `graphify update .` finalizó con 4.453 nodos, 13.555 aristas
y 182 comunidades.

## Matriz de paridad del API P-39

Los estados siguientes se basan en la corrida RED y en el GREEN focal anterior.
No se usan símbolos visuales de aprobación o rechazo.

| Fila P-39 | Endpoint | Estado demostrado | Evidencia y cierre |
|---|---|---|---|
| Listar bases | `GET /admin/bases-centrales?incluirArchivadas=&page=&size=` | **gap** | RED demostró la forma antigua `List` y la falta de paginación/validación. GREEN demuestra `Page<AdminBaseCentralResponse>` con `items,total,page,size,totalPaginas`, defaults `page=0`, `size=25`, orden estable, `size<=200` y respuestas 400 para parámetros inválidos. |
| Crear base | `POST /admin/bases-centrales` | **paridad** | Se preservan 201, UUIDv7 y validación del request. GREEN demuestra además una sola emisión exitosa con `operacion=crear`. |
| Renombrar base | `PUT /admin/bases-centrales/{id}` | **paridad** | Se preservan 200/400/404. GREEN demuestra emisión exitosa con `operacion=renombrar`. |
| Archivar base | `POST /admin/bases-centrales/{id}/archivar` | **paridad** | Se preservan archivado, 200/404 y ocultamiento del catálogo normal. GREEN demuestra emisión exitosa con `operacion=archivar`. |
| Borrar base | `DELETE /admin/bases-centrales/{id}` | **paridad** | La línea base ya rechazaba la base activa con 409 `base-no-archivada` y borraba la archivada con 204. GREEN conserva ambos caminos, no emite ante el 409 y emite `operacion=borrar` solo en el éxito. |
| Crear insumo | `POST /admin/bases-centrales/{id}/insumos` | **paridad** | Se preservan 201/400/404 y el conflicto de código duplicado. GREEN demuestra emisión exitosa con `operacion=crearInsumo`. |
| Editar insumo | `PUT /admin/bases-centrales/{id}/insumos/{iid}` | **paridad** | Se preservan 200/400/404. GREEN demuestra emisión exitosa con `operacion=editarInsumo`; la semántica A9 mantiene aisladas las copias PROYECTO. |
| Borrar insumo | `DELETE /admin/bases-centrales/{id}/insumos/{iid}` | **gap** | RED demostró que la línea base no protegía una referencia real de `apu_detalle`. La consulta real de usos reemplaza el stub y GREEN demuestra 409 `insumo-en-uso` para el referenciado, 204 para el libre y ausencia de evento en el rechazo. |
| Importar CSV | `POST /admin/bases-centrales/{id}/insumos/import?soloValidar=` | **paridad** | Se preservan 200/400/404 y el upsert D-06. GREEN demuestra `operacion=importar` para importación material y ninguna emisión para `soloValidar=true`. |

No quedó una fila con estado **divergencia**: las divergencias históricas de
DELETE ya habían sido resueltas por el acta 032; la auditoría encontró dos gaps
ejecutables, no una decisión canónica pendiente.

## Emisión D-13

El GREEN focal cubre las ocho operaciones cerradas de
`admin.base_editada`: `crear`, `renombrar`, `archivar`, `borrar`, `importar`,
`crearInsumo`, `editarInsumo` y `borrarInsumo`. Cada detalle contiene solo
`operacion` y `cantidadInsumos`; el UUIDv7 de la base se registra en
`entidadId` top-level. Los GET, `soloValidar=true` y los DELETE rechazados no
emiten.

## Cobertura defensiva de corrupción

El fixture que inserta directamente un `apu_detalle` apuntando a un insumo
CENTRAL representa un estado defensivo de corrupción o datos heredados. No
modela el flujo normal: la regla A9 materializa o reutiliza una copia en una
base PROYECTO antes de asociar el insumo a una fila APU. El caso directo existe
para comprobar que, si ese estado anómalo aparece, el DELETE no destruye la
referencia y responde 409 `insumo-en-uso`.

## Cierre

La evidencia focal y amplia quedó medida sin regresiones atribuibles a Plan 035.
Los únicos fallos de la suite completa son GM-19 y GM-20, residuales aceptados;
GM-24 permanece omitido. El cierre no incluye ni reclama la actualización final
de Graphify.
