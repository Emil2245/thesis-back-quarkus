# Plan 04 — Implementar plantillas de APU

## Resultado esperado

Un usuario puede guardar un APU como plantilla PERSONAL, administrar sus plantillas y crear otro APU desde una plantilla SISTEMA o PERSONAL. Los insumos se resuelven hacia una base PROYECTO y los faltantes generan advertencias, no enlaces ocultos.

## Dependencia

Completar primero el [Plan 03](./03-contrato-apu-actual.md).

## Estado de cierre

**DONE 2026-08-29.** Suite nueva `ec.uce.propuestas.plantilla.*` **34/34
verde** (`SnapshotApuMapperTest` 5/5 + `PlantillaApuResourceIT` 12/12 +
`ApuCalculoServiceNullableInsumoTest` 1/1 + `PlantillaApuServiceTest`
16/16). Regresión dirigida en módulos colindantes **47/47 verde**
(`ApuResourceIT` 36/36 + `ApuCalculoServiceIT` 2/2 + `ResolverInsumoProyectoTest`
9/9). **No** se ejecuta ni se reporta la suite completa del proyecto;
el pase de las suites `ec.uce.propuestas.{motor,schema,identifier,proyecto,insumo,documento}.*`
queda fuera del cierre de Plan 04.

1. **Snapshot totalmente price-free (Plan 04 §1)** — `SnapshotApuMapper` (`ec.uce.propuestas.plantilla.service`) escribe y lee JSONB **sin precios efectivos, sin IDs de insumo y sin links al APU origen**. Sólo persiste `insumoCodigo`, `cantidad`, `rendimiento` y `esHerramientaMenor`. El compilador previene el uso accidental de cualquier campo de precio: las records internas (`SnapshotFila`, `SnapshotLinea`) **no exponen** `precioOverride`, `tarifaJornal`, `precioUnitarioTarifa` ni `costo`, así que ningún caller puede serializar un override histórico. El writer **nunca** emite esos campos — tampoco un override explícito del APU origen. El reader **tolera e ignora** silenciosamente los campos heredados del seed V004 (`tarifaJornal`, `costo`, `orden`, `descripcion`, `seccionTipo`, `tipoInsumo`, `publicId`). Cargar una plantilla nunca arrastra un precio del APU origen — los precios siempre se recalculan desde la base del proyecto al aplicar.
2. **Plantillas SISTEMA read-only + PERSONAL con scope de dueño** — `PlantillaApuService` aplica la regla de visibilidad (`SISTEMA` visible a todos; `PERSONAL` sólo del caller; PUT/DELETE sobre SISTEMA o PERSONAL ajena → 404 vía `findByPublicIdAndOwnerScope`).
3. **Guardar desde APU** — `POST /apus/{id}/guardar-plantilla` exige APU del caller (404 si ajeno), crea `PlantillaApu(tipo=PERSONAL, usuarioId=caller, unidad=apu.unidad)` con `publicId` UUIDv7. El snapshot persiste el `insumoCodigo` resuelto (vía `insumoRepository.findById(d.insumoId).codigo`); para filas pendientes (`insumoId=null`) preserva el código pendiente guardado en `descripcion`.
4. **Listar / renombrar / eliminar** — `GET/PUT/DELETE /plantillas-apu` siguen el contrato UUIDv7 y devuelven códigos consistentes (200/204/400/404).
5. **Cargar plantilla al crear APU** — integrado en `POST /presupuestos/{id}/apus` vía `plantillaId` opcional. `ApuCrudService.crear(...)` delega la materialización a `PlantillaApuService.aplicarPlantilla(apu, plantilla)` (reutiliza la creación de secciones + HM auto-creado del CRUD existente). HTTP **201** cuando todas las filas se resuelven; HTTP **200** con `advertencias[]` no vacío cuando hay códigos no resueltos. Si el snapshot no trae fila HM, la carga elimina la HM auto-creada, procesa las filas del snapshot y, tras el bucle, si EQUIPO quedó sin HM shiftea las filas no-HM +1 e inserta HM en `orden=1` para preservar orden único y contiguo.
6. **Fallback PROYECTO → CENTRAL → PERSONAL → pendiente (Plan 04 §N04 §B.4)** — `ResolverInsumoPlantillaService.resolver(codigo, seccionTipo, proyectoId)` filtra por tipo de sección compatible (`TipoInsumo` derivado de `SeccionTipo`) y por `archivada=false` para CENTRAL/PERSONAL. Resuelve contra la base PROYECTO del proyecto, luego CENTRAL (no archivada, tipo compatible), luego PERSONAL del dueño del proyecto (no archivada, tipo compatible); usa `ResolverInsumoProyectoService` para materializar copias. Si nada resuelve, la fila pendiente se persiste con **`insumoId = NULL`**, `descripcion = codigo`, y **override explícito `0`** en la columna de la sección (EQUIPO/MANO_OBRA → `tarifaJornal`; MATERIAL/TRANSPORTE → `precioUnitarioTarifa`) — la columna de override **no queda vacía**, se escribe a `0`. Esto lo habilita la migración estructural **V005**, que relaja `CHECK (> 0)` a `CHECK (>= 0)` en esas dos columnas. El motor opera con la regla `COALESCE(override, precio_insumo)` y, con `insumo == null && override == 0`, produce costo `0` sin NPE (`ApuCalculoService.snapshotDeDetalle` fuerza `override = 0` cuando llega `null` para que el motor no se entere de la diferencia entre fila pendiente y override explícito).
7. **Write-through de cálculo corregido** — `ApuCalculoService.recalcular` y `proyectar` comparten el helper `mapearDetallesACalc(entidades, secciones)` que asigna cada `ApuDetalle.id` a su índice en `out.filas()` respetando el layout del motor (`[M con HM primero, N, O, P]`). Mover el HM a un orden no primero ya no mezcla su costo con las filas no-HM adyacentes.
8. **Migración V005 (estructural, no es reseed)** — `V005__allow_zero_pending_apu_detail_prices.sql` relaja `CHECK (tarifa_jornal > 0)` y `CHECK (precio_unitario_tarifa > 0)` a `>= 0`. Es la **única** migración del bloque; es **estructural** (relaja CHECKs, no reseed). **No** se toca V001/V002/V003/V004 ni `insumo.precio_unitario` (los precios de catálogo siguen siendo `> 0`, invariante V001). El seed V004 queda intacto y se sigue leyendo tal cual: el snapshot canónico y el reader tolerante conviven con el JSONB histórico sin reescritura.

**Verificación** (suite de tests nueva bajo `ec.uce.propuestas.plantilla.*`, **34/34 verde**):

- `SnapshotApuMapperTest` (5/5) — writer nunca persiste precios efectivos, IDs de insumo, ni overrides explícitos heredados del APU origen; reader round-trip y reader tolera campos V004 (`tarifaJornal`/`costo`/`orden`/`descripcion`/`seccionTipo`/`tipoInsumo`/`publicId`) ignorándolos.
- `PlantillaApuServiceTest` (16/16) — listar/detalle/renombrar/eliminar con autorización (SISTEMA read-only / PERSONAL con dueño); snapshot price-free total verificado en JSONB serializado; **distinción fila pendiente vs insumo real con `precio_unitario = 0`** (la primera tiene `insumo_id = NULL` + override `0` + `advertencias[]`; el segundo mantiene `insumo_id` poblado, sin advertencias); fila pendiente con `advertencias[]`; HM no-presente en snapshot → shift +1 al insertar; HM en posición no primera preserva costos de fila al recalcular.
- `ApuCalculoServiceNullableInsumoTest` (1/1) — el motor calcula `costo = 0` sin NPE con `insumo_id = NULL` + override `0`.
- `PlantillaApuResourceIT` (12/12) — REST end-to-end (201/200/204/400/404) + UUIDv7 + preservación del APU tras borrar plantilla + fallback con advertencias para códigos no resueltos.

**Regresión dirigida (47/47 verde)** sobre los paquetes cuya costura tocó Plan 04:

- `ApuResourceIT` (36/36)
- `ApuCalculoServiceIT` (2/2)
- `ResolverInsumoProyectoTest` (9/9)

**No** se ejecuta ni se reporta `./gradlew test` completo (motor/identifier/schema/proyecto/insumo/documento); el cierre de Plan 04 es estrictamente dirigido a su suite + regresión adyacente.

**Decisiones del autor:**

1. **Integración en `ApuCrudService` (no nuevo método `cargarPlantilla`).** El seam de creación de APU + secciones + HM vive en `ApuCrudService.crear(...)`; añadir `plantillaId` como parámetro opcional reusa esa seam sin duplicarla. El método público original `crearComoRespuesta(...)` se mantiene para los tests existentes (no usa plantilla, no necesita caller).
2. **Snapshot totalmente price-free — el compilador bloquea cualquier precio.** El writer NO emite nunca `precioOverride`, `tarifaJornal`, `precioUnitarioTarifa` ni `costo`, **incluso aunque el APU origen traiga un override explícito**: las records internas (`SnapshotFila`, `SnapshotLinea`) no exponen esos campos, así que ningún caller puede accidentalmente serializar un override histórico. Cargar una plantilla siempre recalcula precios desde la base del proyecto — esta regla **elimina el "override histórico"** del APU origen (ningún `precioOverride` del APU origen se arrastra a un APU nuevo vía plantilla; el seed V004 con sus campos `tarifaJornal`/`costo` se ignora por reader tolerante).
3. **`ApuDetalle.tarifaJornal` y `precioUnitarioTarifa` como columnas de override para filas pendientes.** En lugar de tocar `insumo.precio_unitario` (catálogo, `> 0`, invariante V001) ni relajar las invariantes del motor, la fila pendiente escribe override `0` en la columna de la sección (EQUIPO/MANO_OBRA → `tarifaJornal`; MATERIAL/TRANSPORTE → `precioUnitarioTarifa`). El motor opera con `COALESCE(override, precio_insumo)` y produce costo `0` sin NPE. La fila pendiente se distingue de un insumo real con `precio_unitario = 0` por **`insumo_id IS NULL` + `advertencias[]`** en la respuesta HTTP `200`; un insumo real con `precio_unitario = 0` mantiene `insumo_id` poblado y la respuesta es HTTP `201` sin advertencias (`Insumo.precio_unitario` sigue siendo `> 0` en BD — la regla catálogo `> 0` no se relaja; el caso "precio `0`" sólo aplica al override de fila pendiente y a `apu_detalle`, no a catálogo).
4. **No se crea `cargarDesdePlantilla` como endpoint nuevo.** El endpoint existente `POST /presupuestos/{id}/apus` acepta `plantillaId` opcional, evitando una URL duplicada. El comportamiento se decide por la presencia/ausencia de advertencias (201/200) según el contrato 07-api-contract §5.
5. **`ApuResponse.advertencias` con `@JsonInclude(NON_NULL)`.** El campo sólo aparece en la respuesta del endpoint que carga plantilla (no se rompe la forma del response en otros endpoints).
6. **SISTEMA read-only desde el seam USUARIO.** `PUT/DELETE` sobre una plantilla SISTEMA devuelven 404 (RNF-05) en lugar de 403, para no filtrar existencia. La administración real vive en `/admin/plantillas-apu` (P-40, fuera de alcance de Plan 04).

## Alcance

### Incluye

- DTOs, resource y servicio de plantillas APU dentro del paquete `plantilla` existente.
- Listar SISTEMA y PERSONALES propias.
- Obtener, renombrar y eliminar una plantilla PERSONAL propia.
- Guardar snapshots JSONB sin precios.
- Crear un APU desde plantilla con fallback de insumos.
- Tolerar campos extra del seed V004.

### No incluye

- **No hay V005 de reseed** del JSONB histórico. La única V005 del bloque es estructural (relaja CHECKs `> 0` → `>= 0` en `apu_detalle.tarifa_jornal` y `apu_detalle.precio_unitario_tarifa`); V001/V002/V003/V004 quedan intactos y el snapshot V004 se sigue leyendo tal cual por el reader tolerante.
- Compartir plantillas PERSONALES entre usuarios.
- Plantillas de proyecto (P-46 — vive en Plan 06; **no** se marca DONE aquí).
- Crear un módulo nuevo.

## Contrato esperado

```text
GET    /plantillas-apu
GET    /plantillas-apu/{id}
PUT    /plantillas-apu/{id}
DELETE /plantillas-apu/{id}
POST   /apus/{id}/guardar-plantilla
POST   /presupuestos/{id}/apus  { plantillaId }
```

Los IDs públicos deben seguir la convención UUIDv7 ya vigente en APU. La uniformidad global se cierra en el Plan 07.

## Pasos

1. Auditar `PlantillaApu`, su repository, el formato JSONB de V004 y los contratos canónicos P-26.
2. Definir DTOs mínimos para listado, detalle, renombrado, guardado, creación y advertencias.
3. Implementar autorización:
   - SISTEMA: visible y no editable por usuarios;
   - PERSONAL: solo visible/editable/eliminable por su propietario;
   - recurso ajeno: 404.
4. Guardar un snapshot totalmente price-free que preserve:
   - códigos de insumo (`insumoCodigo`);
   - cantidades y rendimientos;
   - sección y orden;
   - fila HM (placeholder `esHerramientaMenor: true`);
   - **sin override de precio** (los precios efectivos nunca se persisten, ni siquiera cuando el APU origen traiga un override explícito — invariante "snapshot price-free total").
5. Integrar `plantillaId` en la creación de APU sin duplicar la lógica normal de creación.
6. Resolver cada código mediante `ResolverInsumoProyectoService`:
   - ya existe en PROYECTO: reutilizar;
   - existe en CENTRAL/PERSONAL visible: copiar a PROYECTO;
   - no existe: crear fila con precio 0 y agregar `advertencias[]`.
7. Rechazar o ignorar de forma documentada campos JSONB desconocidos; no crear V005 solo por compatibilidad.
8. Probar propiedad, fallback, faltantes y atomicidad de la creación.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'   # 34/34 verde (DONE)
./gradlew test --tests 'ec.uce.propuestas.apu.*'         # 36/36 verde (regresión)
./gradlew test --tests 'ec.uce.propuestas.insumo.*'      # 9/9 verde (regresión ResolverInsumoProyectoTest)
./gradlew spotlessCheck

git diff --check
```

> **No** se ejecuta la suite completa del proyecto en este cierre (motor/identifier/schema/proyecto/documento quedan fuera del alcance de Plan 04).

## Criterios de terminado

- [x] SISTEMA y PERSONALES propias aparecen en el listado correcto.
- [x] Una plantilla PERSONAL ajena devuelve 404.
- [x] El snapshot no guarda precios efectivos ni relaciones con el APU original (writer nunca emite `precioOverride`/`tarifaJornal`/`precioUnitarioTarifa`/`costo`; reader los ignora).
- [x] Crear desde plantilla materializa insumos en PROYECTO (reusar PROYECTO, copiar CENTRAL/PERSONAL vía `ResolverInsumoProyectoService`).
- [x] Los códigos faltantes producen precio 0 y advertencias explícitas — fila pendiente con `insumo_id = NULL` + override `0` en la columna de la sección + `advertencias[]` (HTTP 200).
- [x] V004 continúa legible sin reseed: V005 estructural relaja los CHECKs (`>= 0`) sólo en `apu_detalle`; V001/V002/V003/V004 intactos; `insumo.precio_unitario` sigue `> 0`.
- [x] `ec.uce.propuestas.plantilla.*` **34/34** verde y regresión dirigida (APU + insumo) **47/47** verde (sin suite completa).

## Siguiente plan ejecutable

[Plan 05 — administración de bases actuales](./05-administracion-bases.md)
(`D-12` archivar/borrar central sin bloqueo + `A9` cerrar CRUD de bases PERSONALES).
P-46 (plantilla de proyecto) vive en [Plan 06](./06-plantillas-proyecto.md) y
**no** se cierra aquí.

## Condiciones de parada

Detener y reportar si:

- el snapshot canónico exige copiar precios (Plan 04 §1 rechaza cualquier override — ni siquiera explícito);
- el fallback requiere enlazar el nuevo APU con insumos CENTRAL/PERSONAL directos (siempre se copia a PROYECTO vía `ResolverInsumoProyectoService`);
- se necesita una migración de reseed del seed V004 (no aplica; sólo V005 estructural);
- la implementación requiere un nuevo módulo de primer nivel.
