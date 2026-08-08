# Plan 02 — Módulo `insumo` (P-13…P-18)

- Playbook auto-conductor. Implementa `docs/modulos/README.md`. No crea
  migraciones (tablas V001–V003 ya siembran `base_insumos` CENTRAL + 93 insumos).
- Base: `ec.uce.propuestas.insumo`.
- **Estado (2026-08-02):** implementado. Base + Insumo + Unidad catalogo +
  CRUD + catálogo unidades + selector multi-fuente + copia + import CSV.
  P-18 (verificación de uso en APU, D-08) queda `stub → 0` hasta módulo APU.

## 1. Empaquetado

```
ec/uce/propuestas/insumo/
├── entity/
│   ├── BaseInsumos.java
│   ├── TipoBase.java          (enum CENTRAL|PROYECTO)
│   ├── Insumo.java
│   ├── TipoInsumo.java        (enum EQUIPO|MANO_OBRA|MATERIAL|TRANSPORTE)
│   └── UnidadCatalogo.java    (@Entity LECTURA — catálogo)
├── repository/
│   ├── BaseInsumosRepository.java
│   ├── InsumoRepository.java
│   └── UnidadCatalogoRepository.java
├── mapper/
│   ├── InsumoMapper.java
│   └── BaseInsumosMapper.java
├── dto/
│   ├── InsumoCrearRequest.java
│   ├── InsumoEditarRequest.java
│   ├── InsumoResponse.java
│   ├── InsumoUsoResponse.java
│   ├── InsumoBusquedaResponse.java
│   ├── BaseInsumosResponse.java
│   ├── CopiarBaseRequest.java
│   ├── CopiaBaseResultadoResponse.java
│   ├── ImportResultadoResponse.java
│   └── ErrorFila.java
├── service/
│   ├── BaseInsumosService.java
│   ├── InsumoCatalogoService.java   (listado, busqueda)
│   ├── InsumoCrudService.java       (CRUD, uso, bloqueo)
│   ├── importacion/
│   │   ├── CsvInsumoParser.java      (PRA puro: byte[] → List<Fila>)
│   │   ├── FilaInsumo.java           (record)
│   │   └── ImportacionInsumoService.java (aplicar, upsert D-06, transaccional)
│   └── CopiaBaseService.java         (P-17, D-07)
└── resource/
    ├── InsumoResource.java           (sub-path /proyectos/{id}/insumos)
    └── BaseInsumosResource.java       (/bases-centrales)
```

## 2. Entidades

**`BaseInsumos`**: `id` IDENTITY; `nombre`, `tipo` (CENTRAL/PROYECTO), `proyecto_id`
(null si CENTRAL), `archivada`. Métodos de dominio: `esProyectoDe(proyectoId)`.

**`Insumo`**: `id`, `base_id`, `codigo`, `tipo`, `descripcion`, `unidad`,
`precio_unitario` (`BigDecimal`), `createdAt`, `updatedAt`. UNIQUE(`base_id,codigo`).
- `UnidadCatalogo` (LECTURA): `codigo` PK, `descripcion`.

## 3. Servicio base

**`BaseInsumosService`**:
- `obtener(proyectoId)` → crea/retorna `BaseInsumos` PROYECTO del proyecto (usado por módulo `proyecto` P-06).
- `centrales()` → lista `BaseInsumos` tipo=CENTRAL, `archivada=false` (`BaseInsumosResponse[]`).

## 4. Catálogo (P-13/P-16)

**InsumoCatalogoService**:
- `listarBase(baseId, TipoInsumo tipo, String q, boolean desactualizados, page, size)`
  → `Page<InsumoResponse>`; `desactualizado` = `updatedAt < now()-90d`.
- `buscarDirecto(proyectoId, fuente(LOCAL|CENTRAL|COMBINADA), q, tipo)` →
  `InsumoBusquedaResponse[]` (local = base proyecto; central = base(s) CENTRAL; combinada = unión).

## 5. CRUD (P-14)

**InsumoCrudService** (transaccional):
- `crear(baseId, InsumoCrearRequest)`:
  - valida `precio > 0` (BigDecimal >0)· `tipo` → unidad (MO/EQUIPO fuerza `h`)
  - `codigo` duplicado en base → `codigo-duplicado` (400)
  - set `updatedAt = now()`.
- `editar(baseId, iid, InsumoEditarRequest)` — igual reglas; edición de precio es
  punto de propagación (futuro `recalculo` RNF-02).
- `eliminar(baseId, iid)` — si `apu_detalle.insumo_id` referencia → 409
  `insumo-en-cuso` con `List<InsumoUsoResponse>`.
- `uso(iid)` → `InsumoUsoResponse[]` (buscar `apu_detalle` por insumo).

## 6. Importación (P-15)

`importacion/CsvInsumoParser` (PQIRE, sin I/O):
- `parse(byte[] csv, TipoInsumo tipo)` → `List<FilaInsumo>` (o lanza `csv-invalido`
  si columnas/ilegible). Uso `apache commons-csv`.

`ImportacionInsumoService`:
- `validar(byte[] csv, tipo)` → `ImportResultadoResponse` (soloValidar=true; crea
  resumen publicado; no persiste).
- `aplicar(baseId, byte[] csv, tipo)` (transacción): **upsert por `codigo`** (D-06) —
  si no existe → crear; si existe → actualizar precio/descripcion/unidad. Reporta
  `creados` / `actualizados` / `errores[]` (fila con `ErrorFila`).

## 7. Copia de base (P-17)

**CopiaBaseService** `copiarAProyecto(proyectoId, CopiarBaseRequest)`:
- fuente = base CENTRAL (por `baseId`) o PROYECTO (por `proyectoId`).
- obtiene base destino del proyecto ; copia insumos **independiente**
  (`createdAt/updatedAt = now`).
- conflict (D-07): si `codigo` existe en destino → conserva existente, omite y
  reporta en `omitidos[]`.

## 8. Resources (RestResponse)

**InsumoResource** `@Path("/proyectos/{proyectoId}/insumos")` `@RolesAllowed({USUARIO,SUPER_ADMIN})`
- `GET ""` → `RestResponse<Page<InsumoResponse>>`
- `POST ""` → 201 `InsumoResponse` (@Valid)
- `PUT "/{iid}"` → `InsumoResponse`
- `DELETE "/{iid}"` → 204 / 409
- `GET "/{iid}/uso"` → `InsumoUsoResponse[]`
- `POST "/import?soloValidar={b}"` → consumición `multipart/form-data` (`archivo` file + `tipo`) → `ImportResultadoResponse`
- `POST "/copiar-base"` → `CopiaBaseResultadoResponse`
- `GET "/busqueda?fuente=&q=&tipo="` → `InsumoBusquedaResponse[]`

**BaseInsumosResource** `@Path("/bases-central")` (usuario)
- `GET ""` → `BaseInsumosResponse[]` (no archivadas)
- `GET "/{baseId}/insumos?tipo&q&page"` → `Page<InsumoResponse>`

## 7. Tests

- `ImportacionCsvTest` (PQREU): CSV válido → N filas; columna faltante →
  `csv-P- invalid`; código duplicado interno → 2 filas con error (TC-P15-03/04).
- `InsumoResourceIT` (@QuarkusTest): crear (201); duplicado → 400; uso → 409;
  `unidad='h'` for MO.
- `BusquedaResourceIT`: busqueda local+central, `desactualizado` flag.
- `CopiaBaseServiceIT`: copiar central_base (93) → `copiados`; segunda copia →
  `omitidos=all` (D-079).

## 8. Verificación

/inscador;run
```bash
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
```

## 9. Fuera de alcance (TODO)
- Editar/eliminar `base_insumos` CENTRAL (admin P-39) → I-11.
- Recalculo de precios/APU (RNF propios) en edición.
- ETag/Cache-Control en bases centrales (economic, no prior).
- Refactor de la app a nueva convención (limpieza separada).