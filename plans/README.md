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
| 006 | [Motor consolidación fix (GM-19/GM-20, GM-21 audit, GM-24 real)](./006-motor-consolidacion-fix.md) | I-02 | **PARTIAL / ESCALATED** (2026-07-24, §1 stub fix + §3 allowlist audit + §4 GM-24 @Disabled all done; GM-19/20 still red — root cause is workbook-rounding semantics, needs domain decision from Emil + director before Motor can be modified) |
| 007 | [Migración Maven → Gradle](../docs/007-migracion-gradle.md) | build tooling | **DONE** (2026-08-01; build/tests/dev-mode verified; CI/CD deferred — plan 002 debt stays open; refinements modernos en §008 del mismo doc) |
| 009 | [Módulos `proyecto` + `insumo`](../docs/modulos/README.md) | I-03 | **DONE** (2026-08-02; ver §009 post-execution notes) |
| 010 | [Seed de escenarios reales (V004)](../docs/04-SEED-ESCENARIOS.md) | I-04 | **DONE** (2026-08-02; 3 proyectos uno por estado, FINALIZADO = workbook CMT; verificado en Postgres limpio + suite sin regresión) |
| 011 | [Módulo APU núcleo (P-19…P-22)](../docs/modulos/03-apu.md) | I-05 | **DONE** (2026-08-11; P-19…P-22, editor APU, filas M/N/O/P, fila HM protegida, override precio + `JsonNullable` write-through vía `Motor.calcularApu`; 10 tests verdes, colección Bruno `api/bruno/08-apu/`) |
| 012 | [Formatter + lint (Spotless/Palantir + -Xlint:all)](../docs/012-format-lint.md) | tooling | **DONE** (2026-08-11; 142 archivos formateados, 0 warnings lint, sin regresión; ver nota post-ejecución) |

### v1.3 backend plans (013-020)

Written 2026-08-29 against `97280ab`. These cover every remaining API gap between
the frontend (fully built) and the backend.

| # | Plan | Priority | Effort | Depends on | Status |
|---|---|---|---|---|---|
| 013 | [Remove esAuxiliar / no-links](./013-remove-es-auxiliar.md) | P0 | S-M | — | **DONE** (2026-08-29; V005 migration, motor & entities cleaned, 76 tests green) |
| 014 | [Presupuesto, capitulos, rubros, versiones](./014-presupuesto-versiones.md) | P0 | L | 013 | TODO |
| 015 | [Cronograma module](./015-cronograma.md) | P1 | M | 014 | TODO |
| 016 | [Document export + config/display](./016-document-export.md) | P2 | L | 014, 015 | TODO |
| 017 | [APU advanced operations + plantillas APU](./017-apu-advanced.md) | P1 | M | 013, 014 | TODO |
| 018 | [Project operations + plantilla proyecto](./018-project-operations.md) | P2 | M | 014 | TODO |
| 019 | [Super-admin module](./019-admin-module.md) | P2 | L | 013, 014 | TODO |
| 020 | [Bases personales](./020-bases-personales.md) | P3 | S | 013 | TODO |

**Recommended execution order:** 013 first (unblocks everything), then 014 (highest
leverage), then 015+017 in parallel, then 016+018+019, finally 020.

**Migrations introduced:** V005 (013), V006 (016), V007 (017), V008 (018), V009 (019), V010 (020).

## Dependency graph

```
001 bootstrap ──┬── 002 CI ────────────────────┐
                │                               │
                ├── 003 schema baseline ────────┤
                │        └── 004 auth ──────────┤   ← end of I-01
                │                               │
                └── 005 motor de cálculo ───────┘   ← I-02 hito (semana 4)

v1.3 plans:

013 remove esAuxiliar ──┬── 014 presupuesto ──┬── 015 cronograma ──┐
                        │                     │                     ├── 016 export
                        │                     ├── 017 APU advanced  │
                        │                     ├── 018 project ops   │
                        │                     └── 019 admin ────────┘
                        └── 020 bases personales
```

**Key ordering notes (v1.3):**
- **013 blocks everything** — the `esAuxiliar` removal is the foundation.
  Every entity, DTO, and the Motor itself change.
- **014 is the highest-leverage single plan** — without it the frontend
  can't reach APUs, chapters, or rubros. Recommend executing immediately
  after 013.
- **015 and 017 can run in parallel** after 014 — cronograma and APU
  advanced have no mutual dependency.
- **016 (export) waits on both 014+015** — needs presupuesto tree and
  cronograma data to generate documents.
- **020 is the smallest and most independent** — only needs 013's clean
  model. Can be done anytime.

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
- **Ponytail mode required.** Before writing any code, the executor agent MUST
  activate `/ponytail:ponytail` (full level). Shortest working diff wins. No
  unrequested abstractions, no scaffolding "for later", no boilerplate. Stdlib
  and platform features first. Deletion over addition. If a one-liner solves it,
  write the one-liner. See the ponytail skill for the full ladder and rules.

## Post-execution notes

### 009 — Módulos `proyecto` + `insumo` (raised 2026-08-02)

Build second-vertical-slice milestones per `docs/modulos/01-proyecto.md` and
`docs/modulos/02-insumo.md`. **State: DONE**, full suite green minus the two
known GM-19/GM-20 reds.

**Proyecto** (`ec.uce.propuestas.proyecto`): entidades `Proyecto`, `Firmante`,
`ParametrosProyecto`, `ParametrosSistema` (LECTURA), enums `EstadoProyecto`,
`PlazoUnidad`, `RolFirmante`, `ModoCodigoRubro`; DTOs/mappers; servicios
`ProyectoService`, `FirmanteService`, `ParametrosProyectoService`; resources
`/proyectos`, `/proyectos/{id}/firmantes`, `/proyectos/{id}/parametros`,
`/proyectos/parametros-sistema`. Propiedad resuelta por email del claim JWT →
404 si ajeno (RNF-05).

**Insumo** (`ec.uce.propuestas.insumo`): `BaseInsumos`, `Insumo`,
`UnidadCatalogo` + enums `TipoInsumo`, `TipoBase`; `InsumoCrudService`,
`BaseInsumosService`, `ImportacionInsumoService`, `CopiaBaseService`,
`UnidadCatalogoService`, `InsumoCatalogoService` (selector multi-fuente
P-16/P-21); parsing CSV puro (`CsvInsumoParser`, commons-csv) con validación
por tipo. Recursos `InsumoResource` (`/proyectos/{proyectoId}/insumos`) y
`BaseInsumosResource` (`/bases-centrales`).

**Deviations (approved, documented in `docs/modulos/`):** (1) `commons-csv`
added a catalog + build; (2) errores vía `ProblemaException` sobre
`GlobalExceptionMapper` (no enum nuevo); (3) resources JAX-RS `Response`
(en vez de `RestResponse<T>`) para coincidir con `PerfilResource`;
(4) D-08 `eliminar insumo` = `stub → 0` hasta el módulo APU (TODO en código);
(5) `parametros_sistema.id` es `Short`; (6) **repositorios obligatorios por
entidad** (`repository/` en ambos módulos) — decisión del autor para separar
SQL de reglas y evitar refactor al añadir consultas; `docs/01-ARQUITECTURA.md`
§5 actualizado; `usuario/` queda como caso previo a alinear; (7) **colecciones Bruno**
`api/bruno/06-proyecto/` y `api/bruno/07-insumo/` con requests autenticados
(login helper + token en variables) y CSV de ejemplo para importación.

**Suite state:** 63 tests total (antes 56), 2 red (GM-19/GM-20 preexistents,
sin tocar el motor), 2 skipped (GM-24, DIAG — como antes).

**Scope decision (2026-08-02): P-09 "duplicar proyecto" EXCLUDED.** Interview
N02 §3 advises against cloning whole projects ("propenso a errores al arrastrar
cronogramas o cantidades pasadas") and approves only copying **insumo bases**
(P-17, implemented). Therefore `POST /proyectos/{id}/duplicar` will NOT be
built; only insumo duplication between bases remains. D-04 is moot. Docs updated
in `docs/modulos/01-proyecto.md`, `docs/modulos/README.md`,
`docs/00-ESTADO-ACTUAL.md` and `docs/03-BASE-DATOS.md` (§6 matiz 2).

### 010 — Seed de escenarios reales (V004) (raised 2026-08-02)

`V004__seed_escenarios.sql` (723 KB, ~2600 líneas) añade 3 proyectos completos,
uno por estado, más soporte (`valor_referencia` Anexo A, `plantilla_apu`
1 SISTEMA + 1 PERSONAL, `log_actividad` catálogo D-13 sin PII) y 2 usuarios
logueables (John Doe / Ana de Armas, `Clave1234`, ids 1 y 2). Escenario C
(FINALIZADO) reutiliza el workbook real **Cetro Médico Tulcán** desde los
fixtures del motor (298 rubros, 395115.32). **State: DONE.**

**Generación:** script desechable `/tmp/opencode/gen_seed_cmt.py` (fragmento C)
+ `gen_esc_b.py` (escenario B sintético con write-through coherente) +
`gen_act_cmt.py`/`gen_act_b.py` (actividades; pesos Σ=100.0000). Los 6 códigos
de APU reutilizados en 2 rubros del workbook se desambiguan con copia `-B`
(D-09 `rubro.apu_id` UNIQUE).

**Verificación (Postgres 18 limpio + suite):** todas las consultas de Done
criteria en §7 del plan pasan; `./gradlew test` = 63 tests, 2 red (GM-19/20
preexistentes), 2 skipped — sin regresión. Notas: la query del plan para el
capítulo 1 CMT se verificó sobre el **subárbol** (los rubros cuelgan de
subcapítulos 1.x); el APU real `501772` no tiene MO → no lleva HM (19 HM totales).

### 011 — Módulo APU núcleo (P-19…P-22) (raised 2026-08-11)

Plan: [`docs/modulos/03-apu.md`](../docs/modulos/03-apu.md). **State: DONE.**

Implementa lista/crea APUs por presupuesto (P-19/P-20), editor de cabecera (P-20),
filas M/N/O/P (P-21) con fila HM automática protegida, y override de precio vía
`JsonNullable` (P-22: presente = setea, `null` = vuelve a heredar, omitido = no
toca). Write-through de derivados (totales, subtotales de sección, costo/costo_hora
por fila) vía `Motor.calcularApu` — RNF-02 a nivel APU, sin invocar internos de
`motor/`.

**Desvíos aprobados (documentados en `docs/modulos/03-apu.md` §6):**
(1) `JsonNullable` vive en `org.openapitools:jackson-databind-nullable` (no viene
con `quarkus-rest-jackson`); se añadió `jackson-databind-nullable = "0.2.10"` al
version catalog + `JacksonConfig` (registra `JsonNullableModule`).
(2) TRUNCATE de `InsumoResourceIT`/`ProyectoResourceIT` ampliado con
`apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto` (cascada FK).

**Verificación:** `./gradlew test --tests 'ec.uce.propuestas.apu.*'` → BUILD
SUCCESSFUL (10 tests). `./gradlew test` completo → 73 tests, 2 red (GM-19/20
preexistentes), 2 skipped — sin regresión. Colección Bruno `api/bruno/08-apu/`
apunta al presupuesto v1 del seed de John Doe ("Rehabilitación de consultorios
UCE"); `presupuestoId` se fija en `environments/dev.bru` (id 1 en BD seed limpia).

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

**Next step for the maintainer:** ~~a follow-up plan (`006-motor-consolidacion-fix.md`)~~
Plan 006 written and partially executed — see the 006 section below for
the actual root cause (spoiler: none of the hypotheses in this paragraph
were right; the delta is intermediate-workbook-rounding in the source
data, not a bug in the Motor).

**Environment quirks logged during verification** (not blockers, but
worth writing down): the machine's default `java` on PATH
(`/usr/lib/jvm/java-25-openjdk`) is a JRE-only install with no `javac`,
so `./mvnw` fails until `JAVA_HOME=/home/etverkade/.jdks/temurin-25.0.3`
is exported. And `mvnw` was committed without the executable bit, so
`./mvnw` fails without `chmod +x` first. Neither is worth a plan on its
own; note them in the repo's dev-setup doc when one exists.

### 006 — executed partially, STOPPED per plan protocol (raised 2026-07-24)

**What landed cleanly** (safe to keep, not merged yet — uncommitted in the
working tree):
- `Fixtures.java` §1 stub-precision fix: 279 stubbed rubros now reproduce
  the workbook's `precioTotal` exactly. This is a strict improvement in
  test-data fidelity even though it didn't fix GM-19/20.
- `MotorConsolidacionTest.java` §3 GM-21 audit: all 11 allowlist entries
  survived the fix (zero removable). Each now has a source comment showing
  motor vs fixture 2dp values. Plan 005's estimate of "6 entries" was
  approximate; the workbook has 11 rounding artifacts.
- `MotorConsolidacionTest.java` §4 GM-24: replaced `assertTrue(true)`
  no-op with a real `@Disabled` annotation citing the upstream
  fixture bug (empty `secciones`, null `codigo`). Confirmed independently
  by reading the first 30 lines of the EMELNORTE fixture.

**What did NOT land** — plan 006's §2 STOP condition triggered:
- GM-19 and GM-20 still fail with the same deltas after §1 (verified with
  fresh `./mvnw -q test`). Executor added a temporary `@Disabled`
  `DIAG_rubro_expected_vs_actual` diagnostic method that dumps per-rubro
  expected-vs-actual; running it (with `@Disabled` removed) produced:
  ```
  DIAG SUMMARY: fixtureSum=395115.32 motorSum=395112.82 totalDelta=-2.499876
  divergentCount=16 stubCount=279
  first divergent: item=1.1.1 codigo=501BM6 fixturePT=1568.308000 motorPT=1569.791937 delta=+1.483937
  ```
  All 16 divergent rubros are real-APU rubros (not stubs). Motor is
  arithmetically correct; the workbook is computing
  `precioTotal = cantidad × precioUnitario_2dp` rather than
  `cantidad × CT_full_precision`.

**Verified reviewer arithmetic** (independent of executor):
  `501BM6: 283.6 × 5.5352325 = 1569.79` (Motor) vs
  `283.6 × 5.53 = 1568.308` (workbook). Delta +$1.48 for one rubro.
  Sum of 16 similar rows nets to −$2.50.

**Escalation decision** (2026-07-24, per Emil): pause the fix. Do not
edit `Motor.java` or `Consolidador.java` yet. Raise with the director
before deciding whether:
  (a) Motor rounds `apu.costoTotal` to 2dp when constructing
      `RubroConPrecio.precioUnitario` (matches workbook exactly; motor
      internals stay 6dp),
  (b) Motor stays arithmetically pure and GM-19/GM-20 expected values
      are updated to $395,112.82 (thesis defense point becomes "motor
      is more accurate than the reference workbook"),
  (c) Upstream fixture is regenerated so `precioTotal =
      cantidad × CT_full_precision` — Motor matches automatically,
      but the audit trail against the real published SERCOP workbook
      is lost.

**Diagnostic left in place** for the follow-up session:
`MotorConsolidacionTest.DIAG_rubro_expected_vs_actual` is
`@Disabled`; remove `@Disabled` and re-run to reproduce the dump.

### 007 — Migración Maven → Gradle (raised 2026-08-01)

Build system migrated from Maven to Gradle 9.5.1 (Kotlin DSL). The plan lives
in [`../docs/007-migracion-gradle.md`](../docs/007-migracion-gradle.md) (per
author decision it was placed in `docs/`, not `plans/`). Outcome:

- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`, `target/` deleted; added
  `settings.gradle.kts`, `gradle.properties`, `build.gradle.kts`, wrapper
  (`./gradlew`, Gradle 9.5.1).
- `./gradlew build -x test` → BUILD SUCCESSFUL, fast-jar at
  `build/quarkus-app/quarkus-run.jar`.
- `./gradlew test` → full suite **63 tests, 2 red (GM-19, GM-20 — known domain
  issue, not a regression), 2 skipped (GM-24 @Disabled, DIAG)**.
- `./gradlew --console=plain quarkusDev` smoke-tested against the compose
  Postgres: health UP, Flyway validated 3 migrations, OpenAPI 200.
- **CI/CD deferred** (author decision 2026-08-01): the plan 002 workflow
  debt stays open; when written it must use `./gradlew` and Gradle's
  `build/test-results/` + `build/*-runner` paths.
- Gotcha captured: Gradle `Test` task `include` patterns match compiled
  `.class` names, not `.java` files — `**/*Test.java` leaves the task at
  `NO-SOURCE`. Use `**/*Test.class` / `**/*IT.class`.
- Pre-existing (not migration) warnings: `quarkus.health.extensions.enabled`
  unrecognized and `quarkus.hibernate-orm.database.generation` deprecated,
  both from `application.yml`.

### 008 — Refinamientos modernos del build (raised 2026-08-01)

Detalle completo en [`../docs/007-migracion-gradle.md`](../docs/007-migracion-gradle.md) §008.
Resumen (decisión del autor: los planes son base, no muro; se aplica lo moderno
que aporta valor real):

- **Version catalog** `gradle/libs.versions.toml` — única fuente de versiones
  (Quarkus 3.37.4, poi, openpdf, jqwik). Plugin vía `alias(libs.plugins.quarkus)`.
- **Toolchain JDK 25** — el build corre sobre JDK 25 (auto-detectado o
  auto-descargado vía foojay). Target Java: **21 → 25** (decisión del autor).
- `gradle.properties` limpio: fuera `quarkusPlatform*` (ahora en el catalog);
  dentro `org.gradle.caching` + `org.gradle.parallel`.
- **Lombok/@Builder RECHAZADO** (verificado: DTOs de entrada no se construyen a
  mano; solo 14 sitios `new <Record>()` en main; motor es Java puro sin framework).
- **Consul/Stork/Micrometer/OTel/K8s/Jib RECHAZADO** — monólito, no microservicios;
  `thesis-docs` no lo contempla.
- Verificado: `./gradlew build` OK, `./gradlew test` 56 tests (2 red GM-19/20,
  2 skipped — sin regresión), `quarkusDev` arranca en `:8080` (credenciales
  compose: `DB_USER=postgres DB_PASSWORD=postgres`).

### 012 — Formatter + lint (raised 2026-08-11)

Plan: [`docs/012-format-lint.md`](../docs/012-format-lint.md). **State: DONE.**

Spotless 8.9.0 (`com.diffplug.spotless`, palantir-java-format sin versión fija)
+ `-Xlint:all` en `JavaCompile`. `spotlessApply` formateó 142 archivos Java
(main + tests, +1465/−1196, whitespace-only — verificado vía `git diff -w` y
suite). `spotlessCheck` acoplado a `check` via `tasks.named("check")`.

**Decisiones del autor:** (1) motor formateado *sin* exclusión — los cambios
en `Motor.java`/`internal/Consolidador.java` son whitespace-only (GM-19/20
siguen red por el tema de rounding del workbook, no por formato); (2) Error
Prone NO se añade (requiere su propio plan); (3) `-Werror` NO — solo ver.

**Verificación:** `./gradlew spotlessCheck` OK · `./gradlew build -x test` OK ·
`./gradlew test` → 77 tests, 2 red (GM-19/20 preexistentes), 2 skipped.

## Considered and rejected

*(empty — no findings have been rejected yet; this section grows as future
planning sessions triage findings)*
