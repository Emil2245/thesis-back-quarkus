# I-11 — Panel Super-Admin y piloto SUS

> Subdirectorio de planes ejecutables para la iteración **I-11** (semanas
> 21–22) del roadmap XP. Cada plan es autocontenido: una ejecutora menos
> capaz con cero contexto de la conversación de planificación debe poder
> abrir un archivo, seguir sus pasos, correr sus comandos de verificación y
> entregar el cambio. Esta es la **secuencia canónica** del panel
> Super-Admin; ningún plan introduce un módulo nuevo de primer nivel y
> ningún plan reabre el motor, los snapshots, `recalculo/`, V001–V009 ni
> los planes ya ejecutados (015, 019–031).
>
> **032 es el único plan autorizado para resolver lagunas canónicas.** Los
> planes 033–040 citan el acta firmada de 032 y nunca pre-deciden
> superficies que el acta pueda dejar abiertas. **El acta está
> firmada al 2026-09-07** (ver
> [`docs/modulos/panel-admin/00-acta-reconciliacion.md`](../../docs/modulos/panel-admin/00-acta-reconciliacion.md));
> 033–038 quedaron **DONE al 2026-09-08**; 039 es la siguiente tarea autorizada.
>
> **Emisión D-13:** solo operaciones exitosas emiten. No existe
> `emitirFailure`, `REQUIRES_NEW`, persistencia de eventos para
> operaciones rechazadas ni `codigoError` logueado. Las operaciones
> rechazadas (401/403/404/409) no producen fila `log_actividad`.

**Fuente de verdad:** `../../../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md`
(I-11), `../../../thesis-docs/plan/design/03-procesos-detalle.md` §H/P-38…P-42 y
tabla D-13, `../../../thesis-docs/plan/architecture/07-api-contract.md` §9
(`/admin/*`) y §1 (forma canónica `Page<T>` con
`items,total,page,size,totalPaginas`, default `size=25`, tope `size<=200`),
`../../../thesis-docs/plan/architecture/06-database-schema.md`
(§2.15 `log_actividad`, §2.14 `valor_referencia`, §2.12 `plantilla_apu`),
`../../../thesis-docs/plan/quality/02-catalogo-pruebas.md` (TC-P38, TC-P39,
TC-P40, TC-P41, TC-P42; §5 protocolo SUS).

> **Estado al 2026-09-08:** I-08/I-09/I-10 (cronograma + export) DONE;
> módulo `cronograma` cerrado por Plan 031. **032 DONE (2026-09-07)** —
> acta firmada e inventario operativo publicados. **033 DONE
> (2026-09-08)** — fundación P-42 implementada y verificada, sin commit.
> **034 DONE (2026-09-08)** — P-38 gestión de usuarios e invitaciones
> implementada y verificada, sin commit; emisión D-13 vía 033. **035 DONE
> (2026-09-08)** — P-39 con GREEN focal 41/41
> (`AdminBaseCentralResourceIT` 29 + `AdminBaseCentralLogAuditoriaIT` 12),
> regresión `insumo.*` 74/74, build y Spotless PASS, y suite completa
> 715 = 712 pass + GM-19/GM-20 aceptados + GM-24 skipped, 0 errors.
> Plan 036 cerró con focal admin 10/10, regresión `plantilla.*` 85/85 y
> suite completa 725 = 722 pass + GM-19/GM-20 aceptados + GM-24 skipped.
> **Plan 037 DONE (2026-09-08):** focal 7/7, `proyecto.*` 25/25 y suite
> 732 = 729 pass + GM-19/GM-20 aceptados + GM-24 skipped; build/Spotless PASS.
> **Plan 038 DONE (2026-09-08):** focal 6/6, regresión conjunta de módulos 234/234 y suite completa 738 = 735 pass + GM-19/GM-20 aceptados + GM-24 skipped; build/Spotless PASS. **039–040 PLANNED / TODO**; 039 es la siguiente tarea autorizada. Los
> cambios de Plan 031 permanecen preservados y fuera de alcance.

## ¿Por qué nueve planes y no menos?

Los nueve planes 032–040 son la **mínima granularidad coherente** con el
canon y la matriz `P-38…P-42` × `D-13`. Fundir dos o más capacidades en un
solo plan destruiría al menos una de las siguientes invariantes:

| Invariante que la fragmentación protege | Plan que la sostiene |
|---|---|
| Decisión canónica congelada **antes** de codificar (gate documental) | **032** |
| Cimientos de `LogActividad` listos antes de cualquier emisor | **033** |
| Cada capacidad admin emite sus eventos D-13 a través de un único seam (033) — nunca se duplica instrumentación | **034**–**037** |
| Eventos históricos (auth, proyecto, insumo, base, APU, presupuesto, cronograma, documento) cubiertos sin regresión | **038** + **039** |
| Cierre integrado: Bruno admin, regresiones, Graphify, docs, piloto SUS | **040** |

La carga de revisión es **alta** pero predecible: cada plan 034–039 entrega
un único P-xx con su `@QuarkusTest` y su TC; el cierre 040 corre las
suites completas y actualiza docs. Las dos auditorías previas (P-39/Parametros
Sistema ya implementados; P-38 invitación ya implementada) no eliminan
planes: las absorben como gates de auditoría sin reescritura.

## Estado de los planes

| # | Plan | Iteración | Procesos / Historias | Estado |
|---|---|---|---|---|
| 032 | [Sincronizar contrato e inventario admin](./032-sincronizar-contrato-inventario-admin.md) | I-11 | gate documental (pre-P-38…P-42) | **DONE (2026-09-07)** — acta en [`docs/modulos/panel-admin/00-acta-reconciliacion.md`](../../docs/modulos/panel-admin/00-acta-reconciliacion.md); inventario en [`docs/modulos/panel-admin/00-inventario-trabajo.md`](../../docs/modulos/panel-admin/00-inventario-trabajo.md) |
| 033 | [Log de actividad — base](./033-log-actividad-base.md) | I-11 | P-42 / US-39 / TC-P42-01..02 (foundation) | **DONE (2026-09-08)** — V010; catálogo/validador de 26 eventos; emisor `MANDATORY`; JSONB como `String` vía `ObjectMapper`; `GET /admin/logs` paginado y `SUPER_ADMIN`; focal audit 26/26 y usuario 51/51 |
| 034 | [Gestión de usuarios e invitaciones](./034-gestion-usuarios-invitaciones.md) | I-11 | P-38 / US-35 / TC-P38-01..03 | **DONE (2026-09-08)** — `UsuarioAdminService` + `UsuarioAdminResource` (`/admin/usuarios` con `@RolesAllowed("SUPER_ADMIN")`); invitación 72 h sin contraseña temporal (contrato acta 032 D-04); DELETE con proyectos → 409 `usuario-con-proyectos-impedido` (test focal FK); emisión D-13 vía 033 `MANDATORY`; focal admin 18/18 + log 6/6 + sin contraseña temporal 1/1 |
| 035 | [Bases centrales — cierre](./035-bases-centrales-cierre.md) | I-11 | P-39 / US-36 / TC-P39-01..03 | **DONE (2026-09-08)** — RED 41 con 18 failures (9 auditoría + 9 recurso); GREEN focal fresco 41/41 (`AdminBaseCentralResourceIT` 29 + `AdminBaseCentralLogAuditoriaIT` 12); `insumo.*` 74/74; build/Spotless PASS; suite completa 715 = 712 pass + GM-19/GM-20 aceptados + GM-24 skipped, 0 errors; diff limpio; sin cambios en migraciones/motor/recalculo; Graphify final 4.453 nodos / 13.555 aristas / 182 comunidades; sin commit |
| 036 | [Plantillas APU de sistema](./036-plantillas-apu-sistema.md) | I-11 | P-40 / US-37 / TC-P40-01 | **DONE (2026-09-08)** — CRUD/listado `SUPER_ADMIN`; SISTEMA server-authored; snapshot canónico price-free; D-13 solo en mutaciones exitosas; focal 10/10, `plantilla.*` 85/85, suite 725 con solo GM-19/GM-20 aceptados y GM-24 omitido |
| 037 | [Parámetros del sistema y valores de referencia](./037-parametros-valores-referencia.md) | I-11 | P-41 / US-38 / TC-P41-01..02 | **DONE (2026-09-08)** — DTO canónico de 22 campos; defaults materializados al crear proyecto; CRUD/upsert paginado de valores de referencia; D-13 solo en mutaciones efectivas; focal 7/7, `proyecto.*` 25/25, suite 732 con baseline aceptado; sin commit |
| 038 | [Instrumentación D-13: identidad y catálogos](./038-instrumentacion-d13-identidad-catalogos.md) | I-11 | eventos D-13 auth/usuario/proyecto/insumo/base/APU | **DONE (2026-09-08)** — 16 nombres únicos + camino adicional de invitación; `proyecto.duplicado` sigue `no producer yet`; focal 6/6, módulos 234/234, suite 738 con baseline aceptado; sin commit |
| 039 | [Instrumentación D-13: presupuesto, cronograma, documento](./039-instrumentacion-d13-presupuesto-cronograma-documento.md) | I-11 | eventos D-13 presupuesto/cronograma/documento/export | **PLANNED / TODO** |
| 040 | [Integración del panel y piloto SUS](./040-integracion-panel-piloto-sus.md) | I-11 | cierre I-11 + piloto SUS 1–2 (protocolo quality/02 §5) | **PLANNED / TODO** |

## DAG de dependencias

```
032 (gate documental, resuelve decisiones pendientes; STOP si queda
    contradicción; corrige drift canónico en 07-api-contract.md §1/§9
    antes de 033)
  │
  ▼
033 (P-42 base: UNA migración aditiva con el siguiente número disponible
     (nombre neutral `V???__log_actividad_identidad_publica.sql`)
     que añade `log_actividad.public_id UUID NOT NULL DEFAULT uuidv7()`
     + UNIQUE + inmutabilidad (D-01) **y**
     `log_actividad.entidad_public_id UUID NULL` sin FK, sin DEFAULT,
     sin UNIQUE (D-21 — server-authored; los logs sobreviven al
     borrado de la entidad afectada); V001–V009 intactas;
     `entidad_id BIGINT` legacy permanece inalterado y nunca cruza
     REST; `LogActividadResponse.entidadId` mapea exclusivamente
     desde `entidad_public_id`;
     enum `EventoLogActividad` con 26 entradas verbatim;
     emisión MANDATORY dentro de la misma @Transactional exterior —
     sin ghost events; sin emitirFailure / REQUIRES_NEW / codigoError)
  │
  ├──► 034 (P-38; emite D-13 P-38 a través de 033; DELETE con proyectos
  │        → 409 `usuario-con-proyectos-impedido` con test focal FK)
  ├──► 035 (P-39; auditoría de Plan 015bis + emisión `admin.base_editada`
  │        solo en operaciones exitosas; divergencias canónicas resueltas
  │        por acta 032)
  ├──► 036 (P-40; `tipo=SISTEMA`, `usuario_id=NULL` server-authored;
  │        longitud `descripcionRubro` confirmada contra columna real —
  │        sin tope arbitrario)
  └──► 037 (P-41; `/proyectos/parametros-sistema` se conserva canónico;
            DTO de respuesta según acta 032; cualquier `clave` única con
            `fuente` no blank se acepta — sin CAMICON sembrado)
            │
            ▼
          038 (D-13 instrumentación: 16 nombres únicos del catálogo
               D-13 (auth/proyecto/insumo/base/APU) **más un camino
               emisor adicional** para `usuario.activado` con origen
               `invitacion` (camino compartido con 034, que emite el
               mismo nombre con origen `admin`); excluyendo los ya
               emitidos por 034–037; sin emisor en motor/ ni
               recalculo/; 4 nombres legacy V004 fuera del enum;
               `proyecto.duplicado` gated por `STOP-032-P09-DUPLICAR`:
               si el acta 032 implementa el seam canónico, 038 lo
               emite; en otro caso, "no producer yet")
            │
            ▼
          039 (D-13 instrumentación: presupuesto/cronograma/documento/export
               preservando transacción única y TOCTOU de Plan 031; las 6
               operaciones canónicas de `cronograma.editado` y los 4
               formatos de `documento.exportado`)
            │
            ▼
          040 (Bruno 12-admin autocontenido — 16 requests exactos;
               regresiones; `graphify update .` (no `codegraph`);
               docs; piloto SUS 1–2 con cita Brooke (1996))
```

033 → 040 son **estrictamente secuenciales** y 033–037 ya están cerrados. 038 es
la siguiente tarea autorizada; 034–037 podían reordenarse entre sí (emiten
por el mismo seam y sus TC no comparten fixtures), pero todos dependen de
la fundación entregada por 033. 038 no puede iniciar hasta que 034,
035, 036 y 037 estén todos cerrados (para no duplicar eventos). 039 no
puede iniciar hasta que 038 esté cerrado y Plan 031 siga sin tocar. 040
es la integración final.

## Asignación P-xx / US / TC → plan

| Proceso | Historia | Casos | Plan responsable | Notas |
|---|---|---|---|---|
| P-38 Gestión de usuarios | US-35 | TC-P38-01..03 | 034 | Invitación 72 h sin contraseña temporal; desactivar/reactivar/delete con FK RESTRICT → 409 (test focal mapeo FK); UUIDv7; **matriz canónica self-delete/last-admin y cambio de email solo si acta 032 los cerró** (en otro caso, se difieren a I-12) |
| P-39 Bases centrales (admin) | US-36 | TC-P39-01..03 | 035 | Auditoría de Plan 015bis (DONE 2026-08-29); cierra gap estrecho solo si está probado con test rojo previo; **divergencias canónicas DELETE base/insumo resueltas por acta 032**; emite `admin.base_editada` solo en operaciones exitosas |
| P-40 Plantillas APU de sistema | US-37 | TC-P40-01 | 036 | Reusa `SnapshotApuMapper` price-free; `tipo=SISTEMA`, `usuario_id=NULL` server-authored; **longitud `descripcionRubro` confirmada contra columna real — sin tope arbitrario**; emite `admin.plantilla_editada` solo en operaciones exitosas |
| P-41 Parámetros del sistema + valores de referencia | US-38 | TC-P41-01..02 | 037 | `/proyectos/parametros-sistema` se conserva canónico; **DTO de `GET` decide acta 032**; `/admin/valores-referencia` permite cualquier `clave` única con `fuente` no blank (sin allowlist vacío que bloquee); emite `admin.parametros_editados` solo en operaciones exitosas |
| P-42 Logs de actividad | US-39 | TC-P42-01..02 | 033 (foundation) + 040 (verificación de cobertura) | Catálogo cerrado D-13 (26 eventos verbatim, enum Java `EventoLogActividad`); sin PII ni secretos (RNF-08); **LogActividadResponse.id / usuarioId / entidadId en UUIDv7**; sin `emitirFailure` ni `REQUIRES_NEW` |

## Matriz ya-hecho vs falta

| Capacidad | Estado al cierre de Plan 031 (2026-09-07) | Lo que I-11 entrega |
|---|---|---|
| Invitación por correo (D-11, 72 h, sin contraseña temporal) | **DONE** (`auth/aceptar-invitacion`; `TipoToken.INVITACION`; TTL `PT72H` configurable; `emailVerificado=true` al aceptar) | 034 agrega **emisión admin** (`GET/POST/PUT /admin/usuarios`, `…/desactivar`/`/reactivar`, `DELETE` con proyectos → 409 `usuario-con-proyectos-impedido`); no toca `AuthService.aceptarInvitacion` |
| `GET/PUT /proyectos/parametros-sistema` | **DONE** (lectura pública, escritura `@RolesAllowed("SUPER_ADMIN")`; 12 columnas + 8 rangos) | 037 **conserva** la ruta canónica (DTO de `GET` solo si acta 032 lo decide); agrega logging `admin.parametros_editados` y entrega el CRUD `valor_referencia` (clave única con fuente no blank) |
| CRUD/archivar/borrar bases centrales | **DONE 2026-08-29 (Plan 015bis)** (`AdminBaseCentralResource` bajo `/admin/bases-centrales`) | **DONE en 035:** reporte por fila con estados `paridad`/`divergencia`/`gap`; paginación canónica y DELETE referenciado cerrados; `admin.base_editada` solo en operaciones exitosas. GREEN focal 41/41, `insumo.*` 74/74, build/Spotless PASS y suite completa 715 con solo GM-19/GM-20 aceptados y GM-24 omitido. |
| `plantilla_apu` personal (`tipo=PERSONAL`) + SISTEMA con `usuario_id NULL` | **DONE 2026-08-29 (Plan 04)**; SISTEMA sembrado en V004 | **DONE 2026-09-08 (Plan 036):** flujo admin `GET/POST/PUT/DELETE /admin/plantillas-apu[/{id}]`; creación desde APU cross-owner tras rol `SUPER_ADMIN`; SISTEMA server-authored; snapshot price-free compartido; `admin.plantilla_editada` solo en mutaciones exitosas |
| `log_actividad` tabla + índices | **DONE** (V001 §2.15; tabla, `ix_log_fecha`, `ix_log_usuario`, FK `usuario_id → usuario ON DELETE SET NULL`) | 033 crea la **capa de servicio** (`LogActividadService.emitir(...)` con MANDATORY), entidad/repo (UUIDv7), `LogActividadResource` (`GET /admin/logs` con filtros `usuarioId&evento&desde&hasta&page…`), DTOs; **una migración aditiva con el siguiente número disponible** (nombre neutral `V???__log_actividad_identidad_publica.sql`) añade `public_id UUID NOT NULL DEFAULT uuidv7()` + índice único + inmutabilidad (D-01) **y** `entidad_public_id UUID NULL` sin FK/sin DEFAULT/sin UNIQUE (D-21; server-authored; los logs sobreviven al borrado de la entidad afectada); la columna legacy `entidad_id BIGINT` permanece inalterada y nunca cruza REST; `LogActividadResponse.entidadId` mapea exclusivamente desde `entidad_public_id`; V001–V009 intactas; el catálogo cerrado D-13 vive en el enum `EventoLogActividad` |
| `valor_referencia` tabla | **DONE** (V001 §2.14; PK `clave`, columnas `valor/descripcion/fuente/updated_at`) | 037 entrega `GET /admin/valores-referencia`, `PUT /admin/valores-referencia/{clave}` (upsert; cualquier clave única con fuente no blank), `DELETE /admin/valores-referencia/{clave}` |
| D-13 eventos (catálogo cerrado) | **PENDIENTE** (la tabla existe, no hay emisores; V004 siembra 19 log rows; 6 filas fixture usan los 4 nombres legacy fuera del catálogo) | 033 (foundation: enum 26 verbatim + `detallesEsperados()` completo + `LogActividadDetalleValidator`) + 034–039 (emisores por capacidad; consumen el mapa congelado por 032) + 040 (verificación de cobertura: enum tiene 26 verbatim; cobertura runtime solo de productores efectivamente canonicados; `STOP-032-P09-DUPLICAR` puede diferir `proyecto.duplicado` como "no producer yet") |
| Piloto SUS 1–2 participantes | **PENDIENTE** (gate humano dependiente del frontend) | 040 entrega artefactos, comandos y plantilla con cita Brooke (1996); **no fabrica** ejecución ni puntaje |

## Decisiones locked para I-11

Las siguientes decisiones quedan **cerradas** desde la planificación; un
plan que intente revertirlas activa `STOP` y reabre el plan 032. Las
decisiones marcadas **(superficie a confirmar por acta 032)** requieren
verificación contra el código real — el acta las cierra explícitamente,
y los planes 033–040 nunca las pre-deciden:

1. **Identidad pública admin (UUIDv7)** — todas las entidades
   navegables exponen UUIDv7; PK/FK internas siguen siendo `BIGINT`.
   `UsuarioAdminResponse.id`, `BaseInsumos.publicId`,
   `PlantillaApu.publicId`, `Insumo.publicId`, `Apu.publicId`,
   `Presupuesto.publicId`, `Cronograma.publicId`, `Proyecto.publicId`,
   **`LogActividadResponse.id`** y **`LogActividadResponse.usuarioId`** son
   UUIDv7; **`entidadId`** es UUIDv7 nullable. Plan 033 crea **una**
   migración aditiva con el siguiente número disponible
   (nombre neutral `V???__log_actividad_identidad_publica.sql`,
   porque cubre dos columnas; nunca
   `V???__log_actividad_public_id.sql`); nunca se pre-asigna V010
   ciegamente; nunca se editan V001–V009. La columna legacy
   `log_actividad.entidad_id BIGINT` (V001 §2.15) **no** se convierte
   y **no** se expone vía REST. `usuarioNombre` y el campo canónico
   `fecha` se conservan donde el canon los requiera. **No PII** aplica
   **solo** a `detalle` JSONB y a secretos.
2. **`log_actividad.detalle` JSONB (RNF-08, TC-P42-02):** solo claves
   canónicas en español neutro; nunca correos, `passwordHash`, JWT,
   tokens, hashes de invitación, ni el contenido de
   `especificacion_tecnica`/`plantilla_apu.snapshot_secciones`.
3. **Atomicidad (sin ghost events):** todo evento de éxito se emite
   dentro de la **misma `@Transactional` exterior** del servicio que
   materializa el cambio. `LogActividadService.emitir(...)` se anota
   con `@Transactional(TxType.MANDATORY)` y un caller sin tx exterior
   lanza la excepción estándar `jakarta.transaction.TransactionalException`
   (test focal), sin wrapper propio. **No existe**
   `emitirFailure`, `REQUIRES_NEW`, ni persistencia de eventos para
   operaciones fallidas. Los servicios públicos que emiten D-13
   (`AuthService.login`, `AuthService.logout`,
   `AuthService.registrar`, `AuthService.cambiarPassword`,
   `AuthService.restablecerPassword`,
   `AuthService.aceptarInvitacion`, `ProyectoService`,
   `InsumoCrudService`, `ImportacionInsumoService`,
   `CopiaBaseService`, `ApuCrudService`) **ya abren**
   `@Transactional` exterior antes de la emisión; la auditoría
   contra
   `src/main/java/ec/uce/propuestas/usuario/auth/AuthService.java`
   confirma que `login` (línea 108) y `aceptarInvitacion`
   (línea 206) están anotadas con `@Transactional`. 033 no
   ajusta transacciones exteriores; 038 hereda esa verificación
   y reabre 032 si descubre un servicio que requiere ajuste.
4. **Invitación (D-11) — estado inicial y hash inutilizable:**
   `POST /admin/usuarios` crea el `Usuario` con `passwordHash`
   inutilizable, `activo=true`, `emailVerificado=false`. El acta
   032 congela el contrato exacto (D-04): 32 bytes aleatorios
   tomados del `SecureRandom` canónico de la JVM, codificados como
   **Base64URL sin padding**, hasheados **una sola vez** a través
   del `PasswordService` ya existente (bcrypt); el `byte[]`
   aleatorio y el `String` Base64URL se descartan inmediatamente
   después del hash. El algoritmo se ejecuta **únicamente** al
   crear un nuevo usuario invitado (no se regenera en login,
   logout, cambio de contraseña, ni en ningún otro flujo
   posterior). El token de invitación 72 h viaja por el
   `TokenService` ya existente (`SHA-256`, TTL
   `app.app.token.invitacion-ttl: PT72H`, `TipoToken.INVITACION`);
   la contraseña temporal y el token de invitación son **dos
   secretos independientes**. **No** se introduce un helper
   `RandomUtil` nuevo: la fuente aleatoria es el `SecureRandom`
   ya presente en el módulo.
5. **Self-delete / last-active-SUPER_ADMIN (superficie a confirmar
   por acta 032):** la matriz canónica no se prefija en este plan.
   Si el acta la deja abierta, 034 no codifica la matriz como
   canónica y se difiere a I-12.
6. **Cambio de email admin / primer SUPER_ADMIN (superficie a
   confirmar por acta 032):** no se prefija en este plan. Si el acta
   no los cierra, 034 documenta la apertura y no codifica.
7. **Paginación admin — `Page<T>` canónico estable:** los GET de
   listado usan la `Page<T>` ya implementada en `common/dto/Page.java`
   con la forma **estable JSON `items, total, page, size,
   totalPaginas`** y los valores por defecto **`size=25` y tope
   máximo `size=200`**. El plan 032 corrige el drift en
   `thesis-docs/plan/architecture/07-api-contract.md §1`. **No** se
   introduce cursor pagination en I-11.
8. **Plantillas SISTEMA (P-40):** `POST /admin/plantillas-apu`
   acepta `desdeApuId` (UUIDv7) y `nombre`; el snapshot lo
   construye el backend reusando `SnapshotApuMapper`. La longitud
   de `descripcionRubro` la define la columna real
   (`plantilla_apu.descripcion_rubro`) — **no se inventa tope
   arbitrario**.
9. **`/proyectos/parametros-sistema` permanece canónica (P-41):** la
   ruta no se mueve a `/admin/parametros-sistema`. La decisión DTO
   de la respuesta la cierra el acta 032; 037 implementa el DTO
   canónico solo si el acta lo selecciona. `/admin/valores-referencia`
   es la nueva ruta para el CRUD de `valor_referencia`. Cualquier
   `clave` única con `fuente` no blank se acepta; **no** hay
   allowlist vacío que bloquee escrituras. Sin CAMICON sembrado.
10. **Bases centrales (P-39) — divergencias canónicas a resolver por
    el acta:** DELETE base central y DELETE insumo en base central
    tienen divergencias entre canon e implementación; el acta
    resuelve ambos antes de 035. 035 aplica la decisión solo tras
    RED con tests que reproduzcan el comportamiento actual.
    **No** se usan marcas de paridad fabricadas (`✔`/`⚠`/`✗`);
    solo hechos verificados con test rojo previo.
11. **Solo eventos eventos exitosas emiten** (refuerza decisión 3): el
    catálogo D-13 emite únicamente mutaciones materiales con commit.
    Las operaciones rechazadas (401/403/404/409) no producen fila
    `log_actividad`.
12. **P-42 fundación (033):** `LogActividadService` vive en
    `ec.uce.propuestas.usuario.audit` (subpaquete nuevo del módulo
    `usuario`, no un módulo nuevo de primer nivel); el enum
    `EventoLogActividad` también. La entidad `LogActividad` se aloja
    en `ec.uce.propuestas.usuario.audit.entity` y su repositorio en
    `ec.uce.propuestas.usuario.audit.repository`. El recurso admin
    (`LogActividadResource`) vive en
    `ec.uce.propuestas.usuario.audit.resource` con ruta `/admin/logs`
    y `@RolesAllowed("SUPER_ADMIN")` a nivel de clase.
13. **Sin CAMICON en seed ni en valores_referencia.** Cualquier valor
    informativo que requiera licencia/fuente explícita queda fuera
    del MVP; el seed `V004` ya existe y **no** se reabre.
14. **Filas legacy V004 (decisión histórica preservada):** V004
    siembra **19 log rows** en `log_actividad`. De ellas, **6 filas
    fixture** usan los **4 nombres legacy** **fuera del catálogo
    D-13** (`base.insumos.copiada`, `rubro.creado`,
    `cronograma.creado`, `presupuesto.vigente_marcado`) y son
    **historial legacy**: legibles/filtrables por `evento=`,
    nunca se emiten de nuevo, nunca se admiten al enum runtime,
    excluidas de la cobertura exacta de 26 eventos del test 040.
    No se autoriza borrado ni backfill; cualquier modificación
    futura requiere un acta humana explícita.
15. **Plan 031 preservado intacto.** Sus cambios pendientes (export
    MSPDI, `BloqueoExportDetalle`, `CronogramaDocumentoResource`,
    `BloqueoExportResponse`, etc.) no entran en I-11; 039 los reusa
    tal cual al instrumentar `documento.exportado`.
16. **Piloto SUS (I-11) vs SUS n ≥ 5 (I-12):** son protocolos
    distintos. 040 entrega artefactos del piloto 1–2 (con cita
    Brooke (1996)); la medición poblacional es I-12.

## Carga de revisión — alerta

> **Advertencia explícita al revisor:** I-11 entrega 9 planes ejecutables
> sobre módulos existentes + 1 nuevo seam transversal (`LogActividadService`).
> La revisión **no debe** procesarse en un solo PR. El DAG exige al menos:
>
> 1. `feat(admin): foundation log_actividad + D-13 catalog`
>    (033 cerrado con su `@QuarkusTest`; migración aditiva con el
>    siguiente número disponible);
> 2. un commit/PR por cada plan 034, 035, 036, 037 (orden indiferente
>    entre sí, pero todos después de 033);
> 3. un commit/PR por 038 (D-13 auth/usuario/proyecto/insumo/base/APU);
> 4. un commit/PR por 039 (D-13 presupuesto/cronograma/documento/export);
> 5. el cierre 040 (Bruno 12-admin 16 requests + regresiones +
>    Graphify + docs + plantilla de piloto SUS con cita Brooke 1996).
>
> **Workload forecast (alto, esperado):** cada plan 034–039 añade entre
> 3 y 10 archivos nuevos + 1 archivo de test `@QuarkusTest`; 033 añade 4–6
> archivos + 1 migración aditiva; 040 actualiza 2–3 docs y 1 colección
> Bruno. Sumado al Plan 031 sin commitear, el revisor enfrenta un stack
> de ~12 commits/PRs. Si la sesión activa pide fusión en un solo PR,
> abrir `size:exception` antes de empezar y mantener la segmentación
> por work-unit al interno (commit por plan), tal como exige la skill
> `work-unit-commits` de la carpeta `~/.agents`.

## Cómo se conecta con I-12 (sin abrirlo)

I-12 (semanas 23–24) cierra la tesis: SUS `n ≥ 5`, baseline RNF-06,
comparación antes/después y hardening. **I-11 NO entrega esa medición**;
entrega únicamente el **piloto 1–2 participantes** que valida el
protocolo (`quality/02 §5`) y las tareas guiadas. Mezclar ambos
protocolos es un error de planificación — 040 distingue
explícitamente **evidencia humana de piloto** (I-11) y **evidencia
poblacional de SUS** (I-12).

## Convenciones de los planes de este subdirectorio

- **Estado TODO al inicio**, sin excepciones. La sección "Estado de
  cierre" se completa solo cuando el plan se ejecuta.
- **Secuencia TDD estricta RED → GREEN → TRIANGULATE → REFACTOR.** El
  orquestador verifica el ciclo en cada plan; 032 es el único
  documentation-only (no tiene ciclo RED/GREEN — ver su sección TDD
  particular).
- **Catálogo D-13 cerrado.** Cualquier evento fuera del catálogo activa
  STOP y obliga a reabrir 032.
- **Catálogo D-13 cerrado = fuente única de verdad.** El enum Java
  `EventoLogActividad` lista los 26 eventos exactos de
  `design/03 §J` D-13; ningún emisor puede usar una cadena libre. Los
  4 nombres legacy V004 **nunca** entran al enum runtime.
- **Matriz canónica `detalle` por evento, congelada por el acta 032.**
  033 implementa el mapa completo `detallesEsperados()` y el
  `LogActividadDetalleValidator`; 034–039 consumen el mapa sin
  modificarlo. Si una clave faltara, el plan afectado reabre 032.
- **Idioma del log:** códigos en **inglés con puntos** (forma canónica
  D-13: `auth.login`, `proyecto.creado`, `admin.base_editada`); la
  descripción legible vive en `detalle` JSONB con claves en **español
  neutro** (lista exacta en la matriz del acta 032 decisión 2).
- **Verificación sin totales hardcodeados de suite completa.** Cada plan
  enuncia comandos **focales** (`--tests 'ec.uce.propuestas.X.*'`) y
  declara la suite completa solo como **medición opcional al cierre**
  sin predecir conteos; el orquestador decide si la corre.
- **Sin secretos en código de prueba.** Tokens, hashes y JWT son
  `RecordingEnviadorCorreo` (ya existe para `auth`); cualquier helper
  nuevo sigue el mismo patrón.
- **Bruno admin es una sola colección `12-admin/`** (no
  `12-usuarios/`, `12-bases-centrales/`, etc.). Las **16 requests**
  exactas se ejecutan sobre el mismo `folder.bru` con `auth: inherit`.

## Handoff al siguiente paso de planificación

Una vez que 033–040 estén DONE, el siguiente paso de planificación es
**I-12** (medición SUS `n ≥ 5` + hardening RNF-06 + cierre de variables
de tesis). Ese plan no se abre aquí; el orquestador lo autoriza en su
propia sesión siguiendo el mismo formato.