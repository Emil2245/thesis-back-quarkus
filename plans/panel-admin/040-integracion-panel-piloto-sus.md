# 040 — Integración del panel y piloto SUS

**Estado:** TODO · I-11 · cierre I-11 + piloto SUS 1–2.

> Cierra I-11 con una sola colección Bruno `12-admin/`, regresiones
> por módulo, `graphify update .` desde la raíz del proyecto de
> grado, sincronización documental y **artefactos** para el piloto
> SUS 1–2 participantes. El piloto es un **gate humano dependiente
> del frontend**: 040 no fabrica ejecución ni puntaje, solo prepara
> la plantilla, los comandos y la lista de comprobación. La medición
> poblacional `n ≥ 5` es I-12 (semanas 23–24) y no se abre aquí.

## Proceso / historia / criterios

- **Proceso:** cierre I-11 (no asigna P-xx nuevo).
- **Historia:** US-39 (verificación de cobertura completa).
  El piloto SUS 1–2 es **hito de I-11** (artefactos + plantilla
  con cita Brooke (1996)); la historia **US-40** pertenece a
  I-12 y **no** es entregada por 040.
- **Iteración:** I-11 (semanas 21–22).
- **Criterios de cierre I-11:**
  - El enum `EventoLogActividad` enumera los **26 eventos
    exactos del catálogo cerrado D-13**. La cobertura runtime del
    log (≥ 1 fila por evento) aplica **solo** a los productores
    efectivamente canonicados/implementados al cierre del acta
    032 y de 033–039: si el acta 032 difiere un evento como "no
    producer yet" (caso conocido: `proyecto.duplicado` vía
    `STOP-032-P09-DUPLICAR`), el nombre se conserva en el enum y
    la brecha se documenta en el acta y en el reporte del test; no
    se crea un caso omitido. **No se fabrican productores** para
    cerrar la cobertura.
  - Regresión por módulo verde sin regresión.
  - Bruno `12-admin/` autocontenido, todas las requests ejecutan
    verde.
  - `graphify update .` ejecutado.
  - Documentación sincronizada (`plans/README.md`,
    `docs/00-ESTADO-ACTUAL.md`, `docs/modulos/README.md`,
    `docs/modulos/panel-admin/00-acta-reconciliacion.md`,
    `docs/modulos/panel-admin/00-inventario-trabajo.md`).
- **Criterio del piloto SUS 1–2 (artefactos, no ejecución):**
  - `docs/tesis/piloto-sus/00-protocolo-1-2.md` con tareas guiadas
    cronometradas (mismas que `quality/02 §5` pero para 1–2
    participantes).
  - `docs/tesis/piloto-sus/01-cuestionario-brooke-es.md` (10 ítems
    Likert 1–5, español; cita Brooke (1996)).
  - `docs/tesis/piloto-sus/02-plantilla-resultados.md` (tabla de
    puntaje y de tiempos antes/después, sin datos).
  - `docs/tesis/piloto-sus/03-comandos-entorno.md` (cómo correr
    el backend + frontend en local para 1–2 participantes).

## Objetivo medible

Una ejecución futura debe demostrar que:

1. la colección Bruno `api/bruno/12-admin/` autocontenida ejecuta
   todas las requests en verde contra PostgreSQL 18 limpio +
   fast-jar, con `auth: inherit` y `folder.bru` propio;
2. el script `./gradlew test --tests 'ec.uce.propuestas.usuario.audit.*'
   ...` y los suites dirigidas de los módulos modificados por 034,
   035, 036, 037, 038, 039 corren **sin regresión**;
3. `graphify update .` corre desde la raíz del proyecto de grado
   sin errores y actualiza el grafo;
4. la documentación está sincronizada (ver **Sincronización
   documental** abajo);
5. los artefactos del piloto SUS 1–2 están listos para que un
   humano (autor del backend o un colaborador) los aplique;
6. **el piloto SUS no se ejecuta ni se fabrica**: si los
   participantes no están disponibles o el frontend no está
   operativo, 040 cierra **distinto** (cierre de implementación
   sin evidencia humana de piloto). El padre decide si reabre
   040 para una segunda vuelta de piloto en una fecha futura.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — 032 cerrado | Acta firmada. | Habilita código. |
| G1 — 033..039 cerrados | enum `EventoLogActividad` con los 26 eventos verbatim; cobertura runtime de los productores efectivamente implementados (≤ 26; refleja el estado de `STOP-032-P09-DUPLICAR` y de cualquier otro STOP análogo); TC-P38..TC-P42 verdes; suite dirigida verde. | Habilita integración. |
| G2 — Plan 031 cerrado | Cambios sin commit aplicados (o descartados por el padre) antes de 040. | Evita conflictos con la integración. |
| G3 — `graphify` CLI disponible | `which graphify` o `codegraph status`. | Habilita la actualización del grafo. |
| G4 — sin secrets en código | DTOs y `EnviadorCorreo` sin contraseñas temporales. | Cierra TC-P42-02. |
| G5 — cierre | Regresión verde, Bruno verde, Graphify verde, docs sincronizadas. | Evidencia medible. |

`STOP-040-FRONTEND-AUSENTE` se activa si el orquestador exige
ejecutar el piloto y el frontend **no está operativo**; el
plan documenta la dependencia y propone cierre de implementación
sin evidencia humana.

## Fuentes que deben releerse

- `plans/panel-admin/032-sincronizar-contrato-inventario-admin.md`
  hasta `039` (toda la secuencia).
- `../../../thesis-docs/plan/quality/02-catalogo-pruebas.md` §5
  (protocolo SUS completo; las tareas guiadas del piloto 1–2
  son un subconjunto anotado).
- `../../../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md`
  I-11 hito de tesis (semana 22): "piloto SUS (1–2
  participantes) — valida el protocolo y las tareas guiadas
  antes de la medición real".
- `../../../thesis-docs/plan/quality/03-trazabilidad.md` (para
  alinear la matriz final).
- `plans/031-exportacion-cronograma.md` (verificación de
  ausencia de cambios en código de motor).
- `api/bruno/10-presupuesto/folder.bru` (patrón de colección
  autocontenida con `auth: inherit`).
- `api/bruno/README.md` (guía de ejecución de colecciones).
- `src/main/java/ec/uce/propuestas/usuario/audit/EventoLogActividad.java`
  (enum catálogo cerrado).

## Estado inicial esperado

- 033–039 cerrados y verdes.
- `proyecto/parametros-sistema` canónico preservado.
- `AdminBaseCentralResource` reusado estrictamente (Plan 015bis +
  035).
- `SnapshotApuMapper` reusado estrictamente (Plan 04 + 036).
- `CronogramaDocumentoResource` (Plan 031) preservado.
- Colección Bruno `10-presupuesto/` autocontenida sirve como
  referencia de estructura para `12-admin/`.

## Decisiones locked adicionales (040)

Se suman a las anteriores; no las contradicen:

61. **Bruno `12-admin/` autocontenida — inventario exacto (16
    requests):** una sola carpeta bajo `api/bruno/12-admin/`
    con `folder.bru` propio, `auth: inherit`, requests
    organizadas en 6 grupos **exactos**, sumando **16 requests
    en total**:

    - **TC-12-00a..00e** (5 helpers): login SUPER SUPER_ADMIN,
      login titular John Doe, login titular Ana de Armas, crear
      proyecto, capturar presupuesto v1.
    - **TC-12-P38-01..03** (3 requests P-38): invitar,
      desactivar/reactivar, DELETE con proyectos.
    - **TC-12-P39-01..03** (3 requests P-39): CRUD base
      central, archivar, edición central no afecta PROYECTO.
    - **TC-12-P40-01** (1 request P-40): crear SISTEMA desde
      APU; USUARIO ve.
    - **TC-12-P41-01..02** (2 requests P-41): cambiar defaults
      + verificar proyecto nuevo vs viejo; upsert SBU + verificar
      motor intacto.
    - **TC-12-P42-01..02** (2 requests P-42): ejecutar 1 evento
      de cada categoría (auth, usuario, proyecto, insumo, base,
      APU, presupuesto, cronograma, documento, admin) y
      verificar filtro `evento=` y `detalle` sin PII.

    **Total: 5 helpers + 3 + 3 + 1 + 2 + 2 = 16 requests,
    distribuidos en 6 grupos temáticos.** Si se
    descubre una request adicional concreta durante la ejecución
    de 040, se documenta explícitamente y se ajusta el conteo;
    no se inflan a un número arbitrario.

    **Colisión de etiquetas TC:** las etiquetas
    `TC-12-P42-01..02` no colisionan con los `TC-P42-01..02` de
    la documentación canónica (`quality/02-catalogo-pruebas.md`):
    las etiquetas Bruno tienen el prefijo `TC-12-`; las
    etiquetas canónicas no. El orquestador verifica esta
    separación.

62. **Colección autocontenida + environment compartido:** `12-admin/`
    **no** crea `environments/` propio; usa
    `api/bruno/environments/dev.bru` extendido con vars runtime
    `p38*`, `p39*`, `p40*`, `p41*`, `p42*` (mismo patrón que
    `10-presupuesto/`).
63. **Graphify:** se ejecuta `graphify update .` desde la raíz
    del proyecto de grado. **No** se usa `codegraph update` (los
    dos comandos no son sinónimos; Graphify es el nombre del
    producto CLI en uso). Pre-flight con `codegraph status` (o
    equivalente) para validar.
64. **Sincronización documental:**
    - `plans/README.md`: añadir fila "I-11 — Panel Super-Admin y
      piloto SUS" con el bloque DONE/cuenta de 9 planes.
    - `docs/00-ESTADO-ACTUAL.md`: I-11 pasa de `Pendiente` a
      `✅ DONE` (o `✅ DONE / piloto SUS pendiente` si el padre
      decide separar).
    - `docs/modulos/README.md`: fila `panel-admin` añadida con
      enlace al subdirectorio.
    - `docs/modulos/panel-admin/00-acta-reconciliacion.md` y
      `00-inventario-trabajo.md`: marcar 9 planes `DONE`.
    - `docs/modulos/estado-actual.md` §1 y §4: matriz de
      capacidades I-11 marcada DONE; US-35..US-39 con criterios
      cumplidos.
    - `../../../thesis-docs/CLAUDE.md` y `../../../CLAUDE.md` (si
      lo requiere el padre): una sola línea por cada cambio
      mayor (no se reabren).
65. **Piloto SUS 1–2 — cierre dual:**
    - **Implementación cerrada (siempre):** la integración
      backend está completa; el sistema soporta el flujo de
      invitación, CRUD admin, log de actividad, etc.
    - **Evidencia humana (opcional):** el piloto 1–2 puede
      ejecutarse en una sesión posterior cuando (a) haya 1–2
      participantes disponibles del perfil ICP, y (b) el frontend
      esté operativo. Si no, 040 documenta
      `piloto-sus/04-estado.md` con la razón y propone fecha
      futura.
    - **No se mezcla con I-12:** el piloto 1–2 **no** es la
      medición poblacional `n ≥ 5`; los puntajes no entran en
      la matriz de variables de tesis (eso es I-12).
    - **Atribución Brooke (1996):** los 10 ítems del cuestionario
      SUS reproducen verbatim la escala de Brooke (1996) —
      J. Brooke, "SUS: A 'quick and dirty' usability scale", en
      *Usability Evaluation in Industry*, Taylor & Francis, 1996.
      La cita se incluye en el cuestionario
      `01-cuestionario-brooke-es.md` (encabezado o pie).

## Alcance

### Incluye

- Creación de `api/bruno/12-admin/` con `folder.bru`, helpers
  y **16 requests temáticos** (decisión 61).
- Extensión de `api/bruno/environments/dev.bru` con vars
  runtime `p38*`–`p42*`.
- Actualización de `api/bruno/README.md` con la tabla de todas
  las colecciones y la guía de ejecución de `12-admin/`.
- Ejecución de Bruno dinámico contra PostgreSQL 18 limpio +
  fast-jar, con conteo medido y publicación en el doc del plan.
- Ejecución de `graphify update .` desde la raíz del proyecto
  de grado (pre-flight + final).
- Sincronización documental (6 entradas según decisión 64).
- Artefactos del piloto SUS 1–2 (5 artefactos bajo
  `docs/tesis/piloto-sus/`, con cita Brooke (1996) y estado
  dual al cierre).
- Tests `@QuarkusTest` finales:
  - `EventoLogActividadTest` fija los **26 eventos verbatim**.
    `LogActividadCoberturaD13CompletaTest` ejecuta cada productor
    confirmado por el acta con fixture propio y compara el conteo
    antes/después, sin estado cross-class ni dependencia de V004.
    Si el acta difiere un productor (caso conocido:
    `proyecto.duplicado` vía `STOP-032-P09-DUPLICAR`), la brecha se
    registra en el acta y la documentación, pero no crea una prueba
    omitida: el proyecto solo admite el skip histórico GM-24.
    **040 no fabrica el seam faltante.**
  - `BrunoCobertura12AdminIT` (verifica que las 16 rutas
    existen y responden los códigos esperados sin correr Bruno;
    ejecución real de Bruno queda en el comando del orquestador).
- Cierre del piloto: `docs/tesis/piloto-sus/04-estado.md`.

### No incluye

- Reabrir código de 033–039.
- Cambios en `motor/`, `recalculo/`, V001–V009.
- Re-ejecutar el piloto SUS si no hay participantes o frontend.
- Mezclar piloto 1–2 con medición poblacional I-12.
- Sembrar CAMICON ni otros valores referenciales.
- Crear un módulo nuevo de primer nivel.
- Reestructurar la colección `10-presupuesto/`.
- Fabricar puntaje o evidencia humana de piloto.

## Archivos a crear/modificar (candidatos, no autorización)

| Acción | Archivo posible | Condición |
|---|---|---|
| Crear | `api/bruno/12-admin/folder.bru` | `meta { name: "12-admin", seq: 12 } auth { mode: inherit }`. |
| Crear | `api/bruno/12-admin/TC-12-00a..00e-*.bru` | 5 helpers. |
| Crear | `api/bruno/12-admin/TC-12-P38-01..03.bru` | 3 requests P-38. |
| Crear | `api/bruno/12-admin/TC-12-P39-01..03.bru` | 3 requests P-39. |
| Crear | `api/bruno/12-admin/TC-12-P40-01.bru` | 1 request P-40. |
| Crear | `api/bruno/12-admin/TC-12-P41-01..02.bru` | 2 requests P-41. |
| Crear | `api/bruno/12-admin/TC-12-P42-01..02.bru` | 2 requests P-42 (cobertura + sin PII). |
| Modificar | `api/bruno/environments/dev.bru` | +vars `p38*`, `p39*`, `p40*`, `p41*`, `p42*`. |
| Modificar | `api/bruno/README.md` | Tabla de colecciones + guía `12-admin/`. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/audit/LogActividadCoberturaD13CompletaTest.java` | Enum con 26 eventos verbatim y cobertura determinista de cada productor confirmado por el acta; una brecha sin productor se documenta, nunca se representa con una prueba omitida. |
| Crear | `src/test/java/ec/uce/propuestas/usuario/audit/BrunoCobertura12AdminIT.java` | Smoke test de las 16 rutas. |
| Crear | `docs/tesis/piloto-sus/00-protocolo-1-2.md` | Tareas guiadas cronometradas (subconjunto de `quality/02 §5`). |
| Crear | `docs/tesis/piloto-sus/01-cuestionario-brooke-es.md` | 10 ítems Likert 1–5 español + cita Brooke (1996). |
| Crear | `docs/tesis/piloto-sus/02-plantilla-resultados.md` | Tabla de puntaje + tiempos sin datos. |
| Crear | `docs/tesis/piloto-sus/03-comandos-entorno.md` | Cómo correr backend + frontend en local. |
| Crear | `docs/tesis/piloto-sus/04-estado.md` | Estado del piloto al cierre de 040. |
| Modificar | `plans/README.md` | Fila I-11 + cuenta 9 planes. |
| Modificar | `docs/00-ESTADO-ACTUAL.md` | I-11 pasa a `DONE` (o `DONE / piloto pendiente`). |
| Modificar | `docs/modulos/README.md` | Fila `panel-admin`. |
| Modificar | `docs/modulos/panel-admin/00-acta-reconciliacion.md` | Marcar 9 planes `DONE`. |
| Modificar | `docs/modulos/panel-admin/00-inventario-trabajo.md` | Marcar 9 planes `DONE`. |
| Modificar | `docs/modulos/estado-actual.md` | §1, §4 con I-11 DONE. |
| Modificar (opcional) | `../../../CLAUDE.md` y `../../../thesis-docs/CLAUDE.md` | Una línea por cambio mayor (padre decide). |
| No previsto | `src/main/java/ec/uce/propuestas/**` (excepto tests focales y posibles helpers menores) | STOP. |
| No previsto | `src/main/resources/db/migration/**` | STOP. |
| No previsto | `api/bruno/10-presupuesto/**`, `11-cronograma/**` | STOP — preservadas. |

## Artefactos del piloto SUS 1–2 (plantillas)

### `docs/tesis/piloto-sus/00-protocolo-1-2.md`

Subconjunto anotado de `quality/02 §5`:

- Tarea 1: crear proyecto nuevo con cabecera estándar.
- Tarea 2: cargar 3 insumos vía copia de base central.
- Tarea 3: crear 2 APUs (uno desde plantilla SISTEMA P-40).
- Tarea 4: armar presupuesto con 1 capítulo y 2 rubros.
- Tarea 5: exportar el presupuesto a PDF.
- Tarea 6: aceptar una invitación 72 h del admin (P-38).

Cada tarea: tiempo cronometrado, errores observados (nota libre),
satisfacción Likert 1–5. Duración total ≤ 45 min.

### `docs/tesis/piloto-sus/01-cuestionario-brooke-es.md`

10 ítems verbatim de Brooke en español (atribución estándar).
Escala Likert 1–5. Fórmula: impares `x-1`, pares `5-x`, suma × 2.5
(0–100). **Cita:**

> Cuestionario SUS adaptado verbatim de:
> Brooke, J. (1996). *SUS: A "quick and dirty" usability scale*.
> En P. W. Jordan, B. Thomas, B. A. Weerdmeester, &
> A. L. McClelland (Eds.), *Usability Evaluation in Industry*
> (pp. 189–194). Taylor & Francis.

### `docs/tesis/piloto-sus/02-plantilla-resultados.md`

Tabla vacía con columnas:
- participante, fecha, hora de inicio/fin, tiempo total,
- puntaje SUS, comentarios libres.
- Tabla de tiempos por tarea (6 tareas).
- Tabla de errores observados por tarea.

### `docs/tesis/piloto-sus/03-comandos-entorno.md`

Pasos para levantar backend (Quarkus dev) + frontend (referencia
al README del frontend; si el frontend aún no existe en la
sesión, 040 lo documenta).

### `docs/tesis/piloto-sus/04-estado.md`

Estado al cierre de 040:

- ☐ Piloto ejecutado (1–2 participantes; puntaje registrado).
- ☐ Piloto no ejecutado por falta de frontend / participantes;
  fecha futura propuesta: ________.

## Secuencia TDD (estricta)

### RED

1. `EventoLogActividadTest` asserta exactamente 26 nombres únicos.
   `LogActividadCoberturaD13CompletaTest` captura el conteo inicial,
   ejecuta cada productor confirmado por el acta con fixtures propios y
   asserta el incremento esperado, sin depender de filas V004, orden de
   métodos ni estado cross-class. No exige un productor que el acta haya
   declarado como brecha pendiente.
2. `BrunoCobertura12AdminIT`: 16 requests `WebTarget.request().get()`
   verifican status code esperado; las requests autenticadas
   usan el helper `jwt(SUPER_ADMIN)` del setup.

### GREEN

Construir los `@QuarkusTest`. Construir los archivos Bruno
siguiendo el patrón de `10-presupuesto/`. Las las requests
Bruno se ejecutan fuera del Gradle (CLI Bruno 4.1.0); el
orquestador las corre.

### TRIANGULATE

- Matriz 26: ejecutar un evento por categoría (auth, usuario,
  proyecto, insumo, base, APU, presupuesto, cronograma,
  documento, admin); assertar 1 fila por categoría.
- Bruno con vars `p38*`–`p42*` mal seteadas: las requests que
  dependen de ellas devuelven 4xx/5xx y la request documenta
  el pre-requisito.

### REFACTOR

- Consolidar los helpers Bruno en `TC-12-00a..00e` para que
  las requests temáticas sean declarativas (1 línea de URL +
  body + asserts).

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| Matriz 26 eventos verbatim | `LogActividadCoberturaD13CompletaTest` verde: enum exacto y cobertura de todos los productores confirmados por el acta; cero skips nuevos. |
| Bruno dinámico `12-admin/` | 16 requests verde (medido en CLI). |
| Smoke test de rutas | `BrunoCobertura12AdminIT` verde. |
| Regresión por módulo | `proyecto.*`, `insumo.*`, `apu.*`, `plantilla.*`, `presupuesto.*`, `cronograma.*`, `documento.*`, `usuario.*` verdes. |
| Motor intacto | GM-19/GM-20 aceptados y GM-24 omitido según línea base. |
| Sin PII en cualquier log emitido | 0 matches regex. |
| Graphify verde | sin error. |
| Docs sincronizadas | 6 entradas de sincronización documental (las 6 de la decisión 64). |
| Artefactos SUS | 5 artefactos del piloto SUS (los 4 originales + el cierre dual de estado). |

## Comandos de verificación

```bash
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus

# Focales.
./gradlew test --tests 'ec.uce.propuestas.usuario.audit.LogActividadCoberturaD13CompletaTest' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.usuario.audit.BrunoCobertura12AdminIT' \
  -Dquarkus.http.test-port=0 --console=plain

# Regresión por módulo (sin totales hardcodeados).
./gradlew test --tests 'ec.uce.propuestas.usuario.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.proyecto.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.insumo.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.apu.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.plantilla.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' \
  -Dquarkus.http.test-port=0 --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Motor: la suite agrega GM-19/GM-20 aceptados y GM-24 omitido según la línea base.
./gradlew test --tests 'ec.uce.propuestas.motor.*' \
  -Dquarkus.http.test-port=0 --console=plain

# Suite completa (decisión del orquestador; el plan NO predice
# el total porque depende del estado de Plan 031 y de los
# cambios sin commit).
# ./gradlew test --console=plain
# ./gradlew spotlessCheck
# ./gradlew build -x test

# Graphify (desde la raíz del proyecto de grado; usa `graphify`,
# NO `codegraph`).
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado
ls .codegraph/ 2>/dev/null || true
codegraph status || true
graphify update .
cd -

# Bruno dinámico (decisión del orquestador; medir).
cd /home/kaandradec/Documents/workspace/uce/proyecto-grado/thesis-back-quarkus
# (Asumiendo fast-jar en build/quarkus-app/quarkus-run.jar y
# PostgreSQL 18 limpio).
# java -jar build/quarkus-app/quarkus-run.jar &
# bru run api/bruno/12-admin/ --env dev
# medir: requests (esperado 16), asserts, ms, fail/skip.

# Diff limpio.
git diff --check

# Estado de los archivos esperados.
test -f api/bruno/12-admin/folder.bru
test -f docs/tesis/piloto-sus/00-protocolo-1-2.md
test -f docs/tesis/piloto-sus/01-cuestionario-brooke-es.md
test -f docs/tesis/piloto-sus/02-plantilla-resultados.md
test -f docs/tesis/piloto-sus/03-comandos-entorno.md
test -f docs/tesis/piloto-sus/04-estado.md
test -f docs/modulos/panel-admin/00-acta-reconciliacion.md
test -f docs/modulos/panel-admin/00-inventario-trabajo.md
test -f docs/modulos/panel-admin/035-auditoria-p39.md

# Sin migración nueva en todo I-11.
git diff --name-only -- 'src/main/resources/db/migration/**'
# esperado: vacío.
```

No se predicen conteos de suite completa. El orquestador decide.

## Completion checklist (040)

- [ ] `api/bruno/12-admin/` autocontenido; **16 requests**
      exactos; ejecución dinámica medida (CLI Bruno 4.1.0;
      requests / asserts / ms / fail-skip).
- [ ] `LogActividadCoberturaD13CompletaTest` verde para los
      productores implementados (sin dependencia de estado
      cross-class); toda brecha sin productor queda en el acta y la
      documentación, nunca como un test omitido.
- [ ] `BrunoCobertura12AdminIT` verde.
- [ ] Regresión por módulo verde (sin regresión vs Plan 031).
- [ ] Motor intacto (agregado XML del comando: GM-19/GM-20 aceptados y GM-24 omitido según línea base).
- [ ] `graphify update .` ejecutado sin error (no `codegraph`).
- [ ] 6 entradas de sincronización documental completadas (las 6 de la decisión 64).
- [ ] 5 artefactos del piloto SUS creados (con cita Brooke 1996
      en `01-cuestionario-brooke-es.md`).
- [ ] Cierre dual: implementación cerrada; piloto 1–2 con
      estado explícito en `piloto-sus/04-estado.md`.
- [ ] `git diff --check` limpio.
- [ ] `plans/README.md`, `docs/00-ESTADO-ACTUAL.md`,
      `docs/modulos/README.md` actualizados.

## Handoff al siguiente paso

Cuando 040 cierre:

1. el padre actualiza `plans/README.md` con la fila I-11
   DONE/cuenta;
2. el padre actualiza `docs/00-ESTADO-ACTUAL.md` con I-11
   `DONE` (o `DONE / piloto pendiente`);
3. el siguiente paso de planificación es **I-12** (semanas
   23–24): medición SUS `n ≥ 5` + baseline RNF-06 +
   hardening + cierre de variables de tesis. **040 no abre
   I-12**; el orquestador autoriza ese plan en su propia sesión.