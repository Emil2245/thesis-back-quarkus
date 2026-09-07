# 032 — Sincronizar contrato e inventario del panel Super-Admin

**Estado:** TODO · I-11 · gate documental previo a P-38…P-42.

> Este plan **no codifica**. Su único producto es un acta de
> reconciliación canónica del panel Super-Admin más un inventario
> exacto de trabajo pendiente, escrito en `docs/modulos/panel-admin/00.md`
> (no se crea en este pase si 032 no se ejecuta; ver **Paso 9**). Cualquier
> contradicción sin resolver **STOP** y bloquea 033. 032 es la **única**
> sesión autorizada para resolver lagunas canónicas; los planes 033–040
> citan su acta y nunca la pre-deciden.
>
> **Entrega autorizada y exigida:** ejecutar 032 incluye **reconciliar**
> los documentos canónicos de `../thesis-docs` (`api-contract.md`,
> `database-schema.md`, `design/03-procesos-detalle.md`, `quality/02-catalogo-pruebas.md`,
> `roadmap/01-plan-iteraciones-xp.md`) y los `docs/` del backend que
> contradigan el acta. Las correcciones de docs canónicos son parte
> del cierre de 032 y se aplican **antes** de autorizar código de
> 033. Hasta que el acta esté firmada y cada decisión resuelta, **033
> está STOPPED** (no se autoriza ningún código nuevo de I-11).

## Proceso / historia

- **Proceso:** pre-P-38…P-42 (gate de reconciliación).
- **Historias:** US-35…US-39 (cobertura; sin entrega de valor nuevo).
- **Iteración:** I-11 (semanas 21–22, sprint de planificación).

## Objetivo medible

Una ejecución futura debe producir, **sin tocar código de negocio**:

1. un acta firmada (`docs/modulos/panel-admin/00-acta-reconciliacion.md`,
   ruta exacta decidida en el paso 2) que congela las decisiones listadas
   en **Decisiones locked** abajo;
2. un inventario exacto y priorizado del trabajo a ejecutar en
   033…040, con su mapeo `P-xx → plan → TC` y la lista de archivos
   previstos;
3. cero contradicciones abiertas entre el canon
   (`thesis-docs/plan/architecture/07-api-contract.md §9`,
   `thesis-docs/plan/design/03-procesos-detalle.md §H/§J` y la
   implementación actual (`main` + cambios sin commit de Plan 031);
4. **drift corregido** en `thesis-docs/plan/architecture/07-api-contract.md §1`
   para fijar la forma canónica estable de `Page<T>` (ver decisión 16) y
   en las tablas §9 de P-38/P-39/P-40/P-41 que requieran alinearse con
   las decisiones de este plan;
5. STOP conditions que se cierran explícitamente para que 033 arranque
   sin preguntas abiertas.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — código actual | `main` revisable con `git status --short` mostrando exactamente los cambios de Plan 031 + ningún otro. | Aisla el inventario I-11 de deuda ajena. |
| G1 — catálogo D-13 verbatim | `design/03 §J D-13` reusado literalmente como fuente única; ningún evento nuevo. | Impide inventar eventos en 033…039. |
| G2 — D-11 verbatim | Invitación 72 h, sin contraseña temporal, `POST /auth/aceptar-invitacion` con `token-invalido-o-expirado` 410. | Cierra TC-P38-01 con la implementación existente. |
| G3 — D-12 verbatim | Archivar central; borrar sin bloqueo tras archivar; copia PROYECTO intacta. | Cierra TC-P39-02..03 con Plan 015bis. |
| G4 — UUIDv7 público | `UuidV7.parse` en frontera; PK/FK BIGINT internas. | Cierra las decisiones de identidad para 033–036. |
| G5 — `/proyectos/parametros-sistema` canónico | Ruta no se mueve a `/admin/parametros-sistema`; lectura pública + escritura `@RolesAllowed("SUPER_ADMIN")`. | Cierra TC-P41-01. |
| G6 — cierre | Acta firmada + inventario escrito + drift canónico corregido + 0 contradicciones. | Habilita 033. |

`STOP-032-CONTRADICCION` se activa si el acta detecta una inconsistencia
no resoluble desde fuentes citadas.

> **Nombres canónicos de respuesta (mínimo, decidido por el acta):**
> el acta **congela** los nombres canónicos de respuesta que los
> planes 033–038 reutilizan sin协商. Cualquier campo adicional es
> **opt-in** (los planes que lo necesiten lo justifican y lo añaden
> en su propio scope; el acta no lo prefija):
>
> - `LogActividadResponse` (GET `/admin/logs`): campos canónicos
>   mínimos `id` (UUIDv7), `usuarioId` (UUIDv7 nullable),
>   `usuarioNombre` (String, **solo para consumo admin**; se
>   distingue explícitamente de `detalle` y nunca contiene datos
>   personales más allá del nombre del usuario autor de la fila),
>   `evento` (clave D-13 verbatim), `entidad` (String nullable),
>   `entidadId` (UUIDv7 nullable), `detalle`
>   (`Map<String,Object>` Jackson), `fecha` (Instant, canónico
>   frente al drift `created_at` / `createdAt`).
> - `UsuarioAdminResponse` (GET `/admin/usuarios`): campos
>   canónicos mínimos `id` (UUIDv7), `nombre`, `email`, `rol`,
>   `activo`, `emailVerificado`, `fechaCreacion` (Instant). El
>   campo `invitacionExpiraEn` **no** entra al mínimo; si el acta
>   no lo autoriza explícitamente, 034 lo omite (evita el N+1 que
>   exigiría una subconsulta por fila del listado).
>
> Los planes 033 y 034 usan estos nombres verbatim; cualquier
> cambio posterior requiere reabrir el acta 032.

## Fuentes que deben releerse (verbatim)

- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §H (P-38…P-42)
  y §J (D-11, D-12, D-13 literal).
- `../../../thesis-docs/plan/architecture/07-api-contract.md` §9
  (tabla completa `/admin/*`) y §1 (errores problem+json, paginación
  canónica `Page<T>`).
- `../../../thesis-docs/plan/architecture/06-database-schema.md` §2.12
  (`plantilla_apu`), §2.14 (`valor_referencia`), §2.15 (`log_actividad`)
  y §3 (integridad referencial, en particular `log_actividad.usuario_id
  ON DELETE SET NULL`).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P38,
  TC-P39, TC-P40, TC-P41, TC-P42 y §5 (protocolo SUS).
- `../../../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` I-11
  (semanas 21–22; piloto SUS 1–2).
- `docs/modulos/planes-para-estar-al-dia/05-administracion-bases.md`
  (estado DONE de P-39 al 2026-08-29; reutilización estricta).
- `docs/modulos/estado-actual.md` §1 y §4 (capacidades vigentes).
- `plans/015-retirar-descuento-apu.md` (regla descuento retirada).
- `plans/031-exportacion-cronograma.md` (para preservar su transacción
  única y TOCTOU en 039).
- `src/main/resources/db/migration/V001__baseline.sql` §2.14/§2.15
  (DDL verbatim de `valor_referencia` y `log_actividad`).
- `src/main/resources/db/migration/V004__seed_escenarios.sql` (las
  cuatro filas legacy no canónicas: `base.insumos.copiada`,
  `rubro.creado`, `cronograma.creado`, `presupuesto.vigente_marcado`;
  ver decisión 17).
- `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java`
  y `TokenService.java` (TTL invitación, `TipoToken.INVITACION`,
  `aceptarInvitacion` existente).

## Estado inicial esperado

- `main` tiene cambios sin commitear de **Plan 031** (export cronograma;
  ver `git status --short`); ningún otro archivo modificado fuera de
  esos cambios. Si el auditor encuentra archivos modificados ajenos a
  Plan 031, los reporta en el acta y **no** los mezcla con I-11.
- `proyecto/parametros-sistema` (GET/PUT), `insumo/admin/bases-centrales`
  (Plan 015bis DONE), `auth/aceptar-invitacion` y `log_actividad`
  (tabla + índices, sin servicio) existen. El resto de los endpoints
  `/admin/*` listados en `07-api-contract.md §9` están pendientes.
- El catálogo cerrado D-13 tiene **26 eventos** exactos (lista verbatim
  en sección dedicada más abajo; la constante Java
  `EventoLogActividad` los enumera).
- V004 siembra **19 filas** en `log_actividad`: **6 filas fixture**
  usan los **4 nombres legacy** no canónicos (`base.insumos.copiada`,
  `rubro.creado`, `cronograma.creado`, `presupuesto.vigente_marcado`,
  decisión 17) y se conservan en BD como rastro histórico. Las
  restantes 13 filas usan claves que ya entran al catálogo cerrado
  D-13 (`auth.registro`, `auth.login`, `proyecto.creado`,
  `presupuesto.version_creada`, `apu.creado`, `insumo.creado`,
  `documento.exportado`).
- V004 siembra además **4 valores** en `valor_referencia`
  (`SBU`, `APORTE_PATRONAL`, `FAS`, `HORAS_OPERACION_ANUAL`); este
  set queda fuera de la cobertura D-13 y se trata como dato
  informativo (decisión 14).

## Decisiones locked que el acta debe congelar

Si el auditor encuentra cualquier desviación en `main` o en los cambios
sin commit de Plan 031 respecto de estas decisiones, las marca como
**contradicciones** y STOP. Las decisiones marcadas
**(superficie a confirmar)** requieren que el acta verifique contra el
código real (no se inventan firmas; se citan archivos
`src/main/java/...` con la signatura exacta encontrada):

1. **Identidad pública admin (UUIDv7) — todas las entidades
   navegables:** las respuestas admin exponen **UUIDv7** para los
   identificadores externos de las entidades con recurso navegable:
   - `UsuarioAdminResponse.id` → UUIDv7 (`Usuario.publicId`).
   - `BaseInsumos.publicId`, `PlantillaApu.publicId`,
     `Insumo.publicId`, `Apu.publicId`, `Presupuesto.publicId`,
     `Cronograma.publicId`, `Proyecto.publicId` (UUIDv7 ya
     presentes en V001/V008; canónicos).
   - **`LogActividadResponse.id` → UUIDv7** (cambio de canon: la
     entidad `log_actividad` pasa a tener `public_id` UUIDv7).
   - **`LogActividadResponse.usuarioId` → UUIDv7** (referencia a
     `Usuario.publicId`; no BIGINT interno).
   - **`LogActividadResponse.entidadId` → UUIDv7 nullable** (el id
     público del recurso afectado cuando exista; `null` cuando la
     entidad no tiene UUID público o el emisor no lo provee).
   - PK/FK internas siguen siendo `BIGINT`; el BIGINT **nunca**
     cruza REST en respuestas admin (excepto `count`, `bytes`,
     enteros de paginación).
   - **Decisión locked:** el plan 033 crea una migración aditiva
     **con el siguiente número disponible a la hora de ejecutar 033**
     (p. ej. `V010__log_actividad_public_id.sql` si V010 no existe;
     si V010 ya existe por un plan posterior, el siguiente libre).
     El nombre exacto se fija en el paso de ejecución de 033 (no se
     pre-asigna V010 ciegamente). La migración añade
     `log_actividad.public_id UUID NOT NULL DEFAULT uuidv7()`,
     índice único `ux_log_actividad_public_id`, y trigger
     `trg_log_actividad_public_id_immutable` reusando el patrón de
     `fn_assert_public_id_immutable()` (V001 §5). **Nunca** se
     edita V001–V009.
   - Campos canónicos que **se conservan** sin reescritura:
     `usuarioNombre` y `fecha` siguen siendo válidos donde el canon
     los requiera.
   - **No PII** aplica **solo** a `detalle` JSONB y a secretos;
     los campos admin de respuesta (email, nombre) siguen siendo
     accesibles al SUPER_ADMIN. Esta es la matriz canónica y no
     se invierte en I-11.

2. **`log_actividad.detalle` JSONB (RNF-08, TC-P42-02):** solo claves
   canónicas en español neutro. El conjunto **completo y
   congelado** de claves permitidas por evento vive en la
   **matriz canónica del plan** (más abajo) y se publica desde
   este acta; Plan 033 la implementa completa y exacta en
   `EventoLogActividad.detallesEsperados()` y
   `LogActividadDetalleValidator`. Los planes 034–039 **solo
   consumen** esa matriz: ningún plan la amplía, redefine ni
   agrega claves. Si un emisor necesita una clave ausente, **no
   la inventa**: reabre 032.
   - **Nunca** correos / `email` / `emailDestino`, IPs /
     `ipOrigen`, nombres de usuarios o proyectos, ni texto libre
     capturado de formularios.
   - **Nunca** `passwordHash`, JWT, tokens, hashes de invitación,
     ni el contenido de `detalle` / `especificacion_tecnica` de
     APU ni `plantilla_apu.snapshot_secciones`.
   - **Identificadores públicos de entidad** (UUIDv7) prefieren
     vivir en la columna top-level `entidadId` de
     `LogActividadResponse` (con `entidad` top-level: el nombre
     de la entidad afectada). Las claves de detalle solo
     conservan identificadores cuando se requieren **dos o más**
     entidades distintas en el mismo evento (p. ej.
     `proyectoOrigenId` + `proyectoDuplicadoId` en
     `proyecto.duplicado`, o `baseOrigenId` +
     `proyectoDestinoId` en `base.copiada_a_proyecto`) o cuando
     la clave identifica un recurso **auxiliar** distinto del
     afectado (p. ej. `valorReferenciaClave` para
     `admin.parametros_editados` cuando la entidad afectada
     no es `valor_referencia`).
   - El validador interno (`LogActividadDetalleValidator`
     materializado por 033) rechaza las claves ajenas al
     conjunto lanzando `IllegalArgumentException` (evento
     desconocido) o `IllegalStateException` (clave de detalle
     no permitida); la transacción exterior hace rollback y no
     se persiste el evento. **No** se devuelve 400 al cliente:
     la clave de detalle es siempre server-authored. El filtro
     `evento=` de `GET /admin/logs` no aplica este validador:
     véase decisión 26 (es parámetro de query seguro, no se
     parsea contra el enum).

   **Matriz canónica evento → claves de detalle permitidas
   (congelada por este acta; 033 la implementa verbatim; 034–039
   la consumen sin modificarla):**

   | Evento | Claves `detalle` permitidas | `entidadId` top-level |
   |---|---|---|
   | `auth.login` | `{ "resultado": "ok" }` | `null` |
   | `auth.logout` | `{ "resultado": "ok" }` | `null` |
   | `auth.registro` | `{ "usuarioId": <UUIDv7> }` | `null` |
   | `auth.password_cambiada` | `{ "origen": "perfil\|reset" }` | `null` |
   | `usuario.invitado` | `{ "tokenExpiraEn": "<ISO-8601>" }` | UUIDv7 del `Usuario` invitado |
   | `usuario.activado` (origen `admin`) | `{ "origen": "admin" }` | UUIDv7 del `Usuario` activado |
   | `usuario.activado` (origen `invitacion`) | `{ "origen": "invitacion" }` | UUIDv7 del `Usuario` activado |
   | `usuario.desactivado` | `{ "origen": "admin" }` | UUIDv7 del `Usuario` desactivado |
   | `proyecto.creado` | `{}` (sin claves adicionales) | UUIDv7 del `Proyecto` |
   | `proyecto.editado` | `{}` | UUIDv7 del `Proyecto` |
   | `proyecto.eliminado` | `{}` | UUIDv7 del `Proyecto` |
   | `proyecto.duplicado` | `{ "proyectoOrigenId": <UUIDv7>, "proyectoDuplicadoId": <UUIDv7> }` | UUIDv7 del proyecto duplicado |
   | `insumo.creado` | `{ "codigoInsumo": "<texto>", "tipo": "EQUIPO\|MANO_OBRA\|MATERIAL\|TRANSPORTE" }` | UUIDv7 del `Insumo` |
   | `insumo.editado` | `{}` | UUIDv7 del `Insumo` |
   | `insumo.eliminado` | `{}` | UUIDv7 del `Insumo` |
   | `insumos.import_csv` | `{ "creados": <int>, "actualizados": <int>, "errores": <int> }` | UUIDv7 de la `BaseInsumos` destino |
   | `base.copiada_a_proyecto` | `{ "baseOrigenId": <UUIDv7>, "proyectoDestinoId": <UUIDv7>, "cantidadInsumos": <int>, "cantidadOmitidos": <int> }` | UUIDv7 del `Insumo` o de la `BaseInsumos` resultante (server-authored) |
   | `apu.creado` | `{}` | UUIDv7 del `Apu` |
   | `apu.editado` | `{}` | UUIDv7 del `Apu` |
   | `apu.eliminado` | `{}` | UUIDv7 del `Apu` |
   | `presupuesto.version_creada` | `{ "presupuestoOrigenId": <UUIDv7>, "versionNueva": <int> }` | UUIDv7 del `Presupuesto` nuevo |
   | `presupuesto.version_activada` | `{ "version": <int>, "presupuestoPreviamenteVigenteId": <UUIDv7>\|null }` | UUIDv7 del `Presupuesto` activado |
   | `cronograma.editado` | `{ "operacion": "<clave ∈ 6 valores canónicos>" }` | UUIDv7 del `Cronograma` |
   | `documento.exportado` | `{ "formato": "XLSX\|PDF\|MSPDI\|DOCX", "bytes": <long>, "stale": <bool> }` | UUIDv7 del `Presupuesto` afectado o `null` cuando no hay presupuesto asociado |
   | `admin.base_editada` | `{ "operacion": "<clave ∈ 8 valores canónicos>", "cantidadInsumos": <int> }` | UUIDv7 de la `BaseInsumos` central |
   | `admin.plantilla_editada` | `{ "operacion": "crear\|editar\|borrar", "tipo": "SISTEMA" }` | UUIDv7 de la `PlantillaApu` |
   | `admin.parametros_editados` | `{ "porcentajeHerramientaMenor": { "previa": "...", "nueva": "..." }, ... }` (solo campos cambiados) o `{ "clave": "<clave>", "operacion": "insert\|update\|delete" }` | UUIDv7 del `Proyecto` (en cambios a `/proyectos/parametros-sistema`) o `null` (en `/admin/valores-referencia`) |

   Esta matriz **congela** el conjunto exacto de claves de
   detalle. Cualquier clave fuera de ella activará
   `IllegalStateException` en `LogActividadDetalleValidator`
   (decisión 25 de 033) y la fila no se persiste.

3. **Atomicidad (sin ghost events) — solo mutaciones exitosas:** todo
   evento de éxito se emite dentro de la **misma `@Transactional`
   exterior** del servicio que materializa el cambio; si la transacción
   aborta, **no** se emite. `LogActividadService.emitir(...)` se
   anota con `@Transactional(TxType.MANDATORY)` (o equivalente) y
   participa de la transacción exterior; un caller sin tx exterior
   provoca `IllegalStateException` (test focal). Para los servicios
   públicos que hoy no abren `@Transactional` (caso conocido:
   `AuthService.login`), el servicio establece una transacción
   exterior explícita antes de emitir; la aceptación de invitación
   (`AuthService.aceptarInvitacion`) y el flujo de invitación se
   ajustan al mismo patrón. **No existe** un emisor `emitirFailure`,
   `REQUIRES_NEW`, ni persistencia de eventos para operaciones
   fallidas. Las operaciones rechazadas (401/403/404/409) **no**
   producen fila `log_actividad`; el log solo registra cambios
   **efectivos** (transacción commiteada). El catálogo D-13 documenta
   esta regla explícitamente.

4. **Invitación (D-11) — estado inicial y hash inutilizable:**
   `POST /admin/usuarios` crea el `Usuario` con `passwordHash`
   inutilizable, `activo=true`, `emailVerificado=false`. El acta
   032 **documenta el contrato del algoritmo** (algoritmo,
   familia de sal, longitud, fuente aleatoria, política de
   regeneración) que el servicio de invitación aplica; lo que
   **nunca** se documenta fuera del acta, ni en respuestas, ni en
   logs, ni en trazas, ni en código de prueba, ni en código de
   producción es el **valor aleatorio concreto generado** (la
   semilla, el `String` resultado, el `byte[]` subyacente): ese
   es descartado tras el hash. La invitación 72 h viaja por el
   `mail port` existente (`app.app.token.invitacion-ttl: PT72H`);
   el link lleva al `/auth/aceptar-invitacion?token=…` público ya
   implementado. **Nunca** se imprime ni devuelve la contraseña
   temporal. 034 implementa el contrato exactamente como el acta
   lo fija; no inventa estrategias alternativas.

5. **Self-delete / last-active-SUPER_ADMIN — superficie a confirmar
   por el acta (no pre-decide 032):** la matriz canónica de
   comportamientos para `DELETE /admin/usuarios/{id}` (códigos de
   error, mensajes, comportamiento exacto) **no se prefija en este
   plan**. El acta debe:
   - confirmar contra el código existente si existe alguna
     implementación parcial;
   - si no existe, **dejar la decisión abierta** con `STOP-032-DELETE-USUARIO`
     y diferir a una acta humana posterior;
   - el plan 034 cita el acta y **nunca** codifica la matriz como
     canónica. Si el acta la deja abierta, 034 cierra la
     implementación contra el código existente con tests rojos
     previos o la difiere a I-12.

6. **Cambio de email admin (D-03):** la verificación del flujo de
   cambio de email admin (columna auxiliar, re-verificación,
   token, ventana) **no se prefija en este plan**. Si 032 no
   cierra la columna `email_pendiente` ni la existencia del
   endpoint `PUT /admin/usuarios/{id}` para cambio de email, 034
   documenta la superficie real contra el código y no inventa
   campos. El cambio de email sigue siendo flujo P-04 (`PUT /perfil`)
   ya implementado.

7. **Primer SUPER_ADMIN — superficie a confirmar:** la estrategia de
   aprovisionamiento del primer SUPER_ADMIN (seed V002, bootstrap
   externo, registro inicial con flag) **no se prefija en este
   plan**. 034 cita el acta y solo implementa lo que el canon
   vigente permita.

8. **Paginación admin — `Page<T>` canónico estable (decisión
   de drift a corregir):** los GET de listado (`/admin/usuarios`,
   `/admin/logs`, `/admin/bases-centrales?incluirArchivadas=`,
   `/admin/plantillas-apu`, `/admin/valores-referencia`) usan la
   `Page<T>` ya implementada en `common/dto/Page.java` con la forma
   **estable JSON `items, total, page, size, totalPaginas`** y los
   valores por defecto **`size=25` y tope máximo `size=200`**
   (400 `validacion` `tamano-pagina-invalido` cuando se exceda).
   - **Acción de acta:** 032 corrige el drift en
     `thesis-docs/plan/architecture/07-api-contract.md §1` para fijar
     la forma canónica `items, total, page, size, totalPaginas` con
     default `size=25` y tope `size<=200`. **No** se introduce
     cursor pagination en I-11.
   - Los planes 033–040 usan esa forma JSON estable. **Ningún**
     plan usa campos `contenido/totalElementos/totalPaginas` (eso
     era drift histórico; se borra del canon).
   - Nota: la constante Java `Page.java` existente ya tiene
     `items, total, page, size, totalPaginas`; no se renombra.

9. **Plantillas SISTEMA (P-40):** `POST /admin/plantillas-apu` acepta
   `desdeApuId` (UUIDv7 de un APU existente) y `nombre`; el snapshot
   lo construye el backend reusando `SnapshotApuMapper` (ya
   price-free); `tipo=SISTEMA` y `usuario_id=NULL` los fija el
   servidor. **No** se acepta JSONB del cliente. El límite del
   campo `descripcionRubro` lo define la columna real
   (`plantilla_apu.descripcion_rubro`) — **no se inventa tope
   arbitrario**; si la columna es `TEXT`, no hay tope en la capa de
   servicio más allá de la validación nativa. (La auditoría de
   plan 036 confirma la longitud real de columna antes de fijar la
   validación Bean; si la columna es `VARCHAR(N)`, el tope Bean es N
   con un margen de tolerancia.)

10. **`/proyectos/parametros-sistema` permanece canónica (P-41) —
    decisión DTO pendiente:** el endpoint no se mueve a
    `/admin/parametros-sistema`. Lectura pública, escritura
    `@RolesAllowed("SUPER_ADMIN")`; misma superficie. **Decisión
    DTO abierta:** si el `GET` actual devuelve la entidad JPA
    directamente (riesgo de leaky abstraction), 032 acta decide si
    se introduce un DTO canónico de respuesta
    (`ParametrosSistemaResponse`) o se conserva la entidad. **No se
    prefija en este plan.** 037 implementa el DTO canónico solo si
    el acta lo selecciona.
    `/admin/valores-referencia` es la **nueva** ruta para el CRUD del
    segundo tab de S-01 (informativo, textual; nunca entra al motor).
    Sin CAMICON: cero datos sembrados sin licencia/fuente explícitos.

11. **Bases centrales (P-39) — divergencias canónicas a resolver por
    el acta:** 035 audita paridad canónica y cierra **solo** gaps
    estrechos demostrados por TC-P39-01..03; no reescribe el recurso
    existente. **Divergencias que el acta debe resolver antes de 035
    (no se pre-deciden aquí):**
    - **DELETE base central:** canon dice 204/404 directos;
      implementación exige 409 `base-no-archivada` cuando está
      activa. El acta elige el comportamiento canónico.
    - **DELETE insumo en base central:** canon dice 409
      `insumo-en-uso`; implementación actual devuelve 400
      `validacion`. El acta elige el comportamiento canónico y
      define el mapeo de excepciones FK.
    - 035 aplica la decisión del acta solo tras RED con tests que
      reproduzcan el comportamiento actual.
    - **No se usan marcas de paridad `✔`/`⚠`/`✗` fabricadas**;
      solo hechos verificados con test rojo previo.

12. **Solo eventos exitosos emiten:** el catálogo D-13 emite
    **únicamente** mutaciones materiales con commit. Las
    operaciones rechazadas (401/403/404/409) no producen fila
    `log_actividad`. Esta regla se codifica como test focal en
    033 (`emitir-sin-tx` + `rollback-borra-evento`) y se verifica
    en 040 (cobertura completa).

13. **P-42 fundación (033):** `LogActividadService` vive en
    `ec.uce.propuestas.usuario.audit` (subpaquete nuevo del módulo
    `usuario`, **no** un módulo nuevo de primer nivel); la constante
    `LogActividadesEvent` (enum Java `EventoLogActividad`) también.
    La entidad `LogActividad` se aloja en
    `ec.uce.propuestas.usuario.audit.entity` y su repositorio en
    `ec.uce.propuestas.usuario.audit.repository`. El recurso admin
    (`LogActividadResource`) vive en
    `ec.uce.propuestas.usuario.audit.resource` con ruta
    `/admin/logs` y `@RolesAllowed("SUPER_ADMIN")` a nivel de
    clase. Esta ubicación evita crear un módulo nuevo y respeta
    «`common/` solo para utilidades sin identidad de dominio»
    (mismo razonamiento que `common/UuidV7`).

14. **Sin CAMICON en seed ni en valores_referencia.** Cualquier valor
    informativo que requiera licencia/fuente explícita queda fuera
    del MVP; el seed `V004` ya existe y **no** se reabre.

15. **Plan 031 preservado intacto.** Sus cambios pendientes (export
    MSPDI, `BloqueoExportDetalle`, `CronogramaDocumentoResource`,
    `BloqueoExportResponse`, etc.) **no** entran en I-11; 039 los
    reusa tal cual al instrumentar `documento.exportado`.

16. **Drift canónico a corregir (entrega de 032):** el acta debe
    producir ediciones verificables en
    `thesis-docs/plan/architecture/07-api-contract.md §1`
    (forma canónica `Page<T>` con `items, total, page, size,
    totalPaginas`, default `size=25`, tope `size<=200`) y, donde
    proceda, en `§9` para alinear las tablas P-38/P-39/P-40/P-41
    con las decisiones de este plan. Sin esta corrección, 033
    queda STOPPED.

17. **Filas legacy V004 (decisión histórica preservada):** V004
    siembra **19 filas** en `log_actividad`. De ellas, **6 filas
    fixture** usan los **4 nombres legacy** no canónicos
    (`base.insumos.copiada`, `rubro.creado`, `cronograma.creado`,
    `presupuesto.vigente_marcado`) y son **historial legacy**:
    - se conservan en BD sin editarlas;
    - son legibles y filtrables por `evento=`;
    - **nunca** se emiten de nuevo;
    - **nunca** se admiten en el enum `EventoLogActividad` runtime;
    - **se excluyen** de la cobertura exacta de 26 eventos del
      test de cobertura D-13 de 040;
    - 032 **no** autoriza borrado ni backfill; cualquier
      modificación futura requiere un acta humana explícita.
    Las restantes 13 filas usan claves que ya entran al catálogo
    D-13 (`auth.registro`, `auth.login`, `proyecto.creado`,
    `presupuesto.version_creada`, `apu.creado`, `insumo.creado`,
    `documento.exportado`).

18. **Catálogo D-13 cerrado — 26 eventos exactos:** la constante
    Java `EventoLogActividad` (enum) enumera **estrictamente** los
    26 eventos de la sección **Catálogo D-13 verbatim** de este
    plan; nada más. La cobertura de eventos por plan se reparte
    así (tabla explícita, **revisada por el dueño del plan**):

    | # | Evento | Plan | Servicio público |
    |---|---|---|---|
    | 1  | `auth.login` | 038 | `AuthService.login` |
    | 2  | `auth.logout` | 038 | `AuthService.logout` |
    | 3  | `auth.registro` | 038 | `AuthService.registrar` |
    | 4  | `auth.password_cambiada` | 038 | `AuthService.cambiarPassword(...)` (perfil) y `AuthService.restablecerPassword(...)` (reset) — mismo evento canónico, `detalle.origen` distingue |
    | 5  | `usuario.invitado` | 034 | `UsuarioAdminService.invitar` |
    | 6  | `usuario.activado` | 034 (admin) / 038 (aceptación invitación) | `UsuarioAdminService.reactivar`; `AuthService.aceptarInvitacion` |
    | 7  | `usuario.desactivado` | 034 | `UsuarioAdminService.desactivar` |
    | 8  | `proyecto.creado` | 038 | `ProyectoService.crear` |
    | 9  | `proyecto.editado` | 038 | `ProyectoService.actualizar` |
    | 10 | `proyecto.eliminado` | 038 | `ProyectoService.eliminar` |
    | 11 | `proyecto.duplicado` | 038 | `ProyectoService.duplicar(...)` (P-09; **STOP — ver `STOP-032-P09-DUPLICAR`**) |
    | 12 | `insumo.creado` | 038 | `InsumoCrudService.crear` (en base PROYECTO) |
    | 13 | `insumo.editado` | 038 | `InsumoCrudService.actualizar` |
    | 14 | `insumo.eliminado` | 038 | `InsumoCrudService.eliminar` |
    | 15 | `insumos.import_csv` | 038 | `ImportacionInsumoService.importarCsv(...)` |
    | 16 | `base.copiada_a_proyecto` | 038 | `CopiaBaseService.copiar(...)` |
    | 17 | `apu.creado` | 038 | `ApuCrudService.crear` |
    | 18 | `apu.editado` | 038 | `ApuCrudService.actualizar` |
    | 19 | `apu.eliminado` | 038 | `ApuCrudService.eliminar` |
    | 20 | `presupuesto.version_creada` | 039 | `VersionadoService.copiarVersion` |
    | 21 | `presupuesto.version_activada` | 039 | `VersionadoService.marcarVigente` |
    | 22 | `cronograma.editado` | 039 | `CronogramaService` (configurar, programar.*) + `VistasCronogramaService.marcarRevisado` |
    | 23 | `documento.exportado` | 039 | `CronogramaDocumentoResource.descargar` (Plan031) + `DocumentoResource.exportarEspecificacionesTecnicas` (ET DOCX) |
    | 24 | `admin.base_editada` | 035 | `AdminBaseCentralResource` (operaciones canónicas Plan015bis + emisión 035) |
    | 25 | `admin.plantilla_editada` | 036 | `PlantillaApuAdminService` |
    | 26 | `admin.parametros_editados` | 037 | `ParametrosProyectoService.actualizarSistema` + `ValorReferenciaAdminService` |

    **Total: 26 eventos runtime.** Los 4 nombres legacy V004
    (`base.insumos.copiada`, `rubro.creado`, `cronograma.creado`,
    `presupuesto.vigente_marcado`) **no** entran al enum.

19. **Piloto SUS (I-11) vs SUS n ≥ 5 (I-12):** son protocolos
    distintos. 040 entrega artefactos del piloto 1–2; la medición
    poblacional es I-12.

20. **FK exception mapping — tests rojos previos:** las decisiones
    que cierren brechas de mapeo de excepciones FK (por ejemplo,
    `DELETE insumo` con dependencias) requieren un test rojo
    previo que reproduzca la condición de error actual, antes de
    cualquier cambio de código. Sin este test, la implementación
    no cierra la brecha.

## Alcance (recordatorio)

### Incluye

- Acta firmada con las 20 decisiones anteriores verbatim.
- Inventario exacto de archivos a crear/modificar por plan 033–040.
- Matriz `P-xx → plan → TC` con archivos `@QuarkusTest` previstos.
- Verificación de paridad `07-api-contract.md §9` ↔ recursos
  existentes.
- Catálogo D-13 verbatim como enum Java `EventoLogActividad` (no se
  crea el archivo aún; 033 lo materializa).
- Lista de **STOP conditions** que se cierran en este plan.
- **Ediciones de drift** en `thesis-docs/plan/architecture/07-api-contract.md §1`
  y §9 (entrega autorizada por este plan).

### No incluye

- Código de negocio nuevo.
- Reescritura de `AuthService.aceptarInvitacion`,
  `AdminBaseCentralResource`, `ProyectoResource.parametrosSistema`,
  `PlantillaApuService` ni del motor.
- Cambios en `V001__baseline.sql` §2.14/§2.15 (DDL vigente).
- Cualquier cambio en Bruno, Graphify o docs pre-existentes fuera
  de los archivos listados abajo.
- Decisiones sobre superficies no documentadas (decisiones 5, 6,
  7, 10): el acta las deja explícitamente abiertas si no puede
  resolverlas contra el código real.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Crear | `docs/modulos/panel-admin/00-acta-reconciliacion.md` | Acta firmada con 20 decisiones locked. |
| Crear | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Lista priorizada `P-xx → plan → archivos`. |
| Modificar | `docs/modulos/estado-actual.md` | Tabla de capacidades I-11 marcada `PLANNED`. |
| Modificar | `docs/modulos/README.md` | Fila `panel-admin` añadida. |
| Modificar | `plans/panel-admin/README.md` (este subdir) | Referencia al acta. |
| Modificar | `plans/README.md` | Fila de la secuencia I-11 añadida. |
| Modificar | `../../../thesis-docs/plan/architecture/07-api-contract.md` §1 y §9 | Drift canónico corregido (decisión 16). |
| Modificar (opcional) | `docs/00-ESTADO-ACTUAL.md` | Una sola línea: «I-11 PLANNED; ver `plans/panel-admin/`». **Solo si el orquestador lo aprueba** — el padre actualizó el doc, no 032. |
| No previsto | `src/main/java/ec/uce/propuestas/**` | Cualquier cambio activa STOP. |
| No previsto | `src/main/resources/db/migration/**` | Cualquier cambio activa STOP en 032 (la única migración nueva del bloque I-11 se crea en 033). |

## Catálogo D-13 verbatim (a transcribir literal)

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

(26 eventos exactos; el enum Java `EventoLogActividad` los enumera
con `Map<Evento,Set<String>> detallesEsperados()` materializando la
matriz canónica completa del acta 032 decisión 2; sin
sufijos de versión, sin renombramientos, sin abreviaturas, sin
aceptar los 4 nombres legacy V004.)

## STOP conditions (actívalas y reabre el plan si ocurre)

- `STOP-032-V010-NECESARIA`: cualquier columna nueva es indispensable
  (por ejemplo, `log_actividad.public_id`); reabrir el debate antes de
  implementar. **Resolución esperada:** la migración aditiva de
  `public_id` vive en 033 con número siguiente al último aplicado.
- `STOP-032-CONTRADICCION-API`: el canon `07-api-contract.md §9`
  contradice la implementación actual en un endpoint distinto de los
  resueltos arriba; documentar y proponer parche antes de 033.
- `STOP-032-D-13-FUERA`: aparece un evento necesario para I-11 que no
  está en la lista verbatim; reabrir D-13 con el director antes de
  inventarlo.
- `STOP-032-CAMICON`: surge presión de sembrar CAMICON u otro valor
  referencial sin licencia explícita; documentar y negarlo.
- `STOP-032-DUPLICAR-PLAN015BIS`: la auditoría de Plan 015bis detecta
  necesidad de reescritura completa; reabrir 035 antes de 034.
- `STOP-032-FRONTEND-DEPENDIENTE`: 040 (piloto SUS) depende de
  artefactos frontend que no existen en el backend; documentar la
  dependencia humana y NO fabricar el puntaje.
- `STOP-032-PLAN031-AJENO`: los cambios sin commit de Plan 031
  contienen modificaciones que afectan a I-11; resolver antes de
  planificar.
- `STOP-032-DELETE-USUARIO`: la matriz canónica de
  `DELETE /admin/usuarios/{id}` (self-delete / last-admin) no se
  puede confirmar contra el código ni el canon; dejar la decisión
  abierta y diferir 034 o I-12.
- `STOP-032-EMAIL-ADMIN`: el flujo de cambio de email admin no
  tiene canon explícito; dejar la decisión abierta y diferir 034 o
  I-12.
- `STOP-032-FIRST-SUPERADMIN`: la estrategia de aprovisionamiento
  inicial no está canónica; dejar la decisión abierta y diferir 034
  o I-12.
- `STOP-032-DTO-PARAMETROS`: el `GET /proyectos/parametros-sistema`
  expone la entidad JPA directamente y el acta no cierra la decisión
  DTO; 037 queda gated hasta que el acta resuelva.
- `STOP-032-DELETE-BASE-CENTRAL`: el acta no resuelve 204/404 vs
  409 `base-no-archivada`; 035 queda gated.
- `STOP-032-DELETE-INSUMO-CENTRAL`: el acta no resuelve 409
  `insumo-en-uso` vs 400 `validacion`; 035 queda gated.
- `STOP-032-P09-DUPLICAR`: el canon cita `proyecto.duplicado`
  (P-09) como evento canónico D-13, pero la implementación
  actual **no expone** `POST /proyectos/{id}/duplicar` ni un
  método `ProyectoService.duplicar(...)`. El acta debe decidir
  **una** de dos opciones:
  1. **Implementar el seam canónico de P-09** dentro del alcance
     de 032: `ProyectoService.duplicar(UUID proyectoOrigenPublicId,
     Long callerUsuarioId, ProyectoDuplicarRequest req)` +
     `POST /api/v1/proyectos/{id}/duplicar` en `ProyectoResource`,
     con test rojo previo que reproduzca el comportamiento
     actual (ausencia del seam), y emitir `proyecto.duplicado`
     desde 038.
  2. **Diferir el evento** declarando el canon P-09 como
     "no producer yet": el evento `proyecto.duplicado` se
     conserva en el enum `EventoLogActividad` y en la matriz de
     cobertura del test 040, pero ningún emisor runtime lo
     produce hasta que una acta humana posterior autorice la
     implementación del seam.
  038 **no puede** afirmar cobertura runtime completa de los 26
  eventos hasta que este STOP se cierre: o implementa el seam
  canónico o difiere formalmente el evento. 040 refleja el
  cierre del STOP en su wording de cobertura (decisión 61).

## Pasos (orden de ejecución)

1. **Auditar `git status --short` y aislar Plan 031.** Confirmar que
   los cambios sin commit son exactamente los de Plan 031; cualquier
   archivo ajeno activa `STOP-032-PLAN031-AJENO`.
2. **Releer las 6 fuentes verbatim** listadas arriba y transcribir las
   decisiones que el acta debe firmar.
3. **Cruzar `07-api-contract.md §9` con recursos existentes.** Para
   cada fila, confirmar paridad o documentar gap estrecho. Resultado:
   tabla `endpoint | estado actual | gap exacto | plan que lo cierra`.
4. **Transcribir el catálogo D-13 verbatim** (26 eventos) en el
   acta; revisar `auth/AuthService` y
   `insumo/AdminBaseCentralResource` para confirmar que no emiten
   aún (gap esperado).
5. **Inventariar archivos por plan 033–040** con la granularidad
   «archivo → tipo (crear/modificar) → condición». Resultado: 5
   tablas, una por plan 034–037 + una sola tabla para 038/039/040.
6. **Verificar decisiones locked** 1–20 contra la implementación;
   marcar contradicciones en el acta con propuesta de resolución.
   Para las decisiones 5, 6, 7, 10, dejar la superficie explícitamente
   abierta si no se confirma contra código.
7. **Resolver los STOP conditions de divergencias** (11, 12) contra
   código real; documentar la elección en el acta antes de 035.
8. **Corregir el drift canónico** en
   `thesis-docs/plan/architecture/07-api-contract.md §1` (forma
   `Page<T>`, default `size=25`, tope `size<=200`) y §9 (tablas
   P-38/P-39/P-40/P-41) alineadas con las decisiones del acta.
9. **Cerrar STOP conditions** explícitamente (una por una) o
   documentar la que permanece abierta con su `STOP-NNN` y razón.
10. **Publicar el acta y el inventario** en `docs/modulos/panel-admin/`.
11. **Reportar el cierre de 032** en este archivo (no se crea el
    archivo de cierre — la sección «Estado de cierre» se mantiene
    `TODO` hasta que el orquestador lo cambie a `DONE` con la fecha
    del acta firmada y un enlace al archivo).

## TDD (documentation-only)

032 **no tiene ciclo RED/GREEN/TRIANGULATE/REFACTOR** porque no codifica.
Su "test" equivalente es la **revisión por pares** del acta:

- **RED (verificación de no-codificación):** ejecutar
  `git diff --name-only -- 'src/**'` y `git diff --name-only -- 'src/main/resources/db/migration/**'`;
  ambos deben devolver cero archivos modificados. Si devuelven
  archivos, el plan falló su objetivo y se aborta.
- **GREEN (acta firmada):** el archivo
  `docs/modulos/panel-admin/00-acta-reconciliacion.md` existe,
  contiene las 20 decisiones verbatim y cita cada fuente con su
  ruta relativa desde la raíz del backend.
- **TRIANGULATE:** el revisor externo cruza el acta contra
  `git status --short`, `git diff --stat` (cero cambios src/) y el
  catálogo D-13 verbatim; cualquier divergencia reabre el plan.
- **REFACTOR:** si el acta necesita dos iteraciones, la segunda
  iteración debe reducir la longitud total sin perder decisiones.

## Catálogo mínimo de pruebas (verificación)

| Caso | Resultado |
|---|---|
| `git diff --name-only -- 'src/**'` | vacío |
| `git diff --name-only -- 'src/main/resources/db/migration/**'` | vacío |
| Acta existe | `docs/modulos/panel-admin/00-acta-reconciliacion.md` presente |
| 20 decisiones verbatim | grep por cada decisión (`Identidad pública admin`, `Atomicidad`, `Invitación D-11`, `Self-delete`, etc.) encuentra match exacto |
| 26 eventos D-13 | grep del catálogo literal en el acta encuentra 26 líneas |
| Drift `07-api-contract.md §1` corregido | grep por `items,total,page,size,totalPaginas` y `size=25` y `size<=200` |
| Matriz `P-xx → plan → TC` | inventario incluye TC-P38-01..03, TC-P39-01..03, TC-P40-01, TC-P41-01..02, TC-P42-01..02 |
| 14 STOP conditions | cada `STOP-032-*` aparece con estado `cerrada` o `abierta — razón` (incluido `STOP-032-P09-DUPLICAR`) |
| 4 nombres legacy V004 documentados | acta lista sus 4 nombres con nota «histórico, no admitido al enum runtime»; las 6 filas fixture que los usan son legibles/filtrables por `evento=` |

## Comandos de verificación (sin suite completa)

```bash
# Aislar Plan 031 y confirmar 0 cambios ajenos.
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus
git status --short
git diff --name-only -- 'src/**'
git diff --name-only -- 'src/main/resources/db/migration/**'

# Acta e inventario.
test -f docs/modulos/panel-admin/00-acta-reconciliacion.md && \
  test -f docs/modulos/panel-admin/00-inventario-trabajo.md

# Coherencia con el catálogo D-13 (26 eventos) en el acta.
grep -cE '^(auth\.|usuario\.|proyecto\.|insumo|insumos\.|base\.|apu\.|presupuesto\.|cronograma\.|documento\.|admin\.)' \
  docs/modulos/panel-admin/00-acta-reconciliacion.md
# esperado: 26 (un match por evento literal).

# Drift corregido en 07-api-contract.md.
grep -nE 'items,total,page,size,totalPaginas|size=25|size<=200' \
  ../thesis-docs/plan/architecture/07-api-contract.md

# Sin totales de suite completa hardcodeados en este plan.
! grep -nE 'suite completa .* (verde|pass)' \
  plans/panel-admin/032-sincronizar-contrato-inventario-admin.md
```

No se corre `./gradlew test` ni `git diff --check` exhaustivo: 032 es
un plan de documentación y sus verificaciones son de archivos.

## Completion checklist (032)

- [ ] `git status --short` muestra solo cambios de Plan 031; cualquier
      otro archivo activa `STOP-032-PLAN031-AJENO`.
- [ ] Las 6 fuentes verbatim están releídas y citadas en el acta.
- [ ] Las 20 decisiones locked están transcritas verbatim.
- [ ] El catálogo D-13 verbatim (26 eventos) está transcrito.
- [ ] Las 14 STOP conditions tienen estado explícito.
- [ ] Inventario `P-xx → plan → TC → archivos` está completo para
      033–040.
- [ ] `git diff --name-only -- 'src/**'` y
      `git diff --name-only -- 'src/main/resources/db/migration/**'`
      están vacíos.
- [ ] Drift canónico en `07-api-contract.md §1` y §9 corregido.
- [ ] El padre actualiza `docs/00-ESTADO-ACTUAL.md` con la línea
      «I-11 PLANNED; ver `plans/panel-admin/`».
- [ ] El estado de este plan cambia de `TODO` a `DONE` con la fecha
      del acta firmada.

## Handoff al siguiente plan

Cuando 032 cierre:

1. el orquestador inicia **033** (P-42 foundation);
2. el orquestador verifica que el acta está firmada **antes** de
   autorizar código;
3. cualquier contradicción sin cerrar reabre 032 (nunca se reescribe
   033 para "absorber" una decisión);
4. las superficies abiertas en decisiones 5, 6, 7, 10 se documentan
   en el acta con su `STOP-NNN` correspondiente; los planes 034 y
   siguientes citan el acta y no inventan resoluciones.