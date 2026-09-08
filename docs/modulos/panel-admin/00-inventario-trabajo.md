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

- **Estado actual** (corte 2026-09-08, con 032–035 cerrados):
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
- **Evidencia medida:** 033–035 registran sus conteos exactos; el inventario no
  predice resultados para 036–040.

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
**Estado:** **DONE (2026-09-08)** — implementación completa, sin commit.
**Dependencias previas:** 033 cerrado.

### Capacidades actuales vs gap

- `POST /auth/aceptar-invitacion` + `TipoToken.INVITACION` + TTL 72 h:
  **DONE / PARITY** (`AuthService.aceptarInvitacion`,
  `TokenService`, `app.auth.invitacion-ttl: PT72H`).
- `GET/POST/PUT /admin/usuarios[/{id}]` + desactivar/reactivar/delete:
  **DONE / PARITY**.
- DELETE con proyectos propios → 409 `usuario-con-proyectos-impedido`:
  **DONE / PARITY** (test focal FK RESTRICT `proyecto_usuario_id_fkey`).

### Archivos concretos entregados

| Acción | Archivo | Condición |
|---|---|---|
| Creado | `src/main/java/ec/uce/propuestas/usuario/admin/UsuarioAdminService.java` | `invitar`, `reactivar`, `desactivar`, `eliminar`, `listar`, `contar`, `buscarPorPublicId`. Implementa el contrato exacto del acta 032 D-04 (decisión 31): 32 bytes `SecureRandom` → Base64URL sin padding → bcrypt único vía `PasswordService`; descarte inmediato del `byte[]` y del `String` portador; token independiente 72 h vía `TokenService`; sin helper `RandomUtil`. FK mapping en `eliminar` con `esViolacionForeignKey(...)` (recorre la cadena de causas buscando SQLSTATE `23503` y mensajes `violates foreign key constraint` / `fk_proyecto_usuario`). Emisión D-13 (`usuario.invitado`/`usuario.desactivado`/`usuario.activado`) dentro de la misma `@Transactional` exterior. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/admin/UsuarioAdminResource.java` | `GET /admin/usuarios` (listado paginado `q` + `activo` + `page` + `size`), `GET /admin/usuarios/{id}`, `POST /admin/usuarios` (invitación), `PUT /admin/usuarios/{id}` (edición sin email), `POST /admin/usuarios/{id}/desactivar`, `POST /admin/usuarios/{id}/reactivar`, `DELETE /admin/usuarios/{id}`. `@Path("/admin/usuarios")` + `@RolesAllowed("SUPER_ADMIN")` a nivel de clase. `UuidV7.parse` en frontera. Forma canónica `Page<T>`. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioAdminResponse.java` | Record con `id` UUIDv7, `nombre`, `email`, `rol`, `activo`, `emailVerificado`, `fechaCreacion` Instant; mapper estático `from(Usuario)`. Sin `passwordHash`, sin `tokenHash`, sin `invitacionExpiraEn`. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioInvitarRequest.java` | `@NotBlank @Size(max=200) String nombre`, `@NotBlank @Email @Size(max=320) String email`, `@NotNull Rol rol`. |
| Creado | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioAdminEditarRequest.java` | `@NotNull @Size(max=200) String nombre`, `@NotNull Rol rol`, `@NotNull Boolean activo` (sin `email` — decisión 6 del acta 032). |
| Modificado | `src/main/java/ec/uce/propuestas/usuario/UsuarioRepository.java` | `findByPublicId(UUID)` (admin sin owner-scope), `listar(String q, Boolean activo, int page, int size)` con `ILIKE` escapado (`!` como escape character para evitar ambigüedad en HQL/JPQL) y orden `createdAt desc, id asc` (estable), `contar(...)`, escape seguro de `%`/`_` en el texto del filtro. |
| Creado | `src/test/java/ec/uce/propuestas/usuario/admin/UsuarioAdminResourceIT.java` | `@QuarkusTest` con **18 tests**: TC-P38-01 invitación/aceptación/duplicado/validación; TC-P38-02 desactivar/login-403/reactivar/login-200 + uuid-inválido/inexistente; PUT edición sin email; TC-P38-03 DELETE con proyectos → 409 `usuario-con-proyectos-impedido` (test focal FK) + DELETE sin proyectos → 204; listado paginado con `q` y `activo`; orden estable por `fechaCreacion DESC` con secundario `id ASC`; autorización `USUARIO` → 403. |
| Creado | `src/test/java/ec/uce/propuestas/usuario/admin/UsuarioAdminSinContrasenaTemporalTest.java` | Red de seguridad `STOP-034-CONTRASENA-TEMPORAL`: 10 invitaciones, inspecciona todos los DTOs y `RecordingEnviadorCorreo`, asserta 0 matches de regex `(?i).*(password|contrase|clave|temporal|randombytes|temp_pwd|tempassword).*`, y verifica que el token de invitación es exactamente Base64URL de 32 bytes sin padding (43 chars). |
| Creado | `src/test/java/ec/uce/propuestas/usuario/admin/LogActividadEmisionUsuarioTest.java` | **6 tests**: emisión de `usuario.invitado` con `detalle.tokenExpiraEn` ISO-8601 y `entidadId` UUIDv7; emisión de `usuario.desactivado` con `detalle.origen=admin`; emisión de `usuario.activado` con `detalle.origen=admin`; invariante de idempotencia (rollback exterior borra el evento); email duplicado NO emite evento (rechazo); `detallesEsperados()` enum respeta claves canónicas. |
| Reusado | `LogActividadService.emitir(...)` (033) | Sin reescritura; consumido vía seam `MANDATORY` dentro de la misma `@Transactional`. |
| Reusado | `PasswordService` (existente, bcrypt con parámetros vigentes) | Sin reescritura. |
| Reusado | `TokenService.issueOneTimeToken(...)` (existente, SHA-256, TTL `app.auth.invitacion-ttl: PT72H`) | Sin reescritura. |
| Reusado | `EnviadorCorreo.enviarInvitacion(...)` (existente, con `RecordingEnviadorCorreo` para tests) | Sin reescritura. |

### Evidencia de cierre

- Admin focal: `UsuarioAdminResourceIT` **18/18**, 0 failures/errors/skips.
- Admin focal: `LogActividadEmisionUsuarioTest` **6/6**, 0 failures/errors/skips.
- Admin focal: `UsuarioAdminSinContrasenaTemporalTest` **1/1**, 0 failures/errors/skips.
- Regresión `ec.uce.propuestas.usuario.*` completa: **verde, 0 failures/errors/skips** (incluye 033 audit, auth, perfil, registro, login, logout, reset, invitación).
- Spotless (`./gradlew spotlessCheck`): **PASS**.
- `build -x test`: **PASS**.
- `git diff --check`: **limpio** (cero líneas con whitespace-only issues).
- Ninguna migración nueva; `motor/`, `recalculo/`, `auth/`, V001–V010 intactos.
- `grep -RInE 'temporalPassword|contrasenaTemporal|passwordTemporal|tempPassword' src/main/java/ec/uce/propuestas/`: **0 matches** (`STOP-034-CONTRASENA-TEMPORAL` cerrado).
- Sin helper `RandomUtil`; el `SecureRandom` canónico de la JVM es la única fuente aleatoria (`STOP-034-AYUDA-ALEATORIA` cerrado).
- Suite completa autoritativa con `test-port=0`: **698 tests totales** = **695 verdes** + **2 rojos aceptados** (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — residuales Plan 014, no se reabre el motor) + **1 skipped** (GM-24 `@Disabled` por fixture EMELNORTE upstream) + **0 errors**.
- `graphify update .` ejecutado: grafo actualizado.

### TCs cubiertos

- **TC-P38-01:** invitación 72 h sin contraseña temporal (`POST /admin/usuarios` → 201; `RecordingEnviadorCorreo` capturó el correo; `passwordHash` inutilizable bcrypt válido, NO plano; `emailVerificado=false`; `activo=true`); `POST /auth/aceptar-invitacion` reescribe `passwordHash` real y `emailVerificado=true`; login posterior funciona; email duplicado → 409 `email-ya-registrado`; validaciones (nombre/email/rol) → 400.
- **TC-P38-02:** desactivar → login siguiente 403; reactivar → login 200; uuid inválido → 400; uuid inexistente → 404.
- **TC-P38-03:** DELETE con proyectos propios → 409 `usuario-con-proyectos-impedido` (test focal FK RESTRICT `proyecto_usuario_id_fkey`); proyecto persiste; DELETE sin proyectos → 204; uuid inválido/inexistente → 400/404.
- Listado `GET`: paginación canónica `items,total,page,size,totalPaginas`; filtros `q` (ILIKE escapado), `activo` (Boolean); orden estable.
- Emisión D-13: `usuario.invitado` con `detalle.tokenExpiraEn` ISO-8601; `usuario.desactivado`/`usuario.activado` con `detalle.origen=admin`; rollback exterior borra evento; email duplicado NO emite.
- USUARIO 403: cualquier endpoint admin responde 403 sin pistas.
- Sin contraseña temporal: 0 matches regex en DTOs ni en `EnviadorCorreo`.

### Deferidos a I-12 (no incluidos en 034)

- Self-delete (`usuario-self-delete-impedido`).
- Last-active-SUPER_ADMIN (`ultimo-super-admin-impedido`).
- Cambio de email admin (`PUT /admin/usuarios/{id}` para email).
- Primer SUPER_ADMIN bootstrap.

## 3. Plan 035 — Bases centrales — cierre (P-39) — DONE / PARITY

**Cierre:** DONE con verificación focal y amplia (2026-09-08), sin commit.
**Prioridad histórica:** 3.
**Proceso / historia:** P-39 / US-36 / TC-P39-01..03.
**Dependencias previas:** 033 cerrado.

### Capacidades actuales vs gap

- `AdminBaseCentralResource` (P-39): **DONE / PARITY**.
- Listado admin con `Page<T>`, defaults `page=0`/`size=25`, máximo 200 y
  validación de parámetros: **DONE / PARITY**.
- DELETE base activa → 409 `base-no-archivada`: **DONE / PARITY**.
- DELETE insumo con referencia real en `apu_detalle` → 409
  `insumo-en-uso`: **DONE / PARITY**.
- Emisión `admin.base_editada` para ocho mutaciones exitosas, sin emisión en
  GET, dry-run ni rechazos: **DONE / PARITY**.
- Reporte por fila P-39: **DONE / PARITY** en
  [`035-auditoria-p39.md`](./035-auditoria-p39.md).

### Evidencia de cierre

- RED focal contra baseline: **41 tests**, **18 failures**, **0 errors** y
  **0 skips**; 9 fallos de auditoría y 9 del recurso por forma `List`,
  paginación/parámetros inválidos y DELETE referenciado.
- GREEN fresco:
  `./gradlew test --tests 'ec.uce.propuestas.insumo.resource.AdminBaseCentral*IT' -Dquarkus.http.test-port=0 --console=plain --rerun-tasks`
  → **41/41 pass**: `AdminBaseCentralResourceIT` 29 y
  `AdminBaseCentralLogAuditoriaIT` 12.
- Regresión dirigida fresca `ec.uce.propuestas.insumo.*`: **74/74 pass**, 0 failures/errors/skips.
- El primer `spotlessCheck` detectó solo cuatro archivos Java de Plan 035; `spotlessApply` los normalizó y el check posterior pasó como parte de `build -x test`.
- `build -x test`: **PASS**.
- Suite completa fresca: **715 = 712 pass + 2 failures aceptados** (GM-19/GM-20) **+ 1 skipped** (GM-24), 0 errors.
- `git diff --check` limpio; sin cambios en migraciones, `motor/` ni `recalculo/`.
- `graphify update .` finalizado: 4.453 nodos, 13.555 aristas y 182 comunidades.
- El fixture `apu_detalle → CENTRAL` cubre defensivamente corrupción o datos
  heredados; el flujo normal A9 copia a PROYECTO.

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
cierre completo de Plan 035 el 2026-09-08. La siguiente tarea es Plan 036;
036–040 permanecen pendientes según el DAG. No se creó commit ni se reclama
Graphify final en este cierre.**