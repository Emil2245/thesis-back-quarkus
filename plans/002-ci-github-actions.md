# 002 — CI: GitHub Actions (JVM build + tests, native build on push to main)

- **Status:** TODO
- **Iteration:** I-01 (roadmap/01, weeks 1–2) — the "hito de semana 2: pipeline CI activo"
- **Written against:** repo state after plan 001 DONE (commit not yet made)
- **Depends on:** 001 (Quarkus skeleton must exist and build)
- **Blocks:** all subsequent feature plans (they will use CI green as their gate)

---

## Context

Plan 001 produced a runnable Quarkus skeleton. Roadmap week-2 hito is "pipeline
CI activo con build nativo + tests" — every subsequent iteration's Golden
Master suite (motor de cálculo) treats CI-green as a gate ("una desviación
GM ≠ 0.00 rompe el build", roadmap I-02). This plan sets up that pipeline
before there's any code to break, so we get free regression coverage from
day one.

**Scope decision:** we ship **two workflows**:
1. `ci.yml` — runs on every push and every PR. **JVM build + tests only.**
   Fast (~2–3 min) so PR feedback is quick.
2. `native.yml` — runs on push to `main` and on tags. **GraalVM native
   build** + starts the native binary + hits `/q/health`. Slow (~8–15 min),
   catches native-image regressions (POI/PDFBox reflection is the known
   risk from `../../thesis-docs/plan/backend/01-quarkus-backend.md §4`) but
   not per-PR.

Testcontainers / Dev Services works in GitHub Actions runners without extra
setup — the `ubuntu-latest` image ships with Docker. No Postgres service
container is needed for now (there are zero `@QuarkusTest` classes yet);
Dev Services will spin one automatically once integration tests land.

## In scope

- `.github/workflows/ci.yml` (new)
- `.github/workflows/native.yml` (new)
- (no other files)

## Out of scope — do NOT touch or add

- Any `pom.xml` edits. If a CI change requires a pom edit, STOP.
- Any Java source, test code, config files, migrations. This plan wires CI to
  the existing skeleton only.
- Any deployment step (Render/Koyeb/Cloud Run). Deploy pipelines are a later
  concern.
- Any secrets. This plan uses no `${{ secrets.* }}` — nothing yet needs them.
- Coverage tooling (jacoco), Sonar, code-quality gates. All later.
- Caching beyond Maven's built-in `~/.m2/repository`.

## Repo conventions to match

- Workflow filenames are `kebab-case.yml`.
- The one command that runs every gate is `./mvnw -B -ntp ...` (batch mode +
  no-transfer-progress; standard for CI to keep logs readable).
- Java 21 (LTS) — plan 001 pinned `<maven.compiler.release>21</maven.compiler.release>`;
  the CI must use the same major.

## Steps

### 1 — Confirm plan 001 state

Before writing any workflow, confirm the skeleton actually builds locally:

```bash
./mvnw -q -DskipTests package
```

Expected: BUILD SUCCESS. If not: STOP. Plan 001 has drifted; do not layer CI
on a broken base.

### 2 — Create `ci.yml`

Path: `.github/workflows/ci.yml`. Contents — verbatim:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Verify Maven wrapper
        run: ./mvnw -B -ntp -version

      - name: Package (JVM, skip tests)
        run: ./mvnw -B -ntp -DskipTests package

      - name: Run tests
        run: ./mvnw -B -ntp test

      - name: Upload surefire reports on failure
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: surefire-reports
          path: target/surefire-reports/
          if-no-files-found: ignore
          retention-days: 7
```

**Why this shape:**
- `concurrency`: cancels superseded runs on the same branch — saves CI minutes
  when you push twice fast.
- Two Maven steps (package first, then test): if the package step fails you
  don't waste time waiting for tests; also, most compile errors show up in
  `package` with clearer output than surefire's.
- `cache: maven` on `setup-java` handles `~/.m2/repository` caching.
  No custom `actions/cache` block needed.
- Surefire report upload is `if: failure()` so PRs stay clean.
- `timeout-minutes: 15` — the first build with a cold Maven cache can take
  ~5 min; steady-state is ~2 min. 15 gives comfortable headroom without
  hiding a runaway.

### 3 — Create `native.yml`

Path: `.github/workflows/native.yml`. Contents — verbatim:

```yaml
name: Native build

on:
  push:
    branches: [main]
  workflow_dispatch:

concurrency:
  group: native-${{ github.ref }}
  cancel-in-progress: true

jobs:
  native-build:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build native image (container build)
        run: ./mvnw -B -ntp package -Dnative -Dquarkus.native.container-build=true

      - name: Smoke-test the native binary
        run: |
          BIN=$(ls target/*-runner 2>/dev/null | head -1)
          if [ -z "$BIN" ]; then
            echo "No native runner binary produced." >&2
            exit 1
          fi
          # Boot the binary in the background against a DB-less profile.
          # Datasource start is skipped so we don't need a Postgres for the smoke test.
          "$BIN" -Dquarkus.datasource.health.enabled=false -Dquarkus.hibernate-orm.enabled=false &
          PID=$!
          # Wait up to 20 s for /q/health to be UP.
          for i in $(seq 1 40); do
            if curl -sf http://localhost:8080/q/health >/dev/null; then
              echo "Native binary responded UP after ${i}×500ms"
              kill $PID
              exit 0
            fi
            sleep 0.5
          done
          echo "Native binary did not respond in 20 s" >&2
          kill $PID 2>/dev/null || true
          exit 1

      - name: Upload native binary
        uses: actions/upload-artifact@v4
        with:
          name: native-runner
          path: target/*-runner
          if-no-files-found: error
          retention-days: 14
```

**Why this shape:**
- `-Dquarkus.native.container-build=true` uses the Quarkus-provided
  Mandrel/GraalVM container. No need to install GraalVM in the runner —
  saves cache setup, keeps the workflow reproducible.
- The smoke test disables datasource + Hibernate at binary startup so we can
  hit `/q/health` without a Postgres running. This validates *the native
  binary boots at all* (the actual "does POI/PDFBox reflection work"
  regression comes online once those libs are used by real code in later
  plans; for now the smoke test is basic-liveness only).
- 30 min timeout: native container builds routinely take 8–12 min on
  `ubuntu-latest`; leave headroom for cold-cache runs.
- `push: [main]` + `workflow_dispatch` — no per-PR native build (too slow);
  can trigger manually from the Actions UI when you want to verify a
  native-critical PR.

### 4 — Verify locally (dry-run of what CI will do)

```bash
# The commands ci.yml runs
./mvnw -B -ntp -DskipTests package        # expected: BUILD SUCCESS
./mvnw -B -ntp test                       # expected: BUILD SUCCESS, 0 tests

# The command native.yml runs — SKIP if Docker is not available locally
# (this is a heavy 10-min build; skipping locally is fine, CI will run it)
```

Do NOT run the native build locally as part of this plan's verification — it
takes 8–15 min and is fully covered by the `native.yml` workflow itself.

### 5 — Sanity-check the workflow YAML

Both files must be valid YAML. Any decent editor / IDE will flag syntax
errors, but as a belt-and-braces check:

```bash
python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"
python -c "import yaml; yaml.safe_load(open('.github/workflows/native.yml'))"
```

Expected: no output (exit 0) for both. If `python` isn't available, skip
this — the actual validation happens the moment you push and GitHub tries
to parse the workflow.

## Done criteria

- [ ] `.github/workflows/ci.yml` exists with the exact contents from step 2.
- [ ] `.github/workflows/native.yml` exists with the exact contents from
  step 3.
- [ ] `./mvnw -B -ntp -DskipTests package` → BUILD SUCCESS locally.
- [ ] `./mvnw -B -ntp test` → BUILD SUCCESS locally, 0 test failures.
- [ ] Both YAML files parse (step 5) OR the executor confirms it lacks
  `python` and skipped that step.

**Post-merge criterion (executor cannot verify — the reviewer/user will):**
after the next push to `main`, both workflows run to green in GitHub
Actions. Failure of either → BLOCKED, not DONE.

## Test plan

No new production tests. The workflows themselves are the tests: they
exercise `./mvnw package` + `./mvnw test` + native build every time. Future
plans will add real tests that these workflows will pick up automatically.

## Maintenance note

- **When feature plans add `@QuarkusTest` integration tests:** Dev Services
  will spin up Postgres in the runner. If runner disk pressure becomes
  visible in logs (image pulls competing with Maven cache), switch to a
  GitHub Actions `services: postgres:16` block and point Quarkus at
  `localhost:5432` via env vars in the workflow — this is documented but
  not needed yet.
- **When adding coverage tooling** (later): add a `jacoco:report` step and
  upload to Codecov via `codecov/codecov-action`. Do not add coverage
  gating until the motor plan (005) is DONE — otherwise the gate blocks
  every early PR.
- **If native build starts failing on POI reflection** (plan 004+ or plan
  where document generation lands): add reflect-config JSON under
  `src/main/resources/META-INF/native-image/`. Do NOT fall back to JVM-only
  deploys silently — record the failure and address it as a separate plan.
- **Renovate / Dependabot:** consider adding after plan 005 to keep
  dependency versions fresh; skip for now.

## Escape hatches — STOP conditions

- `./mvnw -B -ntp -DskipTests package` fails after step 2 (i.e., before you
  even touch native) → STOP. This means plan 001 has drifted since it was
  marked DONE. Do not "fix up" pom.xml here.
- The `python` YAML validation reports a parse error → STOP. Report the
  exact line and column; do not silently guess-fix indentation.
- Any step wants you to add a service container, add secrets, add caches,
  add coverage, add deployment, or add any workflow beyond the two named
  here → STOP. Scope is exactly these two workflows.
