# 001 — Bootstrap Quarkus project via code.quarkus.io

- **Status:** DONE (executed 2026-07-24, reviewer-verified)
- **Actual platform version used:** `3.37.4` (Quarkus CLI 3.23.3 selected current stable; plan's `3.15.1` was the pinned Maven fallback only)
- **Iteration:** I-01 (roadmap/01, weeks 1–2)
- **Written against docs commit:** `d7508eb` (of `../../thesis-docs/`)
- **Depends on:** — (this is the first plan)
- **Blocks:** all subsequent backend plans

---

## Context

`thesis-back-quarkus/` is currently empty. Before any feature work (auth, motor
de cálculo, insumos, APU, presupuesto, cronograma, export) can start, we need
a runnable Quarkus 3 skeleton with the exact extension set the backend design
depends on — persistence over Postgres, JWT auth with bcrypt, OpenAPI, health,
YAML config, Flyway, bean validation, Panache, REST + Jackson.

The upstream backend spec
(`../../thesis-docs/plan/backend/01-quarkus-backend.md`) picks a single set of
project coordinates and extensions and mandates using **code.quarkus.io** or
the Quarkus CLI so the skeleton is generated once, coherently, with matching
Dockerfiles and test scaffolding — rather than adding extensions one by one
later. That decision is not up for re-litigation in this plan; we execute it.

Two libraries the initializer cannot provide (Apache POI, OpenPDF) are needed
for the `documento` module in a later iteration and are added to `pom.xml` here
so the build graph is complete from the start. No document-generation code is
written in this plan — just the dependencies.

**Why this matters:** the thesis dependent variables
(`exactitud_calculo`, `integridad_referencial`, `conformidad_formato`,
`cumplimiento_estructura`) all require an existing runnable backend to measure.
Delaying the skeleton delays everything. Roadmap I-01 hito (week 2) is "CI
activo con build nativo" — this plan produces the artifact CI will build.

## In scope

Only file operations under `thesis-back-quarkus/` (the current working
directory). The plan will produce:

- Every file that `quarkus create app …` generates (pom, `src/main`, `src/test`,
  `.mvn`, `mvnw`, `mvnw.cmd`, `.gitignore`, `README.md`, `src/main/docker/*`).
- Edits to two files after generation:
  - `pom.xml` — add Apache POI and OpenPDF dependencies (see §2.3).
  - `src/main/resources/application.yml` — replace the generated
    `application.properties` with a YAML file that sets the datasource,
    Hibernate, Flyway, HTTP port, CORS, and OpenAPI settings for **dev**
    and **prod** profiles (see §2.4).
- A stub `src/main/resources/db/migration/V001__baseline.sql` **with a single
  `SELECT 1;` line** as a placeholder — the real schema baseline is a separate
  future plan (003). Flyway needs at least one migration file to be happy on
  first boot.
- A `.env.example` documenting the env vars the app reads (DB URL, JWT keys).

## Out of scope — do NOT touch or add

- Any Java source under `src/main/java/ec/uce/propuestas/**` beyond what the
  initializer generates. **Delete** the sample `GreetingResource` and its test
  if the initializer produces one, but do not write new resources, entities,
  services, or DTOs. Those belong to later plans (motor: 005; auth: 004;
  insumos: I-04; etc.).
- The database schema. `V001__baseline.sql` stays as a `SELECT 1;` stub.
- GitHub Actions workflows. That is plan 002.
- JWT signing keys — do not generate real keys; document env var names only.
- Frontend, deployment configs (Render/Koyeb/Cloud Run manifests), Cloudflare,
  Neon setup. None of those live in this repo.

## Repo conventions to match

- Domain vocabulary is **Spanish** (`../../thesis-docs/CLAUDE.md` §"What this
  project is"). Package names in the module map use Spanish nouns
  (`insumo`, `apu`, `presupuesto`, `cronograma`, `documento`, `usuario`,
  `common`) — see `../../thesis-docs/plan/backend/01-quarkus-backend.md §2`.
  Do **not** create those packages in this plan; just know the group id
  `ec.uce.propuestas` and artifact `propuestas-api` land in `pom.xml`.
- Naming: `snake_case` for DB tables/columns (schema doc), `camelCase` for Java
  identifiers, `kebab-case` for YAML config keys (Quarkus convention).
- `NUMERIC(14,6)` + `BigDecimal` for all money math (never `double`) — a
  discipline enforced later; noted here so no `double` sneaks in via a sample.

## Steps

### 1 — Confirm prerequisites

Run each; abort and report if any fails.

```bash
java -version            # must be 21.x (LTS); if not, install JDK 21 first
mvn -version             # any recent Maven works; the initializer also emits mvnw
quarkus --version        # optional but preferred; if absent, use the Maven route in §2b
```

**If `java -version` reports anything other than 21.x:** STOP. Report back with
the installed version. Do not proceed with a different JDK — Quarkus 3.x + the
native image path in later plans assume Java 21.

### 2a — Generate the project (Quarkus CLI route, preferred)

Run **from inside** `thesis-back-quarkus/` (the directory is currently empty,
so the CLI will populate it directly rather than nesting a subfolder):

```bash
quarkus create app ec.uce.propuestas:propuestas-api \
  --java=21 \
  --no-code \
  --extension='rest,rest-jackson,hibernate-orm-panache,jdbc-postgresql,flyway,smallrye-jwt,smallrye-jwt-build,security-jpa,hibernate-validator,smallrye-openapi,smallrye-health,config-yaml'
```

Flags:

- `--no-code` — skip the sample `GreetingResource`. If your CLI version rejects
  this flag, omit it and delete the sample files manually in step 4.
- The extension list is the one from
  `../../thesis-docs/plan/backend/01-quarkus-backend.md §1` **plus**
  `smallrye-jwt-build` (needed to issue tokens at login time; `smallrye-jwt`
  alone only verifies them).

**If the CLI populates a subdirectory instead of the current directory** (some
CLI versions always create a folder named after the artifact): move the
generated contents up one level so `pom.xml` sits directly under
`thesis-back-quarkus/`, then remove the now-empty subdirectory.

### 2b — Generate the project (Maven fallback, if the CLI is not installed)

From inside `thesis-back-quarkus/`:

```bash
mvn io.quarkus.platform:quarkus-maven-plugin:3.15.1:create \
  -DprojectGroupId=ec.uce.propuestas \
  -DprojectArtifactId=propuestas-api \
  -DjavaVersion=21 \
  -DnoCode=true \
  -Dextensions='rest,rest-jackson,hibernate-orm-panache,jdbc-postgresql,flyway,smallrye-jwt,smallrye-jwt-build,security-jpa,hibernate-validator,smallrye-openapi,smallrye-health,config-yaml'
```

Then flatten if a subfolder was created (same note as 2a).

**If neither route works** (the Quarkus platform version 3.15.1 has been
superseded by the time you run this): open `https://code.quarkus.io`, tick
exactly the extensions listed above, set group/artifact/java-version to match,
download the zip, and extract into `thesis-back-quarkus/`. Record the exact
platform version used in a comment in `pom.xml` near the
`<quarkus.platform.version>` property.

### 3 — Sanity check the generated skeleton

Run:

```bash
./mvnw -q -DskipTests package
```

**Expected:** BUILD SUCCESS, and `target/quarkus-app/quarkus-run.jar` exists.

If the build fails, STOP and report the failure verbatim. Do not try to
"fix up" the generated pom — a broken initial generation means we picked the
wrong initializer version and should re-run §2 pinned to a known-good
`quarkus.platform.version`.

### 4 — Delete sample code (only if `--no-code` did not apply)

If `src/main/java/ec/uce/propuestas/GreetingResource.java` (or similarly named)
exists, delete it along with any matching test under `src/test/java/…`.

The `src/main/java/ec/uce/propuestas/` directory should end up **empty**. Do
not create replacement classes — feature packages belong to later plans.

### 5 — Add Apache POI and OpenPDF to `pom.xml`

Inside the `<dependencies>` block of `pom.xml`, append (versions pinned to
current stable at the time this plan was written; bump if newer stable exists,
but do not switch libraries):

```xml
<dependency>
  <groupId>org.apache.poi</groupId>
  <artifactId>poi-ooxml</artifactId>
  <version>5.3.0</version>
</dependency>
<dependency>
  <groupId>com.github.librepdf</groupId>
  <artifactId>openpdf</artifactId>
  <version>2.0.3</version>
</dependency>
```

**Do not** add PDFBox — the backend design (backend/01 §4) picks OpenPDF; if
that decision needs revisiting, it is a separate plan, not an ad-hoc swap here.

Re-run `./mvnw -q -DskipTests package` — must still succeed.

### 6 — Replace `application.properties` with `application.yml`

Delete `src/main/resources/application.properties` (if present). Create
`src/main/resources/application.yml` with the following content — verbatim:

```yaml
quarkus:
  application:
    name: propuestas-api

  http:
    port: 8080
    cors:
      ~: true
      origins: http://localhost:5173,http://localhost:3000
      methods: GET,POST,PUT,DELETE,OPTIONS
      headers: accept,authorization,content-type

  datasource:
    db-kind: postgresql
    username: ${DB_USER:propuestas}
    password: ${DB_PASSWORD:propuestas}
    jdbc:
      url: ${DB_URL:jdbc:postgresql://localhost:5432/propuestas}

  hibernate-orm:
    database:
      generation: validate
    log:
      sql: false

  flyway:
    migrate-at-start: true
    baseline-on-migrate: true
    locations: db/migration

  smallrye-jwt:
    enabled: true
  # JWT keys are provided via env vars mp.jwt.verify.publickey (or .location)
  # and smallrye.jwt.sign.key (or .location). Set in prod deploy; not in repo.

  swagger-ui:
    always-include: true    # /q/swagger-ui in dev+prod; useful as thesis appendix

  smallrye-openapi:
    path: /q/openapi

  health:
    extensions:
      enabled: true

"%dev":
  quarkus:
    hibernate-orm:
      log:
        sql: true
    log:
      category:
        "ec.uce.propuestas":
          level: DEBUG

"%prod":
  quarkus:
    hibernate-orm:
      log:
        sql: false
```

Notes for the executor:

- `hibernate-orm.database.generation: validate` means Hibernate will **not**
  create/alter tables — Flyway owns the schema. This is intentional. Do not
  change to `update` or `drop-and-create`.
- `datasource.username / password / jdbc.url` are read from env vars with the
  `${VAR:default}` fallback. The defaults let Dev Services skip container spin
  for dev if a local Postgres exists; if none exists, Quarkus Dev Services will
  auto-spin a Postgres container in dev mode (that's a Quarkus feature enabled
  by having `quarkus-jdbc-postgresql` on the classpath).
- **CORS origins are placeholders** — the real production origin (Cloudflare
  Pages URL) is set by a later plan when frontend deployment lands.

### 7 — Create the Flyway migration stub

Create `src/main/resources/db/migration/V001__baseline.sql`:

```sql
-- Placeholder baseline. The real schema is defined in plan 003
-- (thesis-docs/plan/architecture/06-database-schema.md).
-- This file exists solely so Flyway does not fail on empty locations.
SELECT 1;
```

### 8 — Create `.env.example`

At the repo root of `thesis-back-quarkus/`, create `.env.example`:

```
# Postgres — override in local .env or hosting provider secrets.
DB_URL=jdbc:postgresql://localhost:5432/propuestas
DB_USER=propuestas
DB_PASSWORD=change-me

# JWT — generate real keys before deploying; NEVER commit real keys.
# See https://quarkus.io/guides/security-jwt#generating-a-key-pair
MP_JWT_VERIFY_PUBLICKEY_LOCATION=classpath:META-INF/resources/publicKey.pem
SMALLRYE_JWT_SIGN_KEY_LOCATION=classpath:META-INF/resources/privateKey.pem
MP_JWT_VERIFY_ISSUER=https://propuestas-api.local
```

Add `.env` (without the `.example` suffix) to `.gitignore` if it isn't already
there — the generated `.gitignore` from Quarkus normally ignores `.env`, but
verify with `grep -n '^\.env' .gitignore`. If missing, append `.env`.

**Do not** create real `publicKey.pem` / `privateKey.pem` files in this plan.
JWT key generation is a step in the auth plan (004).

### 9 — Verify

Run each command; each must succeed with the expected output before this plan
is DONE.

```bash
# Build succeeds
./mvnw -q -DskipTests package
# Expected: BUILD SUCCESS. target/quarkus-app/quarkus-run.jar exists.

# Generated tests still pass (should be zero tests until later plans add them,
# but Maven surefire must not error)
./mvnw -q test
# Expected: BUILD SUCCESS, "Tests run: 0" (or the sample test if --no-code did
# not apply — in which case delete it per step 4 and re-run).

# Dev mode starts, health endpoint responds
./mvnw quarkus:dev &          # note: leaves a process running
sleep 15
curl -sf http://localhost:8080/q/health | grep -q '"status":"UP"'
# Expected: exit 0 (grep found UP).
curl -sf http://localhost:8080/q/openapi | head -3
# Expected: an OpenAPI document (starts with 'openapi: 3.' or similar).
curl -sf http://localhost:8080/q/swagger-ui/ | head -3
# Expected: HTML.
# Stop the dev process: press 'q' in its terminal, or `kill %1`.
```

**If the dev server fails to start with a Flyway or datasource error** and Dev
Services did not spin a Postgres container: your Docker/Podman is not running
or is unreachable. Either start it and re-try, or set
`quarkus.datasource.devservices.enabled=false` and provide a real local
Postgres — but the recommended path is to fix Docker, since later plans lean
on Dev Services for integration tests.

## Done criteria

Machine-checkable. All must pass.

- [ ] `pom.xml` exists at `thesis-back-quarkus/pom.xml` with
  `<groupId>ec.uce.propuestas</groupId>`, `<artifactId>propuestas-api</artifactId>`,
  and `<maven.compiler.release>21</maven.compiler.release>` (or the Quarkus
  equivalent property).
- [ ] The `<dependencies>` block includes: `quarkus-rest`, `quarkus-rest-jackson`,
  `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`,
  `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`, `quarkus-security-jpa`,
  `quarkus-hibernate-validator`, `quarkus-smallrye-openapi`,
  `quarkus-smallrye-health`, `quarkus-config-yaml`,
  `org.apache.poi:poi-ooxml`, `com.github.librepdf:openpdf`.
- [ ] `src/main/resources/application.yml` exists with the contents from step 6
  (no `application.properties`).
- [ ] `src/main/resources/db/migration/V001__baseline.sql` exists with the
  placeholder `SELECT 1;`.
- [ ] `src/main/java/ec/uce/propuestas/` exists but is **empty** (no sample
  `GreetingResource` remains).
- [ ] `.env.example` exists at the repo root.
- [ ] `./mvnw -q -DskipTests package` → BUILD SUCCESS.
- [ ] `./mvnw -q test` → BUILD SUCCESS, 0 test failures.
- [ ] `./mvnw quarkus:dev` starts within ~30 s and
  `curl http://localhost:8080/q/health` returns `"status":"UP"`.
- [ ] `curl http://localhost:8080/q/openapi` returns an OpenAPI document.

## Test plan

No production code is added in this plan, so no new production tests are
written. The verification steps above **are** the acceptance tests — treat
them as a checklist to run at plan completion and again in CI (plan 002).

## Maintenance note

- **Quarkus platform version:** when bumping, re-run the full verification
  above. Extension IDs occasionally rename between majors (e.g. `resteasy` →
  `rest` in 3.x). If an extension ID no longer resolves, check
  `https://quarkus.io/extensions` for the current ID before renaming; don't
  silently drop it.
- **CORS origins:** must be updated when the frontend production URL is known.
  That change is a one-line YAML edit; do not build a config abstraction for
  it.
- **JWT keys:** the auth plan (004) generates real keys and documents the
  secret-management story. Until then, no request that requires JWT will
  succeed — that is intentional.

## Escape hatches — STOP conditions

- Java version is not 21.x → STOP; do not proceed with a different JDK.
- The Quarkus initializer produces an extension the current platform version
  no longer supports → STOP and report which extension; do not substitute.
- `./mvnw -q -DskipTests package` fails after step 5 → STOP; do not "fix" by
  removing dependencies. Report the exact error.
- Any step wants you to create a Java class, an entity, a resource, a service,
  a DTO, or a mapper → STOP. This plan is *only* scaffolding. Feature classes
  belong to plans 004+.
