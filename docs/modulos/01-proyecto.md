# Plan 01 — Módulo `proyecto` (P-05…P-11)

> Playbook auto-contenido. Sigue `docs/modulos/README.md`. No crea migraciones
> SQL (tablas en V001). Alcance: `ec.uce.propuestas.proyecto`.
>
> **Estado de cierre (2026-08-30 — sincronización Plan 08):**
> implementación cerrada de P-05…P-11. Los recursos `Proyecto`,
> `Firmante` y `ParametrosProyecto` ya migran el path
> `proyectoId`/`firmanteId` a UUIDv7 ([Plan 07 — DONE
> 2026-08-30](./planes-para-estar-al-dia/07-uuidv7-fronteras-rest.md));
> `ParametrosProyectoResponse.proyectoId` y la seam
> `ParametrosProyectoCambio` ya están alineadas. El módulo
> `proyecto` ya no expone `Long` en path ni JSON.

## 1. Arquitectura de empaquetado

```
ec/uce/propuestas/proyecto/
├── entity/
│   ├── Proyecto.java
│   ├── EstadoProyecto.java          (enum BORRADOR|EN_PROCESO|FINALIZADO)
│   ├── PlazoUnidad.java             (enum SEMANA|MES)
│   ├── Firmado.java
│   ├── RolFirmante.java           (enum CONSOLIDADO|APROBADO)
│   ├── ParametrosProyecto.java
│   ├── ModoCodigoRubro.java       (enum AUTOGENERADO|MANUAL)
│   └── ParametrosSistema.java      (@Entity LECTURA, singleton id=1)
├── repository/
│   ├── ProyectoRepository.java
│   ├── FirmanteRepository.java
│   ├── ParametrosProyectoRepository.java
│   └── ParametrosSistemaRepository.java
├── mapper/
│   ├── ProyectoMapper.java
│   ├── FirmanteMapper.java
│   └── ParametrosMapper.java
├── dto/
│   ├── ProyectoCrearRequest.java
│   ├── ProyectoEditarRequest.java
│   ├── OrigenInsumos.java
│   ├── ProyectoResponse.java
│   ├── ProyectoDetalleResponse.java
│   ├── FirmanteCrearRequest.java
│   ├── FirmanteResponse.java
│   ├── ParametrosProyectoActualizarRequest.java
│   └── ParametrosProyectoResponse.java
├── service/
│   ├── ProyectoService.java
│   ├── FirmanteService.java
│   └── ParametrosProyectoService.java
└── resource/
    ├── ProyectoResource.java
    ├── FirmanteResource.java
    └── ParametrosProyectoResource.java
```

## 2. Entidades (mapean V001)

**`Proyecto`** (`tabla proyecto`): campos como en DDL. Nota: `logo BYTEA` como
`byte[]`; propietario `@ManyToOne Usuario` **NO** — se guarda `@Column(name =
"usuario_id") Long usuarioId` (evita dependencia de entidad `Usuario`; aislamiento
RNF-05 se resuelve por id del claim).

**`Firmado`** (`tabla firmante):` `@ManyToOne Proyecto` con `usuario_id` col; único
(rol, orden) vía `proyectoId + rol + orden`.

**`ParametrosProyecto`** (`tabla parametros_proyecto):` PK `proyecto_id` (`@Id Long
proyectoId`), 12 campos.

**`ParametrosSistema`** (LECTURA): mapea `parametros_sistema` (id=1 seed). Sin
resource/servicio CRUD (admin I-11). Solo lectura para copiar en P-06.

Todos `extends PanacheEntityBase` + `@Id @GeneratedValue(IDENTITY)`/`@Id` (PKISSN
compuesta no may — parametro uso PK propio).

## 3. Recorte de alcance (decisionıy al crear proyecto)

Al crear (P-06, transacción) se crea:
- `Proyecto` (dueño desde claim JWT)
- `ParametrosProyecto` := copia de `ParametrosSistema` (id=1)
- `base_insumos` PROYECTO del proyecto (en módulo insumo, servicio `InsumoService`
  provee `crearBaseProyecto`)
- si `origenInsumos.tipo != VACIA`: copia de insumos de la fuente (delegación al
  servicio de insumo `copiarBaseFuenteAProyecto`).

**NOT** crear aquí: `presupuesto` v1 ni `cronograma` (módulos futuros). El DTO
`ProyectoDetalleResponse.versionVigente` queda `null` / `alertas` básicas hasta
que existing módulo presupuesto.

**Duplicar proyecto (P-09 / P-46):** **RECONSIDERADO** tras N04 18-08-2026
(entrevista Ing. Carlosama, dossier `07-decisiones-i06-pendientes.md` §A8).
El ingeniero confirmó que **se deben agregar plantillas para proyectos
completos, siguiendo un proceso similar a las plantillas de APUs**. Por lo
tanto:

- `POST /proyectos/{id}/duplicar` (duplicar destructivo) sigue
  **EXCLUIDO** (decisión N02 original; no se reactiva).
- `POST /proyectos/{proyectoId}/desde-plantilla/{plantillaId}` (**NUEVO —
  P-46**) **SÍ se implementa** en I-06, vía módulo `plantilla/` + nuevo
  `PlantillaProyectoService`. Mecánica similar al de P-26 (carga con
  fallback, advertencias, insumos sin precios). Ver
  `04-apu-avanzado.md` §2.7.

> **Nota de scope:** la funcionalidad "duplicar" como tal (clonar proyecto
> completo del mismo usuario) **no se reactiva** — el ingeniero desaconsejó
> clonar proyectos completos. Lo nuevo es **"cargar desde plantilla"**, que
> es distinto: el usuario elige una snapshot guardada como favorita y crea
> un proyecto nuevo basado en ella.

## 4. Services

- **ProyectoService** (transacciones CRUD). Aísla por `usuarioId`
  (del claim JWT). Al listar/detallar/editar/eliminar comprueba propiedad → 404
  si ajeno.
  - `Page<ProyectoResponse> listar(Long duenoId, String q, Estado, int page, int size)`
  - `ProyectoDetalle detalle(id, duenoId)`
  - `crear(duenoId, ProyectoCrearRequest)` (transacción; call `insumoService`)
  - `editar`, `eliminar` (404 si ajeno; `presupuesto` hoy no existe → eliminar
    borra en cascade por BD).
- **FirmanteService**: CRUD; `UNIQUE(rol, orden)` — `POST` más de una vez con
  mismo orden → 400 `validacion`. no reordena automáticamente el resto (simple).
- **ParametrosProyectoService**: `obtener(proyectoId)` → si no existe fila, crea
  una copia de `ParametrosSistema` (P-11). `actualizar(usuarioId, proyectoId, req)`
  valida rangos **leídos desde `ParametrosSistema`** (N04 §A6 — rangos
  parametrizables; default %HM ∈ [0, 0.20]; %CI ∈ [0,1]; IVA ∈ [0, 0.30];
  `moneda` libre). Tras persistir devuelve un seam **neutro**
  `ParametrosProyectoCambio` con:
  - `proyectoId` interno (Long resuelto),
  - flags de cambio numérico **escala-insensibles y null-safe**:
    `porcentajeHerramientaMenorCambio`, `porcentajeIndirectoCambio`,
  - `ParametrosProyectoResponse` ya materializado.

  Este seam **no dispara recálculo**: queda preparado como entrada para
  WU-07, que conectará `RecalculoService` cuando exista el motor de
  presupuesto/APU vigente. Hoy, la mutación se limita a persistir la fila;
  las actualizaciones globales de rangos en `actualizarSistema` (sólo
  SUPER_ADMIN) permanecen sin efectos colaterales. Implementación futura
  del recálculo: ver `04-apu-avanzado.md` §2.9.

## 5. REST resources (RestResponse<T>)

**ProyectoResource** `@Path("/proyectos")` `@RolesAllowed({"USUARIO","SUPER_ADMIN"})`
- `GET ""` → `RestResponse<Page<ProyectoResponse>>` (query params q, estado, page, size)
- `GET "/{id}"` → `RestResponse<ProyectoDetalleResponse>` / 404
- `POST ""` → `RestResponse` 201 `ProyectoDetalleResponse` (req @Valid)
- `PUT "/{id}"` → `200` `ProyectoDetalleResponse`
- `DELETE "/{id}"` → `204`
- `PUT "/{id}/logo"` (multipart) → 204 (guarda `byete[]`; valida tipo/tamaño)
- `GET "/{id}/logo"` → bytes image/*

**FirmanteResource** `@Path("/proyectos/{proyectoId}/firmantes")`
- GET/POST/PUT/DELETE (RestResponse<>).

**ParametrosProyectoResource** `@Path("/proyectos/{proyectoId}/parametros")`
- GET → `ParametrosProyectoResponse`
- PUT → `ParametrosProyectoResponse` (valida rangos)

El propietario se resuelve con `@Inject UsuarioContext` o lectura de `JsonWebToken`
(`getSubject` / `getClaim`). Reusaremos la resolución del token que ya tiene
`usuario/auth` (claim `upn=email`) → `usuarioRepository.findByEmail`.

## 6. Tests (@QuarkusTest)

- `ProyectoResourceIT`: crear → 201; listar propios; detalle ajeno → 404; editar;
  eliminar; parametos default al crear.
- `ProyectoServiceIT`: transacción de creación (base insumos + parametos).
- `ParametrosRangoTest` (unit): fuera de rango → `validacion`.

## 7. Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
```

## 8. Fuera de alcance (TODO)
- `presupuesto` v1 al crear, `versionVigente` en detalle.
- `cronograma`.
- **Duplicar proyecto destructivo (P-09): NO se implementará** — solo se
  duplican insumos entre bases (P-17, módulo `insumo`). Decisión N02 §3
  ratificada por N04 §A8 (se prefiere "cargar desde plantilla" — P-46).
- **NUEVO I-06 — Cargar proyecto desde plantilla (P-46)** — **DONE
  2026-08-29 (Plan 06)**, ver
  [`planes-para-estar-al-dia/06-plantillas-proyecto.md`](planes-para-estar-al-dia/06-plantillas-proyecto.md).
  Endpoint vigente:
  `POST /proyectos/desde-plantilla/{plantillaId}` con `UuidV7.parse` en
  el path (Plan 07). Verificación principal 83/83 verde.
- Admin P-39 (CRUD bases CENTRALES) — **DONE 2026-08-29 (Plan 05)**,
  ver
  [`planes-para-estar-al-dia/05-administracion-bases.md`](planes-para-estar-al-dia/05-administracion-bases.md).
  Recurso `AdminBaseCentralResource` en `insumo/resource/` bajo
  `@RolesAllowed("SUPER_ADMIN")`.