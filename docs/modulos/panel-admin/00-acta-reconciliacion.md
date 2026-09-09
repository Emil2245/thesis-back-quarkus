# 00 — Acta de reconciliación del panel Super-Admin (I-11, gate de Plan 032)

> **Tipo:** gate documental (sin código).
> **Iteración:** I-11 (semanas 21–22).
> **Plan autoritativo:** [`../../../plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`](../../../plans/panel-admin/032-sincronizar-contrato-inventario-admin.md).
> **Fecha de firma:** 2026-09-07.
> **Firmado por:** orquestador de I-11 (delegación de Plan 032) + revisión del responsable del panel.
> **Acompañante operativo:** [`00-inventario-trabajo.md`](./00-inventario-trabajo.md) (mapa priorizado `P-xx → plan → TC → archivos` para 033–040).
>
> **Verificación:** este acta **no** ejecuta `./gradlew` ni corre suites.
> El Plan 031 ya está contenido en el commit preexistente `5673615`; Plan
> 032 no crea commits. Su cierre verifica únicamente documentos y conserva
> intacta la implementación heredada.

## 1. Resultado en una línea

Plan 032 firma su acta con **21 decisiones congeladas** (20 originales + adenda firmada D-21 sobre `entidad_public_id`), **15 STOP conditions cerradas o diferidas explícitamente**, el catálogo D-13 verbatim (26 eventos) y la matriz canónica `evento → claves de detalle`; el acta **corrige el drift** en `thesis-docs/plan/architecture/07-api-contract.md §1` (forma canónica `Page<T>` con `items,total,page,size,totalPaginas`, default `size=25`, tope `size<=200`) y `§9` (alineación de las tablas P-38/P-39/P-40/P-41). La divergencia **DELETE base central activa** se **resuelve en este acta**: la implementación actual ya cumple el contrato canónico (409 `base-no-archivada` ratificado). La divergencia **DELETE insumo central** **no se afirma resuelta**: la implementación actual expone el stub `conteoUsosApu() = 0L` (la rama 409 `insumo-en-uso` nunca se ejecuta hoy; la rama 400 `validacion` cubre el caso de conteo 0L) y queda cerrada por **035 con RED-first** (test rojo previo que reproduce 400 → mapeo a 409). La superficie self-delete / last-admin / cambio de email / primer SUPER_ADMIN **se difiere a I-12** por decisión del usuario.

## 2. Decisiones locked (verbatim del plan 032)

> Las decisiones se numeran según el plan. Cada una cita la evidencia en
> `src/main/java/...` o `src/main/resources/db/migration/...` encontrada por la
> auditoría, sin inventar firmas.

### D-01 · Identidad pública admin (UUIDv7)

- Todas las entidades navegables exponen **UUIDv7** en respuestas admin
  (`Usuario.publicId`, `BaseInsumos.publicId`, `PlantillaApu.publicId`,
  `Insumo.publicId`, `Apu.publicId`, `Presupuesto.publicId`,
  `Cronograma.publicId`, `Proyecto.publicId`).
- **`LogActividadResponse.id`** y **`LogActividadResponse.usuarioId`** son
  UUIDv7 (cambio de canon: `log_actividad.public_id` UUIDv7 introducido en
  033).
- **`LogActividadResponse.entidadId`** es **UUIDv7 nullable** (top-level;
  distinto de las claves de detalle: nunca se duplica como clave
  dentro de `detalle` salvo que la matriz canónica lo autorice). Mapea
  **exclusivamente** desde la columna nueva `log_actividad.entidad_public_id`
  (UUIDv7 nullable introducida por la migración aditiva de 033; ver
  D-21 más abajo). **No** se mapea desde la columna legacy
  `log_actividad.entidad_id BIGINT` (V001 §2.15), que permanece
  inalterada como rastro histórico y nunca cruza REST.
- PK/FK internas siguen siendo `BIGINT`; el BIGINT **nunca** cruza REST en
  respuestas admin (excepto `count`, `bytes`, enteros de paginación).
- 033 crea **una migración aditiva** con el siguiente número disponible.
  El nombre preferido del archivo es **neutral** y describe el alcance
  completo: `V???__log_actividad_identidad_publica.sql`
  (no `V???__log_actividad_public_id.sql`, porque la migración cubre
  dos columnas: `public_id` y `entidad_public_id`). Highest migration al
  corte de la firma: **V009** (`V009__cronograma_persistencia.sql`); por
  tanto la próxima libre es **V010**, **sin** pre-asignar ciegamente.
  Si al ejecutar 033 ya existe V010/V011, el nombre exacto se ajusta
  en ese momento — **nunca** se reescriben V001–V009.
- `usuarioNombre` y `fecha` (Instant canónico) **se conservan** donde el
  canon los requiera; **no** se reemplaza `created_at` / `createdAt`.
- **No PII** aplica **solo** a `detalle` JSONB y a secretos; los campos
  admin (`email`, `nombre`) siguen siendo accesibles al SUPER_ADMIN.

**Evidencia auditada** (sin tocar nada):
- `src/main/java/ec/uce/propuestas/common/UuidV7.java` (parser canónico de frontera).
- `src/main/java/ec/uce/propuestas/common/dto/Page.java` (`items,total,page,size,totalPaginas`).
- PK `BIGINT` confirmada en `src/main/resources/db/migration/V001__baseline.sql` (§2.x por entidad).

### D-02 · `log_actividad.detalle` JSONB (RNF-08, TC-P42-02)

- Solo claves canónicas en **español neutro**.
- **Nunca** correos / `email` / `emailDestino`, IPs / `ipOrigen`, nombres
  de usuarios o proyectos, ni texto libre de formularios.
- **Nunca** `passwordHash`, JWT, tokens, hashes de invitación, ni el
  contenido de `detalle` / `especificacion_tecnica` de APU ni
  `plantilla_apu.snapshot_secciones`.
- Identificadores públicos de entidad prefieren vivir en la columna
  top-level `entidadId` (con `entidad` top-level); las claves de detalle
  solo conservan identificadores cuando se requieren **dos o más**
  entidades distintas en el mismo evento o cuando la clave identifica
  un recurso **auxiliar** distinto del afectado.
- `LogActividadDetalleValidator` rechaza el evento desconocido con
  `IllegalArgumentException` y **tanto** la clave de detalle no
  permitida **como** el valor acotado fuera del conjunto cerrado
  (p. ej. `detalle.operacion` fuera de los 8 valores canónicos
  de `admin.base_editada`, `detalle.formato` fuera de
  `XLSX|PDF|MSPDI|DOCX`, `detalle.tipo` fuera de
  `EQUIPO|MANO_OBRA|MATERIAL|TRANSPORTE`) con `IllegalStateException`;
  la transacción exterior hace rollback y **no** se persiste el
  evento. **No** se devuelve 400 al cliente: la clave y el valor
  son siempre server-authored.
- El filtro `evento=` de `GET /admin/logs` **no** aplica este validador
  (es parámetro de query seguro, no se parsea contra el enum). Acepta
  cadenas seguras, incluidos los **4 nombres legacy** V004 (decisión 17)
  para mantener visible el historial.

#### Matriz canónica `evento → claves de detalle` (congelada)

| Evento | Claves `detalle` permitidas | `entidadId` top-level |
|---|---|---|
| `auth.login` | `{ "resultado": "ok" }` | `null` |
| `auth.logout` | `{ "resultado": "ok" }` | `null` |
| `auth.registro` | `{ "usuarioId": <UUIDv7> }` | `null` |
| `auth.password_cambiada` | `{ "origen": "perfil\|reset" }` | `null` |
| `usuario.invitado` | `{ "tokenExpiraEn": "<ISO-8601>" }` | UUIDv7 del `Usuario` invitado |
| `usuario.activado` (admin) | `{ "origen": "admin" }` | UUIDv7 del `Usuario` activado |
| `usuario.activado` (invitación) | `{ "origen": "invitacion" }` | UUIDv7 del `Usuario` activado |
| `usuario.desactivado` | `{ "origen": "admin" }` | UUIDv7 del `Usuario` desactivado |
| `proyecto.creado` | `{}` | UUIDv7 del `Proyecto` |
| `proyecto.editado` | `{}` | UUIDv7 del `Proyecto` |
| `proyecto.eliminado` | `{}` | UUIDv7 del `Proyecto` |
| `proyecto.duplicado` | `{ "proyectoOrigenId": <UUIDv7>, "proyectoDuplicadoId": <UUIDv7> }` | UUIDv7 del proyecto duplicado (**no producer yet — ver STOP-032-P09-DUPLICAR**) |
| `insumo.creado` | `{ "codigoInsumo": "<texto>", "tipo": "EQUIPO\|MANO_OBRA\|MATERIAL\|TRANSPORTE" }` | UUIDv7 del `Insumo` |
| `insumo.editado` | `{}` | UUIDv7 del `Insumo` |
| `insumo.eliminado` | `{}` | UUIDv7 del `Insumo` |
| `insumos.import_csv` | `{ "creados": <int>, "actualizados": <int>, "errores": <int> }` | UUIDv7 de la `BaseInsumos` destino |
| `base.copiada_a_proyecto` | `{ "baseOrigenId": <UUIDv7>, "proyectoDestinoId": <UUIDv7>, "cantidadInsumos": <int>, "cantidadOmitidos": <int> }` | UUIDv7 de la `BaseInsumos` **destino** del tipo PROYECTO (server-authored; el inventario copiado vive en la `BaseInsumos` PROYECTO resultante) |
| `apu.creado` | `{}` | UUIDv7 del `Apu` |
| `apu.editado` | `{}` | UUIDv7 del `Apu` |
| `apu.eliminado` | `{}` | UUIDv7 del `Apu` |
| `presupuesto.version_creada` | `{ "presupuestoOrigenId": <UUIDv7>, "versionNueva": <int> }` | UUIDv7 del `Presupuesto` nuevo |
| `presupuesto.version_activada` | `{ "version": <int>, "presupuestoPreviamenteVigenteId": <UUIDv7>\|null }` | UUIDv7 del `Presupuesto` activado |
| `cronograma.editado` | `{ "operacion": "configurar\|programar.reemplazar_avances\|programar.distribuir_uniforme\|programar.mover_segmento\|programar.redimensionar_segmento\|revisar" }` | UUIDv7 del `Cronograma` |
| `documento.exportado` | `{ "formato": "XLSX\|PDF\|MSPDI\|DOCX", "bytes": <long>, "stale": <bool> }` | UUIDv7 del `Presupuesto` afectado o `null` cuando no hay presupuesto asociado |
| `admin.base_editada` | `{ "operacion": "crear\|renombrar\|archivar\|borrar\|importar\|crearInsumo\|editarInsumo\|borrarInsumo", "cantidadInsumos": <int> }` | UUIDv7 de la `BaseInsumos` central |
| `admin.plantilla_editada` | `{ "operacion": "crear\|editar\|borrar", "tipo": "SISTEMA" }` | UUIDv7 de la `PlantillaApu` |
| `admin.parametros_editados` | Para `PUT /proyectos/parametros-sistema`: `{ "operacion": "defaults.update", "camposModificados": ["iva"] }` (ejemplo concreto; el arreglo admite únicamente nombres canónicos realmente modificados). Para `PUT/DELETE /admin/valores-referencia/{clave}`: `{ "operacion": "valor_referencia.insert\|valor_referencia.update\|valor_referencia.delete", "clave": "SBU" }`. Claves permitidas exactas: `operacion`, `camposModificados`, `clave`. **Sin** elipsis, **sin** claves dinámicas top-level, **sin** valores `previa`/`nueva`, **sin** PII. | `null` en ambos casos; `entidad` distingue `parametros_sistema` de `valor_referencia` |

> **Distinción crítica:** `entidadId` top-level es **único por evento**;
> las claves de detalle **no** deben duplicar `entidadId` salvo cuando
> dos entidades distintas participan (p. ej. origen + destino en
> `proyecto.duplicado` o `base.copiada_a_proyecto`) o cuando la clave
> es auxiliar. En `admin.parametros_editados`, `entidadId=null` en ambos
> casos porque ni `parametros_sistema` ni `valor_referencia` exponen UUIDv7;
> `entidad` distingue el agregado y `detalle.clave` identifica el valor de
> referencia cuando aplica.

### D-03 · Atomicidad (sin ghost events)

- Solo operaciones **exitosas** emiten; las rechazadas (401/403/404/409)
  **no** producen fila `log_actividad`.
- `LogActividadService.emitir(...)` se anota con
  `@Transactional(TxType.MANDATORY)` y un caller sin tx exterior recibe
  la excepción estándar `jakarta.transaction.TransactionalException` del
  interceptor Jakarta/Quarkus, sin escritura alguna (test focal). No se añade
  una fachada artificial para convertir el tipo.
- **No existe** `emitirFailure`, `REQUIRES_NEW`, ni persistencia para
  operaciones fallidas.
- **Corrección al plan:** el plan 032 lista "caso conocido:
  `AuthService.login`" como servicio público que no abría transacción.
  La auditoría **verifica contra código** y encuentra que
  `AuthService.login` **ya está** anotada con `@Transactional`
  (`src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java`
  línea 108). Lo mismo aplica a `AuthService.aceptarInvitacion`
  (línea 206) y al resto de operaciones del servicio. **No se
  requiere ajuste** por parte de 038; el plan 032 transcribía un
  supuesto que el código ya satisface. 038 hereda esta corrección
  sin reabrir el acta.

### D-04 · Invitación (D-11) — estado inicial y hash inutilizable

`POST /admin/usuarios` crea el `Usuario` con `passwordHash`
inutilizable, `activo=true`, `emailVerificado=false`. El acta congela
el contrato exacto del algoritmo de generación de contraseña temporal
inicial; este contrato se aplica **únicamente** al crear un nuevo
usuario invitado (no se regenera en cada login; no se regenera por
rotación; no se regenera por nada posterior):

1. **Origen aleatorio:** 32 bytes aleatorios tomados del `SecureRandom`
   canónico de la JVM (`java.security.SecureRandom`). **No** se
   introduce un helper `RandomUtil` nuevo; el `SecureRandom` ya
   presente en el módulo es la única fuente aleatoria.
2. **Codificación intermedia:** los 32 bytes se codifican como
   **Base64URL sin padding** (`Base64.getUrlEncoder().withoutPadding()`).
   El `String` resultante es **únicamente** un portador efímero hacia
   el hash y **no** se considera "la contraseña" ni se persiste, se
   loguea, se devuelve al caller, se envía por correo ni se imprime en
   trazas.
3. **Hash único:** ese `String` Base64URL se hashea **una sola vez**
   a través del `PasswordService` ya existente (bcrypt con los
   parámetros vigentes del servicio). El `byte[]` aleatorio y el
   `String` Base64URL se descartan **inmediatamente** después de
   obtener el hash; solo persiste el `passwordHash`.
4. **Regeneración:** el algoritmo se ejecuta **únicamente** cuando
   `UsuarioAdminService.invitar(...)` crea un nuevo usuario invitado.
   No se regenera en login, logout, cambio de contraseña, ni en
   ningún otro flujo posterior. La contraseña temporal jamás se
   imprime, se devuelve en respuestas REST, se loguea, se persiste
   fuera del `passwordHash` ni se envía en claro por el `mail port`.
5. **Token de invitación separado:** la aceptación de la invitación
   viaja por el `TokenService` ya existente (`SHA-256`, TTL
   `app.app.token.invitacion-ttl: PT72H`, `TipoToken.INVITACION`); el
   link público `/auth/aceptar-invitacion?token=…` recibe ese token,
   no la contraseña temporal. La contraseña temporal y el token de
   invitación son **dos secretos independientes**: cambiar uno no
   expone ni regenera el otro.
6. 034 implementa el contrato exactamente como el acta lo fija.

### D-05 · Self-delete / last-active-SUPER_ADMIN — **DIFERIDO a I-12**

- La matriz canónica de `DELETE /admin/usuarios/{id}` (self-delete,
  último SUPER_ADMIN activo) **no se prefija** en este plan ni en
  este acta.
- 034 **no codifica** la matriz como canónica. Cualquier superficie
  encontrada se documenta contra código y, si no hay canon, se deja
  abierta con `STOP-032-DELETE-USUARIO`.
- Decisión del usuario (2026-09-07): **diferir a I-12** por
  preferencia explícita — el piloto SUS 1–2 no requiere esta
  capacidad para correr.

### D-06 · Cambio de email admin (D-03) — **DIFERIDO a I-12**

- No existe flujo de cambio de email admin canónico en `main`.
- El cambio de email sigue siendo flujo P-04 (`PUT /perfil`) ya
  implementado.
- Decisión del usuario (2026-09-07): **diferir a I-12**.

### D-07 · Primer SUPER_ADMIN — **DIFERIDO a I-12**

- La estrategia de aprovisionamiento del primer SUPER_ADMIN (seed
  V002, bootstrap externo, registro inicial con flag) **no se
  prefija** en este plan ni en este acta.
- Decisión del usuario (2026-09-07): **diferir a I-12**.

### D-08 · Paginación admin — `Page<T>` canónico estable

- Los GET de listado admin (`/admin/usuarios`, `/admin/logs`,
  `/admin/bases-centrales?incluirArchivadas=`,
  `/admin/plantillas-apu`, `/admin/valores-referencia`) usan la
  `Page<T>` ya implementada en
  `src/main/java/ec/uce/propuestas/common/dto/Page.java` con la
  forma **estable JSON `items, total, page, size, totalPaginas`** y
  los valores por defecto **`size=25` y tope máximo `size=200`**.
  `size > 200` ⇒ 400 `validacion` con clave
  `tamano-pagina-invalido`.
- **No** se introduce cursor pagination en I-11.
- **Acción de acta:** corregir el drift en
  `thesis-docs/plan/architecture/07-api-contract.md §1`: el canon
  cita erróneamente la forma `contenido` / `totalElementos`. La
  forma canónica vigente es `items, total, page, size, totalPaginas`
  con default `size=25` y tope `size<=200`. La corrección se aplica
  en `../thesis-docs/plan/architecture/07-api-contract.md §1`
  (responsabilidad del orquestador, no del presente pase). **No** se
  renombra la constante Java `Page.java`.

### D-09 · Plantillas SISTEMA (P-40)

- `POST /admin/plantillas-apu` acepta `desdeApuId` (UUIDv7 de un APU
  existente) y `nombre`; el snapshot lo construye el backend reusando
  `SnapshotApuMapper` (price-free).
- `tipo=SISTEMA` y `usuario_id=NULL` los fija el servidor.
- **No** se acepta JSONB del cliente.
- El límite de `descripcionRubro` lo define la columna real
  (`plantilla_apu.descripcion_rubro`); **no** se inventa tope
  arbitrario. Si la columna es `TEXT`, no hay tope en la capa de
  servicio; si es `VARCHAR(N)`, el tope Bean es N (Plan 036 confirma
  la longitud real antes de fijar la validación).

### D-10 · `/proyectos/parametros-sistema` permanece canónica (P-41) — DTO decidido

- La ruta **no se mueve** a `/admin/parametros-sistema`.
- Lectura pública, escritura `@RolesAllowed("SUPER_ADMIN")`.
- **Decisión DTO (resuelta por este acta):**
  `GET /proyectos/parametros-sistema` **deja de exponer la entidad
  JPA** directamente. Plan 037 introduce el DTO
  **`ParametrosSistemaResponse`** con los 12 campos numéricos + 8
  rangos canónicos (lista exacta en el plan 037). El seam queda
  server-authored y no filtra la entidad JPA; el cliente consumidor
  (incluido el frontend) consume el DTO.
- `/admin/valores-referencia` es la **nueva** ruta para el CRUD del
  segundo tab de S-01 (informativo, textual; nunca entra al motor).
- Sin CAMICON: cero datos sembrados sin licencia/fuente explícitos.

### D-11 · Bases centrales (P-39) — divergencias canónicas **resueltas**

- **DELETE base central activa:** la implementación actual ya devuelve
  **409 `base-no-archivada`** (verificado en
  `src/main/java/ec/uce/propuestas/insumo/service/BaseInsumosService.java`
  línea 156:
  `throw ProblemaException.conflicto("base-no-archivada", ...)`).
  **El acta confirma este comportamiento como canónico**: 204 solo
  tras archivar; 409 cuando la base está activa. Plan 035 **no**
  modifica este camino; emite `admin.base_editada` solo en la
  mutación exitosa que sigue a archivar.
- **DELETE insumo en base central:** la implementación actual
  devuelve **400 `validacion`** cuando el conteo de usos es 0L
  (stub). El canon exige **409 `insumo-en-uso`** para el caso real de
  FK. **Decisión del acta:** el stub 0L actual significa que el
  branch 409 `insumo-en-uso` **nunca se ejecuta hoy**. Plan 035
  reemplaza el stub por una consulta real (FK en `apu_detalle` o
  equivalente) y mapea el rechazo a **409 `insumo-en-uso`**, todo
  **con RED-first** (test rojo previo que reproduce el
  comportamiento actual 400 antes del cambio). Plan 035 **no** usa
  marcas de paridad `✔`/`⚠`/`✗` fabricadas: solo hechos verificados
  con test rojo previo.
- **No se reabre** `AdminBaseCentralResource` ni `BaseInsumosService`
  más allá del seam descrito.

### D-12 · Solo eventos exitosos emiten

Refuerza D-03. El catálogo D-13 emite únicamente mutaciones
materiales con commit. Codificado como test focal en 033
(`emitir-sin-tx` + `rollback-borra-evento`) y verificado en 040
(cobertura completa, sin fabricar eventos).

### D-13 · P-42 fundación (033)

- `LogActividadService` vive en `ec.uce.propuestas.usuario.audit`
  (subpaquete nuevo del módulo `usuario`, **no** un módulo nuevo de
  primer nivel); el enum Java `EventoLogActividad` también.
- La entidad `LogActividad` se aloja en
  `ec.uce.propuestas.usuario.audit.entity` y su repositorio en
  `ec.uce.propuestas.usuario.audit.repository`.
- El recurso admin (`LogActividadResource`) vive en
  `ec.uce.propuestas.usuario.audit.resource` con ruta `/admin/logs`
  y `@RolesAllowed("SUPER_ADMIN")` a nivel de clase.
- Esta ubicación evita crear un módulo nuevo y respeta
  «`common/` solo para utilidades sin identidad de dominio».

### D-14 · Sin CAMICON

- En seed ni en `valor_referencia`. Cualquier valor informativo que
  requiera licencia/fuente explícita queda fuera del MVP.
- El seed `V004` ya existe y **no** se reabre.

### D-15 · Plan 031 preservado intacto

- Sus cambios pendientes (export MSPDI, `BloqueoExportDetalle`,
  `CronogramaDocumentoResource`, `BloqueoExportResponse`, etc.) **no**
  entran en I-11; 039 los reusa tal cual al instrumentar
  `documento.exportado`.

### D-16 · Drift canónico a corregir (entrega de 032)

- **Acción:** ediciones verificables en
  `thesis-docs/plan/architecture/07-api-contract.md §1` (forma
  canónica `Page<T>` con `items, total, page, size, totalPaginas`,
  default `size=25`, tope `size<=200`) y, donde proceda, en `§9`
  para alinear las tablas P-38/P-39/P-40/P-41 con las decisiones de
  este acta.
- Sin esta corrección, 033 queda STOPPED.
- **Responsable del drift:** el padre actualiza `../thesis-docs`;
  este pase **no** modifica `../thesis-docs` (solo deja registro
  verificable en este acta).

### D-17 · Filas legacy V004 (decisión histórica preservada)

- V004 siembra **19 filas** en `log_actividad`
  (`src/main/resources/db/migration/V004__seed_escenarios.sql`
  §11 — verificado: 19 tuplas entre las líneas 2718–2740 de la
  inserción única `INSERT INTO log_actividad (...) VALUES ...`).
- De esas 19, **6 filas fixture** usan los **4 nombres legacy** no
  canónicos:
  - `base.insumos.copiada` (×1)
  - `rubro.creado` (×2)
  - `cronograma.creado` (×2)
  - `presupuesto.vigente_marcado` (×1)
- Las 6 filas son **historial legacy**:
  - se conservan en BD sin editarlas;
  - son legibles y filtrables por `GET /admin/logs?evento=`
    (parámetro de query seguro que acepta cadenas, incluidos los 4
    nombres legacy);
  - **nunca** se emiten de nuevo;
  - **nunca** se admiten al enum `EventoLogActividad` runtime;
  - **se excluyen** de la cobertura exacta de 26 eventos del test
    040.
- 032 **no** autoriza borrado ni backfill; cualquier modificación
  futura requiere un acta humana explícita.
- Las restantes **13 filas** usan claves que ya entran al catálogo
  cerrado D-13: `auth.registro` (×2), `auth.login` (×2),
  `proyecto.creado` (×3), `presupuesto.version_creada` (×2),
  `apu.creado` (×2), `insumo.creado` (×1), `documento.exportado`
  (×1).
- **Cuatro seeds de `valor_referencia`** (V004 §2, líneas 22–26,
  auditadas y confirmadas):
  - `SBU` — `Salario Básico Unificado USD/mes` —
    `Ministerio del Trabajo 2023`.
  - `APORTE_PATRONAL` — `12.15` — `IESS 2023`.
  - `FAS` — `1.538` — `Factor de ajuste salarial (360/234)` —
    `v1.1 Anexo A`.
  - `HORAS_OPERACION_ANUAL` — `1800` — `Referencia h/año equipos` —
    `v1.1 Anexo A`.
- Este set queda fuera de la cobertura D-13 y se trata como dato
  informativo (decisión 14).

### D-18 · Catálogo D-13 cerrado — 26 eventos exactos

La constante Java `EventoLogActividad` enumera **estrictamente** los
26 eventos transcritos abajo. El enum **no admite** los 4 nombres
legacy V004.

```
auth.login
auth.logout
auth.registro
auth.password_cambiada
usuario.invitado
usuario.activado
usuario.desactivado
proyecto.creado
proyecto.editado
proyecto.eliminado
proyecto.duplicado
insumo.creado
insumo.editado
insumo.eliminado
insumos.import_csv
base.copiada_a_proyecto
apu.creado
apu.editado
apu.eliminado
presupuesto.version_creada
presupuesto.version_activada
cronograma.editado
documento.exportado
admin.base_editada
admin.plantilla_editada
admin.parametros_editados
```

(26 eventos; sin sufijos de versión, sin renombramientos, sin
abreviaturas.)

### D-19 · Piloto SUS (I-11) vs SUS n ≥ 5 (I-12)

- 040 entrega artefactos del piloto 1–2 (con cita Brooke, 1996);
- la medición poblacional es I-12.
- 040 **no fabrica** ejecución ni puntaje.

### D-20 · FK exception mapping — tests rojos previos

- Toda decisión que cierre brechas de mapeo de excepciones FK (p. ej.
  `DELETE insumo` con dependencias) requiere un test rojo previo
  que reproduzca la condición de error actual, **antes** de cualquier
  cambio de código.
- Sin este test, la implementación no cierra la brecha.
- Aplica a 035 (DELETE insumo central → 409 `insumo-en-uso`) y a 034
  (DELETE usuario con proyectos propios → 409
  `usuario-con-proyectos-impedido`).

### D-21 · Incompatibilidad `log_actividad.entidad_id` → identidad pública (decisión firmada)

Esta decisión **corrige y amplía** D-01 después de la firma inicial
(2026-09-07). La incompatibilidad detectada durante la fase de revisión
de planes: `LogActividadResponse.entidadId` debe ser UUIDv7 nullable
(D-01), pero la única columna que contiene la identidad de la entidad
afectada en V001 §2.15 es `log_actividad.entidad_id BIGINT` —
incompatible con un valor BIGINT para REST y, peor, inservible para
clientes admin porque las entidades afectadas exponen UUIDv7 y los
identificadores internos `BIGINT` se borran con la entidad.

**Decisión del usuario (firmada, ver §7 punto 6):** la migración
aditiva de 033 añade **dos** columnas nuevas a `log_actividad` (no
una), descritas a continuación. La columna legacy `entidad_id BIGINT`
se mantiene **exactamente como V001 §2.15 la define**, sin FK y sin
default; su semántica futura es **legacy-only**.

#### Columnas nuevas (033, una sola migración)

1. **`log_actividad.public_id UUID NOT NULL DEFAULT uuidv7()`** —
   la columna ya prevista por D-01. Índice único
   `ux_log_actividad_public_id`; trigger
   `trg_log_actividad_public_id_immutable` reusando
   `fn_assert_public_id_immutable()` (V001 §5).
2. **`log_actividad.entidad_public_id UUID NULL`** — la columna
   **nueva** firmada por esta acta. **Sin** `DEFAULT`, **sin** `NOT
   NULL`, **sin** `UNIQUE`, **sin** FK a ninguna tabla de entidad
   (los logs sobreviven al borrado de la entidad afectada; aplicar una
   FK reintroduciría la incompatibilidad que esta decisión resuelve).
   - **Server-authored**: la fija exclusivamente
     `LogActividadService.emitir(...)` al persistir cada fila; nunca la
     escribe un caller externo ni una migración `reseed`.
   - **Nullable por diseño**: las filas V004 ya existentes (19 filas,
     6 sobre los 4 nombres legacy) y cualquier fila histórica
     pre-033 quedan con `entidad_public_id IS NULL`. El DTO devuelve
     `entidadId: null` para esas filas; el cliente admin las
     identifica por `id` (UUIDv7 una vez emitidas por 033+), por
     `usuarioId` y por `fecha`, no por `entidadId`.
   - **Sin intento de backfill** desde `entidad_id BIGINT`: la
     conversión BIGINT→UUID es imposible (un BIGINT no es un UUIDv7 y
     no existe mapeo canónico); los registros históricos que necesiten
     identidad pública deben re-emitirse manualmente, decisión que
     este acta **no** prejuzga y que requeriría un acta humana futura.

#### Mapeo DTO (033)

`LogActividadResponse.entidadId` (UUIDv7 nullable) **mapea
exclusivamente** desde `log_actividad.entidad_public_id`. La columna
`log_actividad.entidad_id BIGINT` **nunca** se expone en REST — ni
siquiera como `@JsonIgnore` derivado — y ningún plan de I-11
reintroduce esa exposición. Tests focales verifican:

- fila nueva con `entidad_public_id` poblado → `entidadId`
  serializado con ese mismo UUIDv7;
- fila legacy V004 con `entidad_public_id IS NULL` → `entidadId`
  serializado como `null`;
- el DTO **no** contiene ningún campo derivado de `entidad_id`
  (`entidadIdLegacy`, `entidadIdInterno`, etc. están **prohibidos**).

#### Concurrencia con el canon ya escrito

D-21 **no** reabre D-01, D-11, D-13, D-18 ni el catálogo D-13. La
matriz canónica `evento → detalle` permanece idéntica; las claves
`proyectoOrigenId`, `proyectoDuplicadoId`, `baseOrigenId`,
`proyectoDestinoId`, `presupuestoOrigenId`,
`presupuestoPreviamenteVigenteId`, `clave` y el
resto siguen siendo **claves de `detalle` JSONB** en español neutro,
no se mueven a columnas top-level. **Esta acta no introduce una
segunda migración**: 033 crea una sola migración aditiva con dos
columnas nuevas en `log_actividad`. La cantidad de migraciones del
bloque I-11 no cambia.

#### Nombre del archivo de migración

El nombre preferido es **neutral** y describe el alcance completo:
`V???__log_actividad_identidad_publica.sql`. Se rechaza el antiguo
`V???__log_actividad_public_id.sql` (solo describía una columna y
queda obsoleto). El número `V???` se fija en el paso de ejecución
de 033 (siguiente libre tras V009, sin pre-asignar V010).

## 3. STOP conditions — 15 cierres explícitos

| STOP | Estado | Disposición y razón |
|---|---|---|
| `STOP-032-V010-NECESARIA` | **CLOSED — diferido a 033** | La columna `log_actividad.public_id` se crea en 033 con migración aditiva siguiente al último aplicado. Highest migration al cierre de 032: **V009** (`V009__cronograma_persistencia.sql`). 033 selecciona el siguiente libre en ejecución (`V???__log_actividad_identidad_publica.sql`, nombre neutral que describe el alcance completo de la migración); si V010 ya existe por planes posteriores, el nombre se ajusta **sin** reescribir V001–V009. |
| `STOP-032-LOG-ENTIDAD-ID-INCOMPATIBLE` (nuevo, post-firma) | **CLOSED — firmado** | La incompatibilidad `entidad_id BIGINT` vs `LogActividadResponse.entidadId` UUIDv7 se cierra con la decisión D-21 firmada en este acta: 033 añade **además** la columna `log_actividad.entidad_public_id UUID NULL` (sin FK, server-authored, sin DEFAULT) en la misma migración aditiva; `entidad_id BIGINT` permanece **legacy-only** y nunca cruza REST. `LogActividadResponse.entidadId` mapea exclusivamente desde `entidad_public_id`. No se intenta conversión BIGINT→UUID ni backfill de filas V004; los logs históricos devuelven `entidadId: null`. Plan 032 mantiene su estado **DONE (2026-09-07)**; esta corrección entra como adenda firmada (no reabre el acta, no introduce una segunda migración). |
| `STOP-032-CONTRADICCION-API` | **CLOSED** | No hay contradicciones adicionales a las resueltas por D-08 (drift `Page<T>`), D-10 (DTO `ParametrosSistemaResponse`), D-11 (DELETE base/insumo) y D-17 (legacy V004). Las tablas `§9` de P-38/P-39/P-40/P-41 se alinean en `../thesis-docs/plan/architecture/07-api-contract.md` (responsabilidad del padre). |
| `STOP-032-D-13-FUERA` | **CLOSED** | Ningún evento necesario para I-11 queda fuera del catálogo verbatim. Los 26 eventos cubren auth/usuario/proyecto/insumo/base/APU/presupuesto/cronograma/documento/admin. |
| `STOP-032-CAMICON` | **CLOSED** | El seed V004 ya existe y no se reabre (D-14); no se introduce CAMICON ni otro valor sin licencia/fuente explícita. |
| `STOP-032-DUPLICAR-PLAN015BIS` | **CLOSED** | La auditoría de Plan 015bis (DONE 2026-08-29) no detecta necesidad de reescritura. La superficie de `AdminBaseCentralResource` se reutiliza tal cual en 035; los gaps estrechos (D-11, mapping FK) se cierran con RED-first sin reescritura. |
| `STOP-032-FRONTEND-DEPENDIENTE` | **CLOSED — gate humano en 040** | 040 entrega artefactos del piloto SUS 1–2; la dependencia frontend queda documentada y no se fabrica puntaje. |
| `STOP-032-PLAN031-AJENO` | **CLOSED** | La implementación del Plan 031 está en el commit preexistente `5673615`; `git diff --name-only -- 'src/**' 'api/**'` al cierre de 032 queda vacío. Cualquier cambio runtime atribuible a 032 activa este STOP. |
| `STOP-032-DELETE-USUARIO` | **DEFERRED — I-12** | Self-delete / last-active-SUPER_ADMIN **no** se prefija en este acta ni en 034; se difiere a I-12 por decisión del usuario (D-05). |
| `STOP-032-EMAIL-ADMIN` | **DEFERRED — I-12** | Cambio de email admin **no** se prefija; se difiere a I-12 (D-06). |
| `STOP-032-FIRST-SUPERADMIN` | **DEFERRED — I-12** | Estrategia de aprovisionamiento del primer SUPER_ADMIN **no** se prefija; se difiere a I-12 (D-07). |
| `STOP-032-DTO-PARAMETROS` | **CLOSED** | El acta **selecciona el DTO** `ParametrosSistemaResponse` (D-10). Plan 037 lo implementa; el `GET /proyectos/parametros-sistema` deja de exponer la entidad JPA. |
| `STOP-032-DELETE-BASE-CENTRAL` | **CLOSED** | D-11 ratifica 204/404/409 `base-no-archivada`. Verificado contra `BaseInsumosService.java:156`. |
| `STOP-032-DELETE-INSUMO-CENTRAL` | **CLOSED — gate RED-first en 035** | D-11 ratifica 409 `insumo-en-uso`. Plan 035 reemplaza el stub `conteoUsosApu() = 0L` por consulta real con test rojo previo que reproduzca el 400 actual. |
| `STOP-032-P09-DUPLICAR` | **DEFERRED — "no producer yet"** | El canon cita `proyecto.duplicado` (P-09) como evento canónico D-13. La auditoría **confirma** que la implementación **no expone** `POST /proyectos/{id}/duplicar` ni un método `ProyectoService.duplicar(...)` (verificado por búsqueda exhaustiva en `src/main/java/ec/uce/propuestas/proyecto/`). Decisión histórica N02 §3 desaconseja clonar proyectos enteros. **Decisión del acta:** la opción (2) del STOP — **diferir el evento** declarando el canon P-09 como "no producer yet". El evento `proyecto.duplicado` se **conserva** en el enum `EventoLogActividad` y en la matriz de cobertura del test 040, pero ningún emisor runtime lo produce hasta que una acta humana posterior autorice la implementación del seam. 038 **no** implementa el seam; 040 refleja la cobertura como "no producer yet" (no se fabrica test skipped). |

> **Resumen del cierre de STOP:** **10 CLOSED**, **4 DEFERRED a I-12**
> (D-05, D-06, D-07, P-09) y **1 cerrado con gate RED-first en 035**
> (DELETE insumo central). Total: **15 STOP conditions** con
> disposición explícita.

## 4. Paridad endpoint ↔ recurso (P-38…P-42) vs `07-api-contract.md §9`

> La tabla contrasta el canon (`thesis-docs/plan/architecture/07-api-contract.md
> §9`) contra los recursos existentes en `src/main/java/...`. Las filas marcadas
> **GAP** se cierran en 033–039 con RED-first; las **PARITY** ya están
> implementadas y se reutilizan.

| Capacidad | Endpoint canon §9 | Estado actual | Gap exacto | Plan que cierra |
|---|---|---|---|---|
| P-38 Gestión de usuarios | `GET/POST/PUT/DELETE /admin/usuarios[/{id}]` + `/{id}/desactivar` + `/{id}/reactivar` | **MISSING** — no existe `AdminUsuarioResource` | Crear recurso + servicio; invitaciones 72 h reusando `TipoToken.INVITACION` ya implementado; `DELETE` con proyectos propios → 409 `usuario-con-proyectos-impedido` con test focal FK (D-20) | **034** |
| P-39 Bases centrales | `GET/POST/PUT/DELETE /admin/bases-centrales[/{id}]` + `/{id}/archivar` + `/{id}/insumos[/{iid}]` + `/{id}/insumos/import` | **PARITY** — `AdminBaseCentralResource` ya existe (`src/main/java/ec/uce/propuestas/insumo/resource/AdminBaseCentralResource.java`) | Solo mapping FK insumo-en-uso (D-11) + emisión `admin.base_editada` en operaciones exitosas (RED-first) | **035** |
| P-40 Plantillas SISTEMA | `GET/POST/PUT/DELETE /admin/plantillas-apu[/{id}]` | **MISSING** — solo existe el flujo `/plantillas-apu` (P-26) | Crear recurso admin reusando `SnapshotApuMapper`; `tipo=SISTEMA`, `usuario_id=NULL` server-authored; longitud `descripcionRubro` confirmada contra columna real | **036** |
| P-41 Parámetros del sistema | `GET/PUT /proyectos/parametros-sistema` | **PARITY parcial** — existe la ruta, pero `GET` devuelve la entidad JPA (`ProyectoResource.java:127` retorna `ParametrosSistema`) | DTO `ParametrosSistemaResponse` (D-10); ruta canónica conservada | **037** |
| P-41 Valores de referencia | `GET/PUT/DELETE /admin/valores-referencia[/{clave}]` | **MISSING** — tabla existe, no hay recurso | CRUD completo; `clave` única con `fuente` no blank; emisión `admin.parametros_editados` en operaciones exitosas | **037** |
| P-42 Logs de actividad | `GET /admin/logs?usuarioId=&evento=&desde=&hasta=&page=&size=` | **MISSING** — tabla existe, no hay recurso | Recurso + servicio `LogActividadService.emitir(MANDATORY)` + enum `EventoLogActividad` con 26 verbatim; matriz canónica `detallesEsperados()`; `LogActividadDetalleValidator`; **una** migración aditiva que añade `public_id UUID NOT NULL DEFAULT uuidv7()` + `entidad_public_id UUID NULL` (D-01 + D-21; nombre neutral `V???__log_actividad_identidad_publica.sql`); `LogActividadResponse.entidadId` mapea exclusivamente desde `entidad_public_id` (la columna legacy `entidad_id BIGINT` no cruza REST) | **033** + verificación de cobertura en **040** |

## 5. Tabla de emisores por capacidad (cobertura D-13)

| # | Evento | Plan | Servicio público | Estado del emisor |
|---|---|---|---|---|
| 1 | `auth.login` | 038 | `AuthService.login` | pendiente (no emite hoy) |
| 2 | `auth.logout` | 038 | `AuthService.logout` | pendiente |
| 3 | `auth.registro` | 038 | `AuthService.registrar` | pendiente |
| 4 | `auth.password_cambiada` | 038 | `AuthService.cambiarPassword(...)` (perfil) + `AuthService.restablecerPassword(...)` (reset) — mismo evento canónico, `detalle.origen` distingue | pendiente |
| 5 | `usuario.invitado` | 034 | `UsuarioAdminService.invitar` | nuevo |
| 6 | `usuario.activado` | 034 (admin) / 038 (invitación) | `UsuarioAdminService.reactivar` + `AuthService.aceptarInvitacion` | nuevo / pendiente |
| 7 | `usuario.desactivado` | 034 | `UsuarioAdminService.desactivar` | nuevo |
| 8 | `proyecto.creado` | 038 | `ProyectoService.crear` | pendiente |
| 9 | `proyecto.editado` | 038 | `ProyectoService.actualizar` | pendiente |
| 10 | `proyecto.eliminado` | 038 | `ProyectoService.eliminar` | pendiente |
| 11 | `proyecto.duplicado` | 038 | `ProyectoService.duplicar(...)` | **no producer yet** (STOP-032-P09-DUPLICAR) |
| 12 | `insumo.creado` | 038 | `InsumoCrudService.crear` (en base PROYECTO) | pendiente |
| 13 | `insumo.editado` | 038 | `InsumoCrudService.actualizar` | pendiente |
| 14 | `insumo.eliminado` | 038 | `InsumoCrudService.eliminar` | pendiente |
| 15 | `insumos.import_csv` | 038 | `ImportacionInsumoService.importarCsv(...)` | pendiente |
| 16 | `base.copiada_a_proyecto` | 038 | `CopiaBaseService.copiar(...)` | pendiente |
| 17 | `apu.creado` | 038 | `ApuCrudService.crear` | pendiente |
| 18 | `apu.editado` | 038 | `ApuCrudService.actualizar` | pendiente |
| 19 | `apu.eliminado` | 038 | `ApuCrudService.eliminar` | pendiente |
| 20 | `presupuesto.version_creada` | 039 | `VersionadoService.copiarVersion` | pendiente |
| 21 | `presupuesto.version_activada` | 039 | `VersionadoService.marcarVigente` | pendiente |
| 22 | `cronograma.editado` | 039 | `CronogramaService` (configurar, programar.*) + `VistasCronogramaService.marcarRevisado` | pendiente |
| 23 | `documento.exportado` | 039 | `CronogramaDocumentoResource.descargar` (Plan 031) + `DocumentoResource.exportarEspecificacionesTecnicas` (ET DOCX) | pendiente |
| 24 | `admin.base_editada` | 035 | `AdminBaseCentralResource` (operaciones Plan 015bis + emisión 035) | pendiente |
| 25 | `admin.plantilla_editada` | 036 | `PlantillaApuAdminService` | nuevo |
| 26 | `admin.parametros_editados` | 037 | `ParametrosProyectoService.actualizarSistema` + `ValorReferenciaAdminService` | nuevo |

Total: **26 eventos runtime**. Los **4 nombres legacy V004** NO entran al
enum (D-17).

## 6. Hechos numéricos verificables (sin totales de suite futura)

| Hecho | Valor | Fuente verificada |
|---|---|---|
| Highest migration | **V009** | `src/main/resources/db/migration/V009__cronograma_persistencia.sql` |
| Próxima migración libre | **V010** (selección final en ejecución 033) | D-01, D-21 |
| Nombre preferido de la migración aditiva 033 | **`V???__log_actividad_identidad_publica.sql`** (neutral; describe las dos columnas añadidas) | D-21 |
| Columnas añadidas por la migración aditiva 033 | **`log_actividad.public_id UUID NOT NULL DEFAULT uuidv7()`** (D-01) **y** **`log_actividad.entidad_public_id UUID NULL`** (D-21, sin FK, sin DEFAULT, server-authored) | D-01 + D-21 |
| `log_actividad.entidad_public_id` FK | **ninguna** (los logs sobreviven al borrado de la entidad afectada) | D-21 |
| `log_actividad.entidad_id` (legacy BIGINT) | preservada tal cual V001 §2.15; **nunca** se expone vía REST; ningún plan de I-11 la reintroduce | D-21 |
| `LogActividadResponse.entidadId` mapea desde | **`log_actividad.entidad_public_id`** exclusivamente (no desde `entidad_id` BIGINT) | D-21 |
| Filas V004 con `entidad_public_id IS NULL` | **19 / 19** (toda la población V004 queda con la columna nullable sin valor; DTO responde `entidadId: null`) | D-21 + V004 §11 |
| `log_actividad` filas V004 | **19** | `V004__seed_escenarios.sql` §11, líneas 2718–2740 |
| `log_actividad` filas legacy V004 | **6** (sobre 4 nombres no canónicos) | mismo §11, conteo auditado |
| Nombres legacy no canónicos | **4**: `base.insumos.copiada`, `rubro.creado`, `cronograma.creado`, `presupuesto.vigente_marcado` | mismo §11 |
| Seeds `valor_referencia` | **4**: `SBU`, `APORTE_PATRONAL`, `FAS`, `HORAS_OPERACION_ANUAL` | `V004__seed_escenarios.sql` líneas 22–26 |
| Eventos catálogo D-13 | **26** (D-18) | sección 2 D-18 del acta |
| `@Transactional` en `AuthService.login` | **sí** (línea 108) | `AuthService.java` |
| `@Transactional` en `AuthService.aceptarInvitacion` | **sí** (línea 206) | `AuthService.java` |
| Forma `Page<T>` | `items,total,page,size,totalPaginas` | `common/dto/Page.java` |
| Default `size` | **25** | `InsumoResource.java:89`, `ProyectoResource.java:82`, `PresupuestoApuResource.java:92` |
| Tope `size` | **200** (a confirmar en 033–040; 400 `validacion` `tamano-pagina-invalido` si excede) | D-08 |
| Stub `conteoUsosApu` | **`return 0L`** (`InsumoCrudService.java`) | D-11, Plan 035 |
| Mapping FK insumo-en-uso actual | **400 `validacion`** (lanzado en `InsumoCrudService.eliminar`) | D-11 |
| Mapping FK base-no-archivada actual | **409 `base-no-archivada`** (`BaseInsumosService.java:156`) | D-11 |
| `GET /proyectos/parametros-sistema` retorno actual | **entidad JPA** `ParametrosSistema` (`ProyectoResource.java:127`) | D-10 |
| Ruta `/admin/proyectos/{id}/duplicar` | **ausente** (búsqueda exhaustiva en `src/main/java/ec/uce/propuestas/proyecto/`) | D-19, STOP-032-P09-DUPLICAR |

> **Nota sobre totales de suite completa:** este acta **no predice**
> conteos de `./gradlew test` para 033–040. Cada plan enuncia comandos
> focales y la suite completa se reporta solo como medición opcional al
> cierre, sin números pre-fijados.

## 7. Decisiones del usuario registradas verbatim (2026-09-07)

1. **DELETE base central activa:** el comportamiento actual (409
   `base-no-archivada`, 204 tras archivar) es **canónico**. No se
   invierte.
2. **Plan 037 introduce `ParametrosSistemaResponse`**; el `GET
   /proyectos/parametros-sistema` deja de exponer la entidad JPA.
3. **Self-delete / last-active SUPER_ADMIN / cambio de email admin /
   primer SUPER_ADMIN bootstrap** se difieren a **I-12**. 034 no
   codifica estas matrices; el acta las deja explícitamente abiertas.
4. **P-09 duplicación de proyecto** permanece diferida según decisión
   histórica N02 §3. El evento `proyecto.duplicado` se mantiene en el
   enum D-13 (D-18) pero **no** tiene productor runtime
   (`STOP-032-P09-DUPLICAR`, opción 2). 038 no implementa seam; 040 no
   fabrica test skipped.
5. **DELETE insumo central** se cierra con **409 `insumo-en-uso`**,
   reemplazando el stub `conteoUsosApu() = 0L` y el mapping 400
   `validacion` actual. Plan 035 debe entregar RED-first (test rojo
   previo que reproduce 400 → luego mapeo a 409).
6. **`log_actividad.entidad_id BIGINT` vs `LogActividadResponse.entidadId`
   UUIDv7** (adenda firmada, ver D-21): la migración aditiva de 033
   añade **ambas** columnas — `public_id UUID NOT NULL DEFAULT
   uuidv7()` y `entidad_public_id UUID NULL` — en una sola migración
   con nombre neutral `V???__log_actividad_identidad_publica.sql`.
   La columna legacy `entidad_id BIGINT` permanece inalterada y
   **legacy-only**: nunca se expone vía REST, ningún plan de I-11
   la reintroduce. `LogActividadResponse.entidadId` mapea
   exclusivamente desde `entidad_public_id`. **No** se intenta
   conversión BIGINT→UUID ni backfill: los logs sobreviven al
   borrado de la entidad afectada, por lo que la nueva columna no
   lleva FK. Las filas históricas V004 (19 filas, 6 sobre los 4
   nombres legacy) quedan con `entidad_public_id IS NULL` y el DTO
   devuelve `entidadId: null` para esas filas. Esta adenda **no
   reabre** el acta 032 ni el catálogo D-13; se publica como D-21
   y se refleja en 033 antes de su ejecución.

## 8. Completion checklist del plan 032

- [x] La implementación heredada del Plan 031 está contenida en el commit
      preexistente `5673615`; Plan 032 no creó commits ni cambios en
      `src/**` o `api/**`.
- [x] Las 6 fuentes verbatim están releídas y citadas en este acta.
- [x] Las 21 decisiones locked (20 originales + adenda firmada D-21) están transcritas verbatim.
- [x] El catálogo D-13 verbatim (26 eventos) está transcrito.
- [x] Las 15 STOP conditions tienen estado explícito.
- [x] Inventario `P-xx → plan → TC → archivos` completo en
      [`00-inventario-trabajo.md`](./00-inventario-trabajo.md).
- [x] Los cinco documentos canónicos de `../thesis-docs` fueron
      reconciliados con esta acta antes de autorizar 033.
- [x] `docs/00-ESTADO-ACTUAL.md` actualizado a I-11 "EN PROGRESO —
      Plan032 DONE; 033–040 cerrados técnicamente al 2026-09-09; piloto SUS 1–2 pendiente".
- [x] Estado de Plan 032 marcado **DONE** con fecha 2026-09-07.

## 9. Handoff al siguiente plan

- 033 (P-42 base) es la **siguiente tarea autorizada** una vez que el
  acta esté firmada y el drift canónico corregido por el padre en
  `../thesis-docs/plan/architecture/07-api-contract.md §1/§9`.
- 034–037 pueden reordenarse entre sí, pero **ninguno** puede emitir
  antes de 033.
- 038 no puede iniciar hasta que 034–037 estén cerrados (para no
  duplicar eventos).
- 039 no puede iniciar hasta que 038 esté cerrado y Plan 031 siga sin
  tocar.
- 040 es la integración final (Bruno `12-admin/` 16 requests +
  piloto SUS 1–2 con cita Brooke 1996).
- I-12 (semanas 23–24): SUS `n ≥ 5` + baseline RNF-06 + cierre de
  variables de tesis. **No** se abre aquí.

---

**Este acta se firma el 2026-09-07 y se publica en
`docs/modulos/panel-admin/00-acta-reconciliacion.md`. Su inventario
operativo vive en el archivo hermano
[`00-inventario-trabajo.md`](./00-inventario-trabajo.md).**