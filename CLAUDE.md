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
- **Rounding to 2 dp happens ONLY at export.** Never in the motor. Never
  in the DB. If you find yourself calling `.setScale(2, ...)` in main code,
  stop and ask why.

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

**Current motor status** (2026-07-24): 21/25 GMs green. **GM-19 and GM-20
are RED**, escalated to the director. The failing tests reflect a
semantic tension between motor arithmetic (6-dp full precision) and
workbook fixtures (2-dp precioUnitario rounding). See
[`plans/README.md`](plans/README.md) §"006" for details.
**Do not touch `Motor.java` or `internal/Consolidador.java`** until the
director decides. The current answer to "why are GM-19/20 red" is not
"a Motor bug"; it's a documented open question.

## Verification commands (memorize)

```bash
./mvnw -q -DskipTests package                          # sanity build
./mvnw -q test                                         # full suite (~2 min cold)
./mvnw -q test -Dtest='ec.uce.propuestas.motor.*'      # motor only
./mvnw -q test -Dtest='ec.uce.propuestas.usuario.*'    # auth only
./mvnw quarkus:dev                                     # live reload
./mvnw package -Dnative -Dquarkus.native.container-build=true   # native (~10 min)
```

Expected test count (as of 2026-07-24): **56 total, 2 red, 2 skipped**.
The 2 red are GM-19 and GM-20 (see above). The 2 skipped are GM-24
(`@Disabled`, upstream fixture bug) and `DIAG_rubro_expected_vs_actual`
(temporary diagnostic method for the GM-19/20 investigation).

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
- **`mvnw` sometimes ships without the executable bit** — `chmod +x mvnw`
  if `./mvnw` says "permission denied" on WSL/macOS.

## What NOT to do

- Do not add framework code to `motor/`. Not even a `@Slf4j`.
- Do not add English translations of domain terms.
- Do not lower a golden-master tolerance from 0.00.
- Do not modify an applied Flyway migration; add a new one.
- Do not use `double` or `float` in any file that participates in cost
  arithmetic.
- Do not log tokens, passwords (raw or hashed), or PII (RNF-08).
- Do not add dependencies to `pom.xml` without a plan authorizing it.
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
