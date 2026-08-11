# Plan 03 — Módulo `apu` (P-19…P-21, P-22) — I-05 núcleo

- Playbook auto-conductor. Implementa `docs/modulos/README.md`. No crea
  migraciones (las tablas `apu`, `apu_seccion`, `apu_detalle` ya viven en
  `V001__baseline.sql` §2.10; V004 ya siembra 3 escenarios con APUs).
- Base: `ec.uce.propuestas.apu`.
- **Estado (2026-08-11):** plan de iteración I-05 (editor de APU: crear, leer,
  editar cabecera, filas M/N/O/P, fila HM auto-generada, recalculo write-through
  vía `Motor.calcularApu`, override de precio por fila P-22). I-06 (P-23 %CI,
  P-24 descuento, P-25 auxiliares, P-26 plantillas, P-27 desglose) queda fuera.
- Pendiente del motor: GM-21/GM-24 detalles — sin relación con este módulo.

## 1. Alcance (qué entra en I-05 y qué no)

**Contrato de referencia:** `thesis-docs/plan/architecture/07-api-contract.md` §5
y Apéndice B, filas P-19…P-22. **Reglas de dominio:** `thesis-docs/plan/domain/02-data-model.md`
§6–§9 y §16 (fórmulas), más decisiones §17 #9 (HM primera fila del bloque M,
asunción entrevista 02), #16 (null-means-inherit para precios de fila),
#17 (%CI null = hereda del proyecto).

En alcance (endpoints implementados en esta iteración):

| Endpoint | Contrato | Errores (type) |
|---|---|---|
| `GET /presupuestos/{presupuestoId}/apus?q&soloAuxiliares&page&size` | P-19 | `no-encontrado` |
| `POST /presupuestos/{presupuestoId}/apus` | P-20 | `validacion` · `codigo-duplicado` |
| `GET /apus/{apuId}` | P-21 | `no-encontrado` |
| `PATCH /apus/{apuId}` | P-21 (subset: codigo, descripcion, unidad) | `validacion` · `codigo-duplicado` |
| `DELETE /apus/{apuId}` | P-19 | `no-encontrado` · `apu-referenciado` |
| `POST /apus/{apuId}/detalles` | P-21 | `validacion` · `no-encontrado` |
| `PATCH /apus/{apuId}/detalles/{detalleId}` | P-21/P-22 | `validacion` · `no-encontrado` · `fila-protegida` |
| `DELETE /apus/{apuId}/detalles/{detalleId}` | P-21 | `no-encontrado` · `fila-protegida` |

Fuera de alcance (I-06 — futura `docs/modulos/04-apu-avanzado.md`):
`esAuxiliar`, `apuAuxiliarId` (P-25), `porcentajeIndirecto` override (P-23),
`/apus/{id}/descuento` (P-24), plantillas (P-26), `/apus/{id}/calculo`
(desglose P-27), `POST /apus/{id}/duplicar`. La propagación a
rubro/capítulo/presupuesto (RNF-02) es del módulo presupuesto (I-07): aquí se
persiste write-through **a nivel APU** (sus propias columnas de costo).

**Decisiones propias del plan (documentadas):**

- **Fila HM:** se crea automáticamente al crear el APU (DM §9 literal: «una fila
  generada automáticamente en el bloque M de cada APU»), primera del bloque M
  (decisión §17 #9). Es `fila-protegida`: `PATCH`/`DELETE` → 409. Su
  `cantidad` en BD es NULL (lo exige el CHECK de `apu_detalle`); el snapshot que
  se da al motor lleva `cantidad = %HM×100` (entero, p.ej. `5`), como
  documenta `FilaSnapshot` y como hace `Jixtures.lineaToFila`. Se mantiene
  presente incluso cuando Subtotal_N = 0 (costo 0); la plantilla de export
  decidirá si la omite (el workbook CMT sin MO no lleva HM — tema de export,
  no de modelo, ya que el motor calcula `CostHM = %HM×Subtotal_N` y con N=0 da 0).
  La descripción se regenera en cada recalculo: `"Herramienta Menor {n}%MO"`.
- **Override de precio en la fila (P-22):** NULL = hereda (null-means-inherit,
  §17 #16). La columna de override es `tarifa_jornal` (EQUIPO/MANO_OBRA) o
  `precio_unitario_tarifa` (MATERIAL/TRANSPORTE) según la sección
  (matrix de columnas DM §8). `precioEfectivo = COALESCE(override, Insumo.precio_unitario)`.
- **Recalculo write-through:** tras cada mutación de una fila (crear/editar/
  eliminar) se reejecuta `Motor.calcularApu` y se persisten los derivados:
  `apu.costo_directo/indirecto/total`, `apu_seccion.subtotal`, y por fila
  `apu_detalle.costo` (+ `costo_hora` en EQUIPO/MO). Los cambios de cabecera
  (codigo/descripcion/unidad) no alteran totales → no recalculan. El APU es su
  propio agregado (contract §1: las mutaciones devuelven el `ApuResponse`
  completo — una sola cache key del cliente).
- **Resolución de insumo para el snapshot:** el insumo referenciado se resuelve
  contra la **base PROYECTO** del proyecto (RNF-05/localidad). En esta
  iteración no se aplica la congelación de precios de bases CENTRALES
  (asunción §17 #16 / agenda A9) — las filas con insumo CENTRAL heredan en vivo
  y el comportamiento de congelado se decide con el director en I-06. Si el
  insumo pertenece a una base CENTRAL se usa igualmente su precio vigente.
- **`vinculado`** en el resumen: `true` si la tabla `rubro.apu_id` referencia a
  este APU (D-09 1:1). Se resuelve con una consulta nativa de conteo sobre
  `rubro` (la entidad `rubro` pertenece al módulo presupuesto, I-07). Con eso
  `DELETE /apus/{id}` devuelve 409 `apu-referenciado` ya desde I-05 para el APU
  vinculado a un ítem (fila de contrato P-19); los usos como auxiliar se suman
  en I-06.

## 2. Empaquetado

```
ec/uce/propuestas/apu/
├── entity/
│   ├── Apu.java
│   ├── ApuSeccion.java
│   └── ApuDetalle.java                  (usa SeccionTipo de motor — mismo vocabulario)
├── repository/
│   ├── ApuRepository.java
│   ├── ApuSeccionRepository.java
│   └── ApuDetalleRepository.java
├── mapper/
│   ├── ApuMapper.java
│   ├── ApuResumenMapper.java
│   └── ApuDetalleMapper.java
├── dto/
│   ├── ApuCrearRequest.java
│   ├── ApuPatchRequest.java
│   ├── ApuResponse.java
│   ├── ApuResumenResponse.java
│   ├── ApuSeccionResponse.java
│   ├── ApuDetalleResponse.java
│   ├── ApuDetalleCrearRequest.java
│   └── ApuDetallePatchRequest.java
├── service/
│   ├── ApuCrudService.java              (CRUD + validaciones de dominio + P-21/P-22)
│   └── ApuCalculoService.java           (snapshot → Motor.calcularApu → persistir write-through)
└── resource/
    ├── PresupuestoApuResource.java      (@Path "/presupuestos/{presupuestoId}/apus")
    └── ApuResource.java                 (@Path "/apus/{apuId}" — /detalles anidados)
```

## 3. Entidades (mapean V001 §2.10)

**`Apu`**: `id` IDENTITY; `presupuestoId` (FK); `codigo` (unique por presupuesto);
`descripcion`, `unidad`, `esAuxiliar` (default false, no expuesto en I-05);
`porcentajeIndirecto` (nullable — herencia), `porcentajeDescuento` (default 0);
`costoDirecto`, `costoIndirecto`, `costoTotal` (write-through);
`createdAt`, `updatedAt`. `@PrePersist/@PreUpdate` como `Insumo`.

**`ApuSeccion`**: `id`, `apuId`, `tipo` (`SeccionTipo` de `motor`), `subtotal`
(write-through), `orden` (fijo EQUIPO=1…TRANSPORTE=4). UNIQUE `(apu_id, tipo)`.

**`ApuDetalle`**: `id`, `seccionId`, `insumoId` (nullable), `apuAuxiliarId`
(nullable — I-06), `descripcion` (copia del Insumo al crear fila),
`orden` (dentro de la sección), `esHerramientaMenor`, `cantidad` (nullable),
`tarifaJornal` (override E/MO), `costoHora` (write-through), `rendimiento`
(nullable), `unidad` (copia del Insumo, M/T), `precioUnitarioTarifa` (override
M/T), `costo` (write-through). `SeccionTipo seccion` se resuelve por la sección
padre, no por columna propia.

## 4. Servicios

### `ApuCrudService` (transaccional, recibe `usuarioId` y `proyectoId` ya validados por el resource)

- `crear(presupuestoId, ApuCrearRequest)`:
  - `codigo` duplicado en la versión → `codigo-duplicado` (400). Si `codigo`
    viene null/blank → autogenera `APU-{n}` (n = count de APUs de la versión + 1,
    padded 3). El plan no inventa reglas de negocio: la autogeneración se
    documenta como provisional hasta que I-06/P-26 defina la semántica de código
    con plantillas.
  - persiste `Apu` + crea las 4 `ApuSeccion` (orden fijo) + fila HM
    (`esHerramientaMenor=true`, descripcion dinámica, primera del bloque M).
  - rebota por `ApuCalculoService.recalcular(apu)` (totales 0) y responde
    `ApuResponse` completo.
- `obtener(apuId)` → `ApuResponse`.
- `editarCabecera(apuId, ApuPatchRequest)`: aplica solo los campos
  presentes (`JsonNullable`); `codigo` duplicado → `codigo-duplicado`.
  No recalcula.
- `eliminar(apuId)`: si `estaVinculado(apuId)` (rubro lo referencia) → 409
  `apu-referenciado` (D-09); si no, DELETE (cascada a secciones/detalles vía FK).
- `agregarDetalle(apuId, ApuDetalleCrearRequest)`:
  - valida `insumoId` obligatorio (I-05 no tiene auxiliares) y que exista;
  - `cantidad` > 0; `rendimiento` obligatorio solo EQUIPO/MANO_OBRA (> 0);
  - en MATERIAL/TRANSPORTE el precio efectivo proviene del insumo (sin
    rendimiento — fórmula DM §16);
  - copia `descripcion`/`unidad` del insumo; al añadir EQUIPO (no HM) la fila
    toma `orden` = max(existentes EQUIPO)+1 (la HM se queda primero);
  - `ApuCalculoService.recalcular(apu)` y responde `ApuResponse`.
- `editarDetalle(apuId, detalleId, ApuDetallePatchRequest)`:
  - fila HM → 409 `fila-protegida`;
  - `precioOverride` con `JsonNullable`: presente = setea `tarifaJornal`/
    `precioUnitarioTarifa` (según sección); null = libera la columna (hereda);
  - `cantidad`/`rendimiento` presentes (> 0) → actualizan;
  - recalcula y responde `ApuResponse`.
- `eliminarDetalle(apuId, detalleId)`: fila HM → 409 `fila-protegida`; si no,
  delete + recalcular + `ApuResponse`.

### `ApuCalculoService` (deep — interactúa con el motor; sin lógica de framework)

- `ApuCalculado recalcular(Apu apu)` (transaccional, join persistido):
  1. Carga secciones+detalles en orden (EQUIPO: HM primero; luego orden).
  2. Para cada fila construye `FilaSnapshot`:
     - insumo resuelto → `precioInsumo = Insumo.precioUnitario`;
     - `overridePrecio` = `tarifaJornal` (E/MO) o `precioUnitarioTarifa` (M/T);
     - HM → `cantidad = %HM×100`, sin precio/rendimiento (como `Fixtures`);
  3. `ParametrosCalculo` desde `ParametrosProyectoService.obtenerOCrear`:
     `porcentajeHerramientaMenor`, `porcentajeIndirectoDefault`,
     `porcentajeIndirectoApu = apu.porcentajeIndirecto` (null → hereda),
     `porcentajeDescuento = apu.porcentajeDescuento`.
  4. `Motor.calcularApu(snapshot, params)`.
  5. Persiste: `apu.costo*`, `apu_seccion.subtotal` por sección,
     `apu_detalle.costo` (+ `costo_hora` E/MO), descripción y costo de la fila HM.
- No toca ninguna clase de `motor/` (regla intocable).

## 5. Recursos (JAX-RS `Response`, convención real `PerfilResource`)

**`PresupuestoApuResource`** `@Path("/presupuestos/{presupuestoId}/apus")`
`@RolesAllowed({"USUARIO","SUPER_ADMIN"})`:
- `GET ""` → `Page<ApuResumenResponse>` (`q`, `soloAuxiliares` — no-op en I-05
  porque no hay auxiliares, `page`, `size`).
- `POST ""` → 201 `ApuResponse`.
- Resuelve `presupuestoId → proyectoId` (consulta nativa en `ApuRepository`),
  `proyectoService.validarPropietario(usuarioId, proyectoId)` (RNF-05),
  y `parametrosService.obtenerOCrear(proyectoId)` para el recalculo.

**`ApuResource`** `@Path("/apus/{apuId}")`:
- `GET ""` → `ApuResponse`
- `PATCH ""` → `ApuResponse`
- `DELETE ""` → 204
- `POST "/detalles"` → 201 `ApuResponse`
- `PATCH "/detalles/{detalleId}"` → `ApuResponse`
- `DELETE "/detalles/{detalleId}"` → `ApuResponse`
- Resuelve el APU, de ahí `proyectoId` (vía `presupuesto.proyecto_id`) y valida
  propietario en cada llamada.

`usuarioId()` se resuelve vía `SecurityIdentity` + `UsuarioRepository`
(patrón `ProyectoResource.usuarioId()`).

## 6. `JsonNullable` para PATCH (omitir vs null)

El contrato (§1 PATCH policy) exige distinguir «omitir campo» de «enviar null»:
- **PATCH cabecera:** `ApuPatchRequest(String? codigo, String? descripcion,
  String? unidad)` con `JsonNullable<String>`. Omitir = no tocar; null = no
  tiene semántica de limpieza (los campos no son nullable en BD) → ignorado.
- **PATCH fila:** `ApuDetallePatchRequest(JsonNullable<BigDecimal> cantidad,
  JsonNullable<BigDecimal> rendimiento, JsonNullable<BigDecimal> precioOverride)`.
  `precioOverride` presente = setea override; `null` = restaura herencia (P-22).
  `cantidad`/`rendimiento` `null` = se ignora (no son nullable en BD).

`JsonNullable` NO viene embebido en `quarkus-rest-jackson`: es el artefacto
`org.openapitools:jackson-databind-nullable` (mismo `JsonNullable` que usa
OpenAPI Generator). Exige:
1. Dependencia `jackson-databind-nullable` en `build.gradle.kts`
   (autorizada por este plan).
2. Registrar `JsonNullableModule` en el `ObjectMapper` vía
   `ObjectMapperCustomizer` (`common/JacksonConfig.java`).

Cada resource hace `@Valid` y el service usa
`isPresent()`/`isUndefined()`; `JsonNullable.of(null)` = «null explícito»,
`JsonNullable.undefined()` = «campo omitido».

> **Traps conocidos (documentados en upstream):**
> - No funciona en parámetros de constructor `@JsonCreator`; en DTOs `record`
>   con `JsonNullable` el campo omitido no debe deserializar como `null` — los
>   tests `ApuResourceIT` verifican esto (TC-P21-02/03, P-22 restaura herencia
>   con `null` explícito y no muta con campo omitido).
> - Regresión Quarkus 3.37.0 con `quarkus-rest-jackson` reflection-free
>   serializers que descartaba el tipo contenido (gh#55143). Fijada en
>   3.37.1 — estamos en 3.37.4, no aplica; si un tipo contenido fallara al
>   leer (ClassCastException), el fallback es
>   `quarkus.rest.jackson.optimization.enable-reflection-free-serializers=false`.

> **Desvío de formato JSON (ya existente en el repo):** el contrato pide dinero/
> porcentajes como string decimal (`"61.390000"`), pero la convención real de
> `insumo/proyecto` serializa `BigDecimal` como número JSON. Este plan mantiene
> `BigDecimal` para concordar con el código existente (ver nota de
> `docs/modulos/README.md`). La precisión se conserva en BD (NUMERIC(14,6)); el
> formateo a string es decisión del serializador del cliente, no del DTO.

## 7. Errores

Se extiende `common/ProblemaException` con factories tipadas (catálogo del
contract) — cambio aditivo, no rompe nada:

| type | Factory | Status |
|---|---|---|
| `codigo-duplicado` | `ProblemaException.codigoDuplicado(String)` | 400 |
| `apu-referenciado` | `ProblemaException.apuReferenciado(String)` | 409 |
| `fila-protegida` | `ProblemaException.filaProtegida(String)` | 409 |

(Existentes: `validacion` 400, `no-encontrado` 404 → `GlobalExceptionMapper` de `common/`.)

## 8. Tests (TDD — contrato primero)

- `ApuCalculoServiceIT` (`@QuarkusTest`): construye un APU y comprueba que el
  write-through reproduce las fórmulas DM §16 con caso conocido (HM 5 % × 8.99 =
  0.45; CM/…). Aserciones a scale 6.
- `ApuResourceIT` (`@QuarkusTest`, patrón `InsumoResourceIT`):
  - crear APU → 201 con 4 secciones ordenadas + HM presente (TC-P20-01);
  - codigo duplicado en la versión → 400 `codigo-duplicado`; el mismo codigo en
    otra versión sí (crear segunda versión vía SQL directo) (TC-P20-03);
  - agregar MO + material → totales correctos según motor (TC-P21-02);
  - PATCH fila HM → 409 `fila-protegida`; DELETE fila HM → 409 (TC-P21-03);
  - PATCH override precio → `precioEfectivo` cambia; `null` → hereda (TC-P22-02/03);
  - editar precio del insumo → fila sin override refleja el cambio (TC-P22-01);
  - DELETE APU vinculado a `rubro` → 409 `apu-referenciado` (inserta rubro por SQL);
  - recurso ajeno → 404 (RNF-05).
- TRUNCATE en `@BeforeEach`: añadir `apu_detalle, apu_seccion, apu, rubro,
  presupuesto, capitulo` a la lista de `InsumoResourceIT` (cascada por FK).

## 9. Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.*'      # nuevas suites
./gradlew test                                        # suite completa (sin regresión)
./gradlew build -x test                               # sanity build
```

Expected: nuevas suites verdes; baseline 63 tests (2 rojos GM-19/20 conocidos,
2 skipped) sin cambios.

## 10. Fuera de alcance (TODO hacia I-06)
- Auxiliares (`esAuxiliar`, `apuAuxiliarId`, `flag-auxiliar-bloqueado`, D-08);
- %CI override + herencia proyecto→APU (P-23/D-05) — la infraestructura
  (columna + ParametrosCalculo) queda lista;
- descuento por rubro (P-24); plantillas (P-26); desglose (P-27);
- duplicar APU; congelación de precios de bases CENTRALES (aserción §17 #16);
- propagación a rubro/capítulo/Total General (módulo presupuesto, I-07).