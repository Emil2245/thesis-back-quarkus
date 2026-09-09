# 034 — Gestión de usuarios e invitaciones (P-38)

**Estado:** **DONE (2026-09-08)** · I-11 · P-38 / US-35 / TC-P38-01..03 · sin commit.

> Reutiliza **strictamente** lo existente: `auth/aceptar-invitacion`
> (DONE, TTL `PT72H`, `TipoToken.INVITACION`,
> `emailVerificado=true` al aceptar), `TokenService.issueOneTimeToken(...)`,
> `mail port` ya cableado. **Nunca** se imprime ni devuelve contraseña
> temporal. Login desactivar/reactivar/delete respeta RESTRICT.
> Emite los eventos D-13 del proceso (`usuario.invitado`,
> `usuario.activado`, `usuario.desactivado`) a través de
> `LogActividadService.emitir(...)` (033).
>
> **Superficies abiertas (citadas del acta 032, no pre-decididas en
> este plan):** la matriz canónica de `DELETE /admin/usuarios/{id}`
> (self-delete / last-admin), el cambio de email admin, el
> aprovisionamiento del primer SUPER_ADMIN y los códigos de error
> exactos para esos casos **no se fijan aquí**. Si el acta 032 deja
> estas superficies abiertas, este plan **no** codifica la matriz
> como canónica: la implementación de esos casos se cierra contra el
> código existente con tests rojos previos, o se difiere a I-12. La
> regla **«canonical P-38 409 para proyectos»** (decisión 20 de 032)
> sí permanece obligatoria: `DELETE /admin/usuarios/{id}` con
> proyectos propios devuelve 409 con un test focal de mapeo de
> excepción FK.

## Proceso / historia / criterios

- **Proceso:** P-38.
- **Historia:** US-35.
- **Iteración:** I-11.
- **Criterios de aceptación (quality/02):**
  - **TC-P38-01:** `POST /admin/usuarios` → 201 + invitación 72 h;
    `POST /auth/aceptar-invitacion` establece contraseña (D-11);
    nunca contraseña temporal.
  - **TC-P38-02:** desactivar → login siguiente 403; datos persisten;
    reactivar restaura.
  - **TC-P38-03:** `DELETE /admin/usuarios/{id}` con proyectos → 409
    (RESTRICT — decidir destino primero).

## Objetivo medible

Una ejecución futura debe demostrar que:

1. `GET /admin/usuarios?q&activo&page…` lista usuarios con
   `Page<UsuarioAdminResponse>` (UUIDv7 en `id`; campos canónicos mínimos
   decididos por el acta 032: `nombre`, `email`, `rol`, `activo`,
   `emailVerificado`, `fechaCreacion`). El campo
   `invitacionExpiraEn` **no** entra al mínimo canónico
   (evita el N+1 que exigiría una subconsulta por fila del
   listado); si el acta 032 lo autoriza explícitamente, 034 lo
   añade como **opt-in** y justifica la subconsulta; en cualquier
   caso, sin `passwordHash` ni token);
2. `POST /admin/usuarios` con `UsuarioInvitarRequest{nombre, email,
   rol}` crea el `Usuario` con la estrategia inicial resuelta
   **exactamente como acta 032** (decisión 4 de 032): la forma
   del `passwordHash` inutilizable, los flags iniciales `activo` y
   `emailVerificado`, y el camino exacto del helper aleatorio
   **los decide el acta 032 D-04** y 034 los implementa **exactamente**
   como el acta los fija (ver decisión 31). El plan emite el
   token de invitación 72 h por el `mail port` y responde 201
   con `UsuarioAdminResponse`; el caller **nunca** ve la
   contrasenña.
3. `PUT /admin/usuarios/{id}` permite cambiar `nombre`, `rol`,
   `activo`; **no** permite cambiar `email` (decisión 6 de 032);
4. `POST /admin/usuarios/{id}/desactivar` fija `activo=false`; el
   login siguiente devuelve 403 (gate existente en `AuthService`);
   los datos del usuario persisten (proyectos, APUs, etc.);
5. `POST /admin/usuarios/{id}/reactivar` restaura `activo=true` y
   el usuario puede autenticarse de nuevo sin re-verificar email;
6. `DELETE /admin/usuarios/{id}` con **proyectos propios** (FK
   RESTRICT V001 §3) devuelve **409** con código tipado
   `usuario-con-proyectos-impedido` (decisión 20 de 032; test
   focal de mapeo de excepción FK);
7. las superficies abiertas del acta 032 (self-delete, last-admin,
   cambio de email, primer SUPER_ADMIN) se implementan **solo** si
   el acta las cerró con códigos canónicos; en caso contrario este
   plan documenta la apertura y no codifica la matriz como
   canónica;
8. los eventos D-13 `usuario.invitado`, `usuario.activado` y
   `usuario.desactivado` se emiten a través de 033 con
   `LogActividadService.emitir(...)` dentro de la misma transacción
   exterior (`MANDATORY`); un rollback del flujo borra el evento
   (sin ghost events).

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada; decisiones 1–20 vigentes. | Habilita código. |
| G1 — 033 cerrado | `LogActividadService.emitir` operativo; `MANDATORY` verificado; `EventoLogActividad` con 26 entradas. | Habilita emisión D-13. |
| G2 — auth DONE | `auth/aceptar-invitacion` implementado; TTL `PT72H`; `RecordingEnviadorCorreo` para tests. | Habilita invitación sin contraseña temporal. |
| G3 — roles canónicos | `USUARIO`, `SUPER_ADMIN` únicos; sin middleware de roles. | Habilita `@RolesAllowed("SUPER_ADMIN")`. |
| G4 — FK RESTRICT | `proyecto.usuario_id → usuario` con `ON DELETE RESTRICT` (V001 §3). | Habilita 409 en TC-P38-03 (test focal de mapeo de excepción FK). |
| G5 — UUIDv7 | `UuidV7.parse` en frontera; PK/FK BIGINT internas. | Habilita `id` UUIDv7 en DTOs. |
| G6 — cierre | Focales verdes, regresión verde, Bruno 12-admin agrega 3 requests para P-38. | Evidencia medible. |

`STOP-034-CONTRASENA-TEMPORAL` se activa si un test o un caller
obtiene una contraseña temporal por cualquier vía (DTO, log,
correo, respuesta). Reabrir 032.

`STOP-034-SUPERFICIE-ABIERTA` se activa si el acta 032 deja
abiertas las superficies de self-delete, last-admin, cambio de
email o primer SUPER_ADMIN y este plan intenta codificarlas como
canónicas. Reabrir 032 o diferir a I-12.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  (decisiones 4, 5, 6, 7, 20).
- `plans/panel-admin/033-log-actividad-base.md` (API de
  `LogActividadService.emitir`, enum `EventoLogActividad`).
- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §H/P-38
  y §J/D-11 (literal).
- `../../../thesis-docs/plan/architecture/07-api-contract.md` §9 filas
  P-38 y §1 (errores `problem+json`, paginación
  `items,total,page,size,totalPaginas`).
- `../../../thesis-docs/plan/architecture/06-database-schema.md` §2.1
  (`usuario`), §3 (FK RESTRICT), §2.2 (`token_usuario`).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md`
  TC-P38-01..03.
- `src/main/java/ec/uce/propuestas/usuario/Usuario.java` (campos
  `passwordHash`, `emailVerificado`, `activo`, `rol`,
  `Rol.USUARIO|SUPER_ADMIN`).
- `src/main/java/ec/uce/propuestas/usuario/UsuarioRepository.java`
  (consultas existentes; búsqueda por `publicId` UUIDv7).
- `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java`
  (especialmente `aceptarInvitacion`).
- `src/main/java/ec/uce/propuestas/usuario/auth/TokenService.java`
  (`issueOneTimeToken`, `invitacionTtl`, `consumeOneTimeToken`).
- `src/main/java/ec/uce/propuestas/usuario/auth/mail/EnviadorCorreo.java`
  (interfaz + adapter; tests con `RecordingEnviadorCorreo`).
- `src/main/java/ec/uce/propuestas/usuario/TipoToken.java`
  (`INVITACION`).
- `src/main/java/ec/uce/propuestas/common/UuidV7.java`,
  `common/ProblemaException.java`,
  `common/dto/Page.java`.
- `src/main/resources/application.yml` `app.app.token.invitacion-ttl:
  PT72H` (ruta exacta a confirmar contra el código).

## Estado inicial esperado

- `auth/aceptar-invitacion` implementado y verde (Plan 004).
- `Usuario.passwordHash` se hashea con BCrypt; `aceptarInvitacion`
  reescribe el hash real al aceptar.
- `TokenService.issueOneTimeToken(...)` toma `(usuario, TipoToken,
  Duration ttl, emailDestino)`; los tests usan
  `RecordingEnviadorCorreo` para capturar el cuerpo.
- Sin servicio emisor D-13 todavía (034 lo introduce para P-38).
- Sin recurso admin (`/admin/usuarios`).

## Decisiones locked adicionales (034)

Se suman a las 20 de 032 y a las 9 de 033; no las contradicen:

29. **Prerrequisito fundación 033 (transición):** la transición 033
    → 034 exige la **fundación de P-42 cerrada**: `LogActividadService`
    operativo, `MANDATORY` verificado, enum `EventoLogActividad`
    con las 26 entradas verbatim, validación de detalle mediante
    `LogActividadDetalleValidator` consumida por 034 (no se crea
    de nuevo en 034; el validador ya implementa el mapa completo
    `detallesEsperados()` publicado por el acta 032 — 034 no
    redefine, no agrega y no amplía ese mapa), y `GET /admin/logs`
    listo. 034 **no** introduce cimientos de log; emite a través
    del seam ya materializado en 033. Si el acta 032 reabre alguna
    decisión que afecte a 033, 034 reabre también antes de emitir.
30. **DTOs admin (034):**
    - `UsuarioAdminResponse(UUID id, String nombre, String email,
      Rol rol, boolean activo, boolean emailVerificado,
      Instant fechaCreacion)`. Los nombres canónicos
      mínimos los fija el acta 032 (decisión **Nombres
      canónicos de respuesta**); `invitacionExpiraEn` no entra al
      mínimo (evita el N+1) y, si el acta lo autoriza, 034 lo
      añade como **opt-in** con subconsulta justificada. Sin
      `passwordHash`, sin `tokenHash`, sin JWT.
    - `UsuarioInvitarRequest(@NotBlank @Size(max=200) String nombre,
      @NotBlank @Email @Size(max=320) String email,
      @NotNull Rol rol)` (USUARIO o SUPER_ADMIN).
    - `UsuarioAdminEditarRequest(@Size(max=200) String nombre,
      @NotNull Rol rol, @NotNull Boolean activo)` (sin `email`).
31. **Contrato exacto del hash inutilizable inicial (self-contained,
        fijado por el acta 032 D-04):** el contrato se aplica
        **úncamente** cuando `UsuarioAdminService.invitar(...)` crea
        un nuevo usuario invitado. **No** se regenera en login,
        logout, cambio de contrasenña, reactivación ni en
        ningún otro flujo posterior. Los usuarios existentes
        inactivos (creados antes de este plan) **no** regeneran
        su hash; solo `aceptar-invitacion` (flujo canónico 004)
        reescribe el hash real al aceptar la invitación. Pasos:
        1. **Origen aleatorio:** 32 bytes aleatorios tomados del
           `SecureRandom` canónico de la JVM
           (`java.security.SecureRandom`); **no** se introduce
           un helper `RandomUtil` nuevo.
        2. **Codificación intermedia:** los 32 bytes se
           codifican como **Base64URL sin padding**
           (`Base64.getUrlEncoder().withoutPadding()`); ese
           `String` es úncamente un portador efímero
           hacia el hash y **no** se persiste, se loguea, se
           devuelve al caller, se envía por correo ni se
           imprime en trazas.
        3. **Hash único:** ese `String` Base64URL se hashea
           **exactamente una vez** a través del `PasswordService`
           ya existente (bcrypt con los parámetros vigentes del
           servicio). El `byte[]` aleatorio y el `String`
           Base64URL se descartan **inmediatamente** después
           de obtener el hash; solo persiste el `passwordHash`.
        4. **Token de invitación independiente:** el link
           `/auth/aceptar-invitacion?token=…` viaja por el
           `TokenService` ya existente (`SHA-256`, TTL
           `app.app.token.invitacion-ttl: PT72H`,
           `TipoToken.INVITACION`); el `TokenUsuario` persiste
           úncamente el **digest SHA-256** del token (nunca
           el token en claro). La contrasenña temporal
           inutilizable y el token de invitación son **dos
           secretos independientes**: cambiar uno no expone ni
           regenera el otro.
        **Decisiones contradictorias retiradas:** las frases
        "034 no prefija algoritmo", "034 usa la estrategia ya
        presente en el módulo", "034 usa la utilidad que ya
        usa `AuthService`" y "034 descubre el nombre y la ruta
        exactos contra el código" quedan **explícitamente
        eliminadas** porque el acta 032 D-04 cierra el contrato
        y 034 lo implementa tal cual, sin invocar estrategias
        alternativas ni helpers nuevos.
32. **Emisión D-13 (034):** consume verbatim la matriz canónica
    publicada por el acta 032 (decisión 2); 034 **no** redefine,
    **no** agrega ni **no** amplía claves.
    - `usuario.invitado`: en `POST /admin/usuarios`, dentro de la
      tx exterior, **después** de persistir el `Usuario` y el
      `TokenUsuario`. `detalle = { "tokenExpiraEn": "<ISO-8601>" }`;
      `entidadId` top-level lleva el UUIDv7 del `Usuario` invitado.
      La expiración del token es el único dato del lado invitación
      que no es PII; el correo del invitado **no** entra al detalle
      canónico.
    - `usuario.activado`: en `POST /admin/usuarios/{id}/reactivar`.
      `detalle = { "origen": "admin" }`; `entidadId` top-level
      lleva el UUIDv7 del `Usuario` reactivado. La aceptación de
      invitación emite `usuario.activado` con `detalle.origen =
      "invitacion"` (responsabilidad de 038, que cubre
      `AuthService.aceptarInvitacion`); `entidadId` top-level lleva
      el mismo UUIDv7 del `Usuario` activado.
    - `usuario.desactivado`: en `POST /admin/usuarios/{id}/desactivar`.
      `detalle = { "origen": "admin" }`; `entidadId` top-level lleva
      el UUIDv7 del `Usuario` desactivado.
33. **DELETE /admin/usuarios/{id} con proyectos propios — 409
    obligatorio (decisión 20 de 032):** cuando el caller intenta
    borrar un usuario que tiene proyectos (`proyecto.usuario_id → usuario
    ON DELETE RESTRICT`), el endpoint devuelve **409** con código
    tipado `usuario-con-proyectos-impedido`. Un test focal de
    `@QuarkusTest` verifica que la excepción FK real (p. ej.
    `ConstraintViolationException`, `PersistenceException`,
    `SQLIntegrityConstraintViolationException`, o la envoltura
    nativa de Hibernate) se mapea a `ProblemaException.conflicto`
    con ese código.
34. **Self-delete / last-admin / cambio de email / primer
    SUPER_ADMIN — superficies abiertas:** estos casos **no**
    codifican la matriz canónica en este plan. Si el acta 032 los
    cerró, este plan implementa siguiendo el acta; si el acta los
    dejó abiertos (`STOP-032-DELETE-USUARIO`,
    `STOP-032-EMAIL-ADMIN`, `STOP-032-FIRST-SUPERADMIN`), este plan
    documenta la apertura y no codifica. Los códigos de error
    `usuario-self-delete-impedido`, `ultimo-super-admin-impedido`
    y cualquier código nuevo para cambio de email **no** son
    canónicos en este plan y se difieren a I-12.
35. **Listado `GET /admin/usuarios`:** filtros `q` (ILIKE sobre
    `nombre` y `email`), `activo` (Boolean), `page` (default 0),
    `size` (default 25, tope 200). Orden por `fechaCreacion DESC`
    con secundario por `id ASC` (estable). El DTO canónico
    mínimo **no expone `invitacionExpiraEn`** (lo cierra el acta
    032); si el acta lo autoriza explícitamente, 034 lo añade
    como opt-in con la subconsulta `TokenUsuarioRepository
    .findVigentePorUsuarioId(usuarioId, now())` por fila (acepta el
    N+1 que ya evita la paginación canónica); sin esa
    autorización, el detalle de invitación pendiente vive
    solo en el recurso dedicado `GET /admin/usuarios/{id}/invitacion`
    (no en el listado).

## Alcance

### Incluye

- Recurso JAX-RS `UsuarioAdminResource` con `@Path("/admin/usuarios")`
  y `@RolesAllowed("SUPER_ADMIN")` a nivel de clase.
- Endpoints (alineados con `07-api-contract.md §9`):
  - `GET    /admin/usuarios?q&activo&page…` (USUARIO: 403)
  - `POST   /admin/usuarios` (USUARIO: 403; validación; emisión)
  - `PUT    /admin/usuarios/{id}` (UUIDv7; 200/400/404)
  - `POST   /admin/usuarios/{id}/desactivar` (200/404)
  - `POST   /admin/usuarios/{id}/reactivar` (200/404)
  - `DELETE /admin/usuarios/{id}` (204/404/**409 `usuario-con-proyectos-impedido`**)
- DTOs (decisión 30).
- Servicio `UsuarioAdminService` con la matriz 409 confirmada contra
  el acta 032.
- Emisión D-13 a través de 033 (decisión 32).
- Tests `@QuarkusTest`:
  - `UsuarioAdminResourceIT` cubriendo TC-P38-01..03 +
    `DELETE` con proyectos (test focal de mapeo de excepción FK) +
    listado + reactivado;
  - `UsuarioAdminSinContrasenaTemporalTest`: asserts que ningún DTO,
    ningún log ni el `RecordingEnviadorCorreo` contiene una
    contraseña temporal ni el valor aleatorio base.

### No incluye

- Modificar `auth/aceptar-invitacion` (queda canónico; 038 cubre la
  emisión `usuario.activado` con origen `invitacion`).
- Cambiar el `mail port` ni la interfaz `EnviadorCorreo`.
- Endpoint para reenviar invitación (queda fuera; el admin borra y
  crea nuevo si la invitación venció).
- Cambiar `TokenService.issueOneTimeToken` (reutilizado tal cual).
- Agregar `password_hash_temporal` ni ningún seam para
  "contraseñas temporales" — la regla es "nunca".
- Cambiar la FK `proyecto.usuario_id → usuario` (sigue RESTRICT;
  ningún CASCADE silencioso).
- Cambiar el comportamiento de login cuando `activo=false` (gate
  ya implementado en `AuthService.login`).
- Inventar un helper `RandomUtil` u otra utilidad nueva: el contrato
  exacto del acta 032 D-04 (32 bytes `SecureRandom` → Base64URL
  sin padding → hash bcrypt único a través del
  `PasswordService`) descarta explícitamente esa posibilidad.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/UsuarioAdminResource.java` | `@Path("/admin/usuarios")` + `@RolesAllowed("SUPER_ADMIN")`. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/UsuarioAdminService.java` | Matriz 409 confirmada contra acta + emisión D-13. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioAdminResponse.java` | Record canónico (decisión 30). |
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioInvitarRequest.java` | Record canónico. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/admin/dto/UsuarioAdminEditarRequest.java` | Record canónico (sin `email`). |
| Modificar | `src/main/java/ec/uce/propuestas/usuario/UsuarioRepository.java` | + `listar(q, activo, page, size)`, `count(q, activo)`, `findByPublicId(UUID)`, `findWithInvitacionPendiente(UUID)`. |
| Reusar | `PasswordService` ya existente (bcrypt) y `TokenService` ya existente (SHA-256, TTL PT72H) | Sin helper nuevo; contrato exacto del acta 032 D-04 (ver decisión 31). |
| Crear | `src/test/java/ec/uce/propuestas/usuario/admin/UsuarioAdminResourceIT.java` | TC-P38-01..03 + DELETE con proyectos (mapeo FK) + listado + reactivado. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/admin/UsuarioAdminSinContrasenaTemporalTest.java` | Aserts de no-leak. |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marca 034 `DONE`. |
| Modificar (opcional) | `api/bruno/12-admin/TC-12-P38-*` (cuando 040 los cree) | Helpers + casos TC-P38-01..03. |
| No previsto | `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java` | STOP — el flujo de aceptación queda canónico. |
| No previsto | `src/main/java/ec/uce/propuestas/usuario/auth/TokenService.java` | STOP — se reutiliza tal cual. |
| No previsto | `src/main/resources/db/migration/**` | STOP. |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**`, `recalculo/**`, `common/UuidV7.java` | STOP. |

## Contrato REST

```
GET    /api/v1/admin/usuarios?q=&activo=&page=&size=
POST   /api/v1/admin/usuarios
PUT    /api/v1/admin/usuarios/{id}
POST   /api/v1/admin/usuarios/{id}/desactivar
POST   /api/v1/admin/usuarios/{id}/reactivar
DELETE /api/v1/admin/usuarios/{id}
```

Todos requieren rol `SUPER_ADMIN` (USUARIO → 403 sin pistas).

### Errores

| Código | Tipo | Cuándo |
|---|---|---|
| 400 `validacion` | `email-invalido` | `email` no es RFC-5322 o > 320 chars. |
| 400 `validacion` | `rol-invalido` | `rol` no es USUARIO ni SUPER_ADMIN. |
| 400 `validacion` | `nombre-requerido` | `nombre` blank o > 200. |
| 400 `validacion` | `uuid-invalido` | `{id}` no es UUIDv7. |
| 400 `validacion` | `tamano-pagina-invalido` | `size > 200` o `size < 1`. |
| 404 `no-encontrado` | — | UUIDv7 válido pero sin fila. |
| 409 `conflicto` | `usuario-con-proyectos-impedido` | FK RESTRICT (decisión 20 de 032; **canónico obligatorio**). |
| 409 `conflicto` | `email-ya-registrado` | email duplicado. |
| 403 `forbidden` | — | caller `USUARIO`. |

> **Códigos no fijados en este plan:** los códigos `usuario-self-delete-impedido`,
> `ultimo-super-admin-impedido`, y los de cambio de email **no** se
> incluyen en la tabla canónica del contrato REST. Su implementación
  queda gated por la decisión 32 del acta 032 (y, si el acta no los
  fija, se difieren a I-12 con su propio STOP).

## Secuencia TDD (estricta)

### RED

1. `UsuarioAdminResourceIT`:
   - `POST` con `email` único → 201 + `UsuarioAdminResponse`
     (sin `passwordHash`); `RecordingEnviadorCorreo` capturó el
     correo de invitación; `log_actividad` tiene una fila
     `evento=usuario.invitado`.
   - `POST` con `email` duplicado → 409 `email-ya-registrado`.
   - `POST` con `rol=OTRO` → 400.
   - `PUT` con `rol` válido → 200 + `rol` actualizado.
   - `PUT` con `{id}` UUIDv4 → 400 `uuid-invalido`.
   - `PUT` con `{id}` UUIDv7 ajeno → 404.
   - `POST /desactivar` → 200 + `activo=false`; intentar login →
     403 (gate ya existente); `reactivar` → 200 + `activo=true`;
     login funciona; `log_actividad` tiene `usuario.desactivado` y
     `usuario.activado`.
   - `DELETE` con un usuario que **tiene proyectos propios** →
     **409 `usuario-con-proyectos-impedido`** (test focal de
     mapeo de excepción FK); el `proyecto` persiste.
   - `GET` con `q` parcial → filtro ILIKE; paginación canónica
     (`items,total,page,size,totalPaginas`).
2. `UsuarioAdminSinContrasenaTemporalTest`:
   - Crea 10 usuarios; inspecciona todos los DTOs y todos los
     `RecordingEnviadorCorreo.correosEnviados()`; asserta 0 matches
     de la regex `password|contrase|clave|temporal|randomBytes`.
3. `LogActividadEmisionUsuarioTest`:
   - Tras `POST /admin/usuarios` → 1 fila `usuario.invitado`
     (tx commit).
   - Tras rollback simulado (un `@Test` que envuelve un servicio
     con `@QuarkusTransactionException` simulada) → 0 filas
     `usuario.invitado`.
   - Tras `POST /desactivar` → 1 fila `usuario.desactivado`.

### GREEN

Construir DTOs, servicio, recurso. Reusar `TokenService.issueOneTimeToken`
y `EnviadorCorreo` tal cual. Implementar la matriz 409 confirmada
contra acta (al menos `usuario-con-proyectos-impedido`).

### TRIANGULATE

- Concurrencia: dos `DELETE` simultáneos sobre el último admin;
  uno recibe 204, el otro el código que determine el acta (si el
  acta no lo fijó, este test queda gated).
- Reactivación tras desactivar varios meses: el `passwordHash`
  inutilizable se mantiene; al pedir nueva invitación (en otra
  sesión, fuera de 034), la aceptación lo reemplaza (test de 040).
- Filtro `q` con caracteres especiales (`%`, `_`): escape de LIKE.
- Paginación con `size=0` → 400; `size=201` → 400.

### REFACTOR

- Consolidar las verificaciones 409 en un único
  `assertOperacionAdminPermitida(caller, objetivo)` cuando el acta
  032 confirme las superficies abiertas.

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| TC-P38-01 invitación | 201; correo enviado; `emailVerificado=false`; `passwordHash` inutilizable; nunca contraseña temporal. |
| TC-P38-01 aceptación | 204; `passwordHash` real; `emailVerificado=true`; `activo=true`. |
| TC-P38-02 desactivar/reactivar | 403 al login siguiente; datos persisten; reactivar restaura. |
| TC-P38-03 DELETE con proyectos | 409 `usuario-con-proyectos-impedido`; proyecto persiste; mapeo de excepción FK correcto. |
| Listado `GET` | paginación canónica estable; filtros `q`, `activo`. |
| Emisión `usuario.invitado` | fila `log_actividad` con `entidadId` UUIDv7 del usuario y `detalle.tokenExpiraEn` (sin PII). |
| Emisión `usuario.activado` / `desactivado` | fila `log_actividad` con `detalle.origen=admin`. |
| Rollback exterior | no se emite `usuario.invitado`. |
| USUARIO 403 | cualquier endpoint admin responde 403. |
| Sin contraseña temporal | 0 matches regex en DTOs ni en `EnviadorCorreo`. |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Focales.
./gradlew test --tests 'ec.uce.propuestas.usuario.admin.*' \
  -Dquarkus.http.test-port=0 --console=plain
# esperado: BUILD SUCCESSFUL; clases del paquete verde.

# Regresión auth + usuario (el flujo de aceptación no debe romperse).
./gradlew test --tests 'ec.uce.propuestas.usuario.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Eventos D-13: 034 emite exactamente 3 (usuario.invitado,
# usuario.activado, usuario.desactivado).
grep -RInE 'emitir\(.*(usuario\.invitado|usuario\.activado|usuario\.desactivado)' \
  src/main/java/ec/uce/propuestas/usuario/admin/

# Ninguna contraseña temporal en código.
! grep -RInE 'temporalPassword|contrasenaTemporal|passwordTemporal|tempPassword' \
  src/main/java/ec/uce/propuestas/

# Forma canónica Page<T>.
grep -RInE 'items,|total,|page,|size,|totalPaginas' \
  src/main/java/ec/uce/propuestas/usuario/admin/

# FK RESTRICT intacta (ninguna migración nueva).
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío.

# auth intacto (aceptar-invitacion no se toca).
git diff --name-only -- 'src/main/java/ec/uce/propuestas/usuario/auth/**'
# esperado: vacío (excepto si 034 documenta en `AuthService` con un
# comentario, lo cual también se evita).

# Motor y recalculo intactos.
git diff --name-only -- 'src/main/java/ec/uce/propuestas/motor/**' \
  -- 'src/main/java/ec/uce/propuestas/recalculo/**'
# esperado: vacío.
```

No se predicen conteos de suite completa. El orquestador decide.

## Completion checklist (034)

- [ ] `auth/aceptar-invitacion` y `TokenService` intactos.
- [ ] `GET/POST/PUT/DELETE /admin/usuarios` + `…/desactivar` +
      `…/reactivar` implementados con `@RolesAllowed("SUPER_ADMIN")`.
- [ ] `passwordHash` inicial inutilizable; nunca se comunica.
- [ ] `DELETE` con proyectos propios devuelve 409
      `usuario-con-proyectos-impedido` con test focal de mapeo FK.
- [ ] `PUT /admin/usuarios/{id}` no permite cambiar email.
- [ ] Eventos `usuario.invitado`, `usuario.activado`,
      `usuario.desactivado` emitidos vía 033; sin ghost events
      (rollback exterior los borra).
- [ ] `log_actividad.detalle` solo contiene claves canónicas (sin
      passwordHash ni tokenHash ni JWT).
- [ ] TC-P38-01..03 verdes en `@QuarkusTest`.
- [ ] Ninguna migración nueva; motor intacto; auth intacto.
- [ ] Sin helper `RandomUtil` inventado: el contrato del acta 032 D-04
      (32 bytes `SecureRandom` → Base64URL sin padding →
      hash bcrypt único a través del `PasswordService`
      existente) se aplica **úncamente** al crear usuarios invitados.
- [ ] `git diff --check` limpio.

## Handoff al siguiente plan

Cuando 034 cierre, el orquestador puede iniciar **035** (P-39 bases
centrales — cierre) o cualquier otro de 035–037. 035 reusa
`AdminBaseCentralResource` (Plan 015bis DONE) y agrega solo la
emisión `admin.base_editada`; no reabre P-39.