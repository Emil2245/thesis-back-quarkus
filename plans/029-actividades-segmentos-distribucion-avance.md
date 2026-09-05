# 029 — Actividades, segmentos, distribución y avance

**Estado:** TODO — bloqueado por los Planes 026, 027 y 028; no es una implementación.

**Iteración:** I-08. **Proceso:** P-34.

> Los nombres finales de rutas, DTOs, comandos y estados son los aprobados por el
> Plan 026. Este plan no autoriza variantes de compatibilidad ni cambios en
> `motor/`.

## Objetivo medible

Completar el vertical slice editable del cronograma para que:

1. cada `Rubro` de la versión tenga exactamente una `Actividad` y las altas/bajas
   de rubros mantengan esa cobertura en la misma transacción;
2. una actividad admita períodos activos no consecutivos, representados por una
   sola fuente de verdad y proyectados como segmentos máximos consecutivos;
3. el usuario pueda reemplazar atómicamente su distribución, generar una
   distribución uniforme y ejecutar las operaciones semánticas de mover o
   redimensionar que haya canonizado el Plan 026;
4. por actividad, `Σ avancePorPeriodo = pesoPonderado` exactamente a escala 4 y
   `desviacion = 0.0000` determine completitud; el avance global final sea
   `100.0000` para habilitar el gate cuantitativo de exportación;
5. una distribución incompleta se persista y lea como `BORRADOR`, sin pérdida de
   datos y con la desviación visible;
6. `VersionSnapshotBuilder` cargue el `CronogramaSnapshot` y sus
   `ActividadSnapshot` desde persistencia, usando el motor existente sin modificar
   su contrato ni duplicar sus fórmulas;
7. concurrencia, owner-scope, copia de versiones y rollback queden cubiertos por
   pruebas escritas primero.

## Dependencias y gates

| Gate | Requisito | Salida necesaria |
|---|---|---|
| G0 — canon | Plan 026 aprobado, con rutas, DTOs, estados, signo de desviación, residual, límites y comandos de Gantt cerrados. | Ningún nombre o algoritmo pendiente. |
| G1 — persistencia | Plan 027 ejecutado: UUIDv7 públicos, entidades, repositorios y constraints validados. | Actividad resoluble por UUIDv7 y owner. |
| G2 — ciclo de vida | Plan 028 ejecutado: alta 1:1, configuración y autoimportación inicial operativas. | Cronograma con `numeroPeriodos` y actividades iniciales. |
| G3 — presupuesto | Las mutaciones de rubros tienen una costura transaccional donde mantener cobertura. | Alta/baja de rubro y actividad atómicas. |
| G4 — motor | Los records actuales aceptan los datos canónicos sin cambios semánticos. | Builder persistente; `motor/` intacto. |
| G5 — cierre | Focales, regresiones, suite, calidad y Graphify ejecutados. | Evidencia literal y conteos XML reales. |

Si G0 no define la actividad de período con valor cero, el algoritmo de residual,
la semántica de mover/redimensionar o el tratamiento de cambio de peso, detenerse.

## Fuentes que deben releerse

- `docs/modulos/06-cronograma/00.md`.
- `plans/026-sincronizar-contrato-canonico-cronograma.md`.
- `plans/027-identidad-persistencia-cronograma.md`.
- `plans/028-ciclo-vida-configuracion-cronograma.md`.
- `../thesis-docs/plan/architecture/06-database-schema.md` §2.13, §3–§5 y §17.
- `../thesis-docs/plan/architecture/07-api-contract.md` §1, §7 y Apéndice B,
  **después** de la sincronización del Plan 026.
- `../thesis-docs/plan/architecture/08-codebase-design.md` §3–§6.
- `../thesis-docs/plan/domain/02-data-model.md` §13 y §16–§17.
- `../thesis-docs/plan/design/03-procesos-detalle.md` §F/P-34 y §J.
- `../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P34-01…12 y casos añadidos
  por el Plan 026.
- `../thesis-docs/DOCUMENTOS/entrevistas/05/N05_entrevista-cronograma.md` §§1–4.
- `src/main/java/ec/uce/propuestas/motor/{CronogramaSnapshot,ActividadSnapshot,AvancePeriodo,PesoPonderado}.java`.
- `src/main/java/ec/uce/propuestas/recalculo/RecalculoService.java` y
  `recalculo/internal/VersionSnapshotBuilder.java`.
- Recursos/servicios de `presupuesto` que crean, editan y eliminan rubros.
- `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java`.

`../ingepresupuestos/` puede consultarse solo para comprender la UX de segmentos;
no autoriza copiar código, esquema, nombres, fechas, CPM ni dependencias.

## Estado inicial

- V001 ya persiste `actividad.peso_ponderado` y
  `actividad.avance_por_periodo`; Plan 027 añade identidad pública y mapeo.
- Plan 028 deja una actividad por rubro existente, con mapa vacío, pero no resuelve
  edición fina ni sincronización continua al mutar rubros.
- El motor ya contiene snapshots de cronograma y actividad. El builder persistente
  entrega hoy `cronograma = null`; esa es la costura a completar, no una razón para
  reabrir el motor.
- `VersionadoService` copia cronograma y actividad por SQL nativo y remapea rubros.
  El comportamiento debe conservarse.
- No existe una entidad separada de segmento en el baseline. Salvo decisión
  canónica contraria, los segmentos son proyección del mapa, no persistencia nueva.

## Alcance

- Edición owner-scoped de la distribución de una actividad.
- Selección de períodos activos no consecutivos y reemplazo atómico del mapa.
- Distribución uniforme determinista a escala 4, incluido residual.
- Comandos semánticos canonizados para mover y redimensionar barras.
- Estado `BORRADOR`/completo y desviación por actividad; total global derivado.
- Sincronización 1:1 al crear/eliminar rubros y recálculo write-through.
- Carga persistente del snapshot de cronograma en `recalculo`.
- Integridad de deep copy, locks, concurrencia y rollback.

## Fuera de alcance

- Las tres vistas agregadas de I-09, curva S y alerta de desactualización (Plan 030).
- Exportación XLSX/PDF/MSPDI (Plan 031).
- CRUD manual de actividades, actividades sin rubro o cambio de `rubroId`.
- CPM, ruta crítica, FS/SS/FF/SF, lag, auto-programación, fechas e hitos.
- Cambiar `Motor`, `Consolidador` o los records de `motor/`; si fuera necesario,
  activar STOP y crear el plan guard exigido por `CLAUDE.md`.
- Persistir píxeles, coordenadas visuales o un segundo modelo de segmentos.

## Archivos posibles de una ejecución autorizada

Los nombres entre `<...>` dependen del contrato aprobado por 026.

| Acción | Archivo posible | Propósito |
|---|---|---|
| Crear/modificar | `src/main/java/ec/uce/propuestas/cronograma/service/<servicio-canónico>.java` | Operaciones atómicas, distribución y locks. |
| Crear/modificar | `src/main/java/ec/uce/propuestas/cronograma/resource/<recurso-canónico>.java` | PATCH/comandos aprobados, sin aliases. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/dto/<DTOs-canónicos>.java` | Mapas, comandos y respuestas con decimal string. |
| Crear/modificar | `src/main/java/ec/uce/propuestas/cronograma/repository/{CronogramaRepository,ActividadRepository}.java` | Resolución owner-scoped y locks internos. |
| Modificar | `src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java` | Cargar snapshots existentes desde BD. |
| Modificar condicionalmente | Recursos/servicios de rubro autorizados por el plan | Mantener actividad 1:1 en la transacción de alta/baja. |
| Modificar condicionalmente | `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | Solo si una prueba demuestra un gap; preservar SQL nativo y UUIDs nuevos. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/<suites-P34>.java` | Contrato, precisión, ownership y concurrencia. |
| Modificar | `src/test/java/ec/uce/propuestas/recalculo/RecalculoServiceIT.java` | Snapshot persistente y write-through real. |
| Modificar | Suites de rubro/versionado estrictamente necesarias | Cobertura 1:1 y copia independiente. |

No se prevén migraciones en este plan. Si el contrato aprobado exige una nueva
columna o tabla, detenerse y actualizar primero 026/027.

## Datos, precisión y algoritmos

### Fuente de verdad

`avancePorPeriodo` es un mapa completo de claves string que representan enteros
1-based en `1..numeroPeriodos`. Un PATCH válido reemplaza el mapa completo; no hace
merge implícito. Claves duplicadas tras normalización (`"01"` y `"1"`), no
enteras, cero, negativas o fuera de rango se rechazan antes de persistir.

Los valores son decimales no negativos a escala máxima 4, tratados con
`BigDecimal`. El contrato 026 decide si cero significa período activo; la decisión
debe ser uniforme en validación, segmentos, Gantt y exportación.

### Segmentos

Ordenar períodos numéricamente y formar runs máximos consecutivos. Por ejemplo,
los activos `{1, 3, 4, 8}` producen `[1,1]`, `[3,4]`, `[8,8]`. La proyección es
determinista y no se escribe en otra tabla o columna.

- **Mover:** desplaza todos los períodos según el delta entero aprobado, conserva
  forma y valores, y falla sin cambios si algún destino sale de rango o colisiona
  de una manera no definida por el canon.
- **Redimensionar:** aplica el comando de borde/rango canonizado y redistribuye el
  peso mediante el mismo algoritmo determinista; no acepta coordenadas de UI.

### Exactitud

Para actividad `i`:

```text
avanceTotal_i = Σ avance_i,p
completa_i ⇔ avanceTotal_i = pesoPonderado_i a escala 4
desviacion_i = fórmula/signo canonizado en 026
```

El cierre de pesos consume la salida scale-4 del motor como unidades enteras: un
residual global positivo se añade a la primera actividad por precio descendente;
un residual negativo se consume, sin bajar de cero, siguiendo ese orden. Probar
explícitamente `[1,1,4]` (bases `100.0001`).

Una distribución uniforme divide el peso entre períodos activos y cuantiza a escala
4; el residual firmado se asigna al último período numérico para cerrar exactamente.
Nunca se usa `double`.

El cronograma está completo solo si todas las desviaciones son `0.0000`, el final es
`100.0000` y cada actividad de precio positivo tiene una clave activa. Un rubro cuyo
peso redondea `0.0000` requiere una clave presente con valor cero. Presupuesto vacío
o total cero permanece borrador.

### Cambio de presupuesto

Al cambiar un rubro, `recalculo` actualiza el peso conforme al total actual. Los
períodos y valores editados no se borran silenciosamente. Si la nueva igualdad ya
no se cumple, la actividad queda borrador y expone su desviación. La condición de
`desactualizado` y la revisión pertenecen al Plan 030.

### Atomicidad

Resolver cronograma y actividad bajo owner, comprobar que ambos pertenecen al
mismo agregado, bloquear las filas canónicas, validar el mapa/comando completo,
persistir una sola vez, invocar `RecalculoService.recalcular(new
Alcance.Version(...))` en el orden autorizado y leer la respuesta fresca. Cualquier
error revierte mapa, pesos y sincronización.

## Contrato REST y DTO

Implementar únicamente las rutas y records congelados por 026. La ruta
canónica de mutación es `PATCH /cronogramas/{id}/actividades/{aid}`, con los cuatro
comandos discriminados; mover/redimensionar no crean aliases ni rutas adicionales.
La proyección de Gantt, valorizado y curva S se consulta únicamente mediante
`GET /cronogramas/{id}/vistas` y pertenece al Plan 030.

Invariantes públicas:

- IDs de cronograma, actividad y rubro son UUIDv7; no aparecen `BIGINT`.
- UUID malformado/no-v7 → 400 `validacion`; válido inexistente/ajeno → 404.
- actividad de otro cronograma, aunque pertenezca al mismo usuario → 404.
- decimales JSON conservan la forma definida por 026, sin números binarios.
- la respuesta incluye mapa ordenable, peso, total, desviación, estado y segmentos
  derivados; no duplica descripción/precio como datos editables.

## Secuencia TDD

### RED

1. Escribir tests REST/service de reemplazo atómico, UUID y owner-scope.
2. Añadir casos de segmentos no consecutivos y mover/redimensionar.
3. Añadir precisión: uniforme con división exacta y con residual; borrador y
   completo a escala 4.
4. Añadir tests de alta/baja de rubro, cambio de peso, copia y rollback.
5. Añadir `RecalculoServiceIT` que demuestre que el builder carga el cronograma.
6. Ejecutar focales y conservar la salida RED esperada por ausencia de capacidad;
   un fallo de infraestructura no cuenta como RED.

### GREEN

Implementar la mínima lógica del boundary cronograma, sincronización de rubros y
builder para hacer pasar cada grupo, sin tocar `motor/`, sin nueva persistencia de
segmentos y sin adelantar vistas/exportación.

### TRIANGULACIÓN

- Probar un período, varios consecutivos y varios segmentos.
- Probar divisiones con y sin residual y orden de rubros distinto.
- Probar dos writers concurrentes y rollback después del lock.
- Comparar snapshot persistente con la respuesta REST y con el deep copy.

### REFACTOR

Centralizar validación de mapa, algoritmo de residual y proyección de segmentos en
el módulo profundo. No crear helpers públicos innecesarios ni duplicar fórmulas en
resource, mapper o frontend.

## Catálogo mínimo de pruebas

| Caso | Resultado esperado |
|---|---|
| Mapa `{}` | Se guarda como borrador; desviación visible. |
| Un período activo | Distribución exacta al peso; un segmento. |
| `{2,3,7,9,10}` | Tres segmentos ordenados; no se rellenan huecos. |
| Uniforme exacta | Valores escala 4 y suma igual al peso. |
| Uniforme con residual | Residual determinista; suma exacta sin tolerancia. |
| Edición manual incompleta/sobreasignada | Guarda borrador; desviación con signo canónico. |
| Completa por actividad y global | Todas `0.0000`; final `100.0000`. |
| Claves `0`, `n+1`, decimal, negativa, texto, `01`+`1` | 400; mapa anterior intacto. |
| Valor negativo, nulo, no decimal o > escala permitida | 400; sin mutación parcial. |
| Mover válido | Conserva valores/suma/forma y cambia períodos atómicamente. |
| Mover fuera de rango | 400; snapshot anterior idéntico. |
| Redimensionar válido | Usa algoritmo canonizado y conserva peso exacto. |
| Actividad de otro cronograma/cross-presupuesto | 404; no cambia ninguna fila. |
| UUID malformado, UUIDv4, UUIDv7 inexistente/ajeno | 400, 400, 404, 404. |
| Alta de rubro con cronograma | Crea una actividad una sola vez en el mismo commit. |
| Baja de rubro | Elimina cobertura conforme a FK/canon; no deja huérfana. |
| Fallo durante sincronización | Rollback de rubro y actividad. |
| Dos ediciones concurrentes | Resultado serializable o conflicto canónico; no lost update. |
| Cambio de precio/cantidad | Recalcula peso, conserva mapa, actualiza desviación. |
| Copia de versión | Mapas/configuración preservados, rubros remapeados, UUIDs nuevos. |
| Builder | `VersionSnapshot.cronograma()` no es null y coincide con BD. |
| Invocación repetida de lectura | Read-only, orden estable, cero escrituras. |
| Anónimo/rol no permitido | Política global; solo USUARIO/SUPER_ADMIN funcionales. |

## Owner-scope y seguridad

Toda resolución pública debe atravesar
`Actividad → Cronograma → Presupuesto → Proyecto → usuario`. No se autoriza por el
UUID aislado. Los errores no revelan el agregado correcto ni IDs internos. Los
detalles de error/log no incluyen PII, mapas completos sensibles ni SQL.

## STOP conditions

- **STOP-029-CANON:** falta cualquier shape, límite, signo, residual o comando en 026.
- **STOP-029-SCHEMA:** la implementación necesita columna/tabla no aprobada.
- **STOP-029-MOTOR:** se requiere modificar `motor/` o duplicar su aritmética.
- **STOP-029-ZERO:** no está decidido presupuesto vacío/total cero o período con valor cero.
- **STOP-029-CONCURRENCY:** no existe forma de evitar lost update o carrera alta/baja.
- **STOP-029-COPY:** el cambio rompe el SQL nativo o reutiliza identidad/FK.
- **STOP-029-PRECISION:** aparece `double`, tolerancia, redondeo intermedio o suma no exacta.
- **STOP-029-SCOPE:** el trabajo intenta incluir vistas I-09, exportación o CPM.

## Verificación futura

```bash
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
./gradlew test --console=plain
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
grep -RInE '\b(double|float)\b' src/main/java/ec/uce/propuestas/motor
# Debe confirmar que motor/ no cambió y que no existen tipos binarios nuevos.
graphify update .
```

Extraer conteos reales, sin hardcodearlos:

```bash
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-*.xml
```

## Criterios de aceptación

- [ ] Los Planes 026–028 están cerrados y sus decisiones no se reabren.
- [ ] Cobertura rubro–actividad 1:1 se mantiene en alta, baja, carrera y rollback.
- [ ] Períodos no consecutivos y segmentos derivados son deterministas.
- [ ] Reemplazo, uniforme, mover y redimensionar son atómicos y canonizados.
- [ ] Cada actividad completa suma exactamente su peso a escala 4; global final
      `100.0000`; borradores válidos conservan desviación.
- [ ] Builder carga snapshots reales y `motor/` permanece sin cambios.
- [ ] UUIDv7/owner-to-404/cross-cronograma están cubiertos.
- [ ] Deep copy produce filas e identidades independientes.
- [ ] Focales, suite, calidad y Graphify tienen evidencia observada y conteos XML.
- [ ] No se ejecutó commit sin autorización explícita.

## Evidencia de cierre — completar al ejecutar

```text
Plan: 029
Estado inicial/gates 026–028:
Fecha y ejecutor:
Archivos creados/modificados:
RED observado:
GREEN observado:
Triangulación/refactor:
Casos focales (comando y salida):
Regresiones (comando y salida):
Conteo XML real: tests= failures= errors= skipped=
Precisión/residual comprobados:
Cobertura/copia/concurrencia comprobadas:
Motor diff: sin cambios / STOP
STOP activos:
Spotless/build/diff/graphify:
Commit: no realizado; requiere autorización explícita.
```

Este plan permanece TODO hasta que una ejecución futura registre toda la evidencia.
