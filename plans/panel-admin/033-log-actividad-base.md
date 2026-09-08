# 033 — Log de actividad — base (fundación P-42)

**Estado:** DONE (2026-09-08) · I-11 · P-42 / US-39 (foundation).

## Cierre medido (2026-09-08)

Plan 033 entrega la fundación P-42: migración
`V010__log_actividad_identidad_publica.sql`, catálogo cerrado de 26 eventos,
validador de detalle, emisor transaccional y consulta paginada
`GET /admin/logs` exclusiva de `SUPER_ADMIN`.

Decisiones de implementación verificadas:

- `LogActividadService.emitir(...)` conserva
  `@Transactional(TxType.MANDATORY)` y, fuera de una transacción exterior,
  propaga la excepción estándar `jakarta.transaction.TransactionalException`;
  no introduce wrapper propio.
- `detalle` se persiste en JSONB mediante una representación JPA `String`,
  serializada/deserializada explícitamente con `ObjectMapper`. Esta decisión
  evita el fallo de arranque de Quarkus causado por un `FormatMapper`
  personalizado y mantiene el DTO público como `Map<String,Object>`.
- V010 añade `public_id` y `entidad_public_id`; esta última permanece sin FK,
  por lo que el historial sobrevive al borrado de la entidad referenciada.

**Evidencia exacta aportada por la ejecución:**

- `./gradlew test --tests 'ec.uce.propuestas.usuario.audit.*' -Dquarkus.http.test-port=0 --console=plain` → **BUILD SUCCESSFUL**; XML: **26 tests, 0 failures, 0 errors, 0 skipped**.
- `./gradlew test --tests 'ec.uce.propuestas.usuario.*' -Dquarkus.http.test-port=0 --console=plain` → **BUILD SUCCESSFUL**; XML: **51 tests, 0 failures, 0 errors, 0 skipped**.
- `./gradlew test -Dquarkus.http.test-port=0 --console=plain` → **673 tests, 2 failures, 0 errors, 1 skipped**; los únicos fallos son los residuales aceptados GM-19 y GM-20, y la única omisión es GM-24. Una corrida inicial sin puerto dinámico produjo cuatro `QuarkusBindException` de arranque y 500 omisiones en cascada; la repetición con `test-port=0` la reemplaza como resultado autoritativo.
- `spotlessCheck` **PASS**; `build -x test` **PASS**; `git diff` y checks estáticos de superficies prohibidas **PASS**; V010 no define FK sobre `entidad_public_id`.
- **No se creó commit** durante este cierre documental.

> Este plan entrega la **capa transversal** sobre la que 034–039 emiten
> eventos D-13. La tabla `log_actividad` ya existe en V001 (§2.15), con
> FK `usuario_id → usuario ON DELETE SET NULL` y los índices
> `ix_log_fecha DESC` y `ix_log_usuario`. El catálogo cerrado D-13
> (26 eventos, verbatim de `design/03 §J`) vive en un enum Java
> `EventoLogActividad` que rechaza cualquier valor fuera del conjunto.
> Los eventos **exitosos** se emiten en la **misma transacción
> exterior** (sin ghost events). Las operaciones rechazadas no emiten.
>
> **Migración aditiva obligatoria:** este plan crea **una** migración
> nueva con el **siguiente número disponible** a la hora de ejecutar
> 033 (p. ej. `V010__log_actividad_identidad_publica.sql` solo si V010
> no existe; si está ocupado, el siguiente libre). El nombre
> preferido del archivo es **neutral** y describe el alcance completo:
> `V???__log_actividad_identidad_publica.sql` (no
> `V???__log_actividad_public_id.sql`, porque la migración cubre dos
> columnas; ver adenda D-21 del acta 032). La migración añade **dos**
> columnas nuevas a `log_actividad`: (a)
> `log_actividad.public_id UUID NOT NULL DEFAULT uuidv7()` con índice
> único `ux_log_actividad_public_id` y trigger de inmutabilidad
> `trg_log_actividad_public_id_immutable` reusando el patrón de
> `fn_assert_public_id_immutable()` (V001 §5); (b)
> `log_actividad.entidad_public_id UUID NULL` **sin FK**, **sin
> DEFAULT**, **sin UNIQUE** (server-authored, los logs sobreviven al
> borrado de la entidad afectada — aplicar una FK reintroduciría la
> incompatibilidad que D-21 resuelve). La columna legacy
> `log_actividad.entidad_id BIGINT` (V001 §2.15) **no** se toca,
> **no** se convierte y **no** se expone vía REST.
> `LogActividadResponse.entidadId` mapea **exclusivamente** desde
> `entidad_public_id`. **Nunca** se editan V001–V009. **Nunca** se
> crea una segunda migración para I-11: esta es la única aditiva.

## Proceso / historia / criterios

- **Proceso:** P-42.
- **Historia:** US-39 (foundation; verificación de cobertura completa
  en 040).
- **Iteración:** I-11 (semanas 21–22).
- **Criterios de aceptación (foundation):**
  - TC-P42-01: acciones del catálogo D-13 ejecutadas → `GET /admin/logs?evento=`
    devuelve eventos registrados con el nombre exacto del catálogo y
    filtros funcionan.
  - TC-P42-02: log poblado → inspeccionar `detalle` → sin PII ni
    secretos (RNF-08).
- **Estado del proceso P-42:** foundation only; el proceso se considera
  completo cuando 034–039 hayan emitido sus eventos y 040 verifique la
  cobertura TC-P42-01..02 sobre la matriz completa.

## Objetivo medible

El cierre medido demostró que:

1. el enum `EventoLogActividad` enumera los 26 eventos verbatim de
   `design/03 §J D-13` y **nada más** (cero sufijos, cero
   abreviaturas, cero duplicados, sin aceptar los **4 nombres legacy
   V004**);
2. `LogActividadService.emitir(usuarioId, evento, entidad,
   entidadId, detalle)` inserta una fila `log_actividad` cuando se
   invoca dentro de una `@Transactional` exterior (`TxType.MANDATORY`);
   si la transacción aborta, la fila **no** persiste; si se invoca
   sin tx exterior, Jakarta/Quarkus lanza la excepción estándar
   `jakarta.transaction.TransactionalException` y no se escribe fila
   alguna (test focal);
3. los servicios públicos que emiten D-13 (`AuthService.login`,
   `AuthService.logout`, `AuthService.registrar`,
   `AuthService.cambiarPassword`, `AuthService.restablecerPassword`,
   `AuthService.aceptarInvitacion`, `ProyectoService`,
   `InsumoCrudService`, `ImportacionInsumoService`, `CopiaBaseService`,
   `ApuCrudService`) **ya abren** `@Transactional` exterior antes de
   la emisión; la auditoría contra
   `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java`
   confirma que `login` (línea 108) y `aceptarInvitacion` (línea 206)
   están anotadas con `@Transactional`; el resto del servicio
   también. 033 **no** ajusta transacciones exteriores en este pase;
   el plan 032 transcribía un supuesto que el código ya satisface;
   si 038 descubre que un servicio público concreto requiere ajuste,
   reabre 032 antes de tocarlo;
4. `GET /admin/logs?usuarioId&evento&desde&hasta&page…` (todos los
   ids en UUIDv7) devuelve
   `Page<LogActividadResponse>` con la forma canónica estable
   `items, total, page, size, totalPaginas` (default `size=25`,
   tope `size<=200`);
5. `LogActividadResponse` expone `id` UUIDv7, `usuarioId` UUIDv7
   nullable, `usuarioNombre` String (canónico para consumo admin,
   **distinto de `detalle`** — es un campo top-level del DTO y no
   entra al JSONB), `evento` String (clave D-13 verbatim),
   `entidad` String nullable, `entidadId` UUIDv7 nullable
   (**mapeado exclusivamente desde la columna nueva
   `log_actividad.entidad_public_id`**; la columna legacy
   `entidad_id BIGINT` no se expone en REST — ver D-21 del acta),
   `detalle` `Map<String,Object>` (Jackson serializa),
   `fecha` Instant. Los nombres son los canónicos fijados por
   el acta 032 (decisión **Nombres canónicos de respuesta**);
   cualquier campo adicional es opt-in y debe justificarse.
   **No** expone `passwordHash`, tokens, JWT, hashes, correos,
   ni el contenido completo de `especificacion_tecnica` ni de
   `plantilla_apu.snapshot_secciones`;
6. los filtros compuestos (`usuarioId` AND `evento` AND `desde` AND
   `hasta`) producen una sola query con `count(*)` (verificación
   manual vía `quarkus.hibernate.log-sql` en `@QuarkusTest`);
7. el endpoint rechaza `size>200` con 400
   `tamano-pagina-invalido` y exige rol `SUPER_ADMIN` (USUARIO
   recibe 403). `evento=` con charset inseguro o longitud > 60
   → 400 (`evento-formato-invalido` / `evento-largo`); un valor
   con charset y longitud válidos pero ausente del catálogo
   devuelve 200 con página vacía, **no** 400.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada; las 21 decisiones locked vigentes (20 originales + adenda firmada D-21); STOP-032 sin abrir. | Habilita código. |
| G1 — tabla `log_actividad` | V001 §2.15 vigente (BIGINT PK, `usuario_id ON DELETE SET NULL`, índices `ix_log_fecha DESC`, `ix_log_usuario`). | Habilita repositorio sin migración. |
| G2 — token auth | `TokenService` y `AuthService` existentes; `mail port` operativo. | Habilita el emisor `usuario.invitado` desde 034 sin acoplar. |
| G3 — UUIDv7 cerrado | `UuidV7.parse` en frontera REST; PK/FK BIGINT internas. | Habilita el filtro `usuarioId` UUIDv7 (no BIGINT) y `entidadId` UUIDv7 nullable. |
| G4 — errores `problem+json` | `ProblemaException` + `GlobalExceptionMapper` ya en uso. | Habilita errores 400/403/404 tipados. |
| G5 — paginación canónica | `Page<T>` ya definida en `common/dto/Page.java` con `items, total, page, size, totalPaginas`. | Habilita `size<=200` y default 25. |
| G6 — cierre | Focales, TDD completa, suite dirigida verde. | Evidencia medible. |

`STOP-033-CATALOGO-AJENO` se activa si una prueba exige un evento que
no está en los 26 del canon. Reabrir 032.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  (acta firmada; decisiones 1–20).
- `../../../thesis-docs/plan/design/03-procesos-detalle.md` §H (P-42)
  y §J (catálogo D-13 literal).
- `../../../thesis-docs/plan/architecture/07-api-contract.md` §9 fila
  `GET /admin/logs?usuarioId&evento&desde&hasta&page…` y §1 (errores,
  paginación, forma `items,total,page,size,totalPaginas`).
- `../../../thesis-docs/plan/architecture/06-database-schema.md` §2.15
  (DDL `log_actividad`) y §3 (FK `usuario_id ON DELETE SET NULL`).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P42-01..02.
- `../../../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` I-11
  (semanas 21–22).
- `src/main/resources/db/migration/V001__baseline.sql` §2.15 (DDL
  verbatim) y §5 (índices `ix_log_fecha`, `ix_log_usuario`).
- `src/main/resources/db/migration/V004__seed_escenarios.sql` (4 filas
  legacy fuera del catálogo; ver 032 decisión 17).
- `src/main/java/ec/uce/propuestas/common/ProblemaException.java`
  y `common/dto/Page.java` (forma canónica `Page<T>`).
- `src/main/java/ec/uce/propuestas/common/UuidV7.java` (parser
  canónico en frontera).
- `src/main/java/ec/uce/propuestas/usuario/UsuarioRepository.java`
  (consulta por `publicId` para resolver filtros `usuarioId`).

## Estado inicial esperado

- `log_actividad` **no está vacía**: V004 siembra **19 log rows**;
  **6 filas fixture** usan los **4 nombres legacy no canónicos**
  (`base.insumos.copiada`, `rubro.creado`, `cronograma.creado`,
  `presupuesto.vigente_marcado`). Las **restantes 13 filas** usan
  claves que ya entran al catálogo cerrado D-13 (`auth.registro`,
  `auth.login`, `proyecto.creado`, `presupuesto.version_creada`,
  `apu.creado`, `insumo.creado`, `documento.exportado`). Los
  **4 nombres legacy son historial preservado**: 033 **no** los
  borra, **no** los re-seed y **no** los admite al enum runtime
  (decisión 17 de 032). Los TC-P42-01..02 deben filtrar
  `evento`/`fecha`/`usuario_id` para distinguir filas V004 vs filas
  emitidas por el propio test.
- V004 contiene los **4 nombres legacy no canónicos**; 033 **no** los
  toca.
- Sin servicio emisor: el único log que hoy escribe el sistema es el
  `LOG.infof` de `auth/AuthService` (no persistente).
- `ParametrosSistema` y `ParametrosProyecto` ya emiten validación,
  pero **no** eventos D-13 (gap previsto).
- `AdminBaseCentralResource` (Plan 015bis) **no** emite eventos
  D-13 (gap previsto, lo cierra 035).

## Decisiones locked adicionales (033)

Estas decisiones se suman a las 20 de 032; no las contradicen:

21. **Gate 032 autoritativo (transición):** la transición 032 → 033
    es **gate documental autoritativo**: hasta que el acta 032 esté
    firmada, las decisiones 1–21 vigentes (20 originales + adenda firmada D-21), y los `STOP-032-*`
    abiertos del acta estén cerrados, 033 queda **STOPPED**. 033
    no introduce superficies nuevas; cuando el acta abre una
    superficie, 033 la documenta como cerrada/abierta pero **no**
    la codifica por su cuenta. Cualquier contradicción que 033
    descubra contra el acta reabre 032; nunca se reescribe 033 para
    absorber la decisión.
22. **Paquete destino:** `ec.uce.propuestas.usuario.audit`
    (`entity/`, `repository/`, `service/`, `dto/`, `resource/`). No
    se crea un módulo nuevo de primer nivel.
23. **`EventoLogActividad` = enum Java** con un valor por cada
    uno de los 26 eventos verbatim. Métodos del enum:
    `String value()` (clave D-13), `String descripcionEs()`
    (mensaje en español neutro), y un método estático
    `Map<Evento, Set<String>> detallesEsperados()` que materializa
    **completa** la matriz canónica publicada por el acta 032
    (decisión 2): 033 implementa los 26 sets verbatim y nada más.
    El runtime emite **solo** valores del enum; cualquier intento
    de persistir un valor fuera del enum levanta
    `IllegalArgumentException` (test focal). Los **4 nombres legacy
    V004** **nunca** se admiten al enum y quedan excluidos de
    la cobertura exacta D-13 del test de cobertura 040.
24. **`LogActividadService.emitir(...)` exige `TxType.MANDATORY`.**
    Llamarlo fuera de una transacción exterior activa la semántica
    estándar de Jakarta/Quarkus y lanza
    `jakarta.transaction.TransactionalException` (verificado por test),
    sin escribir fila alguna. No se crea un wrapper para convertirla en
    `IllegalStateException`. Sin `MANDATORY`, un caller olvidadizo podría
    escribir eventos "fantasma".
25. **`detalle` JSONB se serializa con Jackson y se valida contra
    un allowlist completo y cerrado por evento.** `033` implementa
    **en este plan** el mapa completo `detallesEsperados()` en
    `EventoLogActividad` con la **matriz canónica evento → claves
    permitidas** publicada por el acta 032 (decisión 2). Esa matriz
    es **la única fuente** de verdad para las claves de detalle;
    los planes 034–039 la consumen verbatim vía
    `LogActividadDetalleValidator.validar(evento, detalle)` (creado
    por 033 en `usuario/audit/`) — no la amplían, no redefinen
    claves, no agregan eventos al enum. Si una clave faltara, el
    plan afectado reabre 032; nunca se reescribe 033 para absorber
    una decisión.
    `LogActividadDetalleValidator` rechaza claves ajenas lanzando
    `IllegalArgumentException` (evento desconocido) o
    `IllegalStateException` (clave de detalle no permitida); la
    transacción exterior hace rollback y no se persiste el evento.
    La firma del método `emitir` exige `Map<String,Object>` (no
    `String`) para imposibilitar free-text server-authored.
26. **`GET /admin/logs`:** filtros `usuarioId` (UUIDv7), `evento`
    (`String` libre — es un parámetro de query **seguro**, **no**
    se parsea contra el enum `EventoLogActividad`), `desde` y
    `hasta` (ISO `8601` `Instant`; `desde > hasta` → 400
    `validacion` `rango-fechas-invalido`), `page` (default 0),
    `size` (default 25, tope `size<=200` → 400
    `tamano-pagina-invalido`). Orden por `fecha DESC` (usa
    `ix_log_fecha`). Semántica del filtro `evento=`:
    - **Longitud:** máximo 60 caracteres (alineado con la columna
      `log_actividad.evento VARCHAR(60)`); `> 60` → 400
      `validacion` `evento-largo`.
    - **Charset seguro:** solo `[a-z0-9._-]`; cualquier otro
      carácter → 400 `validacion` `evento-formato-invalido`.
    - **Contenido:** la query compara como `String` exacto contra
      la columna `evento`. Esto **no** rechaza las 6 filas legacy
      V004 con los 4 nombres legacy (`base.insumos.copiada`,
      `rubro.creado`, `cronograma.creado`,
      `presupuesto.vigente_marcado`): son legibles y filtrables por
      `evento=`, aunque estén excluidas del enum runtime.
    - **`evento=no.en.catalogo` (con charset y longitud válidos):**
      el filtro se ejecuta como `WHERE evento = 'no.en.catalogo'`
      y devuelve **200 con página vacía** (`items: []`,
      `total: 0`, `totalPaginas: 0`); **no** es 400. Esto
      preserva la regla de que el caller admin puede explorar
      cualquier valor histórico o futuro sin verse rechazado por
      el enum.
    - El **único** caso donde `evento` cruza y se valida con
      parseo de enum es cuando el caller pide **emisión** (vía
      `LogActividadService.emitir(...)`); allí el rechazo es
      `IllegalArgumentException` interno (no 400 al cliente — el
      caller es siempre server-authored).
27. **Sin PII (TC-P42-02):** el test `LogActividadSinPiiTest`
    ejecuta cada uno de los 26 eventos con un fixture mínimo y
    asserta que el JSON serializado **no contiene** ninguno de:
    `@`, `passwordHash`, `token`, `jwt`, `bearer`, `Bearer`,
    `Hash`, `hash$`, `ipOrigen`, `emailDestino`, `correo`, ni un correo con formato
    `text@text.tld` (regex `@\S+\.\S+`). Si falla, 033 se aborta
    y reabre.
28. **Servicios públicos con `@Transactional` ya existente:** la
    auditoría contra
    `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java`
    confirma que `AuthService.login` (línea 108) y
    `AuthService.aceptarInvitacion` (línea 206) **ya están**
    anotadas con `@Transactional` (verificación reproducible por
    el revisor). 033 **no** asume ajustes de transacciones
    exteriores; los emisores `MANDATORY` operan bajo la tx
    exterior ya existente de cada servicio público. Si un servicio
    público concreto requiere ajuste durante la implementación
    de 038, el plan reabre 032 antes de tocar el servicio. El
    test focal `MANDATORY-sin-tx-lanza-TransactionalException`
    sigue aplicando como red de seguridad para futuros emisores
    que olviden abrir la tx exterior.

## Alcance

### Incluye

- Constante `EventoLogActividad` (enum) con 26 entradas verbatim.
- Entidad JPA `LogActividad` con `publicId` UUIDv7.
- Repositorio `LogActividadRepository` con consulta owner-agnostic
  (es admin; sin owner-scope), filtros compuestos y paginación.
- DTO `LogActividadResponse` y request helpers (sin request body para
  GET).
- Servicio `LogActividadService` con `emitir(...)` (`MANDATORY`).
- Recurso JAX-RS `LogActividadResource` con `@RolesAllowed("SUPER_ADMIN")`
  y `GET /admin/logs` con la query aprobada.
- **Una** migración aditiva con el siguiente número disponible al
  ejecutar el plan; nombre preferido
  **`V???__log_actividad_identidad_publica.sql`** (no
  `V???__log_actividad_public_id.sql`, porque la migración cubre dos
  columnas; ver adenda D-21 del acta 032):
  - añade `log_actividad.public_id UUID NOT NULL DEFAULT uuidv7()`;
  - crea índice único `ux_log_actividad_public_id`;
  - crea trigger `trg_log_actividad_public_id_immutable` que
    reusa `fn_assert_public_id_immutable()` (V001 §5);
  - añade `log_actividad.entidad_public_id UUID NULL` **sin FK**,
    **sin DEFAULT**, **sin UNIQUE** (server-authored; los logs
    sobreviven al borrado de la entidad afectada — aplicar una FK
    reintroduciría la incompatibilidad que D-21 resuelve);
  - **no** edita V001–V009 ni la columna legacy
    `log_actividad.entidad_id BIGINT`;
  - **no** pre-asigna V010 ciegamente: el nombre se fija en el
    paso de ejecución de 033 verificando
    `ls src/main/resources/db/migration/` y eligiendo el siguiente
    libre.
- Tests `@QuarkusTest`:
  - `EventoLogActividadTest` (26 entradas; unicidad; enum rechaza
    valor fuera del catálogo);
  - `LogActividadServiceTest` (`MANDATORY` lanza sin tx;
    `MANDATORY` con tx persiste; rollback exterior borra el evento);
  - `LogActividadSinPiiTest` (26 eventos × 1 fixture mínimo;
    regex de PII/secretos devuelve 0 matches);
  - `LogActividadResourceIT` (TC-P42-01..02: filtros, paginación,
    `size>200` → 400, USUARIO → 403, evento seguro sin filas → 200).

### No incluye

- Emisores por capacidad (vienen en 034–039).
- Endpoints adicionales (POST/DELETE/PATCH sobre logs son
  **admin-only** y no existen; el log es append-only).
- Persistencia del `LOG.infof` actual de `AuthService` (queda solo en
  servidor; la decisión de moverlo a D-13 vive en 038).
- Exportar logs a CSV/archivo (fuera de I-11).
- Rotación / particionado de tabla (fuera de I-11).
- Reescritura de `AuthService` para abrir tx exterior (responsabilidad
  de 034/038).

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/usuario/audit/EventoLogActividad.java` | Enum Java con 26 entradas verbatim. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/audit/entity/LogActividad.java` | JPA con `publicId` UUIDv7 y `entidadPublicId` UUIDv7 nullable; mapea V001 §2.15 + migración aditiva (incluye `entidadIdLegacy` con `@Column(name="entidad_id")` para preservar la columna legacy BIGINT **sin** exponerla en el DTO). |
| Crear | `src/main/java/ec/uce/propuestas/usuario/audit/repository/LogActividadRepository.java` | Panache; filtros `usuarioId`, `evento`, `desde`, `hasta`; orden `fecha DESC`; `Page<LogActividad>` con `count(*)` separado. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/audit/service/LogActividadService.java` | `@ApplicationScoped`; método `emitir` (`@Transactional(MANDATORY)`); popula `entidadPublicId` server-authored desde el `entidadId` (UUIDv7) provisto por el caller admin; **nunca** deriva nada de la columna legacy `entidad_id BIGINT`. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/audit/dto/LogActividadResponse.java` | Record canónico (decisión 5 del plan); `entidadId` mapeado desde `entidadPublicId` (no desde `entidadIdLegacy`); **sin** campos derivados de la columna legacy. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/audit/dto/LogActividadFiltros.java` | Validación Bean (`@QueryParam`) y normalización. |
| Crear | `src/main/java/ec/uce/propuestas/usuario/audit/resource/LogActividadResource.java` | `@Path("/admin/logs")` + `@RolesAllowed("SUPER_ADMIN")`; `GET` paginado. |
| Crear | `src/main/resources/db/migration/V???__log_actividad_identidad_publica.sql` | Una sola migración aditiva con el siguiente número disponible (preferentemente V010 si está libre). Nombre **neutral** que describe el alcance completo. La migración añade **ambas** columnas: `public_id UUID NOT NULL DEFAULT uuidv7()` + `ux_log_actividad_public_id` UNIQUE + trigger `trg_log_actividad_public_id_immutable` reusando `fn_assert_public_id_immutable()` (V001 §5); `entidad_public_id UUID NULL` sin FK, sin DEFAULT, sin UNIQUE (D-21). **Nunca** V010 a ciegas; el número se confirma en el paso de ejecución de 033. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/audit/EventoLogActividadTest.java` | 26 entradas; unicidad; enum rechaza valor fuera del catálogo. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/audit/LogActividadServiceTest.java` | `@QuarkusTest` con Dev Services PostgreSQL; verifica `MANDATORY`. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/audit/LogActividadSinPiiTest.java` | 26 eventos × fixture mínimo; regex PII/secretos. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/audit/resource/LogActividadResourceIT.java` | TC-P42-01..02; filtros, paginación, `size>200`, USUARIO 403, evento seguro sin filas → 200 y nombres legacy filtrables. |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marca 033 `DONE` cuando cierre. |
| No previsto | `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java` | STOP en 033 (034/038 cubren el ajuste de tx exterior). |
| No previsto | `src/main/resources/db/migration/V001__baseline.sql` … `V009__cronograma_persistencia.sql` | STOP (cancelado por 032). |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**`, `recalculo/**` | STOP. |
| No previsto | `src/main/java/ec/uce/propuestas/common/**` (mover `ProblemaException` o `UuidV7`) | STOP; el paquete `usuario.audit` los reusa, no los mueve. |

## Modelo de datos (con migración aditiva)

`log_actividad` queda **idéntico** a V001 §2.15 más la columna
`public_id` añadida por la migración aditiva:

```
id                BIGINT GENERATED ALWAYS AS IDENTITY PK
public_id         UUID NOT NULL DEFAULT uuidv7()        -- añadida por V??? (D-01)
entidad_public_id UUID NULL                             -- añadida por V??? (D-21; sin FK)
usuario_id        BIGINT  REFERENCES usuario(id) ON DELETE SET NULL
evento            VARCHAR(60) NOT NULL                  -- validado contra EventoLogActividad en runtime
entidad           VARCHAR(30)
entidad_id        BIGINT                                -- legacy-only (V001 §2.15); nunca se expone vía REST
detalle           JSONB
created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
```

Índices (V001 §5 + nueva migración):

- `ix_log_fecha ON log_actividad(created_at DESC)` — orden natural del
  log.
- `ix_log_usuario ON log_actividad(usuario_id)` — filtro por usuario.
- `ux_log_actividad_public_id` (UNIQUE) — añadido por la migración
  aditiva.

Trigger (nueva migración):

- `trg_log_actividad_public_id_immutable` reusa
  `fn_assert_public_id_immutable()` (V001 §5) — la columna no se
  puede modificar tras el `INSERT`.

Restricciones y notas adicionales sobre `entidad_public_id` (D-21):

- **Sin** `UNIQUE`: una entidad puede aparecer como afectada en
  múltiples filas de log.
- **Sin** `FOREIGN KEY`: los logs sobreviven al borrado de la entidad
  afectada; aplicar una FK reintroduciría la incompatibilidad que
  D-21 resuelve.
- **Sin** `DEFAULT`: el valor lo fija exclusivamente
  `LogActividadService.emitir(...)` (server-authored); las filas
  V004 existentes (19/19) y cualquier fila histórica pre-033 quedan
  con la columna en `NULL` y el DTO devuelve `entidadId: null`.

El plan 033 verifica la existencia de los índices y la ausencia de
FK sobre `entidad_public_id` en un `@QuarkusTest`
(`LogActividadIndicesTest`) leyendo de `pg_indexes` y
`pg_constraint`.

## API

### Endpoint

```
GET /api/v1/admin/logs?usuarioId=&evento=&desde=&hasta=&page=&size=
Authorization: Bearer <SUPER_ADMIN>
```

### Respuesta 200 (Page<LogActividadResponse>)

```json
{
  "items": [
    {
      "id": "0192f6c4-7c8a-7abc-8000-000000000017",
      "usuarioId": "0192f6c4-7c8a-7abc-8000-000000000004",
      "usuarioNombre": "juan.perez",
      "evento": "auth.login",
      "entidad": null,
      "entidadId": null,
      "detalle": { "resultado": "ok" },
      "fecha": "2026-09-08T14:23:11.482Z"
    }
  ],
  "total": 17,
  "page": 0,
  "size": 25,
  "totalPaginas": 1
}
```

### Errores

| Código | Tipo | Cuándo |
|---|---|---|
| 400 `validacion` | `evento-formato-invalido` | `evento=` contiene caracteres fuera de `[a-z0-9._-]`. |
| 400 `validacion` | `evento-largo` | `evento=` excede 60 caracteres. |
| 400 `validacion` | `rango-fechas-invalido` | `desde > hasta`. |
| 400 `validacion` | `tamano-pagina-invalido` | `size > 200` o `size < 1`. |
| 400 `validacion` | `parametro-invalido` | `usuarioId` no es UUIDv7 o vacío. |
| 200 `OK` | — | `evento=` con charset y longitud válidos pero **no presente** en el catálogo: la query devuelve página vacía. **No** se devuelve 400 `evento-desconocido` (ese código nunca aplica al filtro `GET /admin/logs`). |
| 403 `forbidden` | — | Caller con rol `USUARIO`. |

> **Decisión locked 29 (nueva):** el caller con `rol=SUPER_ADMIN`
> puede filtrar por cualquier `usuarioId`; el caller `USUARIO`
> recibe 403 sin pistas (no 404 con filtro, RNF-05). Esta regla
> sigue la matriz `07-api-contract.md §1`.

## Secuencia TDD (estricta)

### RED

1. `EventoLogActividadTest`: 26 aserciones `assertEquals(value(),
   <constante>)`; `assertEquals(26, valores().size())`; `assertTrue`
   para descripciones no vacías; `assertThrows` para valor fuera del
   catálogo.
2. `LogActividadServiceTest`: `@QuarkusTest` con Dev Services
   PostgreSQL (no mocks);
   `assertThrows(TransactionalException.class, () ->
   service.emitir(...))` cuando no hay `@Transactional` exterior
   (verifica la semántica estándar de `MANDATORY`) y asserta que no
   se escribió ninguna fila.
3. `LogActividadResourceIT`: `GET /admin/logs` con evento válido y
   fixture `LogActividad` insertada vía repo directo → asserta
   `evento == "auth.login"`; con `size=201` → 400; con
   evento `"no.en.catalogo"` (charset y longitud válidos) → 200 con
   página vacía; con `evento="<<injection>>"` (charset inseguro) →
   400 `evento-formato-invalido`; con `evento=<cadena 61 chars>` →
   400 `evento-largo`; con `evento=base.insumos.copiada` (legacy
   V004) → 200 con la fila histórica si existe; con rol `USUARIO` →
   403.
4. `LogActividadSinPiiTest`: para cada uno de los 26 eventos usa el
   detalle mínimo válido definido por la matriz congelada de 032 y
   asserta que `mapper.writeValueAsString(row.detalle)` no contiene
   ninguno de los patrones regex de PII/secretos.

### GREEN

Construir enum, entidad, repositorio, servicio y recurso. La
implementación mínima no optimiza: una sola query con filtros y
orden `created_at DESC`.

### TRIANGULATE

- Filtro solo `usuarioId` (1 fila esperada).
- Filtro solo `evento` (N filas esperadas, todas con ese evento).
- Filtro `evento` + `desde..hasta` (rango vacío vs rango con datos).
- Concurrencia: dos `emitir` simultáneos en la misma tx exterior (un
  test `@QuarkusTest` con `@QuarkusTransactionScenario` no es
  necesario; basta con dos invocaciones secuenciales con
  `assertEquals(2, count)`).
- **Rollback exterior borra el evento:** un `@Test` que envuelve un
  servicio con `@QuarkusTransactionException` simulada → asserta que
  la fila del evento **no** persiste.
- **Fila legacy V004 sigue legible:** una fila con
  `evento='base.insumos.copiada'` insertada en BD (simulando V004)
  se devuelve con `GET /admin/logs?evento=base.insumos.copiada`
  pero **no** se admite al enum `EventoLogActividad` runtime.

### REFACTOR

- `LogActividadDetalleValidator` se materializa **en 033**: 033
  define el allowlist completo en
  `EventoLogActividad.detallesEsperados()` y crea el validador
  que 034–039 consumen desde la primera emisión real. Si el
  validador rechaza una clave, la fila no se persiste y la
  transacción exterior hace rollback.
- Mover `EventoLogActividad.detallesEsperados()` a un resource
  bundle si crece; 033 lo deja inline.

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| Catálogo cerrado | 26 constantes; ninguna fuera; ninguna duplicada. |
| Enum rechaza valor fuera | `IllegalArgumentException`. |
| `MANDATORY` sin tx | `jakarta.transaction.TransactionalException`; cero filas escritas. |
| `MANDATORY` con tx | fila persiste; rollback exterior la borra. |
| `emitir` puebla `entidad_public_id` desde el `entidadId` UUIDv7 provisto | fila persistida con `entidad_public_id` igual al UUIDv7 del caller; `entidad_id` BIGINT queda `NULL` (no se completa desde el caller). |
| Fila nueva con `entidad_public_id` poblado | `GET /admin/logs` → `entidadId` serializado con ese mismo UUIDv7. |
| Fila V004 (legacy) con `entidad_public_id IS NULL` | `GET /admin/logs` → `entidadId` serializado como `null`; las 19 filas V004 responden con `entidadId: null` y son identificables por `id`, `usuarioId` y `fecha`. |
| DTO **no** expone ningún campo derivado de `entidad_id` | el JSON no contiene `entidadIdLegacy`, `entidadIdInterno`, `entidadIdBigint` ni equivalentes; verificable con grep y aserción sobre la respuesta. |
| `GET /admin/logs` sin filtros | última fila primero; `Page<T>` canónica estable. |
| Filtro `evento=auth.login` | solo eventos con esa clave. |
| Filtro `usuarioId` (UUIDv7) | solo eventos del usuario. |
| Filtro `desde..hasta` | solo eventos en rango. |
| Concatenación de filtros | intersección (AND). |
| `size>200` | 400 `validacion` `tamano-pagina-invalido`. |
| `size=0` | 400 `validacion`. |
| `evento=no.en.catalogo` (charset y longitud válidos) | 200 con `items: []`, `total: 0`, `totalPaginas: 0` (**no** 400). |
| `evento=<<charset inseguro>>` | 400 `validacion` `evento-formato-invalido`. |
| `evento=<<cadena > 60 caracteres>>` | 400 `validacion` `evento-largo`. |
| `evento=base.insumos.copiada` (legacy V004) | 200 con las 1–2 filas históricas; el enum runtime no la enumera. |
| `desde > hasta` | 400 `validacion` `rango-fechas-invalido`. |
| Caller `USUARIO` | 403 sin pistas. |
| Sin PII en `detalle` (TC-P42-02) | 0 matches regex. |
| Índices vigentes | `ix_log_fecha`, `ix_log_usuario`, `ux_log_actividad_public_id` existen en `pg_indexes`. |
| Sin FK sobre `entidad_public_id` | `pg_constraint` no contiene una `FOREIGN KEY` que apunte desde `log_actividad.entidad_public_id`. |
| Migración aditiva | única; número siguiente disponible; sin edición de V001–V009; añade `public_id` y `entidad_public_id` en el mismo archivo. |
| Fila legacy V004 legible pero no admitida al enum | filtro `evento=base.insumos.copiada` devuelve la fila; el enum runtime no la enumera; el DTO expone `entidadId: null`. |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Focales del plan.
./gradlew test --tests 'ec.uce.propuestas.usuario.audit.*' \
  -Dquarkus.http.test-port=0 --console=plain
# esperado: BUILD SUCCESSFUL; cada clase verde.

# Regresión de auth/usuario (los emisores D-13 aún no tocan este módulo
# en 033, pero el seam nuevo no debe romper lo existente).
./gradlew test --tests 'ec.uce.propuestas.usuario.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Regla UUIDv7: el nuevo recurso admin expone y acepta UUIDv7 en
# `id`, `usuarioId`, `entidadId` (todos nullable o no).
grep -RInE 'id = Long|usuarioId.*Long|entidadId.*Long' \
  src/main/java/ec/uce/propuestas/usuario/audit/

# Garantía D-21: el DTO LogActividadResponse NO expone ningún campo
# derivado de la columna legacy `entidad_id BIGINT` (el mapeo a
# `entidadId` se hace exclusivamente desde `entidad_public_id`).
! grep -RInE 'entidadIdLegacy|entidadIdInterno|entidadIdBigint|entidadIdV001' \
  src/main/java/ec/uce/propuestas/usuario/audit/

# D-21: la migración añade `entidad_public_id` **sin** FK. Verificar
# que el DDL de la nueva migración no incluye `REFERENCES`.
! grep -nE 'entidad_public_id.*REFERENCES|REFERENCES.*entidad_public_id' \
  src/main/resources/db/migration/V???__log_actividad_identidad_publica.sql

# Catálogo cerrado: ningún archivo de I-11 añade una constante de
# evento fuera de `EventoLogActividad`.
! grep -RInE '"(auth|usuario|proyecto|insumo|insumos|base|apu|presupuesto|cronograma|documento|admin)\.' \
  src/main/java/ec/uce/propuestas/ \
  | grep -v 'EventoLogActividad.java'

# Forma canónica Page<T>.
grep -RInE 'items,|total,|page,|size,|totalPaginas' \
  src/main/java/ec/uce/propuestas/usuario/audit/

# Una sola migración aditiva; sin edición de V001–V009.
ls src/main/resources/db/migration/
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío en V001–V009; una sola aditiva nueva con el
#           siguiente número disponible.

# Motor intacto.
git diff --name-only -- 'src/main/java/ec/uce/propuestas/motor/**' \
  -- 'src/main/java/ec/uce/propuestas/recalculo/**'
# esperado: vacío.
```

Los comandos se conservan como procedimiento reproducible; los resultados
medidos del cierre están registrados al inicio del plan.

## Completion checklist (033)

- [x] Acta 032 firmada y citada en este plan; adenda D-21 incorporada.
- [x] Enum `EventoLogActividad` con 26 entradas verbatim; rechaza
      valores fuera del catálogo; **4 nombres legacy V004 excluidos**.
- [x] Entidad `LogActividad` mapea V001 §2.15 + migración aditiva
      con `public_id` UUIDv7 + `entidad_public_id UUID NULL` (D-21;
      sin FK, sin DEFAULT, sin UNIQUE).
- [x] `LogActividadService.emitir` rechaza sin tx exterior
      (`MANDATORY`) y popula `entidad_public_id` server-authored desde
      el `entidadId` UUIDv7 provisto por el caller admin.
- [x] `GET /admin/logs` filtra por `usuarioId` UUIDv7, `evento`,
      `desde`, `hasta`, `page`, `size` con la forma canónica
      `Page<T>` (`items,total,page,size,totalPaginas`); las filas
      nuevas exponen `entidadId` mapeado desde `entidad_public_id`,
      las filas V004 (legacy) exponen `entidadId: null`.
- [x] DTO `LogActividadResponse` **no** expone ningún campo derivado
      de la columna legacy `entidad_id BIGINT` (verificable con grep y
      assertion sobre el JSON serializado).
- [x] `size>200` → 400; evento con formato inseguro → 400; evento
      seguro sin coincidencias → 200 con página vacía; USUARIO → 403.
- [x] TC-P42-01 (filtros) y TC-P42-02 (sin PII) verdes en
      `@QuarkusTest`; los nuevos casos focales sobre
      `entidad_public_id` también verdes
      (`emitir-puebla-entidad-public-id`,
      `filas-legacy-sin-entidad-public-id-devuelven-null`,
      `dto-no-expone-entidad-id-legacy`).
- [x] Una sola migración aditiva con el siguiente número disponible;
      nombre neutral `V010__log_actividad_identidad_publica.sql`;
      V001–V009 intactas; la migración añade **ambas** columnas
      (`public_id` con índice único + trigger de inmutabilidad;
      `entidad_public_id` sin FK).
- [x] Ningún archivo de `motor/`, `recalculo/`, `common/`,
      `presupuesto/`, `cronograma/`, `apu/`, `insumo/`, `proyecto/`,
      `plantilla/`, `documento/` modificado.
- [x] `git diff --check` limpio en archivos creados.

## Handoff al siguiente plan

Plan 033 cerró el 2026-09-08. La siguiente tarea autorizada es **034**
(P-38 gestión de usuarios e invitaciones). Los planes 034–037 dependen de
esta fundación y no pueden emitir eventos duplicados con 033; la fundación
ya cubre el recurso de
lectura y el enum runtime. La primera emisión real viene en 034
(`usuario.invitado`, `usuario.activado`, `usuario.desactivado`).