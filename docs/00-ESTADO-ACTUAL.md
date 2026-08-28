# Estado actual del proyecto — `thesis-back-quarkus`

- **Fecha de este resumen:** 2026-08-02
- **Fuentes verificadas:** `plans/README.md`, `README.md`, `build.gradle.kts`,
  `gradle/libs.versions.toml`, `application.yml`, test-results XML,
  `git log`, y docs canónicos en `../thesis-docs/plan/`.
- **Siguiente nivel de detalle:** `01-ARQUITECTURA.md` y `02-TECNOLOGIAS.md`
  (mismos docs/) + los planes en `plans/` + `thesis-docs/`.

---

## 1. Resumen ejecutivo

Backend Quarkus de una plataforma cloud-native para la automatización de
**propuestas técnico-económicas** en licitaciones públicas ecuatorianas
(SERCOP / LOSNCP). Tesis de grado UCE — FICA — Computación, autores Emil
Verkade y Kevin Andrade, plazo de **24 semanas (12 iteraciones XP, I-01…I-12)**.

**Dónde está hoy:** terminadas las fundaciones (I-01), casi toda la iteración
del motor de cálculo (I-02, **bloqueada por un problema de dominio escalado al
director** — GM-19/GM-20), y los **núcleos de dos módulos de negocio** (I-03
proyecto e I-04 insumo: CRUD, firmantes, parámetros, catálogo de insumos,
importación CSV y copia de base). Queda pendiente la capa más profunda (APU,
presupuesto, cronograma, export) y la decisión del director sobre el redondeo.

> **Estimación honesta:** ~3 de 12 iteraciones con avance real (≈25-30 % del
> roadmap). La parte de **riesgo técnico más alto** (motor con 0 % desviación)
> está construida: de **24 GMs habilitados**, **22 verdes** y **2 rojos**
> (GM-19/20, cierran con Plan 014); GM-24 `@Disabled` por fixture upstream.
> Los módulos de negocio ya tienen
> código vertical, no solo DDL/contrato.

---

## 2. Posición en el cronograma (plan de iteraciones XP)

| Iteración | Semanas | Contenido | Estado real (2026-08-11) |
|---|---|---|---|
| I-01 | 1–2 | Bootstrap Quarkus, CI, schema Postgres, auth | ✅ **Completa** (planes 001–004) |
| I-02 | 3–4 | **Motor de cálculo puro** + cierre auth (perfil/recuperar) | 🔶 **~90 %** — motor construido pero GM-19/20 rojos y escalados |
| I-03 | 5–6 | Proyectos (ciclo de vida, parámetros, firmantes) | 🔶 **Núcleo** — crud + firmantes + parámetros + base insumos (plan 009) · TODO: logo, detalle |
| I-04 | 7–8 | Insumos (CRUD, CSV, bases centrales) | 🔶 **Núcleo** — crud, catálogo, selector multi-fuente, copia, importación CSV (plan 009) · TODO: uso en APU (P-18/D-08) |
| I-05 | 9–10 | APU núcleo (editor, filas M/N/O/P, HM) | 🔶 **Núcleo** — P-19…P-22, editor vía `ApuResource`/`PresupuestoApuResource`, filas M/N/O/P, Fila HM protegida, override de precio, write-through vía `Motor.calcularApu` (10 tests verdes) · TODO: auxiliares/%CI en I-06 |
| I-06 | 11–12 | APU completo + decisiones N04 (%CI, descuentos, auxiliares, plantillas, ET, PERSONAL, rangos parametrizables, decimales) | � No iniciada · plan listo: [`docs/modulos/04-apu-avanzado.md`](modulos/04-apu-avanzado.md) |
| I-07 | 13–14 | Presupuesto (capítulos, rubros, totales) | ⬜ No iniciada |
| I-08 | 15–16 | Versiones y cronograma base | ⬜ No iniciada |
| I-09 | 17–18 | Cronograma visual y sincronía | ⬜ No iniciada |
| I-10 | 19–20 | Export SERCOP (.xlsx/.pdf) | ⬜ No iniciada |
| I-11 | 21–22 | Panel Super-Admin + piloto SUS | ⬜ No iniciada |
| I-12 | 23–24 | Validación final, SUS n≥5, hardening | ⬜ No iniciada |

Hitos de tesis ligados a iteraciones: **semana 4** motor GM unit verdes (pendiente
de decisión del director), semana 10 motor a nivel api, semana 14 consolidación
exacta (GM-19/20/21/24), semana 18 integridad intermodular, semana 20 tasa de
conformidad CHK, semanas 22–24 SUS y desempeño.

---

## 3. Estado por plan

| # | Plan | Iteración | Estado | Notas |
|---|---|---|---|---|
| 001 | Bootstrap Quarkus | I-01 | ✅ DONE | Revisado por autor. Base del stack. |
| 002 | CI GitHub Actions | I-01 | ⚠️ **DEUDA** | Plan "DONE" pero **no existe `.github/` en el repo**; se difirió en 007 para reescribirse con `./gradlew`. Sin CI real hasta la fecha. |
| 003 | Schema Postgres (V001–V003) | I-01 | ✅ DONE | **21 tablas** + seed (V002/V003). Calidad del seed IESS con gaps upstream (ver §6). |
| 004 | Módulo auth | I-01 | ✅ DONE | **25/25 tests verdes** (19 IT + 6 unit), 0 fugas de tokens. 4 bugs del plan corregidos inline (documentados). |
| 005 | Motor de cálculo | I-02 | ✅ **DONE** (scaffold + 21 per-APU GMs verdes + 5 propiedades verdes). Consolidación cerrada por Plan 02/006 con residual aceptado: `ConsolidadorFronteraTest` 5/5 verde; GM-21 verde con allowlist 11 ≤ 0.03; GM-19 (`-$6.95`) y GM-20 cap. 1 (`-$0.84`) con residual aceptado por el autor (no se reabre el motor). |
| 006 | Fix consolidación (GM-19/20) | I-02 | 🟡 **IMPLEMENTACIÓN APLICADA workbook-consistent (2026-08-28) — PARTIAL — CLOSED WITH DOCUMENTED IESS RESIDUAL.** Regla workbook-consistent aplicada en `internal/Consolidador.java` (`precioUnitario DOWN 2dp`; `precioTotal = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6; display canónico a 2 dp `HALF_UP` solo en presentación/assertion). `ConsolidadorFronteraTest` 5/5 verde. **Residual aceptado:** GM-19 actual `395108.37` vs esperado `395115.32` (delta `-$6.95`); GM-20 cap. 1 actual `158907.21` vs esperado `158908.05` (delta `-$0.84`). GM-21 verde con allowlist auditado de 11 entradas ≤ 0.03 a nivel PU (atribuido a artefactos de redondeo manual del example workbook IESS — **no** se realizan auditorías exhaustivas per-rubro; preferencia del usuario). **No** se reabre el motor para cerrar el residual; workbook, golden expected values, tolerancias y fórmulas del motor quedan cerradas. T2–T4 + borrar DIAG quedan **OPEN** en [`plans/014-motor-precision-no-links.md`](../plans/014-motor-precision-no-links.md). |
| 007 | Migración Maven → Gradle | tooling | ✅ DONE | Gradle 9.5.1 + Kotlin DSL, verificado: build, 56 tests (2 red/2 skipped, sin regresión), `quarkusDev`. |
| 008 | Refinamientos build | tooling | ✅ DONE | Version catalog, toolchain JDK 25, build cache. Lombok y Consul/Stork/OTel/K8s **rechazados** con justificación. |
| 009 | Módulos `proyecto` + `insumo` | I-03/I-04 | ✅ DONE | CRUD de ambos módulos + importación CSV + copia de base + **repositorios por entidad** (ver `plans/README.md` §009). |
| 010 | Seed de escenarios (V004) | I-04/I-05 | ✅ DONE | `V004__seed_escenarios.sql`: 3 proyectos (uno por estado BORRADOR/EN_PROCESO/FINALIZADO) con todas las tablas relacionadas; FINALIZADO = workbook real Cetro Médico Tulcán (298 rubros, total 395115.32). Verificado contra Postgres limpio + suite sin regresión (ver `docs/04-SEED-ESCENARIOS.md`). |
| 011 | Módulo APU núcleo (P-19…P-22) | I-05 | ✅ DONE | P-19 lista/crea APUs por presupuesto, P-20 editor cabecera, P-21 filas M/N/O/P + fila HM protegida, P-22 override de precio con `JsonNullable`. Write-through vía `Motor.calcularApu` (RNF-02 a nivel APU). **10 tests verdes** (2 suites: `ApuCalculoServiceIT` + `ApuResourceIT`), colección Bruno `api/bruno/08-apu/`. Detalle en `docs/modulos/03-apu.md`. |
| 013 | Módulo APU avanzado (P-23…P-27, P-45, P-46 + N04) | I-06 | ⬜ TODO | Plan completo: [`docs/modulos/04-apu-avanzado.md`](modulos/04-apu-avanzado.md). Incluye: %CI override (P-23), descuento CD legacy (P-24), auxiliares sin anidamiento (P-25 — N04 §A2 — **WITHDRAWN 2026-08-28**: no-links entre APUs confirmado), plantillas con fallback (P-26 — N04 §B.4), desglose cálculo (P-27), Especificaciones Técnicas (P-45 — N04 §ESP, NUEVA), plantilla de proyecto (P-46 — N04 §A8), `POST /apus/{id}/duplicar` (dossier §B.7), módulo `recalculo` (write-through parámetros — dossier §B.6 — **DEFERRED**), base PERSONAL (N04 §A9), rangos parametrizables (N04 §A6), **display global `precisionDinero=2` / `precisionPorcentaje=4` bajo `app.display.*` (Plan 014 supersede N04 §#7; `CALC_PRECISION=3 HALF_UP` retirado).** |

Planes de I-07…I-12 **no escritos** aún (se redactan cuando cada iteración
precedente cierra CI-verde).

---

## 4. Estado de las pruebas (baseline 2026-08-11, verificado en test-results)

**Estado motor (cierre parcial 2026-08-28):** `ConsolidadorFronteraTest` 5/5 verde (nuevo, T1 workbook-consistent); `MotorApuTest` 21/21 verde; `MotorPropiedadesTest` 5/5 verde. **GM-21** verde con allowlist auditado de 11 entradas ≤ 0.03 a nivel PU. **GM-19 / GM-20** siguen RED con residual aceptado por el autor: GM-19 actual `395108.37` vs esperado `395115.32` (delta `-$6.95`); GM-20 cap. 1 actual `158907.21` vs esperado `158908.05` (delta `-$0.84`). GM-24 y DIAG `@Disabled`.

| Suite | Tests | Rojos | Skipped | Estado |
|---|---|---|---|---|
| `MotorApuTest` (GM-01…18, 22, 23, 25) | 21 | 0 | 0 | ✅ verde |
| `MotorConsolidacionTest` (GM-19…21, 24 + DIAG) | 5 | **2** (GM-19, GM-20; **residual aceptado**) | 2 (GM-24 `@Disabled`, DIAG `@Disabled`) | 🔴 residual aceptado |
| `MotorPropiedadesTest` (jqwik) | 5 | 0 | 0 | ✅ verde |
| `ConsolidadorFronteraTest` (T1 workbook-consistent) | 5 | 0 | 0 | ✅ verde |
| `AuthResourceIT` (@QuarkusTest) | 19 | 0 | 0 | ✅ verde |
| `PasswordPolicyTest` | 6 | 0 | 0 | ✅ verde |
| `ProyectoResourceIT` | 4 | 0 | 0 | ✅ verde |
| `InsumoResourceIT` | 3 | 0 | 0 | ✅ verde |
| `ApuCalculoServiceIT` | 2 | 0 | 0 | ✅ verde |
| `ApuResourceIT` | 8 | 0 | 0 | ✅ verde |

El motor de cálculo por APU es **aritméticamente correcto** (21/21 + 5/5). La
desviación de GM-19/20 baseline (sin redondeo intermedio) era de ±$2.50/$1.15
sobre el presupuesto Cetro Médico Tulcán y se debía a que el workbook fuente
calcula `precioTotal = cantidad × precioUnitario_2dp` mientras el motor lo hace
a 6 dp plenos. La variable de tesis `exactitud_calculo` exige 0.00 → es una
decisión de dominio del director, **no** un bug a parchear con tolerancia.
**Estado actual (2026-08-28, corrección workbook-consistent):** la regla
vigente para la frontera APU→Rubro (definida por [`plans/014-motor-precision-no-links.md`](../plans/014-motor-precision-no-links.md))
aplica `RoundingMode.DOWN` 2 dp **solo** a `precioUnitario`; `precioTotal =
cantidad × PU_2dp` se retiene a la escala de persistencia 6 (`NUMERIC(14,6)`)
con `HALF_UP` aplicado únicamente en esa frontera de resultado (sin truncar
cada PT a 2 dp); capítulo y `totalGeneral` agregan esos `precioTotal` a escala 6;
el display canónico a 2 dp `HALF_UP` ocurre solo en la capa de presentación/
assertion. La versión previa con `setScale(2, DOWN)` simétrico en PU y PT
producía deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs
workbook IESS y queda retirada.

---

## 5. Lo que ya existe en código

```
src/main/java/ec/uce/propuestas/
├── common/        # RestApplication (/api/v1), ErrorPayload, GlobalExceptionMapper, ProblemaException
├── usuario/       # Usuario, Rol, RefreshToken, TokenUsuario, TipoToken, UsuarioRepository
│   └── auth/      # AuthResource, PerfilResource, AuthService, TokenService,
│       ├── dto/   #   PasswordService, PasswordPolicy, EnviadorCorreo (puerto)
│       └── mail/  #   LogEnviadorCorreo (adaptador dev)
├── proyecto/      # Proyecto, Firmante, ParametrosProyecto, ParametrosSistema (LECTURA)
│   └── service/   #   ProyectoService, FirmanteService, ParametrosProyectoService
├── insumo/        # BaseInsumos, Insumo, UnidadCatalogo; CRUD, catálogo, selector
│   └── service/   #   multi-fuente, copia de base, importación CSV (/.insumos, /bases-centrales)
└── motor/         # Motor puro Java (sin framework): snapshots, resultados
    └── internal/  #   CalculadorFila, Consolidador (package-private)

src/main/resources/
├── application.yml                    # perfiles dev/prod, JWT, CORS, Flyway
├── db/migration/V001..V004            # 21 tablas + seeds (V004: 3 escenarios demo)
└── META-INF/resources/                # claves JWT dev (publicKey/privateKey.pem)

src/test/ ...                          # 4 suites motor + 2 suites auth + proyecto + insumo + fixtures GM
api/bruno + api/http                   # colecciones de requests manuales (auth, salud)
database/db_schemas/                   # DDL exportado de la BD (documental)
```

**Pendiente de dominio:** `apu/`, `presupuesto/`, `cronograma/`, `documento/`
(planeados, aún vacíos).

---

## 6. Problemas abiertos y deudas (tracker)

| # | Asunto | Tipo | Dueño | Bloquea |
|---|---|---|---|---|
| 1 | **GM-19/GM-20 — RESIDUO ACEPTADO (cierre parcial user-accepted 2026-08-28).** Regla workbook-consistent aplicada en `internal/Consolidador.java`; GM-19 actual `395108.37` vs esperado `395115.32` (delta `-$6.95`); GM-20 cap. 1 actual `158907.21` vs esperado `158908.05` (delta `-$0.84`). Atribuido a artefactos de redondeo manual del example workbook IESS — **no** se realizan auditorías exhaustivas per-rubro (preferencia del usuario). **No** se reabre el motor; workbook, golden expected values, tolerancias y fórmulas del motor quedan cerradas. | — | — |
| 2 | **CI/CD no existe** — `.github/` ausente; plan 002 + 007 lo difieren | Deuda | Autores | Hito "CI activo" (semana 2), todo control de regresión |
| 3 | Seed insumos V003 — sin `codigo` real, `unidad='h'` forzada, mismatch `m²/m³` vs `m2/m3` | Data quality upstream | Kevin / fuente IESS | Ninguno funcional (aviso cosmético), posible `V004__reseed_insumos.sql` |
| 4 | GM-24 `@Disabled` — fixture EMELNORTE con `secciones` vacías y `codigo` null | Fixture upstream | thesis-docs | Cobertura de consolidación parcial |
| 5 | GM-21 allowlist con 11 entradas (plan decía 6) | Auditoría | — | Transparencia de la suite |
| 6 | Extensión `quarkus-security-jpa` presente pero sin uso (JWT-only) | Dead weight | opcional | — |
| 7 | `application.yml`: warnings `quarkus.health.extensions.enabled` no reconocido y `hibernate-orm.database.generation` deprecado | Limpieza | opcional | — |
| 8 | Postgres local en :5432 puede opacar Dev Services en dev (trap conocido) | Entorno | — | Dev local |
| 9 | `http/test1.http` en estado `AD` (staged+deleted) en git | Limpieza | — | — |
| 10 | Decisión pendiente: modularización/microservicios (análisis completo en `docs/analisis-microservicios-auth-core.md`) | Estrategia | Autores | — |

---

## 7. Qué falta para "terminar" (macro)

1. **Motor cerrado (cierre parcial user-accepted 2026-08-28).** La regla workbook-consistent quedó aplicada en `internal/Consolidador.java` (`PU DOWN 2dp`; `PT = cantidad × PU_2dp` retenido a escala 6 `HALF_UP`; agregación consistente de capítulo y `totalGeneral`). GM-19 (`-$6.95`) y GM-20 cap. 1 (`-$0.84`) quedan con residual aceptado; **no** se reabre el motor. T2 (no-links estructural en `Motor.java`/`CalculadorFila.java`), T3 (display config global + `GET /api/v1/config/display`), T4 (`@Digits` en DTO del catálogo cerrado) y borrado de DIAG siguen **OPEN** en [`plans/014-motor-precision-no-links.md`](../plans/014-motor-precision-no-links.md). **STOP conditions de `plans/014` (cerrado, auditoría):** la versión previa con `setScale(2, DOWN)` por rubro total producía deltas sistemáticos `GM19 = -$9.37` y `GM20 cap1 = -$3.09` vs workbook IESS y queda retirada; el residual actual es aceptado por el autor.
2. **Escribir y ejecutar planes I-05…I-12** — el grueso del código:
   APU → presupuesto → versiones/cronograma → export → admin → validación.
   (I-03 proyecto núcleo e I-04 insumo núcleo ya sentados en plan 009.)
3. **Montar CI** con Gradle (`build/test-results/`, `build/*-runner`).
4. **Gate de I-10:** suite GM completa verde antes de export.
5. **Mediciones de tesis:** GM en CI, tasa de conformidad CHK-01…31 (POI/PDFBox
   parse-back), SUS n≥5, baseline RNF-06.
6. **Frontend** (React, repo separado) acoplado al contrato OpenAPI.

---

## 8. Conclusión

- **Fundaciones sólidas:** auth completo y probado, schema de 21 tablas,
  build Gradle moderno y reproducible, motor puro con 26/30 checks de cálculo
  verdes (21 GM + 5 propiedades).
- **Riesgo número uno del proyecto controlado a medias:** la exactitud de
  cálculo (variable de tesis) está resuelta a nivel APU, pero la consolidación
  depende de una decisión de dominio que no está tomada.
- **Riesgo número dos:** el calendario — quedan 10 iteraciones de funcionalidad
  y la velocidad real aún no se ha recalibrado (se hará al cierre de I-02).
- **El código que existe sigue el diseño canónico** (`thesis-docs`), no lo
  contradice; la organización de paquetes es deliberada y buena (ver
  `01-ARQUITECTURA.md`).

Fuentes vivas: `plans/README.md` (estado por plan + notas post-ejecución),
`README.md`, `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md`.
