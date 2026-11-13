# propuestas-api

Gradle multiproject with `:core` (API, PostgreSQL/Flyway, business rules),
`:documentos` (independent service, no database: renders documents and stores them in a
private S3 bucket) and `:documentos-contract` (typed projections, not a third service).
Every download goes through `POST …/exportaciones` plus a one-use ticket from Core.

Quarkus backend for the SERCOP propuestas técnico-económicas platform —
a cloud-native web application that automates APU calculation, budget
consolidation, and execution-schedule generation for Ecuadorian public
procurement bids (LOSNCP / SERCOP).

**Thesis:** *Diseño e implementación de una plataforma cloud-native para
la automatización y optimización de la elaboración de propuestas
técnico-económicas en proyectos de obras civiles*
Universidad Central del Ecuador — FICA — Computación.
Authors: Emil Verkade, Kevin Andrade. 24-week / 6-month timeline.

---

## Where the design lives

This repo contains code. **All design decisions live in `../thesis-docs/`**:

| Document | What it decides |
|---|---|
| `../thesis-docs/PROJECT_SPEC.md` | Scope, objectives, out-of-scope, methodology |
| `../thesis-docs/plan/backend/01-quarkus-backend.md` | Stack (Quarkus, Panache, POI, OpenPDF, JWT/bcrypt) |
| `../thesis-docs/plan/architecture/06-database-schema.md` | Full DDL — canonical |
| `../thesis-docs/plan/architecture/07-api-contract.md` | REST contract per endpoint |
| `../thesis-docs/plan/architecture/08-codebase-design.md` | Module map, deep-module discipline |
| `../thesis-docs/plan/domain/02-data-model.md` | Motor de cálculo formulas (§16) — non-negotiable |
| `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` | 12 iteration plan (I-01…I-12), TC/GM/CHK ids |
| `../thesis-docs/plan/quality/02-catalogo-pruebas.md` | Test-case catalog and golden masters |

Code changes that alter documented behavior must be reflected upstream in
the same commit set. Do not divergently re-decide things here.

---

## Stack

- Java 25 (toolchain) · Gradle 9.8.2 (wrapper) · Quarkus 3.40.1
- Core: Panache ORM (Hibernate) · PostgreSQL 18; Documentos: sin PostgreSQL
- Flyway migrations
- SmallRye JWT (bcrypt via Elytron)
- SmallRye OpenAPI · Swagger UI at `/q/swagger-ui`
- Apache POI (xlsx) + OpenPDF (pdf) — server-side document export
  (ET, cronograma XLSX/PDF, MSPDI XML — implemented across I-06, I-09
  and I-10)
- JUnit 5 · RestAssured · jqwik (property tests)
- Dev Services (auto-Postgres) in tests

---

## Running locally

```bash
# Core with live reload (Postgres via Dev Services when Docker is available)
./gradlew --console=plain :core:quarkusDev

# Documentos (independent port 8081; no PostgreSQL datasource)
# Configure MP_JWT_VERIFY_PUBLICKEY_LOCATION in Documentos with the dedicated
# service public key; Core uses DOCUMENTOS_SERVICE_SIGNING_KEY_LOCATION for its
# matching PKCS#8 private key. Core rejects reuse of its user JWT RSA key,
# even if the same key material is copied to a different path.
./gradlew --console=plain :documentos:quarkusDev

# Root tasks aggregate both applications; no tests in this command
./gradlew build -x test

# Focused suites
./gradlew :core:test -PexcludeKnownResiduals
./gradlew :documentos:test
./gradlew test -PexcludeKnownResiduals
bash scripts/check-document-contract.sh
bash scripts/test-check-document-contract.sh   # fixtures for the guard itself

# Core native image (container build; ~10 min)
./gradlew :core:build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

## Internal document boundary

Documentos accepts only the signed Core service JWT and versioned projections
from `:documentos-contract` (`DocumentProjectionV1`, `SpecificationProjectionV1`,
`CronogramaProjectionV1`). Core populates `snapshot.apuRender` with ordered rows
and reconciled printable amounts before transport; Documentos must not pair
motor rows or recompute prices. Core's transport limits serialized requests to
8 MiB and responses to 64 MiB (`DocumentosLimites`); Documentos rejects bodies
over 8 MiB. Configure `DOCUMENTOS_BASE_URL` (no default),
`DOCUMENTOS_SERVICE_SIGNING_KEY_LOCATION` (a dedicated RSA key) and
`DOCUMENTOS_TIMEOUT` (default `PT20S`); a missing URL, key or remote response
fails closed. Documentos embeds Liberation Sans for PDF output.

**Retired in stage 05 (2026-11):** the stage 02 synchronous byte path — public
`GET /documentos/{presupuesto|apus|cronograma|especificaciones-tecnicas}/{id}`,
Core's render gateways, `DocumentosHttpClient`, the Core XLSX/PDF/MSPDI writers
and their POI/OpenPDF dependencies, and Documentos' synchronous internal render
routes. Core no longer renders documents; every download goes through the
private upload and one-use ticket below. See
[`plans/separar-en-microservicios/README.md`](plans/separar-en-microservicios/README.md).

## Internal private upload (stage 03)

`POST /internal/v1/export` accepts only the short-lived Core service JWT and an
`ExportUploadV1` containing **one** versioned budget/APU or specification
projection, a UUIDv7 `operationId`, its matching private `exports/<operationId>`
key, a 64-character lowercase hex request fingerprint and a safe filename. The
short-lived service JWT must contain the same `operationId` claim. Core's new
export transport requires HTTPS except for explicit loopback test endpoints;
this code check is not a substitute for deployed internal TLS or NetworkPolicy.
Documentos renders and conditionally writes to its configured private
S3 bucket (`If-None-Match: *`), then confirms object length, SHA-256 checksum
fingerprint, MIME and filename with `HeadObject` before returning internal metadata; it never
returns a public S3 URL. On an uncertain write, Core may call
`POST /internal/v1/export/reconcile` with the same immutable projection and key:
Documentos validates the request and confirms the existing S3 object's
fingerprint, S3-side SHA-256 checksum and size against the checksum/size
metadata written with the original Put, plus MIME and filename, without
another Put. Rendering the same PDF at another time is not byte-deterministic, so
recovery must not compare newly rendered bytes with the original object. A 412
conflict is not interpreted as success. Set `DOCUMENTOS_STORAGE_BUCKET` and configure the S3
region/identity for deployment; no implicit bucket or DevServices container is
created. SDK credentials in tests are fake and the local FLoCI check is opt-in:

```bash
RUN_STAGE03_FLOCI_S3_TEST=true \
STAGE03_FLOCI_ENDPOINT=http://127.0.0.1:14568 \
STAGE03_FLOCI_BUCKET=<task-owned-isolated-bucket> \
./gradlew :documentos:test --tests '*S3PrivateObjectStoreFlociIT'
```

This check requires an explicitly isolated test bucket and does not establish
production IAM, Pod Identity, TLS, object cleanup or public download behavior.
Core's private object reader is a separate, read-only S3 client. Configure
`CORE_STORAGE_BUCKET` with the **same private bucket** only when the Core
identity has `GetObject` permission; absent configuration fails closed. The
reader bounds bytes to the confirmed size (at most 64 MiB) and verifies SHA-256.
`GET /api/v1/documentos/descargas/{ticket}` consumes a Core-issued one-use
capability before any private read, then returns the verified bytes with
`Cache-Control: no-store` and `Referrer-Policy: no-referrer` even on 404/500.
A failed S3 read never restores a ticket. This local route does not prove
deployment IAM, ingress/APM ticket-path redaction, or physical cleanup yet.

The authenticated `POST /api/v1/documentos/{presupuesto|apus|especificaciones-tecnicas}/{presupuestoId}/exportaciones`
accepts JSON (`formato` plus only the relevant options) and a UUIDv7
`Idempotency-Key`. A confirmed export returns JSON with `operationId`, a
relative Core `downloadUrl`, expiration timestamps, filename, MIME, byte count,
SHA-256, and capture warnings. Repeat the POST with the same key and unchanged
request/revision to issue another one-use ticket without uploading again; a
conflicting key returns 409. An uncertain upload is reconciled against the
original object's HEAD metadata before confirmation. The POST body is capped
at 8 MiB, and duplicate JSON members are rejected. Only specifications (and cronograma) record
the export audit event. These are local application behaviors, **not**
evidence of real IAM/TLS, browser access, or 24-hour physical deletion. The legacy
byte GETs were retired in stage 05; the React frontend uses only the POST.
Cronograma has its own `POST /api/v1/documentos/cronograma/{presupuestoId}/exportaciones`
(stage 04, XLSX/PDF A4·A3/MSPDI XML), which records `documento.exportado` and
returns stale-schedule notices in `warnings`. For cross-origin
browser requests, configure `CORS_ORIGINS` to the trusted frontend origin;
Core's CORS preflight allows `Authorization`, `Content-Type`, and
`Idempotency-Key` without allowing unconfigured origins. Local preflight tests
do not prove the deployed ingress/TLS configuration.

The cleanup worker is **not** scheduled in the ordinary Core service. To build
its one-shot Quarkus entry point for a separate cleanup workload, select the
named main at build time:

```bash
./gradlew :core:quarkusBuild -Dquarkus.package.main-class=cleanup
```

Package this output as a **different artifact** from the ordinary Core image; the build output directory is shared
and the last build replaces the previous entry point. In the cleanup workload
only, configure `CLEANUP_STORAGE_BUCKET` with the private export bucket and
provide a separate, prefix-scoped `s3:DeleteObject` identity. The regular Core
identity must have only `s3:GetObject`; Documentos uses `PutObject`/`HeadObject`.
The command processes at most 100 expired claims, exits with nonzero status on
failure and has no public delete route. The scheduled workload must run with
HTTP listening disabled and its own service account; neither a CronJob nor
these IAM restrictions are deployed or verified by this repository. Local
fenced-leasing and DeleteObject tests do not prove actual 24-hour deletion.
To check SDK `DeleteObject` against the isolated task-owned FLoCI bucket, use:

```bash
RUN_STAGE03_FLOCI_S3_TEST=true \
STAGE03_FLOCI_ENDPOINT=http://127.0.0.1:14568 \
STAGE03_FLOCI_BUCKET=<task-owned-isolated-bucket> \
./gradlew :core:test --tests '*PrivateObjectDeleterFlociIT'
```

The test creates a unique export key, deletes only that key, and confirms two
subsequent HEAD requests return 404. It cannot establish IAM or deployed
retention.

## Correo

Por defecto (`MAIL_MODE=log`) los correos no se envían: en dev/test el token se
imprime en el log; en producción solo se registra el destinatario enmascarado.
Para enviar por SMTP defina en `.env` `MAIL_MODE=smtp`, `QUARKUS_MAILER_HOST`,
`_PORT`, `_USERNAME`, `_PASSWORD`, `_FROM`, `_START_TLS` y
`APP_FRONTEND_BASE_URL` (ver `.env.example`). Los correos son texto plano.

## Formatting & lint

The build uses **Spotless** (palantir-java-format) for Java style and
`-Xlint:all` for javac warnings. Run before committing:

```bash
# Format all Java in place (main + tests)
./gradlew spotlessApply

# Check only (also runs automatically as part of `./gradlew build` / `check`)
./gradlew spotlessCheck
```

If `spotlessCheck` fails in CI, run `spotlessApply` and re-commit.

Endpoints in dev:
- `/q/health` — liveness
- `/q/openapi` — OpenAPI 3 YAML
- `/q/swagger-ui/` — interactive API docs
- `/api/v1/auth/*`, `/api/v1/perfil/*` — auth surface (see plan 004)

Environment variables (see `.env.example`):
- `DB_URL`, `DB_USER`, `DB_PASSWORD` — Postgres connection (dev/prod)
- `JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION`, `JWT_ISSUER`
  — JWT keys (dev: `scripts/gen-jwt-keys.sh` → `.secrets/jwt/`; prod: Kubernetes
  Secret montado en `/etc/propuestas/jwt/`)

## Contenedor y CI

![CI](https://github.com/emil2245/thesis-back-quarkus/actions/workflows/ci.yml/badge.svg)

Imagen OCI multi-stage (Gradle + Java 25 en el build, runtime UBI9 OpenJDK 25 (x86-64-v2; UBI10 exige x86-64-v3 y el clúster de laboratorio no lo soporta), usuario 185):

```bash
podman build -f core/src/main/docker/Dockerfile -t propuestas-api:dev .
podman build -fdocumentos/src/main/docker/Dockerfile -t propuestas-documentos:dev .
scripts/gen-jwt-keys.sh /tmp/jwt && chmod 644 /tmp/jwt/*   # solo para pruebas locales
podman run --rm --network host --read-only --tmpfs /tmp \
  -v /tmp/jwt:/etc/propuestas/jwt:ro,Z \
  -e DB_URL=jdbc:postgresql://localhost:5436/propuestas -e DB_USER=postgres -e DB_PASSWORD=postgres \
  -e OTEL_SDK_DISABLED=true propuestas-api:dev
```

Puertos: `8080` (API, `/api/v1`) y `9000` (gestion: `/q/health/{live,ready,started}`, `/q/metrics`).

| Variable | Uso |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexion PostgreSQL |
| `JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION` | Claves JWT (default `file:/etc/propuestas/jwt/*.pem`) |
| `JWT_ISSUER` | Issuer de firma y verificacion |
| `CORS_ORIGINS` | Origenes permitidos (lista separada por comas) |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | Endpoint OTLP gRPC (default `http://localhost:4317`) |
| `OTEL_SDK_DISABLED` | `true`/`false` (default `false` en prod) |

CI (`.github/workflows/ci.yml`): en cada push/PR ejecuta `./gradlew build -PexcludeKnownResiduals` (Spotless + tests de ambas aplicaciones), construye las dos imágenes y las escanea con Trivy (solo reporte). Las imágenes no se publican desde este repo.

---

## Repo layout

```
build.gradle.kts                  # Root aggregate build/check/test/Spotless
core/build.gradle.kts             # Existing API, PostgreSQL, Flyway and domain
core/src/main/java/ec/uce/propuestas/
├── common/        # RestApplication, ErrorPayload, GlobalExceptionMapper
├── usuario/       # Auth, JWT, audit and roles
├── documento/     # Preflight, POST exportaciones and download ticket
└── motor/         # Pure Java calculation engine
core/src/main/resources/
├── application.yml
└── db/migration/   # Only Core owns schema migrations
core/src/test/     # Existing resource and domain test suites
documentos/build.gradle.kts       # Separate Quarkus app, no DB/Flyway
documentos/src/main/resources/application.yml  # Port 8081
documentos/src/test/            # Separate startup and contract tests
plans/                           # Implementation plans
```

---

## Status

I-01 … I-11 are technically done (auth, motor, APU, presupuesto, cronograma, document
export, Super-Admin panel); I-12 (final validation, hardening, SUS `n ≥ 5`) is not yet
planned and SUS pilot 1–2 is pending. Per-route status and open items:
[`plans/README.md`](plans/README.md).

---

## Where to look when

- **"How does X in the domain work?"** → `../thesis-docs/plan/domain/`
- **"How does the API expose Y?"** → `../thesis-docs/plan/architecture/07-api-contract.md`
- **"What migration adds table Z?"** → `core/src/main/resources/db/migration/`
- **"What plan produced this file?"** → `plans/<NNN>-*.md`
- **"How do I run tests locally?"** → `./gradlew test`

For AI-assisted development: [`CLAUDE.md`](CLAUDE.md).
