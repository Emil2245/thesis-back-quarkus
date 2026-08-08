# Plan 01 — Módulo `proyecto` (P-05…P-11)

> Playbook auto-contenido. Sigue `docs/modulos/README.md`. No crea migraciones
> SQL (tablas en V001). Alcance: `ec.uce.propuestas.proyecto`.

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

**Duplicar proyecto (P-09):** **EXCLUIDO del alcance** (decisión 2026-08-02).
La entrevista N02 §3 desaconseja clonar proyectos completos ("propenso a errores
al arrastrar cronogramas o cantidades pasadas"); solo se permite **duplicar
insumos** entre bases (P-17, módulo `insumo`). No existe endpoint
`POST /proyectos/{id}/duplicar` ni se añadirá.

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
  una copia de `ParametrosSistema` (P-11). `actualizar(proyectoId, req)` valida
  rangos (RNF-09):
  - %HM ∈ [0, 0.20]; %CI ∈ [0,1]; IVA ∈ [0, 0.30]; `monto>0` para plazo.

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
- **Duplicar proyecto (P-09): NO se implementará** — solo se duplican insumos
  entre bases (P-17, módulo `insumo`). Decisión 2026-08-02, entrevista N02 §3.
- Admin P-39.