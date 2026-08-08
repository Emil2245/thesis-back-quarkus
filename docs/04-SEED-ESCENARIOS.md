# 04 — Seed de escenarios reales (`V004__seed_escenarios.sql`)

- **Status:** ✅ DONE — implementado y verificado (2026-08-02).
- **Blocks:** demos con datos realistas, pruebas manuales en Bruno, desarrollo
  de los módulos APU/presupuesto/cronograma (I-05…I-09).
- **Depends on:** `V001__baseline.sql` (21 tablas), `V002__seed.sql`
  (unidades + parámetros sistema), `V003__seed_insumos.sql` (base central IESS).
- **Source of truth:** `thesis-docs/plan/quality/04-poblamiento-bd.md`,
  `thesis-docs/plan/architecture/06-database-schema.md`,
  fixtures del motor en `src/test/resources/motor/fixtures/`.

---

## Context

El sistema tiene el esquema completo (21 tablas) pero la BD casi vacía: solo
existen `unidad_catalogo`, `parametros_sistema` y una base central IESS con 93
insumos (V002/V003). Los módulos de negocio (proyecto, insumo) ya tienen código
vertical, pero no hay **datos de negocio realistas** para demos, desarrollo del
editor de APUs y presupuesto (I-05…I-09), ni para validar el cronograma.

Este plan añade `V004__seed_escenarios.sql`: **tres proyectos completos, uno
por estado** (`BORRADOR`, `EN_PROCESO`, `FINALIZADO`), con todas sus tablas
relacionadas llenas de forma coherente, más las tablas de soporte
(`valor_referencia`, `log_actividad`, `plantilla_apu`) y los dos usuarios de
demo logueables. Uno de los escenarios (FINALIZADO) reutiliza el workbook real
**Cetro Médico Tulcán** (7 capítulos, 26 subcapítulos, 298 rubros, 18 APUs con
detalle) ya importado como fixtures del motor — cerrando el bucle entre
motor (GM) y datos de BD.

**Decisiones del autor (2026-08-02):**
1. **Un proyecto por estado**: BORRADOR, EN_PROCESO, FINALIZADO.
2. **FINALIZADO = workbook real CMT**; EN_PROCESO y BORRADOR = sintéticos pero
   coherentes.
3. **Usuarios seedeados con datos de Bruno**: John Doe (`john.doe@uce.edu.ec`)
   y Ana de Armas (`ana.armas@gmail.com`), ambos contraseña `Clave1234`,
   `email_verificado = TRUE` (el login lo exige). La BD se borrará desde cero;
   los ids serán 1 y 2.
4. **Todo incluido**: `valor_referencia` (Anexo A, sin CAMICON — agenda A7),
   `log_actividad` (catálogo D-13, sin PII), `plantilla_apu` (1 SISTEMA +
   1 PERSONAL).
5. **Volumen realista y manejable**: 1–2 capítulos completos con APUs
   detalladas + stubs (solo totales) para el resto.

## In scope

| Archivo | Acción |
|---|---|
| `src/main/resources/db/migration/V004__seed_escenarios.sql` | **Nuevo** — seed completo de 3 escenarios + soporte |
| `docs/04-SEED-ESCENARIOS.md` | Este plan |
| `docs/00-ESTADO-ACTUAL.md` | Actualizar estado de BD |

## Out of scope — no tocar

- `V001`–`V003` (migraciones aplicadas — nunca se editan).
- `Motor.java` / `internal/Consolidador.java` y los GM-19/GM-20 (escalados al
  director).
- Módulos APU/presupuesto/cronograma (no existen aún; la seed los alimenta).
- `refresh_token` / `token_usuario`: **no se seedean** — se crean en runtime.

---

## 1. Datos de autoridad (verificados contra fixtures)

### 1.1 Workbook real CMT (fixtures del motor)

| Archivo | Contenido |
|---|---|
| `presupuesto-apus-cetro-medico-tulcan.json` | 7 capítulos, 26 subcapítulos, **298 rubros** (item, codigo, descripcion, unidad, cantidad, precioUnitario 2dp, precioTotal). Total general **395115.32** |
| `apus-sample-apus-cetro-medico-tulcan.json` | **18 APUs reales con detalle** (4 secciones, líneas MO/EQ/MA, HM 5%MO, costoDirecto, costoTotal). Códigos: 501BM6, 501B64, 501D1V, 501DQR, 501D00, 501AKN, 502897, 500AT8, 502ARV, 503B30, 500ASU, 501DH5, 505APQ, 504BA0, 504B3I, 500C2S, 500AT1, 501772 |
| `insumos-seed-apus-cetro-medico-tulcan.csv` | 93 insumos (ya en V003: MO-001…MO-016, EQ-001…EQ-011, MA-001…MA-066) |

**Distribución por capítulo:** 1=102 rubros, 2=51, 3=26, 4=43, 5=52, 6=8, 7=16.

**Capítulos y subcapítulos (resumen):**
- 1 SISTEMA ARQUITECTONICO (subcap. 1.1–1.12)
- 2 SISTEMA ELECTRICO
- 3 SISTEMA ELECTRÓNICO (3.1–3.5)
- 4 SISTEMA HIDROSANITARIO (4.1–4.2)
- 5 SISTEMA MECANICO (5.1 con 5.1.1–5.1.4, 5.2, 5.3)
- 6 IMPACTO AMBIENTAL Y SEGURIDAD INDUSTRIAL
- 7 SISTEMA ESTRUCTURAL

### 1.2 Usuarios (datos de Bruno)

| id | nombre | email | password (bcrypt `$2a$10$`, verificado contra `BcryptUtil.matches`) | rol |
|---|---|---|---|---|
| 1 | John Doe | john.doe@uce.edu.ec | `Clave1234` | USUARIO |
| 2 | Ana de Armas | ana.armas@gmail.com | `Clave1234` | USUARIO |

Hashes bcrypt generados y verificados con el classpath real de Quarkus
(WildFly Elytron `ModularCrypt.decode` + `PasswordFactory.verify`, exactamente
lo que hace `PasswordService.verify`). Ver §6 para el procedimiento de
regeneración.

> **IDs 1 y 2:** la BD se recrea desde cero antes de aplicar migraciones, así
> que la secuencia de `usuario` asigna 1 y 2 en orden de inserción. Los demás
> IDs de la seed **no** se fijan explícitamente (IDENTITY); se referencian por
> subselects de claves naturales (códigos, items, emails).

### 1.3 `valor_referencia` — Anexo A (sin CAMICON, agenda A7)

| clave | valor | descripcion | fuente |
|---|---|---|---|
| `SBU` | `450.00` | Salario Básico Unificado USD/mes | Ministerio del Trabajo 2023 |
| `APORTE_PATRONAL` | `12.15` | Aporte patronal IESS % | IESS 2023 |
| `FAS` | `1.538` | Factor de ajuste salarial (360/234) | v1.1 Anexo A |
| `HORAS_OPERACION_ANUAL` | `1800` | Referencia h/año equipos | v1.1 Anexo A |

### 1.4 Catálogo de eventos `log_actividad` (D-13)

Solo se usarán eventos del catálogo cerrado (auth.*, proyecto.*, insumo.*,
apu.*, presupuesto.*, cronograma.*, base.*, documento.exportado). `detalle`
JSONB **sin PII** (RNF-08).

---

## 2. Diseño de los tres escenarios

### Escenario A — `BORRADOR` (Ana, id 2) — proyecto sintético

Proyecto recién creado: ciclo de vida recién iniciado, sin APUs todavía.

| Tabla | Filas | Notas |
|---|---|---|
| `proyecto` | 1 | estado `BORRADOR`, plazo 4 MES, sin presupuesto aún (v1 pendiente de armar) |
| `parametros_proyecto` | 1 | COPIA de `parametros_sistema` (porcentaje_indirecto NULL → hereda, DM §11) |
| `firmante` | 2 | 1 CONSOLIDADO + 1 APROBADO |
| `base_insumos` | 1 | tipo PROYECTO |
| `insumo` | ~5 | pocos insumos copiados de la central (preparación de base) |
| `log_actividad` | ~4 | registro, login, proyecto.creado, insumo.creado |

### Escenario B — `EN_PROCESO` (John, id 1) — proyecto sintético

Proyecto con presupuesto v1 armado a medias: 2 capítulos completos con APUs
detalladas + stubs.

| Tabla | Filas | Notas |
|---|---|---|
| `proyecto` | 1 | estado `EN_PROCESO` |
| `parametros_proyecto` | 1 | |
| `firmante` | 2 | |
| `base_insumos` + `insumo` | 1 + ~15 | base PROYECTO con insumos propios |
| `presupuesto` | 1 | version 1, `es_vigente = TRUE`, `porcentaje_indirecto = 0.18`, total coherente |
| `capitulo` | 2 + 4 sub | capítulos con subcapítulos |
| `apu` | ~12 | 2 con secciones/detalle completos + 10 stubs (solo totales) |
| `apu_seccion` + `apu_detalle` | 4×2 + filas | solo en los 2 APUs completos |
| `rubro` | ~12 | 1:1 con APU, cantidad realista |
| `cronograma` + `actividad` | 1 + 12 | cronograma configurado, avances parciales |
| `log_actividad` | ~12 | eventos del ciclo hasta hoy |

### Escenario C — `FINALIZADO` (John, id 1) — **workbook real CMT**

El presupuesto completo del Cetro Médico Tulcán.

| Tabla | Filas | Notas |
|---|---|---|
| `proyecto` | 1 | estado `FINALIZADO`, nombre real, año 2023 |
| `parametros_proyecto` | 1 | |
| `firmante` | 3 | consolidado + 2 aprobados |
| `base_insumos` + `insumo` | 1 + 18 | base PROYECTO con los insumos usados por las 18 APUs reales |
| `presupuesto` | 1 | version 1, vigente, total = **395115.32** |
| `capitulo` | 7 + 26 sub | estructura real del workbook |
| `apu` | 298 | 18 con detalle real + 280 stubs (solo CD/CT del fixture) |
| `apu_seccion` + `apu_detalle` | 18×4 + filas | solo en las 18 APUs reales |
| `rubro` | 298 | datos reales: item, codigo, descripcion, unidad, cantidad, precioUnitario (2dp), precioTotal |
| `cronograma` + `actividad` | 1 + 298 | 1:1 con rubro, pesos 100%, avances completos (100%) |
| `log_actividad` | ~25 | eventos del ciclo completo + documento.exportado |
| `plantilla_apu` | 1 SISTEMA | snapshot del APU 501BM6 |

---

## 3. Reglas de consistencia (obligatorias)

1. **Write-through del motor respetado**: en cada APU,
   `costo_total = costo_directo × (1 + porcentaje_indirecto)`; en cada rubro,
   `precio_total = cantidad × precio_unitario`. Los stubs del CMT usan los
   valores **2dp del workbook** (el fixture es la autoridad, igual que en los
   GM); el motor no toca estos datos en la seed.
2. **`%CI` por nivel**: `apu.porcentaje_indirecto = NULL` (hereda) salvo que el
   APU tenga override (los 18 reales llevan su `0.18` del fixture).
3. **HM**: la fila `es_herramienta_menor = TRUE` es la primera de EQUIPO,
   costo = 5% de la subsección MO (DM §9).
4. **1:1 obligatorios**: `rubro.apu_id` UNIQUE (D-09), `cronograma.presupuesto_id`
   UNIQUE, `actividad.rubro_id` UNIQUE (N03 §2). **Nada** de un rubro sin APU.
5. **Suma de pesos**: `Σ actividad.peso_ponderado = 100%` por cronograma.
6. **Unidad**: filas EQUIPO/MANO_OBRA con `unidad = 'h'` (CHECK del schema);
   MATERIAL/TRANSPORTE con unidad del catálogo o texto libre.
7. **FK por subselects**: los INSERT de las tablas hijas referencian por clave
   natural (email, nombre_proyecto, item, codigo) para que los ids IDENTITY
   queden libres y la seed sea idempotente por orden de inserción.
8. **`base_insumos` CENTRAL ya existe** (V003, "Base IESS Cetro Médico Tulcán");
   las bases PROYECTO de cada escenario son filas nuevas.

---

## 4. Pasos de implementación

1. **Generar el SQL del escenario C** (298 rubros + 298 APUs + 18×detalle +
   cronograma) desde los fixtures JSON con un script Python desechable
   (`/tmp/opencode/gen_seed_cmt.py`), usando los 2dp del workbook. El resto de
   escenarios (A, B) y soporte se escriben a mano en la migración.
2. **Escribir `V004__seed_escenarios.sql`** con el orden: usuarios →
   `valor_referencia` → proyectos A/B/C → `parametros_proyecto` → `firmante` →
   `base_insumos` PROYECTO + `insumo` → `presupuesto` → `capitulo` (+ sub) →
   `apu` → `apu_seccion` → `apu_detalle` → `rubro` → `cronograma` →
   `actividad` → `plantilla_apu` → `log_actividad`.
3. **Verificar** contra Postgres limpio (Dev Services o compose) y correr la
   suite.

---

## 5. Criterios de aceptación (Done criteria)

Comandos y salidas esperadas:

1. `./gradlew test` → suite **completa verde** (63 tests; los 2 rojos GM-19/GM-20
   y 2 skipped GM-24/DIAG siguen igual, sin regresión). Los tests existentes
   hacen `TRUNCATE ... RESTART IDENTITY CASCADE` en `@BeforeEach`, así que la
   seed no interfiere.
2. Verificación SQL contra una BD limpia con las 4 migraciones aplicadas:
   ```sql
   SELECT count(*) FROM usuario;                 -- 2
   SELECT count(*) FROM proyecto;                -- 3
   SELECT estado, count(*) FROM proyecto GROUP BY estado;  -- BORRADOR 1, EN_PROCESO 1, FINALIZADO 1
   SELECT count(*) FROM presupuesto;             -- 3 (1 vigente por proyecto)
   SELECT count(*) FROM rubro;                   -- 298 + ~12 + 0 = ~310
   SELECT count(*) FROM apu;                     -- 298 + ~12 = ~310
   SELECT count(*) FROM apu WHERE es_auxiliar;   -- 0 (ningún auxiliar en la seed)
   SELECT count(*) FROM cronograma;              -- 2 (A sin cronograma; B y C con)
   SELECT count(*) FROM actividad;               -- ~12 + 298
   SELECT sum(peso_ponderado) FROM actividad GROUP BY cronograma_id;  -- 100.0000 cada uno
   SELECT sum(precio_total) FROM rubro WHERE capitulo_id IN (SELECT id FROM capitulo WHERE item='1');  -- 158908.05 (cap 1 CMT)
   SELECT count(*) FROM valor_referencia;        -- 4
   SELECT count(*) FROM plantilla_apu;           -- 2
   SELECT count(*) FROM log_actividad;           -- > 0, evento en catálogo D-13
   ```
3. Login real en Bruno: `TC-P02-01-login-john-doe` y
   `TC-P02-02-login-ana-de-armas` → 200 (los usuarios seedeados tienen
   `email_verificado = TRUE`).
4. `./gradlew build -x test` → BUILD SUCCESSFUL.

## 6. Regenerar hashes bcrypt (referencia)

```bash
# Entorno desechable
python3 -m venv /tmp/opencode/bcryptenv && /tmp/opencode/bcryptenv/bin/pip install bcrypt
/tmp/opencode/bcryptenv/bin/python -c "
import bcrypt
print(bcrypt.hashpw(b'Clave1234', bcrypt.gensalt(rounds=10, prefix=b'2a')).decode())"
```
Los hashes `$2a$` fueron verificados con el classpath real de Quarkus
(WildFly Elytron 2.9.1: `ModularCrypt.decode` + `PasswordFactory.verify` =
`BcryptUtil.matches`). No cambiar a `$2b$` ni `$2y$` sin re-verificar.

## 7. Verificación ejecutada (2026-08-02)

Postgres 18 limpio (`seedtest`, container `thesis-backend-postgres-1` :5436),
4 migraciones aplicadas en orden con `ON_ERROR_STOP=1` — sin errores. Resultados:

| Criterio | Esperado | Real | ✓ |
|---|---|---|---|
| `usuario` | 2 | 2 (ids 1, 2, ambos `email_verificado`) | ✓ |
| `proyecto` por estado | 1/1/1 | BORRADOR 1, EN_PROCESO 1, FINALIZADO 1 | ✓ |
| `presupuesto` | 3 (1 vigente/proyecto) | 2 (A sin presupuesto aún, según diseño) | ✓ |
| `rubro` | ~310 | 310 | ✓ |
| `apu` | ~310 | 310, `es_auxiliar` = 0 | ✓ |
| `cronograma` / `actividad` | 2 / ~310 | 2 / 310 | ✓ |
| Σ pesos por cronograma | 100.0000 | 100.0000 y 100.0000 | ✓ |
| Total rubros CMT | 395115.32 | 395115.32 | ✓ |
| Capítulo 1 CMT (subárbol) | 158908.05 | 158908.052000 | ✓ |
| `valor_referencia` | 4 | 4 | ✓ |
| `plantilla_apu` | 2 | 2 (1 SISTEMA + 1 PERSONAL) | ✓ |
| `log_actividad` | > 0 | 19, eventos del catálogo D-13 | ✓ |
| Write-through APU | `ct = cd×(1+CI)` | 0 APUs con desviación | ✓ |
| Subtotales sección | = Σ líneas | 0 secciones con desviación | ✓ |
| 1:1 rubro↔apu | 0 duplicados | 0 | ✓ |
| `./gradlew test` | 63, 2 red GM-19/20 + 2 skipped | idéntico baseline, sin regresión | ✓ |

Notas de la ejecución:
- La query del plan para `cap1_cmt` apuntaba a rubros directos del capítulo 1;
  los rubros cuelgan de los subcapítulos 1.x. Verificado sobre el **subárbol**
  del capítulo 1 (158908.05), que es el valor correcto.
- Las 6 APUs `-B` (códigos reutilizados en 2 rubros del workbook) se insertan
  con su rubro correspondiente; `actividad.rubro_id` UNIQUE se cumple.
- El APU real `501772` del fixture no tiene sección MO → no lleva fila HM
  (19 filas HM totales: 17 CMT + 2 escenario B). Coherente con el fixture.

## Escape hatches

- Si un INSERT de la seed falla por CHECK/FK, **STOP y reportar** con el
  mensaje exacto de Postgres; no silenciar con valores inventados.
- Si los totales de CMT no cuadran con el workbook (395115.32), los fixtures
  del motor son la autoridad — revisar el script generador, no "arreglar" a mano.
- Si un test existente se rompe por la seed (no debería), **STOP y reportar**:
  los tests truncan sus tablas en `@BeforeEach`.
