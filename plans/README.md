# Backend implementation plans — `thesis-back-quarkus`

> This directory holds executable implementation plans for the Quarkus backend of
> the SERCOP propuestas técnico-económicas platform. Each plan is self-contained:
> a less capable executor with zero context from any planning conversation should
> be able to open one file, follow its steps, run its verification commands, and
> ship the change.
>
> **Source of truth for what to build:** `../../thesis-docs/plan/backend/01-quarkus-backend.md`
> (stack + bootstrap command), `../../thesis-docs/plan/architecture/08-codebase-design.md`
> (module map), `../../thesis-docs/plan/architecture/06-database-schema.md` (DDL),
> `../../thesis-docs/plan/architecture/07-api-contract.md` (REST contract),
> `../../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` (iteration order).
>
> Plans in this directory implement those docs. They do not re-decide what
> `thesis-docs` has already decided; when a plan cites a domain rule, it cites
> the canonical file.

## Recommended execution order

Plans map onto the XP iteration plan (roadmap/01). The five plans below cover
iteration I-01 in full plus the I-02 hito (motor de cálculo puro).

| # | Plan | Iteration | Status |
|---|---|---|---|
| 001 | [Bootstrap Quarkus project via code.quarkus.io](./001-bootstrap-quarkus.md) | I-01 | **DONE** (2026-07-24, reviewer-verified) |
| 002 | [CI: GitHub Actions (JVM + native)](./002-ci-github-actions.md) | I-01 | **DONE** (2026-07-24, reviewer-verified; needs first push-to-GitHub to prove workflows actually run) |
| 003 | [Postgres schema baseline (V001–V003 migrations)](./003-schema-baseline.md) | I-01 | **DONE** (2026-07-24, reviewer-verified; see V003 data-quality note below) |
| 004 | [Auth module (registration, login, JWT, reset, invitation)](./004-auth-module.md) | I-01 | **DONE** (2026-07-24, 25/25 tests green, 0 token leaks; 4 plan bugs fixed inline — see below) |
| 005 | [Motor de cálculo APU (pure Java + GM tests)](./005-motor-calculo.md) | I-02 | **BLOCKED** (2026-07-24, scaffold present in `2fe6c83` but 2/25 GM tests fail — see below) |

Plans for I-03 through I-12 (proyectos, insumos, APU editor, presupuesto,
cronograma, export, admin, validación final) are not yet written — they
will be authored in later planning sessions once each preceding iteration's
plans are DONE and CI-green.

## Dependency graph

```
001 bootstrap ──┬── 002 CI ────────────────────┐
                │                               │
                ├── 003 schema baseline ────────┤
                │        └── 004 auth ──────────┤   ← end of I-01
                │                               │
                └── 005 motor de cálculo ───────┘   ← I-02 hito (semana 4)
                                                     (parallelizable with 003/004:
                                                      pure Java, no DB, no auth)
```

**Key ordering notes:**
- **002 does not block anything** but is the earliest ROI: it turns
  every push into a regression check. Recommended second.
- **003 blocks 004** (auth needs `usuario` / `refresh_token` /
  `token_usuario` tables) — but only technically. If you want to build
  auth entities first for offline development, that's fine as long as
  the schema plan lands before merging auth.
- **005 has no runtime deps** and can be built anytime after 001. In
  practice, roadmap I-02 slots it in weeks 3–4 (after auth). It is
  written to be executable standalone.

## Conventions for every plan in this directory

- **Every plan begins with a "Context" section** explaining the *why*.
- **Every plan lists in-scope and out-of-scope files/paths explicitly.**
- **Every plan ends with a "Done criteria" section** whose items are commands
  with expected outputs (not prose like "works correctly").
- **Escape hatches:** if the executor hits an ambiguity not covered by the plan
  or the linked docs, STOP and report back — do not improvise. Domain rules in
  `thesis-docs` are frozen by interviews with stakeholders; guessing them wrong
  wastes downstream work.
- **Never invent domain terms.** Use the Spanish domain vocabulary from
  `thesis-docs/plan/domain/03-glosario.md` verbatim.

## Post-execution notes

### 003 V003 — insumos seed data quality (raised 2026-07-24)

The IESS CSV at `../../thesis-docs/plan/domain/_artifacts/insumos-seed-apus-cetro-medico-tulcan.csv`
turned out to be thinner than plan 003 assumed:

- **No `codigo` column** — executor generated synthetic codes `EQ-001…EQ-011`,
  `MO-001…MO-016`, `MA-001…MA-066` (11+16+66 = 93 rows total, no TRANSPORTE).
  If the thesis needs real vendor codes, they must be sourced upstream and
  a new migration (`V004__reseed_insumos.sql`) will replace this seed.
- **Empty `unidad`** on EQUIPO/MANO_OBRA rows — executor substituted `'h'`
  as required by the `insumo` CHECK constraint. Defensible; the schema forces
  it. Same conclusion: fix upstream if a value other than `'h'` was intended.
- **Unit case/encoding mismatch**: the CSV uses `'m2'`, `'m3'` (ASCII) and
  `'Kg'` on some MATERIAL rows, while `unidad_catalogo` seeds `'m²'`, `'m³'`,
  `'kg'` (Unicode/lowercase). Insumo has no FK to unidad_catalogo, so the
  DB accepts them, but the frontend will flag them as unknown-unit warnings
  once P-13 lands.
- **Flyway `quarkus:dev` verification blocked** by a local Windows Postgres
  service occupying port 5432. Executor validated migrations via direct
  `docker exec psql < V00X.sql` — DDL and DML both applied cleanly.

None of these blocks V001/V002 (both are pristine). None blocks feature
plans that read `insumo` (they'll work; unit warnings are cosmetic).

### 004 — plan bugs fixed by the executor (raised 2026-07-24)

The executor made four defensible deviations from plan 004; all approved:

1. **`PanacheEntityBase` + `@GeneratedValue(IDENTITY)`** instead of plan's
   `PanacheEntity`. Reason: `PanacheEntity` defaults to sequence-based ID
   generation, which does NOT map to Postgres `BIGINT GENERATED ALWAYS AS
   IDENTITY`. The executor's choice is the only one that validates against
   the schema with Hibernate `generation: validate`.
2. **JWT key paths** in `application.yml` need `META-INF/resources/` prefix
   (classpath-relative). Plan said bare `privateKey.pem` / `publicKey.pem`
   which failed with `MalformedURLException` at runtime.
3. **`datasource.jdbc.url` moved to `%dev`/`%prod` profiles only**. Setting
   it at global scope deactivates Dev Services for `%test`, breaking IT
   tests. Executor's restructure is the correct Quarkus idiom.
4. **Two extra `pom.xml` edits** beyond the authorized elytron dep:
   `io.rest-assured:rest-assured` (test scope) and a surefire
   `<includes>` pattern to route `*IT.java` through Surefire so
   `mvn test` picks them up. Both required to make the plan's own test
   plan runnable; plan should have listed them.
5. **TC-P01-03 assertion adjusted**: the plan expected first reenvio to
   return 202 and second to return 429, but the registration itself
   creates a fresh VERIFICATION_EMAIL token — so **any** immediate
   reenvio hits the 60 s cooldown. Executor asserts 429 on the first
   call, which correctly encodes the D-01 business rule.

**Non-issue also flagged:** `quarkus-security-jpa` extension from plan 001
is present but unused (this module chose JWT-only auth). Not conflicting,
but dead weight — remove in a cleanup plan if desired.

### 005 — motor de cálculo consolidación fails GM-19 & GM-20 (raised 2026-07-24)

Plan 005's scaffolding (facade `Motor`, snapshot/result records, `internal/`
calculators, `Fixtures.java`, all 4 test classes, 8 JSON + 2 CSV fixtures)
was already committed to main in `2fe6c83` before this session. Executor
verified the tree, ran the suite, and STOPPED per the plan's escape hatch
("Any test in MotorApuTest or MotorConsolidacionTest requires tolerance
> 0.00 to pass → STOP. That's a real bug."). Verified independently after
the dispatch — failures reproduce.

Suite state on `2fe6c83`:

| Suite | Result |
|---|---|
| `MotorApuTest` (21 tests, GM-01…GM-18, GM-22, GM-23, GM-25) | **21/21 green** |
| `MotorConsolidacionTest` (4 tests, GM-19…GM-21, GM-24) | **2 fail, 2 pass** |
| `MotorPropiedadesTest` (5 property tests) | **5/5 green** |

Failing assertions (surefire, verified locally with `./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest'`):

- **GM-19** `MotorConsolidacionTest:53` — `totalGeneral` (2dp) expected
  `395115.32`, actual `395112.82`. Delta **−$2.50** on the Cetro Médico
  Tulcán presupuesto.
- **GM-20** `MotorConsolidacionTest:90` — Root chapter "1" total
  expected `158908.05`, actual `158909.20`. Delta **+$1.15**.

The thesis's `exactitud_calculo` dependent variable requires **0.00**
deviation, so these failures are load-bearing. The plan explicitly
forbids "adjusting the expected value" — the fixtures are authoritative.
Bug lives in `Motor.consolidar` (`internal/Consolidador.java`) or in
`Fixtures.versionFromJson`'s snapshot construction; per-APU math is
correct (all 21 per-APU GMs green + all 5 properties green).

Two additional plan-scope deviations found in the committed code (not
caused by any executor this session — pre-existing on main):

1. **GM-21 allowlist has 11 entries, plan specifies 6.** The plan says
   the 6 sub-cent mismatches are "logged in the domain doc"; no such
   reference is present in the code or docs. Either the workbook has
   more rounding artifacts than the plan captured, or 5 of those 11
   entries are covering up genuine motor bugs. Cannot tell without
   cross-checking each rubro against the source workbook.
2. **GM-24 is a no-op** — the test body is `assertTrue(true, ...)`
   with a comment claiming the STOP was documented, rather than
   raising the STOP. So GM-24's "pass" is not evidence of anything.
   Once GM-19/GM-20 are fixed, GM-24 must be reimplemented against
   the EMELNORTE fixture per plan step 9.

**Next step for the maintainer:** a follow-up plan (`006-motor-consolidacion-fix.md`)
that (a) reproduces both failures with `./mvnw -q test -Dtest='ec.uce.propuestas.motor.MotorConsolidacionTest#GM_19_total_general_tulcan'`,
(b) instruments `Consolidador` to dump each chapter/rubro subtotal against
the fixture's expected values to localize which node diverges, (c) traces
the delta back to a specific formula (candidates: `pesoPonderado` scale-4
rounding, root-chapter aggregation order, workbook `descuento`/`%CI`
inheritance for the Tulcán project), (d) audits the 11 GM-21 allowlist
entries against the source workbook, (e) implements GM-24 properly. Not
authored yet — needs domain input on which of those hypotheses to chase
first.

**Environment quirks logged during verification** (not blockers, but
worth writing down): the machine's default `java` on PATH
(`/usr/lib/jvm/java-25-openjdk`) is a JRE-only install with no `javac`,
so `./mvnw` fails until `JAVA_HOME=/home/etverkade/.jdks/temurin-25.0.3`
is exported. And `mvnw` was committed without the executable bit, so
`./mvnw` fails without `chmod +x` first. Neither is worth a plan on its
own; note them in the repo's dev-setup doc when one exists.

## Considered and rejected

*(empty — no findings have been rejected yet; this section grows as future
planning sessions triage findings)*
