# CLAUDE.md — guidance for AI sessions in `thesis-back-quarkus`

Read this before touching anything. Terse on purpose; every line pays rent.

## What this repo is

Quarkus backend for a thesis project — a SERCOP propuestas técnico-económicas
platform. See [`README.md`](README.md) for the human overview.

## The One Rule

**All design decisions live in `../thesis-docs/`.** This repo *implements*
those decisions; it does not re-decide them. Before proposing an approach,
find the relevant file in `../thesis-docs/plan/` and follow it. If the docs
are wrong, fix the docs *and* the code in the same change — never diverge
silently.

Canonical files (memorize these paths):
- `../thesis-docs/CLAUDE.md` — project-wide guidance for the docs repo
- `../thesis-docs/PROJECT_SPEC.md` — scope, out-of-scope, methodology
- `../thesis-docs/plan/architecture/06-database-schema.md` — DDL, canonical
- `../thesis-docs/plan/architecture/07-api-contract.md` — REST contract
- `../thesis-docs/plan/architecture/08-codebase-design.md` — module map + deep-module discipline
- `../thesis-docs/plan/domain/02-data-model.md` — **motor de cálculo formulas §16 are non-negotiable**
- `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` — iteration order + hitos + TC/GM/CHK ids
- `../thesis-docs/plan/quality/02-catalogo-pruebas.md` — test-case catalog and golden masters
- `../thesis-docs/plan/design/03-procesos-detalle.md` — process detail + `§J` decisions (D-01…D-13)

## Domain vocabulary — always Spanish

Do NOT invent English translations. The following terms are canonical:

| Term | Meaning |
|---|---|
| `insumo` | material / labor / equipment / transport input |
| `APU` | Análisis de Precios Unitarios — unit-price analysis |
| `presupuesto` | budget (versioned; one `es_vigente` per project) |
| `capitulo` | budget chapter (recursive tree, no depth limit) |
| `rubro` | budget line item (1:1 with an APU, per version) |
| `cronograma` | execution schedule (1:1 with presupuesto) |
| `firmante` | signer (CONSOLIDADO / APROBADO roles) |
| `HM` / `herramienta menor` | minor tools row in EQUIPO section |
| `base_insumos` | insumo catalog (CENTRAL or PROYECTO scope) |
| `plantilla_apu` | APU template (SISTEMA or PERSONAL) |
| `descuento` | **Retirado por APU** (Plan 015). Sobrevive solo como **FORMA 1** (mutación de las columnas base de los insumos elegibles copiados a la base PROYECTO; **MO exenta**, reversible, regulada por `parametros_sistema.rango_descuento_min/max`) y **FORMA 2** (edición atómica de un insumo ya PROYECTO). |
| `FORMA 1` / `FORMA 2` | mutually exclusive — FORMA 1 = mutación de la base PROYECTO (`InsumoCrudService.editar` sobre base PROYECTO) con MO exenta; FORMA 2 = edición atómica de un insumo ya PROYECTO. Nunca “monto absoluto”. |
| `%CI` / `porcentaje_indirecto` | indirect-cost percentage |
| `CD`, `CD_ajustado`, `CI`, `CT` | `CD` costo directo; `CI = CD × %CI` indirecto; `CT = CD + CI` total. `CD_ajustado` es solo sinónimo interno de `CD` (el campo `costoDirectoAjustado` y `cdAjustado` de `ApuCalculoResumen` se retiraron en Plan 015). |

Two roles only: `USUARIO`, `SUPER_ADMIN`. No third role. No middleware roles.

## Numeric precision — load-bearing for the thesis

- `NUMERIC(14,6)` — monetary values (BigDecimal scale 6)
- `NUMERIC(5,4)` — percentages (0.1800 = 18%)
- `NUMERIC(12,6)` — quantities
- `NUMERIC(10,6)` — rendimiento (h/unit)
- `NUMERIC(7,4)` — peso_ponderado (%)
- **El motor opera con la precisión natural de `BigDecimal`.** No aplica
  redondeo intermedio a sus operaciones (la regla previa de
  `CALC_PRECISION=3 HALF_UP` queda **retirada** por Plan 014, 2026-08-28;
  ver [`plans/014-motor-precision-no-links.md`](plans/014-motor-precision-no-links.md)).
- **La única rounding dentro del motor** es la frontera APU→Rubro en
  `internal/Consolidador.java`, regla **workbook-consistent**
  (corrección 2026-08-28): `precioUnitario = costoTotal.setScale(2,
  RoundingMode.DOWN)`; `precioTotal = cantidad × precioUnitario`,
  retenido a la escala de persistencia 6 (`NUMERIC(14,6)`) con
  `setScale(6, RoundingMode.HALF_UP)`. **No** se trunca cada `precioTotal`
  a 2 dp — la versión previa con `setScale(2, DOWN)` simétrico en PU y
  PT producía deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09`
  vs workbook IESS (ver STOP conditions de `plans/014`). Cualquier otro
  `.setScale(...)` dentro de `motor/` es un bug — abrir STOP y reportar.
- **Display config** se rige por la config global `app.display.precision`
  (default 2, env `DISPLAY_PRECISION`) y `app.display.precision-porcentaje`
  (default 4, env `DISPLAY_PRECISION_PORCENTAJE`); endpoint público
  `GET /api/v1/config/display` → `{precisionDinero, precisionPorcentaje}`.
  Aplicado únicamente en la capa de presentación/export. La BD nunca
  redondea a 2 dp.

BigDecimal only. `double` and `float` are banned in `motor/`:
```bash
grep -r 'double\|float' core/src/main/java/ec/uce/propuestas/motor/    # must return zero hits
```

## Business rule reference (D-01 … D-13, from `design/03-procesos-detalle.md §J`)

Authoritative decisions. Do not restate. Look up when in doubt:

| ID | Rule |
|---|---|
| D-01 | Password ≥8 chars, ≥1 letter, ≥1 number. Email-verify token TTL **24h**. Reenvío cooldown **60s**. |
| D-02 | JWT access token **60 min**. Refresh token **30 days** if "recordar sesión" true, else browser-session only. |
| D-03 | Password change → revoke ALL refresh tokens. Email change → re-verify new email; account operable on old email until verified. |
| D-08 | Auxiliar APU cannot be modified/deleted if referenced (RESTRICT). |
| D-09 | Rubro ↔ APU is 1:1 per presupuesto version (UNIQUE constraint on `rubro.apu_id`). |
| D-11 | Super-Admin invitation token TTL **72h**. Never temporary passwords. |
| D-12 | Central `base_insumos` are ARCHIVED (`archivada=true`), never deleted. Only exception to the no-soft-delete rule. |
| D-13 | `log_actividad.evento` is a closed catalog. Never PII in `detalle`. |

## Motor de cálculo — the highest-stakes code

Lives in `core/src/main/java/ec/uce/propuestas/motor/`. Contract:

- **No framework.** No `@Inject`, no `@ApplicationScoped`, no Panache, no
  logging framework. Pure Java, `BigDecimal`, JDK only.
- **Two entry points on `Motor`**:
  - `Motor.calcularApu(ApuSnapshot, ParametrosCalculo) → ApuCalculado`
  - `Motor.consolidar(VersionSnapshot) → VersionCalculada`
- **Internal helpers under `motor/internal/`** are package-private. Never
  test them directly. Test through the public interface only.
- **Golden Masters** (`GM-01`…`GM-25`) are the acceptance test. Any GM
  failing → real bug. Do NOT add tolerance, do NOT adjust expected values.

**Current motor status** (Plan 014, cierre parcial USER-DECIDED 2026-08-28; Plan 015, 2026-09-01):
el motor opera con la **precisión natural de `BigDecimal`** y la única rounding es la
frontera APU→Rubro descrita en *Numeric precision* (el workbook IESS no redondea
intermedios; las 3 dp visibles son formato). Plan 015 retiró el descuento por APU:
`ParametrosCalculo.porcentajeDescuento` y `ApuCalculado.costoDirectoAjustado` ya no
existen y la aritmética es `CI = CD × %CI`, `CT = CD + CI`. `@Digits` solo en los campos
monetarios de DTO del catálogo cerrado (`ApuDetallePatchRequest.precioOverride`,
`InsumoCrearRequest.precioUnitario`, `InsumoEditarRequest.precioUnitario`); nunca en
campos de entidad, cantidades, rendimientos ni porcentajes.

**Baseline del motor:** **45 tests = 42 verdes + 2 rojos aceptados** (GM-19 `-$6.95`,
GM-20 cap. 1 `-$0.84`; Plan 014, causa sin diagnosticar) **+ 1 omitido** (GM-24
`@Disabled`, fixture EMELNORTE upstream). **No** se reabre el motor para cerrarlos;
workbook, golden expected values, tolerancias y fórmulas quedan cerradas. Preferencia
del usuario: un único example workbook, sin auditorías exhaustivas per-rubro.

**Módulos construidos sobre el motor** (detalle por plan en el historial git y en
`plans/README.md`; los conteos de tests se miden, no se citan aquí):

| Módulo / paquete (`ec.uce.propuestas.…`) | Planes | Invariantes vigentes |
|---|---|---|
| `recalculo` | 020 | Interfaz `recalcular(Alcance)` con `Alcance = Version \| Apu \| Insumo` (sealed). `Motor` se invoca **estáticamente, nunca por CDI**. `VersionSnapshotBuilder` queda en `recalculo/internal` (no es seam). Cada mutación de presupuesto cierra con `recalcular(new Alcance.Version(id))` tras `flush()`. |
| `presupuesto` (`dto`, `resource`, `service`) | 021–025 | `ProyectoService.crear` auto-crea `presupuesto(version=1, es_vigente=true)` en la misma transacción. Capítulos: numeración jerárquica en backend, renumeración atómica en dos pasadas (aparcar con prefijo `~<id>` + `flush()`, luego asignar) por `UNIQUE (presupuesto_id, item)`. Rubros: D-09, cantidad > 0. `totalGeneral` sale de `presupuesto.total` (incluye CI y rounding de frontera). Versionado: deep copy transaccional con UUIDv7 frescos, lock pesimista de la fila `proyecto`, índice único parcial `ux_presupuesto_vigente`; la vigente no se elimina (409 `version-vigente-protegida`). Validación P-32 sin entidad JPA `Actividad`/`Cronograma` (SQL nativo acotado). |
| `cronograma` | 026–031 | Configuración, actividades/segmentos, vistas y curva S; preflight P-37 antes de exportar. |
| `usuario.audit` | 033, 038, 039 | `LogActividadService.emitir` es `@Transactional(MANDATORY)`; el evento D-13 se emite **solo tras éxito** y dentro de la transacción de la operación; catálogo cerrado `EventoLogActividad`; sin PII, BIGINT ni secretos en `detalle`. `proyecto.duplicado` sigue sin productor (P-09 no existe). |
| `usuario.admin`, `plantilla.admin`, `proyecto.admin`, bases centrales | 034–037 | Todo bajo `@RolesAllowed("SUPER_ADMIN")`. Invitación: token 72 h (`TokenService`) y contraseña temporal descartada tras hashear; borrar usuario con proyectos → 409 `usuario-con-proyectos-impedido`. `GET/PUT /proyectos/parametros-sistema` cambia solo proyectos nuevos (`parametros_proyecto` se materializa al crear). |
| `insumo`, `plantilla`, `apu` (búsqueda y bases) | 041–044 | Orden natural de `item` (no `ORDER BY item` de SQL); bases PERSONAL y plantillas de proyecto SISTEMA. |

Invariantes transversales: **owner-to-404** (RNF-05: un recurso ajeno responde 404, no 403);
**UUIDv7 en la frontera REST** (malformado o no-v7 → 400 `validacion`; el BIGINT interno
nunca sale en DTOs); errores canónicos `validacion` / `no-encontrado` / `conflicto`.

**Etapa 05 de microservicios (2026-11):** los GET de bytes de `/documentos/...`, los
*gateways* de render y los *writers* XLSX/PDF/MSPDI de Core ya no existen (sin POI/OpenPDF
en Core). La única descarga es `POST /documentos/{documento}/{id}/exportaciones` (captura
en Core → render y subida privada en Documentos → ticket de Core); `documento.exportado`
lo emite `ExportReservationService.finalizeAudit`/`finalizeCronogramaAudit`.

**Regla de modificación del Motor (Plan 014, 2026-08-28):**
- **`Motor.calcularApu()` y `internal/CalculadorFila.java`:** el guard "do
  not touch" se levanta **de forma acotada y solo para Plan 014**, para
  **ediciones estructurales mínimas**: borrar las ramas obsoletas
  `esAuxiliar`/`cdAuxiliar` y leer el `%CI` por APU desde
  `ApuSnapshot.porcentajeIndirecto`. **Aritmética intacta:** fórmulas,
  `MathContext`, orden de operaciones y precisión natural de `BigDecimal`
  quedan semánticamente idénticos; **ningún redondeo intermedio** (la regla
  anterior de `CALC_PRECISION=3 HALF_UP` por operación queda **retirada**).
  Fuera de ese scope acotado el guard sigue vigente.
- **`internal/Consolidador.java`:** se permite modificar **solo** para
  aplicar la regla **workbook-consistent** de la frontera APU→Rubro
  (corrección 2026-08-28): `RoundingMode.DOWN` 2 dp **solo** en
  `precioUnitario`; `precioTotal = cantidad × PU_2dp` retenido a
  escala 6 (`NUMERIC(14,6)`) con `HALF_UP`; agregar los totales de
  capítulo y `totalGeneral` desde esos `precioTotal` a escala 6.
  **No** reintroducir `setScale(2, DOWN)` por rubro total (causaba
  deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09`).
  Cada nueva excepción debe documentarse con su plan
  (`plans/0NN`) y referenciarse aquí.
- **Snapshots/resultados del motor:** `ApuSnapshot` y `ApuCalculado` sin
  `esAuxiliar`; `FilaSnapshot` sin `cdAuxiliar`; `ParametrosCalculo` sin
  `porcentajeIndirectoApu` (no-links N04 §2 confirmado por Plan 014).
  `ApuSnapshot.porcentajeIndirecto` (nullable) es el único override
  semántico por APU; `ParametrosCalculo` retiene solo el default del
  proyecto.
- **`Fixture.java`:** no tocar las assertions de los GMs; solo reparaciones
  justificadas (con STOP conditions documentadas).

**Para cambios futuros al motor:** abrir `plans/0NN-motor-fix.md`
siguiendo el patrón de `plans/006`. Cada cambio debe:
1. Justificar el cambio por un cambio de requerimientos funcionales.
2. Actualizar `CLAUDE.md` y `thesis-docs/CLAUDE.md` con la nota
   correspondiente.
3. NO tocar tolerances de GMs existentes.
4. Re-correr `./gradlew test` completo y reportar baseline + delta.

## Verification commands (memorize)

```bash
./gradlew build -x test -PexcludeKnownResiduals            # both apps, no tests
./gradlew test -PexcludeKnownResiduals                     # both suites
./gradlew :core:test --tests 'ec.uce.propuestas.motor.*'   # motor only
./gradlew :core:test --tests 'ec.uce.propuestas.usuario.*' # auth only
./gradlew :documentos:test                                # independent service
./gradlew --console=plain :core:quarkusDev                 # Core live reload
./gradlew --console=plain :documentos:quarkusDev           # Documentos
./gradlew :core:build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

Expected motor test count (post-Plan 015, 2026-09-01): **45 tests totales**
(21 `MotorApuTest` + 4 `MotorConsolidacionTest` + 5 `MotorPropiedadesTest` +
5 `ConsolidadorFronteraTest` + 6 `SnapshotSinAuxiliaresTest` + 4 `DescuentoRetiradoMotorTest`);
**42 verdes** + **2 rojos** (GM-19 `-$6.95`, GM-20 cap. 1 `-$0.84` — **residual
aceptado**, no se reabre el motor) + **1 omitido** (GM-24 `@Disabled` por
fixture EMELNORTE upstream; DIAG borrado por Plan 014). Conteo total de la
suite se **reporta** desde los XML de `build/test-results/`, no se presupone.

Do **not** hardcode a post-change total. After any run, report the real
counts from the XML results instead of predicting them:

```bash
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  core/build/test-results/test/TEST-*.xml documentos/build/test-results/test/TEST-*.xml
```

## Repo conventions

- **Package structure**: vertical slices per module (`usuario/`, `motor/`,
  `common/`, `insumo/`, `apu/`, `presupuesto/`, `cronograma/`, `documento/`, ...)
  under `core/src/main/java/ec/uce/propuestas/`. See `08-codebase-design.md §1`.
- **Deep modules only get `Service` + `Repository`**: motor, recalculo,
  versionado, documento, importacion, invitacion-tokens, correo. Plain
  CRUD is `Resource → Panache` direct. Don't invent `UsuarioService` for
  profile CRUD.
- **DTOs are Java `record`s**, camelCase JSON, `@Valid` at the resource
  boundary. Bean validation for format; business rules in the service.
- **REST base path**: `/api/v1` via `@ApplicationPath` on
  `common/RestApplication.java`. Never hardcode `/api/v1` in per-resource
  `@Path`.
- **Role checks**: `@RolesAllowed({"USUARIO","SUPER_ADMIN"})` on the
  resource class or method. `@PermitAll` explicitly on public endpoints.
- **Panache entities**: `PanacheEntityBase` + explicit `@Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)` to map to Postgres
  `BIGINT GENERATED ALWAYS AS IDENTITY`. **Do not use `PanacheEntity`** —
  its default sequence-based ID does not match the schema and startup
  fails under `hibernate-orm.database.generation: validate`.
- **Migrations**: `V{NNN}__{snake}.sql`. Never edit an applied migration;
  add a new one.
- **Secrets**: Core development keys are local under `.secrets/jwt/`; test
  fixtures are under `core/src/test/resources/jwt/`. Ignored legacy keys in
  `src/main/resources/META-INF/resources/` are not part of either module and
  must not be moved or committed. Never commit production keys. `.env.example`
  documents the relevant environment variables.

## Working with plans

`plans/` conserva solo lo vivo; leer `plans/README.md` primero (qué sobrevive, estado por
ruta, pendientes, cómo añadir un plan).

1. Si un plan aplica, léelo completo. Los planes ejecutados se retiraron; su evidencia está
   en el código, las pruebas y el historial git.
2. **No edites código de una forma que los planes vigentes no autoricen**: sigue un plan
   existente o escribe uno nuevo (`plans/045-*.md`, numeración monótona).
3. Al terminar, actualiza `plans/README.md`; toca este archivo solo si cambia un invariante.

Los planes deben ser ejecutables por un modelo menos capaz: rutas, extractos de código,
comandos de verificación con salida esperada y salidas de escape («si X, STOP y reporta»).

## Traps this project has hit before (real war stories)

- **Datasource URL at global scope kills Dev Services in tests.** Put
  `quarkus.datasource.jdbc.url` under `%dev:` and `%prod:` only. Leaving
  it at global level, even as an env-var default, deactivates Dev
  Services for `%test`.
- **JWT key location must be `META-INF/resources/privateKey.pem`** in
  YAML, not bare `privateKey.pem`. The bare form fails with
  `MalformedURLException` at runtime.
- **`PanacheEntity` extends fails schema `validate`.** Always
  `PanacheEntityBase` + `@GeneratedValue(IDENTITY)`.
- **The IESS insumos CSV has no `codigo` column** and empty `unidad` on
  MO/EQ rows. Current V003 seed generates synthetic codes and
  substitutes `'h'` per schema CHECK. If real vendor codes matter,
  source upstream and write `V004__reseed_insumos.sql`.
- **The EMELNORTE APU fixture has empty `secciones` and null `codigo`**
  — GM-24 is `@Disabled` for this reason. Not a code fix.
- **Workbook `precioUnitario` is 2dp rounded, `precioTotal` is
  `cantidad × precioUnitario_2dp`.** Motor computes at 6dp. This is the
  GM-19/20 open question. Do not "fix" by adding tolerance.
- **A local Windows Postgres on :5432 shadows Dev Services** in dev mode
  (not in tests — tests use container URL directly). If `quarkus:dev`
  fails with auth errors, either stop the local service or set the DB
  env vars to match it.
- **`gradlew` sometimes ships without the executable bit** — `chmod +x gradlew`
  if `./gradlew` says "permission denied" on WSL/macOS.
- **Gradle outputs to `build/`, not `target/`** — the fast-jar lives at
  `build/quarkus-app/quarkus-run.jar` and the native binary at `build/*-runner`.
  Dockerfiles and `.dockerignore` already point at `build/`.

## What NOT to do

- Do not add framework code to `motor/`. Not even a `@Slf4j`.
- Do not add English translations of domain terms.
- Do not lower a golden-master tolerance from 0.00.
- Do not modify an applied Flyway migration; add a new one.
- Do not use `double` or `float` in any file that participates in cost
  arithmetic.
- Do not log tokens, passwords (raw or hashed), or PII (RNF-08).
- Do not add dependencies to `build.gradle.kts` without a plan authorizing it.
- Do not run destructive git commands (`push --force`, `reset --hard`,
  `clean -fd`) without explicit user request.
- Do not silently work around fixture bugs in `thesis-docs`. Report them
  and stop.

## Contact points

- **Director:** Ing. Zoila de Lourdes Ruiz Chavez, PhD — final call on
  domain-semantic decisions like GM-19/20's rounding question.
