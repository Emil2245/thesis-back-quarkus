# propuestas-api

**Plan 03 en ejecución:** Gradle multiproyecto con `:core` (API y PostgreSQL/Flyway),
`:documentos` (servicio independiente sin base) y `:documentos-contract`
(biblioteca de proyecciones tipadas, no un tercer servicio). Los GET públicos
siguen funcionando. La nueva ruta interna de subida privada es una pieza aislada:
el POST público, el ticket de Core, la limpieza a 24 horas y el despliegue aún no
están implementados ni verificados.

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

# Focused suites; legacy Core GETs stay in Core during migration
./gradlew :core:test -PexcludeKnownResiduals
./gradlew :documentos:test
./gradlew test -PexcludeKnownResiduals
bash scripts/check-document-contract.sh
bash scripts/test-check-document-contract.sh   # fixtures for the guard itself

# Core native image (container build; ~10 min)
./gradlew :core:build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

## Internal document boundary (stage 02)

`/internal/v1/render` accepts only the signed Core service JWT and a
`DocumentProjectionV1` from `:documentos-contract`. Core's HTTP transport
limits serialized requests to 8 MiB, responses to 64 MiB, and requires a
bounded request timeout; Documentos rejects bodies over 8 MiB. For APU exports,
Core populates `snapshot.apuRender` with ordered rows and reconciled printable
amounts before transport; Documentos must not pair motor rows or recompute
prices. Budget payloads may omit this APU-only view; that does not alter their
service-JWT checks. The internal endpoint now returns binary budget/APU
XLSX or PDF from the DTO with the format's media type. Core's budget/APU GET services now send the completed capture to Documentos
with `DOCUMENTOS_BASE_URL` (no default) and `DOCUMENTOS_SERVICE_SIGNING_KEY_LOCATION`
(a dedicated RSA key); `DOCUMENTOS_TIMEOUT` defaults to `PT20S`. A missing URL,
key, or remote response fails closed; production never falls back to Core's
writers. The Core test suite retains legacy writer output using a test-only
CDI alternative; `RemotePublicGetIT` disables it and checks public GET routing
against an isolated HTTP stub, but does not prove a two-app runtime. Documentos
embeds the same tracked Liberation Sans font as Core for PDF output.
The specifications DOCX renderer accepts a separate technical-only immutable
projection; `/internal/v1/render/specifications` requires the Core service JWT and
returns DOCX bytes. The public DOCX GET captures ownership, effective titles
and ordered technical content in a separate read-only transaction, renders via
the authenticated Documentos transport outside that transaction, and records
`documento.exportado` in a short separate transaction only after bytes arrive.
Remote errors fail closed without an export event; the local DOCX renderer is a
test-only alternative, not a production fallback.
This is not the public POST/S3 proxy or evidence of deployed TLS.

## Internal private upload (stage 03, partial)

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
at 8 MiB, and duplicate JSON members are rejected. Only specifications record
the existing export audit event. These are local application behaviors, **not**
evidence of real IAM/TLS, browser access, or 24-hour physical deletion. Existing
byte GETs remain available to transition callers; the React frontend now uses
the POST locally. Cronograma POST is reserved for plan 04. For cross-origin
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

To verify the five existing public GET formats against **both real applications**
without using the repository's Compose volume, run:

```bash
STAGE02_REAL_DOCUMENTOS=true ./gradlew :documentos:quarkusBuild :core:test \
  --tests 'ec.uce.propuestas.documento.RealDocumentosGetIT' :core:spotlessCheck
```

The opt-in test starts the packaged Documentos app on a loopback ephemeral port
with a task-owned RSA service key and uses Core's isolated Testcontainers database
and authenticated fixture. It checks budget/APU PDF and XLSX, technical DOCX,
and the DOCX export audit; it stops the app and removes only its own temporary
keys. Without `STAGE02_REAL_DOCUMENTOS=true`, the test is skipped. This does not
validate TLS, browser download, S3, Pod Identity, native images or deployment.

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

If `spotlessCheck` fails in CI, run `spotlessApply` and re-commit. Details and
decisions in [docs/012-format-lint.md](docs/012-format-lint.md).

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
├── documento/     # Existing GET adapters (kept during transition)
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

## Historical iteration snapshot (functional code cut `ac84c945`)

**Historical technical status** (backend `ac84c945`; not a test result of the current multiproject branch):

- **I-01 … I-11 — DONE técnico.** Bootstrap, auth, motor, APU núcleo y
  avanzado, presupuesto (P-28…P-32), cronograma (P-33…P-36),
  exportación XLSX/PDF/MSPDI (P-37) y panel Super-Admin (P-38…P-42)
  están implementados. El paquete de búsqueda de plantillas
  (`plans/plans_busquedas_plantilla/001`–`005`) está DONE e integrado.
- **I-12 — pendiente de planificación** (validación final, hardening,
  SUS `n ≥ 5`).
- **Piloto SUS 1–2 — pendiente** (requiere frontend y participantes
  humanos; no se fabrican resultados).
- **Bases PERSONAL — contrato funcional pendiente:** el backend permite gestionar
  el contenedor y resolver insumos PERSONAL internamente, pero todavía no expone
  el flujo completo para alimentarlo y ofrecerlo como origen del selector; el
  frontend mantiene el Plan 058 bloqueado.
- **Suite completa:** `763 = 760 pass + 2 fallos aceptados
  (GM-19/GM-20, residuales workbook-rounding documentados en
  Plan 014; no se reabre el motor) + 1 skipped (GM-24 `@Disabled`
  por fixture upstream EMELNORTE) + 0 errors`.
- **`./gradlew build -x test`** → **PASS**. `./gradlew build` falla
  únicamente por los residuales aceptados GM-19/GM-20.

| Plan | Iteration | Status |
|---|---|---|
| 001–018 (I-01 … I-06) | bootstrap, CI, schema, auth, motor, APU núcleo y avanzado, presupuesto inicial | **DONE** — ver detalle por plan en [`plans/README.md`](plans/README.md) |
| 019–025 (I-07 presupuesto) | UUIDv7 presupuesto, recalculo write-through, ciclo presupuesto, capítulos, rubros, versionado, validación P-32 | **DONE (2026-09-01)** — Plan 025 cierra I-07 |
| 026–031 (I-08/I-09/I-10 cronograma + export) | cronograma CRUD, vistas/curva S, exportación XLSX/PDF/MSPDI | **DONE (2026-09-07)** — Plan 031 verificado con preflight MSPDI XSD |
| 032–040 (I-11 panel Super-Admin + piloto SUS) | gate documental, log_actividad (V010), usuarios, bases, plantillas SISTEMA, parámetros, instrumentación D-13, integración | **DONE técnico (2026-09-09)** — piloto SUS 1–2 pendiente |
| `plans_busquedas_plantilla/001`–`005` | búsqueda FTS (V011), seed catálogo (V012), lote atómico, APU manual completo, integración Bruno | **DONE (2026-09-10/11)** — ver [`plans/plans_busquedas_plantilla/README.md`](plans/plans_busquedas_plantilla/README.md) |
| I-12 — pendiente | validación final, hardening, SUS `n ≥ 5` | ⬜ **Pendiente de planificación** |

> **Histórico (snapshot 2026-07-24, preservado para auditoría):**
> en ese corte la suite reportaba 56 tests, los planes
> 001–006 cubrían I-01/I-02 únicamente, y la exportación documental
> estaba marcada como "for future document export". Ese estado ya fue
> superado por los planes 007–040 y por el paquete de plantillas
> 001–005; el detalle histórico vive en
> [`plans/README.md`](plans/README.md) y
> [`docs/00-ESTADO-ACTUAL.md`](docs/00-ESTADO-ACTUAL.md).

**Estado detallado y notas post-ejecución**:
[`plans/README.md`](plans/README.md) ·
[`docs/00-ESTADO-ACTUAL.md`](docs/00-ESTADO-ACTUAL.md) ·
[`docs/modulos/README.md`](docs/modulos/README.md).

---

## Where to look when

- **"How does X in the domain work?"** → `../thesis-docs/plan/domain/`
- **"How does the API expose Y?"** → `../thesis-docs/plan/architecture/07-api-contract.md`
- **"What migration adds table Z?"** → `core/src/main/resources/db/migration/`
- **"What plan produced this file?"** → `plans/<NNN>-*.md`
- **"How do I run tests locally?"** → `./gradlew test`

For AI-assisted development: [`CLAUDE.md`](CLAUDE.md).
