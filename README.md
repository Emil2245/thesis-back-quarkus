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
- Apache POI (xlsx) + OpenPDF (pdf) — server-side document export
  (ET, cronograma XLSX/PDF, MSPDI XML — implemented across I-06, I-09
  and I-10)
- JUnit 5 · RestAssured · jqwik (property tests)
- Dev Services (auto-Postgres) in tests

---

## Running locally

```bash
# Dev mode with live reload (spins Postgres via Dev Services if Docker is up)
./gradlew --console=plain quarkusDev

# Full build (no tests)
./gradlew build -x test

# Test suite — current state (HEAD ac84c945): 763 tests = 760 pass +
# 2 accepted residual failures (GM-19, GM-20 — workbook-rounding
# residuals documented in Plan 014/Plan 02; no se reabre el motor) +
# 1 skipped (GM-24 @Disabled by upstream EMELNORTE fixture) + 0
# errors. ./gradlew build -x test PASS; ./gradlew build fails only
# on those accepted residuals.
./gradlew test

# Native image (container build; ~10 min)
./gradlew build -Dquarkus.native.enabled=true -Dquarkus.native.container-build=true
```

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
├── db/migration/         # V001..V012 Flyway migrations
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

## Iteration status (functional code cut `ac84c945`)

**Current technical status** (backend `ac84c945`):

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
- **"What migration adds table Z?"** → `src/main/resources/db/migration/`
- **"What plan produced this file?"** → `plans/<NNN>-*.md`
- **"How do I run tests locally?"** → `./gradlew test`

For AI-assisted development: [`CLAUDE.md`](CLAUDE.md).
