# Execute document export plan 01

## Authorization
User authorized ONLY docs/modulos/07-exportacion-presupuestos-apus/01-formatos-y-contrato.md. Plans 02/03 writers and frontend 04, user DB writes/reset, commit/push are not authorized. Branch feat/document-export-contract; preserve planning documents, immutable samples and two pre-existing Compunex deletions.

## Tasks
- [x] EXP-01: Map plan01 scope and existing boundaries.
- [x] EXP-02: Backward-compatible cronograma PDF A3 horizontal, observed RED/GREEN and independent checks.
- [x] EXP-03: Budget/APU options and owner-scoped preflight; no fake downloads.
- [x] EXP-04: Immutable read-only consistent projection, canonical costs and bounded queries; independently verified.
- [x] EXP-05: Structural documentation gate and plan01 checklist reconciled; no remaining gap within its scope.

## Invariants
Selected UUIDv7/version, not silently vigente; APUs deduplicated by identity. XLSX/PDF only, two future XLSX layouts; budget PDF A4 orientation, future APU PDF A4 portrait/new page. P-32 remains distinct from cronograma distribution gates. Existing display/project parameters; no domain writes, parameter creation or Motor math changes. No placeholder downloads; render/OOXML/visual/output-performance checks belong to future writers.

## Verified gate and limits
Plan01 cerrado en su alcance, sin commit. Evidencia y checklist en [01](../../docs/modulos/07-exportacion-presupuestos-apus/01-formatos-y-contrato.md); índice en [00](../../docs/modulos/07-exportacion-presupuestos-apus/00.md).

- EXP-02 independiente: 191 tests / 20 suites PASS; MediaBox A4 842×595 y A3 1191×842, UUID400/owner404/bloqueo409.
- EXP-03 independiente: 212 tests / 24 suites PASS; versión no vigente, vacíos, P-32, scope y 10 equivalencias stale; sin archivos ficticios.
- EXP-04 corregido: CDI REQUIRES_NEW, Session vía repository, Repeatable Read/Read Only antes de ownership; records inmutables y cálculo canónico precargado. SQLSTATE25006, caller suspendido y concurrencia PASS. RED consultas 1APU19/32APU205; GREEN16 para 1/32/298 APUs. Captura fría independiente 298APUs/298rubros: 16 consultas, 566.908ms; equivalencia canónica de los 298 PASS.
- Verificación independiente última: 255 tests, 3 fallos, 1 skip: documento48 PASS, cronograma174 PASS; schema29/1 fallo, motor4/2 fallos/1 skip. Cuatro clases ORM-off ejecutan ahora: tres PASS; ScheduleIT conserva expectativa obsoleta frente a reparación ponderada V019. No regresión export ni corrección autorizada.
- Baseline HEAD aislado d73bfa5041af20ffe9ec7c4a3695a33ee3407811: motor4/2 fallos/1 skip idénticos al candidato (GM19/GM20); GM21 PASS, GM24 datos ausentes SKIP. Motor preexistente, NO corregido. [Log preservado](../../../motor-head-baseline-vstgqI/build/motor-head-baseline.log).
- Suite completa anterior a corrección: 820 tests/2 fallos/1 skip y cuatro fallos bootstrap; no se repitió completa tras corrección, no se declara verde global. Spotless/diff observados PASS. RDD nativo desactivado/no evaluable; revisión independiente realizada, sin aprobación nativa.

## Cierre documental
Documentación pasiva: RED no significativo; verificación estructural únicamente. No tests nuevos ni reejecución en EXP-05. Pendientes planes02/03/04: writers/descargas, OOXML y PDF renderizados, visual, heap, bytes y distribución de latencia; sin claim UI/browser. DB de usuario y fuentes de referencia intactas; sin despliegue, reset, commit o push.
