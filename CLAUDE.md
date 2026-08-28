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
| `descuento` | per-APU discount at CD level, %, reversible |
| `%CI` / `porcentaje_indirecto` | indirect-cost percentage |
| `CD`, `CD_ajustado`, `CI`, `CT` | direct cost, adjusted direct cost, indirect cost, total cost |

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
grep -r 'double\|float' src/main/java/ec/uce/propuestas/motor/    # must return zero hits
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

Lives in `src/main/java/ec/uce/propuestas/motor/`. Contract:

- **No framework.** No `@Inject`, no `@ApplicationScoped`, no Panache, no
  logging framework. Pure Java, `BigDecimal`, JDK only.
- **Two entry points on `Motor`**:
  - `Motor.calcularApu(ApuSnapshot, ParametrosCalculo) → ApuCalculado`
  - `Motor.consolidar(VersionSnapshot) → VersionCalculada`
- **Internal helpers under `motor/internal/`** are package-private. Never
  test them directly. Test through the public interface only.
- **Golden Masters** (`GM-01`…`GM-25`) are the acceptance test. Any GM
  failing → real bug. Do NOT add tolerance, do NOT adjust expected values.

**Current motor status** (cierre parcial USER-DECIDED 2026-08-28; ver
[`plans/014-motor-precision-no-links.md`](plans/014-motor-precision-no-links.md)
y [Plan 02](docs/modulos/planes-para-estar-al-dia/02-motor-precision-y-consolidacion.md)):
el motor opera con la **precisión natural de `BigDecimal`**; el workbook
IESS no aplica redondeo intermedio al APU (las 3 dp visibles son formato de
display). La **única rounding del motor aplicada** es la frontera
APU→Rubro en `internal/Consolidador.java` con la regla **workbook-consistent**:
`precioUnitario = costoTotal.setScale(2, RoundingMode.DOWN)`;
`precioTotal = cantidad × precioUnitario`, retenido a la escala de
persistencia 6 (`NUMERIC(14,6)`) con `setScale(6, RoundingMode.HALF_UP)`;
los totales de capítulo y `totalGeneral` se agregan desde esos `precioTotal`
a escala 6; el display canónico a 2 dp ocurre solo en la capa de
presentación/assertion. **No** se trunca cada `precioTotal` a 2 dp — la
versión previa con `setScale(2, DOWN)` simétrico en PU y PT quedaba
retirada por deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs
workbook IESS (ver STOP conditions de `plans/014`). Display global
configurable vía `app.display.precision` (default 2) y
`app.display.precision-porcentaje` (default 4); endpoint público
`GET /api/v1/config/display` queda **OPEN** (Plan 014 T3). Validación de
entrada `@Digits` solo en los campos monetarios de DTO del catálogo
cerrado del plan (nunca en campos de entidad como `tarifaJornal` /
`precioUnitarioTarifa`, ni en cantidades, rendimientos o porcentajes) —
queda **OPEN** (Plan 014 T4).

**Baseline post-implementación (cierre parcial 2026-08-28)**
`./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain`:
`MotorApuTest` 21/21 verde; `MotorPropiedadesTest` 5/5 verde;
`ConsolidadorFronteraTest` 5/5 verde (T1 workbook-consistent, nuevo);
GM-21 verde con allowlist auditado de **11 entradas ≤ 0.03 a nivel PU**
(artefactos de redondeo manual del workbook IESS); **GM-19 y GM-20 RED**
con residual aceptado — GM-19 actual `395108.37` vs esperado `395115.32`
(delta `-$6.95`); GM-20 cap. 1 actual `158907.21` vs esperado `158908.05`
(delta `-$0.84`). GM-24 y DIAG `@Disabled`. **No** se reabre el motor
para cerrar este residual; workbook, golden expected values, tolerancias
y fórmulas del motor quedan cerradas. Preferencia del usuario: este es
**un example workbook único**; no se realizan auditorías exhaustivas
per-rubro.

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
./gradlew build -x test                                    # sanity build
./gradlew test                                             # full suite (~2 min cold)
./gradlew test --tests 'ec.uce.propuestas.motor.*'         # motor only
./gradlew test --tests 'ec.uce.propuestas.usuario.*'       # auth only
./gradlew --console=plain quarkusDev                       # live reload
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true   # native (~10 min)
```

Expected motor test count (post-cierre-parcial 2026-08-28): **36 tests
totales** (21 `MotorApuTest` + 5 `MotorConsolidacionTest` + 5 `MotorPropiedadesTest`
+ 5 `ConsolidadorFronteraTest`); **2 red** (GM-19 `-$6.95`, GM-20 cap. 1
`-$0.84` — **residual aceptado**, no se reabre el motor); **2 skipped**
(GM-24 `@Disabled` por fixture EMELNORTE upstream; `DIAG_rubro_expected_vs_actual`
`@Disabled` — pendiente de borrado en Plan 014). Conteo total de la suite
se **reporta** desde los XML de `build/test-results/`, no se presupone.

Do **not** hardcode a post-change total. After any run, report the real
counts from the XML results instead of predicting them:

```bash
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-*.xml
```

## Repo conventions

- **Package structure**: vertical slices per module (`usuario/`, `motor/`,
  `common/`, and future `insumo/`, `apu/`, `presupuesto/`, `cronograma/`,
  `documento/`). See `08-codebase-design.md §1`.
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
- **Secrets**: dev keys ship in `src/main/resources/META-INF/resources/`
  as `publicKey.pem` and `privateKey.pem`. Never commit prod keys.
  `.env.example` documents all env vars.

## Working with plans

`plans/` is the authoritative workflow log. When starting a task:

1. Read `plans/README.md` first — it tells you what's DONE, TODO, BLOCKED.
2. If a plan applies, read that plan file in full.
3. **Never edit source in a way the current plans don't authorize** —
   either follow an existing plan or write a new one (`plans/007-*.md`,
   `plans/008-*.md`, keep the numbering monotonic).
4. When work finishes, update `plans/README.md` with the new status.

Plans in this repo are executable by less-capable models. Keep them
self-contained: paths, code excerpts, verification commands with expected
outputs, escape hatches ("if X, STOP and report").

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

- **Emil** (etverkade@deltamontero.com) — this repo's primary author.
- **Kevin** (kaandradec@uce.edu.ec) — coauthor; owns some upstream
  `thesis-docs` decisions.
- **Director:** Ing. Zoila de Lourdes Ruiz Chavez, PhD — final call on
  domain-semantic decisions like GM-19/20's rounding question.
