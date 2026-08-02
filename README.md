# propuestas-api

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

- Java 25 (toolchain) · Gradle 9.5.1 (wrapper) · Quarkus 3.37.4
- Panache ORM (Hibernate) · PostgreSQL 16
- Flyway migrations
- SmallRye JWT (bcrypt via Elytron)
- SmallRye OpenAPI · Swagger UI at `/q/swagger-ui`
- Apache POI (xlsx) + OpenPDF (pdf) — for future document export
- JUnit 5 · RestAssured · jqwik (property tests)
- Dev Services (auto-Postgres) in tests

---

## Running locally

```bash
# Dev mode with live reload (spins Postgres via Dev Services if Docker is up)
./gradlew --console=plain quarkusDev

# Full build (no tests)
./gradlew build -x test

# Test suite (56 tests currently; 2 known-red pending domain decision — see plans/README.md)
./gradlew test

# Native image (container build; ~10 min)
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

Endpoints in dev:
- `/q/health` — liveness
- `/q/openapi` — OpenAPI 3 YAML
- `/q/swagger-ui/` — interactive API docs
- `/api/v1/auth/*`, `/api/v1/perfil/*` — auth surface (see plan 004)

Environment variables (see `.env.example`):
- `DB_URL`, `DB_USER`, `DB_PASSWORD` — Postgres connection (dev/prod)
- `MP_JWT_VERIFY_PUBLICKEY_LOCATION`, `SMALLRYE_JWT_SIGN_KEY_LOCATION`
  — JWT keys (dev keys ship in the repo; **rotate for prod**)

---

## Repo layout

```
src/main/java/ec/uce/propuestas/
├── common/        # RestApplication, ErrorPayload, GlobalExceptionMapper
├── usuario/       # Usuario, Rol, RefreshToken, TokenUsuario, TipoToken
│   └── auth/      # AuthResource, PerfilResource, AuthService, TokenService,
│       ├── dto/   #   PasswordService, PasswordPolicy, EnviadorCorreo port
│       └── mail/  #   LogEnviadorCorreo (dev/prod fallback)
└── motor/         # Pure Java calc engine (no framework)
    └── internal/  #   CalculadorFila, Consolidador (package-private)

src/main/resources/
├── application.yml
├── db/migration/         # V001..V003 Flyway migrations
└── META-INF/resources/   # publicKey.pem, privateKey.pem (dev-only)

src/test/java/ec/uce/propuestas/
├── usuario/auth/  # AuthResourceIT (@QuarkusTest), PasswordPolicyTest,
│                  # RecordingEnviadorCorreo (test alt)
└── motor/         # MotorApuTest, MotorConsolidacionTest,
                   # MotorPropiedadesTest, Fixtures helper

src/test/resources/
├── META-INF/beans.xml           # CDI alternatives for tests
└── motor/fixtures/              # JSON + CSV golden-master fixtures
                                 # (copied from thesis-docs/_artifacts/)

plans/                           # Implementation plans (executable playbooks)
```

---

## Iteration status (as of 2026-07-24)

| Plan | Iteration | Status |
|---|---|---|
| 001 Bootstrap Quarkus | I-01 | DONE |
| 002 CI (GitHub Actions) | I-01 | DONE (needs first push to prove workflows run) |
| 003 Postgres schema baseline | I-01 | DONE (V003 seed data has upstream gaps — see `plans/README.md`) |
| 004 Auth module | I-01 | DONE (25/25 tests green) |
| 005 Motor de cálculo | I-02 | 21/25 GMs green; 2 fail (GM-19, GM-20) |
| 006 Motor consolidación fix | I-02 | Partial — stub-precision fix landed; GM-19/20 root cause escalated to director (workbook rounding semantics) |

**Full status + post-execution notes**: [`plans/README.md`](plans/README.md).

---

## Where to look when

- **"How does X in the domain work?"** → `../thesis-docs/plan/domain/`
- **"How does the API expose Y?"** → `../thesis-docs/plan/architecture/07-api-contract.md`
- **"What migration adds table Z?"** → `src/main/resources/db/migration/`
- **"What plan produced this file?"** → `plans/<NNN>-*.md`
- **"How do I run tests locally?"** → `./gradlew test`

For AI-assisted development: [`CLAUDE.md`](CLAUDE.md).
