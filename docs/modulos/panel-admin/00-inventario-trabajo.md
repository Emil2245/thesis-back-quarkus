# 00 — Inventario de trabajo del panel Super-Admin (I-11)

> **Acompañante del** [`00-acta-reconciliacion.md`](./00-acta-reconciliacion.md)
> **firmada el 2026-09-07.**
> **Propósito:** mapa priorizado `P-xx → plan → TC → archivos candidatos`
> para los planes **033–040**. Refleja capacidades existentes vs gaps y
> dependencias. **No** incluye código; cada fila lista el seam previsto,
> los archivos a crear/modificar y el TC del catálogo
> `quality/02-catalogo-pruebas.md` que valida la entrega.
>
> **Verificación:** este inventario **no** se ejecuta; queda como guía
> para los planes 033–040. La verificación dirigida de cada plan es
> responsabilidad de su propio archivo ejecutable.

## 0. Convenciones del inventario

- **Estado actual** (corte 2026-09-08, con 032–033 cerrados):
  - **DONE / PARITY:** capacidad implementada y verificada en el árbol de
    trabajo; no implica commit.
  - **GAP parcial:** capacidad existe pero requiere un ajuste estrecho
    (RED-first en el plan correspondiente).
  - **MISSING:** capacidad no existe; el plan la crea desde cero.
- **Archivos candidatos:** ruta relativa a la raíz del backend
  (`/home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus/`).
- **TC:** identificador del catálogo
  `thesis-docs/plan/quality/02-catalogo-pruebas.md`.
- **Acción de cada fila:** `crear`, `modificar` o `reusar`.
- **Evidencia medida:** 033 registra sus conteos exactos; el inventario no
  predice resultados para 034–040.

## 1. Plan 033 — Log de actividad — base (P-42 foundation) — DONE / PARITY

**Cierre:** DONE (2026-09-08). **Prioridad histórica:** 1 (gate de cualquier emisor).
**Proceso / historia:** P-42 / US-39 / TC-P42-01..02 (foundation).
**Dependencias previas:** acta 032 firmada + drift canónico corregido + adenda D-21 incorporada.

### Capacidades actuales vs gap

- `log_actividad` tabla + índices (`V001 §2.15`): **DONE / PARITY**.
- Servicio emisor: **DONE / PARITY** (`LogActividadService`, `MANDATORY`).
- Enum `EventoLogActividad` con 26 verbatim: **DONE / PARITY**.
- Recurso `LogActividadResource`: **DONE / PARITY** (`SUPER_ADMIN`).
- `LogActividadDetalleValidator` + `detallesEsperados()`: **DONE / PARITY**.
- Columna `log_actividad.public_id` UUIDv7: **DONE / PARITY** (V010).
- Columna `log_actividad.entidad_public_id UUID NULL`: **DONE / PARITY**
  (V010; server-authored, sin FK, sin DEFAULT, sin UNIQUE).
- Mapeo `LogActividadResponse.entidadId` desde `entidad_public_id`: **DONE / PARITY**;
  la columna legacy `entidad_id BIGINT` no cruza REST.
- Persistencia `detalle` JSONB: **DONE / PARITY** como `String` JPA mediante
  `ObjectMapper`, manteniendo `Map<String,Object>` en el contrato público y
  evitando el fallo de arranque del `FormatMapper` personalizado de Quarkus.

### Archivos concretos entregados

| Acción | Archivo | Condición |
|---|---|---|
| Creado | `src/main/resources/db/migration/V010__log_actividad_identidad_publica.sql` | Siguiente libre tras V009. Nombre **neutral** (describe el alcance completo: dos columnas nuevas). La migración añade **ambas** columnas en un solo archivo: (a) `public_id UUID NOT NULL DEFAULT uuidv7()` con índice único `ux_log_actividad_public_id` y trigger `trg_log_actividad_public_id_immutable` reusando `fn_assert_public_id_immutable()` de V001 §5; (b) `entidad_public_id UUID NULL` sin FK, sin DEFAULT, sin UNIQUE (D-21: los logs sobreviven al borrado de la entidad afectada). **Nunca** editar V001–V009. La columna legacy `entidad_id BIGINT` (V001 §2.15) **no** se toca: permanece legacy-only. **No** se intenta backfill ni conversión BIGINT→UUID. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/EventoLogActividad.java` | Enum con 26 entradas verbatim (sin 4 legacy V004); método `detallesEsperados()` que materializa la matriz del acta. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/LogActividadDetalleValidator.java` | Validador server-side: rechaza el evento desconocido con `IllegalArgumentException` y la clave de detalle no permitida con `IllegalStateException`. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/service/LogActividadService.java` | `emitir(...)` con `@Transactional(TxType.MANDATORY)`; sin `emitirFailure`/`REQUIRES_NEW`/`codigoError`; popula `entidadPublicId` server-authored desde el `entidadId` (UUIDv7) provisto por el caller admin. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/entity/LogActividad.java` | Entidad JPA con `publicId` UUIDv7 + `entidadPublicId` UUIDv7 nullable + `entidadIdLegacy` BIGINT nullable (mapea la columna legacy `entidad_id` con `@Column(name="entidad_id")` para preservar rastro histórico, **sin** exponerla en el DTO) + `fecha` Instant; mapeo de `detalle` JSONB. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/repository/LogActividadRepository.java` | Panache repository con filtros `usuarioId`, `evento`, `desde`, `hasta` y paginación `Page<T>` canónica. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/resource/LogActividadResource.java` | `GET /admin/logs` con `@RolesAllowed("SUPER_ADMIN")` a nivel de clase; DTO `LogActividadResponse` con `id`/`usuarioId`/`usuarioNombre`/`evento`/`entidad`/`entidadId`/`detalle`/`fecha` mapeando `entidadId` desde `entidadPublicId` (nunca desde `entidadIdLegacy`). |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/dto/LogActividadResponse.java` | Record canónico mínimo del acta (sin `invitacionExpiraEn`; sin PII; sin ningún campo derivado de `entidadIdLegacy`). |
| Creado | `src/main/java/ec/uce/propuestas/usuario/audit/dto/LogActividadFiltros.java` | Filtros y validación de la consulta admin. |
| Creados | `src/test/java/ec/uce/propuestas/usuario/audit/{EventoLogActividadTest,LogActividadDetalleValidatorTest,LogActividadIndicesTest,LogActividadServiceTest,LogActividadSinPiiTest,resource/LogActividadResourceIT}.java` | Cobertura del catálogo/allowlist, `TransactionalException` estándar de `MANDATORY`, commit/rollback, esquema e índices, ausencia de PII y endpoint filtrado/paginado. |

### Evidencia de cierre

- Audit focal: **26/26**, 0 failures/errors/skips.
- Usuario completo: **51/51**, 0 failures/errors/skips.
- Suite completa autoritativa con `test-port=0`: **673 tests, 2 failures,
  0 errors, 1 skipped**; solo GM-19/GM-20 aceptados y GM-24 omitido.
  La corrida inicial sin puerto dinámico, afectada por cuatro
  `QuarkusBindException` y 500 omisiones en cascada, quedó supersedida.
- Spotless, build sin tests, diff y checks de superficies prohibidas: **PASS**.
- V010 sin FK sobre `entidad_public_id`. Sin commit.

### TCs

- **TC-P42-01:** GET `/admin/logs` filtra por `evento`, `usuarioId`, `desde`, `hasta`, `page`, `size`; respeta `Page<T>` canónica. Filas con `entidadPublicId` poblado exponen `entidadId`; filas V004 sin `entidadPublicId` exponen `entidadId: null`.
- **TC-P42-02:** `detalle` JSONB sin PII ni secretos (RNF-08); matriz canónica respetada.

## 2. Plan 034 — Gestión de usuarios e invitaciones (P-38)

**Prioridad:** 2 (emisor 034 + camino emisor adicional para
`usuario.activado` con origen `invitacion`).
**Proceso / historia:** P-38 / US-35 / TC-P38-01..03.
**Dependencias previas:** 033 cerrado.

### Capacidades actuales vs gap

- `POST /auth/aceptar-invitacion` + `TipoToken.INVITACION` + TTL 72 h:
  **DONE / PARITY** (`AuthService.aceptarInvitacion`,
  `TokenService`, `app.app.token.invitacion-ttl`).
- `GET/POST/PUT /admin/usuarios[/{id}]` + desactivar/reactivar/delete:
  **MISSING**.
- DELETE con proyectos propios: **MISSING** (requiere test focal FK).

### Archivos candidatos

| Acción | Archivo | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/UsuarioAdminService.java` | `invitar`, `reactivar`, `desactivar`, `eliminar`. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/UsuarioAdminResource.java` | `GET/POST /admin/usuarios`, `PUT /admin/usuarios/{id}` (reactivar/desactivar), `DELETE /admin/usuarios/{id}`. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioAdminResponse.java` | Record con `id` UUIDv7, `nombre`, `email`, `rol`, `activo`, `emailVerificado`, `fechaCreacion` Instant. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioInvitarRequest.java` | DTO de entrada para invitación (sin contraseña temporal). |
| Reusar | `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.aceptarInvitacion` | Sin reescritura; 038 emite `usuario.activado` con `origen=invitacion` reusando el seam. |
| Modificar | `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java` | **No tocar la transacción existente**; agregar emisión del evento con el seam 033 en `aceptarInvitacion` (038) y los demás métodos del catálogo que correspondan. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/admin/UsuarioAdminResourceIT.java` | `@QuarkusTest` con TC-P38-01..03; **RED-first** para DELETE con proyectos propios → 409 `usuario-con-proyectos-impedido` (D-20). |

### TCs

- **TC-P38-01:** invitación 72 h sin contraseña temporal; token expira y devuelve 410 `token-invalido-o-expirado`.
- **TC-P38-02:** desactivar/reactivar admin y emitir `usuario.desactivado` / `usuario.activado` con `origen=admin`.
- **TC-P38-03:** DELETE con proyectos propios → 409 `usuario-con-proyectos-impedido` (test focal FK); DELETE sin proyectos → 204.

### Deferidos a I-12 (no incluidos en 034)

- Self-delete.
- Last-active-SUPER_ADMIN.
- Cambio de email admin (`PUT /admin/usuarios/{id}` para email).
- Primer SUPER_ADMIN bootstrap.

## 3. Plan 035 — Bases centrales — cierre (P-39)

**Prioridad:** 3.
**Proceso / historia:** P-39 / US-36 / TC-P39-01..03.
**Dependencias previas:** 033 cerrado.

### Capacidades actuales vs gap

- `AdminBaseCentralResource` (P-39): **DONE / PARITY**
  (`src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java`).
- DELETE base activa → 409 `base-no-archivada`: **DONE / PARITY**
  (`BaseInsumosService.java:156`).
- DELETE insumo con FK real → 409 `insumo-en-uso`: **GAP parcial** —
  stub `conteoUsosApu() = 0L` (`InsumoCrudService.java`); hoy
  devuelve 400 `validacion` (rama que nunca se ejecuta porque el
  conteo es siempre 0).
- Emisión `admin.base_editada`: **MISSING**.

### Archivos candidatos

| Acción | Archivo | Condición |
|---|---|---|
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/InsumoCrudService.java` | Reemplazar `conteoUsosApu() = 0L` por consulta real a `apu_detalle` (o equivalente). Cambiar `ProblemaException.validacion(...)` por `ProblemaException.conflicto("insumo-en-uso", ...)` para la rama con `usos > 0`. **RED-first:** test rojo previo que reproduzca 400 actual. |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java` | Instrumentar `admin.base_editada` en operaciones exitosas; **no** modificar el contrato existente. |
| Crear | `src/test/java/ec/uce/propuestas/insumo/service/InsumoCrudServiceIT.java` | **Test rojo previo** que reproduce 400 `validacion` actual; segundo test que verifica 409 `insumo-en-uso` tras el cambio (RED → GREEN). |
| Crear | `src/test/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResourceIT.java` | Cubre TC-P39-01..03 + emisión `admin.base_editada` solo en operaciones exitosas. |

### TCs

- **TC-P39-01:** CRUD bases centrales (crear, renombrar, archivar, listar con `incluirArchivadas`).
- **TC-P39-02:** DELETE base activa → 409 `base-no-archivada`; DELETE archivada → 204; copia PROYECTO intacta.
- **TC-P39-03:** DELETE insumo con `usos > 0` → 409 `insumo-en-uso`; DELETE insumo sin usos → 204.

## 4. Plan 036 — Plantillas APU de sistema (P-40)

**Prioridad:** 4.
**Proceso / historia:** P-40 / US-37 / TC-P40-01.
**Dependencias previas:** 033 cerrado.

### Capacidades actuales vs gap

- `SnapshotApuMapper` (price-free): **DONE / PARITY**.
- `plantilla_apu` personal + carga con fallback: **DONE / PARITY** (P-26).
- Flujo admin `POST /admin/plantillas-apu[/{id}]`: **MISSING**.
- Emisión `admin.plantilla_editada`: **MISSING**.

### Archivos candidatos

| Acción | Archivo | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/plantilla/admin/PlantillaApuAdminService.java` | `crearDesdeApu(UUID desdeApuId, String nombre)`, `editar(UUID, ...)`, `borrar(UUID)`. |
| Crear | `src/main/java/ec/uce/propuestas/plantilla/admin/PlantillaApuAdminResource.java` | `GET /admin/plantillas-apu`, `POST /admin/plantillas-apu`, `PUT /admin/plantillas-apu/{id}`, `DELETE /admin/plantillas-apu/{id}`. |
| Reusar | `src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java` | Sin reescritura. |
| Crear | `src/test/java/ec/uce/propuestas/plantilla/admin/PlantillaApuAdminResourceIT.java` | Cubre TC-P40-01 + emisión `admin.plantilla_editada` solo en operaciones exitosas. |

### TCs

- **TC-P40-01:** crear plantilla SISTEMA desde APU existente con `desdeApuId` (UUIDv7); `tipo=SISTEMA` y `usuario_id=NULL` server-authored; `descripcionRubro` validado contra columna real (sin tope arbitrario).

## 5. Plan 037 — Parámetros del sistema y valores de referencia (P-41)

**Prioridad:** 5.
**Proceso / historia:** P-41 / US-38 / TC-P41-01..02.
**Dependencias previas:** 033 cerrado.

### Capacidades actuales vs gap

- `GET/PUT /proyectos/parametros-sistema` (lectura pública, escritura
  `@RolesAllowed("SUPER_ADMIN")`): **PARITY parcial** — el GET
  actual devuelve la entidad JPA (`ProyectoResource.java:127`).
- `valor_referencia` tabla + 4 seeds V004: **DONE / PARITY**.
- CRUD `/admin/valores-referencia`: **MISSING**.
- DTO `ParametrosSistemaResponse`: **MISSING** (D-10 seleccionado).

### Archivos candidatos

| Acción | Archivo | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/proyecto/dto/ParametrosSistemaResponse.java` | DTO canónico obligatorio (acta 032 D-10; ya no es condicional); 12 numéricos + 8 rangos; `GET /proyectos/parametros-sistema` deja de exponer la entidad JPA. |
| Modificar | `src/main/java/ec/uce/propuestas/proyecto/resource/ProyectoResource.java` | `GET /proyectos/parametros-sistema` retorna `ParametrosSistemaResponse` (no la entidad). La ruta no cambia. |
| Crear | `src/main/java/ec/uce/propuestas/proyecto/admin/ValorReferenciaAdminService.java` | `listar`, `upsert`, `eliminar`; cualquier `clave` única con `fuente` no blank. Vive en `proyecto/admin` (subpaquete del módulo `proyecto`); **no** se crea un módulo `valor/` nuevo de primer nivel. |
| Crear | `src/main/java/ec/uce/propuestas/proyecto/admin/ValorReferenciaAdminResource.java` | `GET/PUT/DELETE /admin/valores-referencia[/{clave}]` con `@RolesAllowed("SUPER_ADMIN")`. |
| Modificar | `src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoService.java` | `actualizarSistema` emite `admin.parametros_editados` con `detalle = { operacion: "defaults.update", camposModificados: ["iva"] }` consumiendo verbatim la matriz congelada por el acta 032. |
| Crear | `src/test/java/ec/uce/propuestas/proyecto/resource/ProyectoResourceParametrosSistemaTest.java` | Verifica que `GET` retorna `ParametrosSistemaResponse` y no la entidad JPA. |
| Crear | `src/test/java/ec/uce/propuestas/proyecto/admin/ValorReferenciaAdminResourceIT.java` | Cubre TC-P41-02 (CRUD + emisión `admin.parametros_editados` con `operacion` + `clave`). |

### TCs

- **TC-P41-01:** `GET /proyectos/parametros-sistema` retorna `ParametrosSistemaResponse`; `PUT` con rol SUPER_ADMIN; sin CAMICON sembrado.
- **TC-P41-02:** `PUT /admin/valores-referencia/{clave}` upsert con `fuente` no blank; cualquier clave única se acepta.

## 6. Plan 038 — Instrumentación D-13: identidad y catálogos

**Prioridad:** 6.
**Proceso / historia:** D-13 auth/usuario/proyecto/insumo/base/APU.
**Dependencias previas:** 033 + 034 + 035 + 036 + 037 cerrados.

### Capacidades actuales vs gap

- Servicios públicos ya implementados (auth, proyecto, insumo, base,
  APU): **DONE / PARITY**.
- Emisión de eventos D-13 desde esos servicios: **MISSING**.

### Archivos candidatos

| Acción | Archivo | Condición |
|---|---|---|
| Modificar | `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java` | Emitir `auth.login` / `auth.logout` / `auth.registro` / `auth.password_cambiada` / `usuario.activado` (origen `invitacion`) con seam 033. La transacción ya existe; no se reescribe `@Transactional`. |
| Modificar | `src/main/java/ec/uce/propuestas/proyecto/service/ProyectoService.java` | Emitir `proyecto.creado` / `proyecto.editado` / `proyecto.eliminado`. **`proyecto.duplicado` no se implementa** (`STOP-032-P09-DUPLICAR`, "no producer yet"). |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/InsumoCrudService.java` | Emitir `insumo.creado` / `insumo.editado` / `insumo.eliminado`. |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/ImportacionInsumoService.java` | Emitir `insumos.import_csv`. |
| Modificar | `src/main/java/ec/uce/propuestas/insumo/service/CopiaBaseService.java` | Emitir `base.copiada_a_proyecto`. |
| Modificar | `src/main/java/ec/uce/propuestas/apu/service/ApuCrudService.java` | Emitir `apu.creado` / `apu.editado` / `apu.eliminado`. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/auth/AuthServiceEventIT.java` | Tests focales: login/registro/cambio password/aceptación invitación emiten eventos correctos; rollback borra eventos. |
| Crear | `src/test/java/ec/uce/propuestas/proyecto/service/ProyectoServiceEventIT.java` | Cubre `proyecto.creado/editado/eliminado`. **`proyecto.duplicado` no se testea** (no producer yet). |
| Crear | `src/test/java/ec/uce/propuestas/insumo/service/InsumoEventIT.java` + `ImportacionInsumoServiceEventIT.java` + `CopiaBaseServiceEventIT.java` | Cobertura focales. |
| Crear | `src/test/java/ec/uce/propuestas/apu/service/ApuCrudServiceEventIT.java` | Cobertura focales. |

### TCs

- Cubre **16 nombres únicos** del catálogo D-13 (auth/proyecto/insumo/base/APU) **más un camino emisor adicional** para `usuario.activado` con origen `invitacion` (camino compartido con 034, que emite el mismo nombre con origen `admin`).
- **Excluye** los 4 nombres legacy V004.
- **`proyecto.duplicado`** queda como "no producer yet" — sin test skipped ni fabricado.

## 7. Plan 039 — Instrumentación D-13: presupuesto, cronograma, documento

**Prioridad:** 7.
**Proceso / historia:** D-13 presupuesto/cronograma/documento/export.
**Dependencias previas:** 033 + 038 cerrados; Plan 031 sin tocar.

### Capacidades actuales vs gap

- Servicios de presupuesto / cronograma / documento: **DONE / PARITY**.
- Plan 031 (export MSPDI, `BloqueoExportDetalle`,
  `CronogramaDocumentoResource`, `BloqueoExportResponse`): **DONE en el
  commit preexistente `5673615` — preservar intacto**.
- Emisión de eventos D-13 desde esos servicios: **MISSING**.

### Archivos candidatos

| Acción | Archivo | Condición |
|---|---|---|
| Modificar | `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | Emitir `presupuesto.version_creada` / `presupuesto.version_activada`. |
| Modificar | `src/main/java/ec/uce/propuestas/cronograma/service/CronogramaService.java` | Emitir `cronograma.editado` con las **6 operaciones canónicas**: `configurar`, `programar.reemplazar_avances`, `programar.distribuir_uniforme`, `programar.mover_segmento`, `programar.redimensionar_segmento`, `revisar`. |
| Modificar | `src/main/java/ec/uce/propuestas/cronograma/service/VistasCronogramaService.java` | Emitir `cronograma.editado` con `operacion=revisar` cuando `marcarRevisado`. |
| Reusar | `src/main/java/ec/uce/propuestas/cronograma/resource/CronogramaDocumentoResource.java` (Plan 031) | Sin reescritura; 039 instrumenta `documento.exportado` con seam 033. |
| Reusar | `src/main/java/ec/uce/propuestas/documento/resource/DocumentoResource.java` | Sin reescritura; 039 instrumenta `documento.exportado` para ET DOCX. |
| Crear | `src/test/java/ec/uce/propuestas/presupuesto/service/VersionadoServiceEventIT.java` | Cubre `presupuesto.version_creada` / `presupuesto.version_activada`. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/service/CronogramaEventIT.java` | Cubre las 6 operaciones canónicas de `cronograma.editado`. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/service/VistasCronogramaEventIT.java` | Cubre `cronograma.editado` con `operacion=revisar`. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/resource/CronogramaDocumentoResourceEventIT.java` | Cubre `documento.exportado` para los formatos `XLSX\|PDF\|MSPDI`. |
| Crear | `src/test/java/ec/uce/propuestas/documento/resource/DocumentoResourceEventIT.java` | Cubre `documento.exportado` para `DOCX` (ET). |

### TCs

- Cobertura D-13 presupuesto/cronograma/documento/export preservando
  la transacción única y TOCTOU de Plan 031.
- **6 operaciones canónicas** de `cronograma.editado` cerradas.
- **4 formatos** de `documento.exportado`: `XLSX`, `PDF`, `MSPDI`,
  `DOCX`.

## 8. Plan 040 — Integración del panel y piloto SUS

**Prioridad:** 8 (último).
**Proceso / historia:** cierre I-11 + piloto SUS 1–2 (protocolo
`quality/02 §5`).
**Dependencias previas:** 033–039 cerrados.

### Capacidades actuales vs gap

- Bruno admin `12-admin/`: **MISSING**.
- `graphify update .`: **MISSING** desde Plan 031.
- Piloto SUS 1–2: **MISSING** (gate humano).

### Archivos candidatos

| Acción | Archivo | Condición |
|---|---|---|
| Crear | `api/bruno/12-admin/folder.bru` | Una sola colección `12-admin/` con `auth: inherit` y las **16 requests exactas** (P-38/P-39/P-40/P-41/P-42). |
| Crear | `api/bruno/12-admin/*.bru` (16 archivos) | Requests del bloque I-11. |
| Modificar | `api/bruno/README.md` | Tabla de todas las colecciones y guía de ejecución de `12-admin/`. |
| Modificar | `api/bruno/environments/dev.bru` | Variables runtime del bloque (sin secretos). |
| Modificar | `docs/modulos/panel-admin/00-acta-reconciliacion.md` (sin cambios de fondo) | Confirmación de cobertura al cierre. |
| Modificar | `plans/panel-admin/040-integracion-panel-piloto-sus.md` | Cierre con evidencia humana pendiente (no fabricada). |
| Modificar | `docs/modulos/05-presupuesto/00.md`, `docs/modulos/06-cronograma/00.md`, `docs/00-ESTADO-ACTUAL.md`, `docs/modulos/README.md` | Sincronización documental de cierre. |

### TCs

- 16 requests exactas en `12-admin/`.
- Cobertura del catálogo cerrado D-13: enum tiene 26 verbatim;
  cobertura runtime solo de productores efectivamente canonicados;
  `proyecto.duplicado` declarado "no producer yet".
- Piloto SUS 1–2 con cita Brooke (1996) — artefactos, comandos y
  plantilla; **no** se fabrica ejecución ni puntaje.

## 9. Resumen de prioridades y dependencias (DAG)

```
033 (P-42 base; UN solo seam nuevo)
 │
 ├──► 034 (P-38; matriz self/last/email/first SUPER_ADMIN dif. I-12)
 ├──► 035 (P-39; insumo-en-uso con RED-first; bases D-11 ya conformes)
 ├──► 036 (P-40; plantillas SISTEMA server-authored)
 └──► 037 (P-41; DTO ParametrosSistemaResponse; valor_referencia)
       │
       ▼
      038 (D-13 auth/usuario/proyecto/insumo/base/APU; sin seam P-09)
       │
       ▼
      039 (D-13 presupuesto/cronograma/documento/export preservando Plan 031)
       │
       ▼
      040 (Bruno 12-admin 16 + graphify + piloto SUS 1–2 Brooke 1996)
```

033 → 040 son **estrictamente secuenciales**. 034–037 pueden
reordenarse entre sí (emisores independientes que comparten el seam
de 033).

---

**Inventario firmado al cierre de Plan 032 el 2026-09-07 y actualizado al
cierre de Plan 033 el 2026-09-08. La siguiente tarea es Plan 034; 034–040
permanecen pendientes según el DAG. No se creó commit en este cierre.**