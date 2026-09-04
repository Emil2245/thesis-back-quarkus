# 028 — Ciclo de vida y configuración del Cronograma

**Estado:** TODO — bloqueado por los gates documentales de 026 y de
persistencia de 027; no es una implementación.

**Iteración:** I-08 (configuración y alta) con P-34 parcial.

**Procesos:** P-33 y P-34 parcial; prepara P-35/P-36, pero no implementa
segmentos, edición fina de avance ni Gantt.

> Los nombres finales de entidades, DTOs, campos y rutas se heredan
> obligatoriamente de Plan 026. Este plan no puede “mejorarlos” durante la
> ejecución. La sesión actual solo guarda planes y no crea Java, SQL,
> migraciones, tests, Bruno, frontend ni documentación canónica.

## Objetivo medible

Una futura ejecución autorizada de este plan debe entregar un vertical slice
acotado y verificable:

1. `POST /presupuestos/{presupuestoId}/cronograma` crea como máximo un
   cronograma por versión, dentro de una transacción, y autoimporta todos los
   rubros actuales como exactamente una actividad por rubro.
2. `GET /presupuestos/{presupuestoId}/cronograma` devuelve el read model del
   cronograma propio o 404 si todavía no existe; no devuelve un objeto vacío que
   finja configuración.
3. La creación acepta únicamente `SEMANA`/`MES` y un `numeroPeriodos` dentro de
   los límites canónicos de 026; valida los datos antes de persistir y no deja
   medias creaciones.
4. La repetición de una configuración ya existente es idempotente en el sentido
   aprobado por 026; un POST duplicado y dos altas concurrentes respetan el
   conflicto 1:1 y nunca duplican cronograma o actividades.
5. `PUT /cronogramas/{id}` reemplaza la configuración con la semántica exacta
   del canon: ampliar no pierde mapas; reducir con datos fuera del rango no
   borra silenciosamente y solo procede con la confirmación/política que 026
   haya fijado. Si 026 no lo canoniza, el paso queda en STOP.
6. La alta inicial crea mapas de avance vacíos y pesos/derivados según la fórmula
   canónica, incluidos los casos de presupuesto sin rubros o total cero. No
   inventa una distribución ni edita segmentos.
7. La copia/versionado conserva configuración y datos de actividades según el
   canon, remapea rubros, usa identidades nuevas y no ejecuta dos veces la
   importación inicial.
8. Solo `USUARIO` y `SUPER_ADMIN` usan el módulo; inexistente y ajeno se
   comportan como 404. UUID malformado/no-v7 es 400 en la frontera.
9. Las pruebas de contrato, alta, idempotencia, conflicto, concurrencia,
   reducción y copia se escriben primero; los conteos se extraen de XML. No se
   entrega CRUD de actividad ni edición fina de avance.

## Dependencias y gates

| Gate | Requisito | Qué desbloquea |
|---|---|---|
| G0 — Plan 026 | Contrato documental aprobado: nombres, rutas, DTOs, estados, fórmulas, límites, reducción, zero-total y errores. | Permite escribir código sin inventar shapes. |
| G1 — Plan 027 | Migración posterior a V008, entidades y repositorios con UUIDv7/ownership/constraints validados. | Permite cargar y persistir el agregado. |
| G2 — presupuesto | `Presupuesto` propio se resuelve por UUIDv7 y su PK interna; rubros se listan por versión. | Permite el autoimport 1:1. |
| G3 — transacción | Hay una costura transaccional de servicio y una forma de bloquear la fila de presupuesto/cronograma. | Garantiza cardinalidad e idempotencia concurrente. |
| G4 — copy | Se conserva el método nativo de `VersionadoService` y la prueba de FKs remapeadas. | Permite probar copia/configuración sin duplicar actividades. |
| G5 — recalculo | `recalculo` sigue activo; cualquier necesidad de cargar cronograma en el builder se evalúa sin tocar `motor/`. | Permite no confundir alta con cálculo del motor. |
| G6 — validación | Focales, regresiones, suite, Spotless, build, diff y Graphify observados. | Cierra la evidencia; el estado del plan sigue TODO/bloqueado hasta autorización. |

**Orden obligatorio:** 026 → 027 → 028. Si cualquiera de los dos primeros
planes cambia de forma durante la ejecución, se detiene y se actualiza el plan
antes de escribir código.

## Fuentes exactas

| Fuente | Uso |
|---|---|
| `docs/modulos/06-cronograma/00.md` | Mapa del módulo, invariantes, secuencia, fórmula objetivo, límites y STOP. |
| `plans/026-sincronizar-contrato-canonico-cronograma.md` | Autoridad de nombres/DTOs/rutas, estados, períodos, reducción, segmentos y exportabilidad. |
| `plans/027-identidad-persistencia-cronograma.md` | Autoridad de migración, mapeo, repositorios, UUIDv7, constraints y deep-module boundary. |
| `../thesis-docs/plan/architecture/06-database-schema.md` §2.13, §3–§5, §17 | DDL, `UNIQUE`, FK, JSONB, escalas y campos derivados. |
| `../thesis-docs/plan/architecture/07-api-contract.md` §1, §7 y Apéndice B | Rutas P-33/P-34, DTOs, códigos, roles y problem+json; se usa solo tras reconciliación 026. |
| `../thesis-docs/plan/architecture/08-codebase-design.md` §3, §5, §6 y §8 | Costura `recalculo`, versionado, límite del motor y coordinación frontend. |
| `../thesis-docs/plan/domain/02-data-model.md` §3, §13 y §16–§17 | 1:1, autoimport, copy, fórmulas, peso y precisión. |
| `../thesis-docs/plan/design/03-procesos-detalle.md` §F/P-33/P-34, §G/P-37 y §J/D-10 | Flujo de configuración, reducción con datos, avance y relación con exportación. |
| `../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P31-01, TC-P32-03/04, TC-P33-01…03 y TC-P34-01…03 | Casos de alta, copia, presupuesto vacío, reducción, avance y sincronía; se ajustan al canon 026. |
| `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` | Orden I-08/I-09/I-10 y gate de calidad. |
| `src/main/resources/db/migration/V001__baseline.sql` y `V008__capitulo_rubro_public_id.sql` | Baseline/constraints y patrón de identidad; no se editan. |
| `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | `copiarVersion` y `copiarCronogramaYActividad`; la copia existente es SQL nativa. |
| `src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java` | Resolución de presupuesto, locks existentes y cobertura P-32 por IDs internos. |
| `src/main/java/ec/uce/propuestas/recalculo/RecalculoService.java` y `recalculo/internal/VersionSnapshotBuilder.java` | Costura de write-through y gap actual de `cronograma = null`; no se modifica `motor/`. |
| `src/main/java/ec/uce/propuestas/presupuesto/entity/Presupuesto.java`, `Rubro.java` y repositorios actuales | Convención de owner, `Long` interno, rubros por versión y `publicId` en la frontera. |

## Estado inicial

- La versión inicial de presupuesto se crea fuera del cronograma y el proyecto
  puede tener una versión sin capítulos/rubros. El cronograma se configura en
  el primer acceso, no durante `POST /proyectos`.
- Las tablas `cronograma` y `actividad` ya existen en el baseline. La unicidad
  de `cronograma.presupuesto_id` protege 1:1; la unicidad de
  `actividad.rubro_id` protege actividad↔rubro 1:1. La identidad pública se
  añade por 027, no por este plan.
- No hay `Cronograma`/`Actividad` JPA, service ni resource funcional en el
  backend actual. El API documental actual muestra `CronogramaCrearRequest`,
  `CronogramaConfigurarRequest`, `ActividadAvanceRequest` y
  `CronogramaResponse`, pero sus ejemplos de actividad aún usan IDs numéricos;
  solo 026 puede cerrar esa contradicción.
- `VersionadoService` ya copia cronograma y actividad por SQL nativa, conserva
  unidad, períodos, revisión, pesos y mapa, y remapea `rubro_id` por código.
  Tras 027, los `public_id` se generan por default al insertar. La creación de
  028 no debe invocar la importación otra vez sobre una copia ya poblada.
- `VersionSnapshotBuilder.build(Long)` entrega cronograma `null`; por ello no
  se debe afirmar que el read/write-through de pesos y avances ya está integrado.
  El builder y el motor no forman parte del vertical slice salvo un gate
  específico.
- `GET /presupuestos/{id}/validacion` considera sin actividad a los rubros no
  cubiertos por el join interno; después de crear el cronograma, el comportamiento
  de P-32 debe seguir siendo coherente con la cobertura real.
- El proyecto de referencia contiene CPM, dependencias, lag y programación; es
  solo referencia UX y no se importa al ciclo de vida de este plan.

## Alcance y fuera de alcance

### Alcance futuro de 028

- Servicio transaccional de cronograma para crear, leer y configurar el
  agregado, con resolución owner-scoped.
- Autoimportación inicial de todos los rubros de la versión en una actividad
  por rubro, con `avancePorPeriodo = {}` y derivados canónicos.
- Validación de unidad y número de períodos, incluyendo límites mínimos/máximos
  definidos por 026 y tratamiento de presupuesto vacío/total cero.
- Idempotencia de PUT/reintentos definidos por el canon, conflicto de POST,
  locking y comprobación de concurrencia.
- Comportamiento de reducción con datos solamente de acuerdo con la decisión
  explícita de 026: body de pérdida, código 409, confirmación y borrado o la
  alternativa canónica. No se decide aquí.
- Lectura de respuesta con derivados del rubro sin duplicar columnas espejo, y
  compatibilidad con el copy de versiones.
- Pruebas focales API/service/schema de este vertical slice y regresiones P-31,
  P-32 y `recalculo`.

### Fuera de alcance

- PATCH de avance/celdas, distribución uniforme, edición de segmentos,
  mover/redimensionar Gantt, acumulados avanzados o S-curve: Plan 029/030 según
  026.
- CRUD manual de actividades, alta de actividades sin rubro, cambio de
  `rubroId`, borrar/reordenar actividades o conservar huérfanas.
- CPM, ruta crítica, dependencias, lag, auto-programación, hitos, fechas y
  calendarios laborales.
- Cambiar `Motor`, `Consolidador`, records del motor o duplicar fórmulas en
  `cronograma`. Si el builder necesita otra interfaz, se abre guard/plan.
- Exportación SERCOP, xlsx/pdf, MSPDI XML, `.mpp`, Bruno y frontend.
- Cambiar V001–V008, crear otra migración fuera de 027, modificar constraints
  históricas o usar UUID público como FK.
- Sincro continua al agregar/eliminar rubros después del alta; Plan 029 define
  la sincronización write-through. En 028 solo se exige la importación inicial y
  que la copia no duplique filas.

## Archivos posibles de una ejecución autorizada

> Son candidatos de implementación, no autorización actual ni nombres finales.
> Plan 026 manda sobre las variantes entre corchetes y Plan 027 debe haber
> creado/validado la persistencia antes de usarlos.

| Acción posible | Archivo | Motivo/guard |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/cronograma/service/CronogramaService.java` | Orquesta alta, lectura, configuración, lock, validación y autoimport. El nombre final lo fija 026. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/resource/<recurso-canónico>.java` | Expone solo las rutas aprobadas; no agregar aliases. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/dto/<DTOs-canónicos>.java` | Records de creación/configuración/respuesta con la forma exacta de 026. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/mapper/<mapper-canónico>.java` | Une datos de `Rubro` y `Actividad` sin persistir espejos derivados. |
| Modificar condicionalmente | `src/main/java/ec/uce/propuestas/cronograma/repository/CronogramaRepository.java` | Lock por presupuesto/cronograma y consultas 1:1; solo dentro del boundary de 027. |
| Modificar condicionalmente | `src/main/java/ec/uce/propuestas/cronograma/repository/ActividadRepository.java` | Inserción/listado inicial y comprobación de cobertura; no CRUD manual. |
| Modificar condicionalmente | `src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java` | Solo para una consulta/lock interno que no rompa `findRubrosCubiertosPorCronograma`. |
| Modificar condicionalmente | `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | Preferencia: no tocar; solo ajustar integración si un test de copy lo exige, manteniendo SQL explícito, UUIDs frescos y FKs remapeados. |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**` | Cualquier necesidad de cambio activa STOP; el motor permanece puro. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/<suite-de-ciclo>.java` | RED/GREEN de contrato, alta, lectura, configuración y concurrencia. |
| Crear/modificar | `src/test/java/ec/uce/propuestas/cronograma/<suite-de-copy>.java` | Verifica comportamiento de copia sin convertirlo en otro CRUD. |
| Modificar | `src/test/java/ec/uce/propuestas/presupuesto/resource/VersionadoResourceIT.java` | Reforzar configuración/copias, solo en casos del canon. |
| Modificar | `src/test/java/ec/uce/propuestas/presupuesto/resource/PresupuestoValidacionResourceIT.java` | Mantener la cobertura P-32 antes/después de crear cronograma. |
| Crear/modificar | `src/test/java/ec/uce/propuestas/schema/<suite-de-migracion>.java` | Regresión de 027; no se reescriben V001–V008. |

## Datos, identidad y precisión

### Alta 1:1 y autoimportación

La operación de alta debe ejecutar una única transacción:

1. resolver el presupuesto por UUIDv7 + owner;
2. bloquear la fila del presupuesto o usar el lock canónico aprobado;
3. comprobar si existe cronograma para el `presupuesto.id` interno;
4. validar el body completo (`unidadTiempo`, `numeroPeriodos`) con las reglas de
   026;
5. insertar el cronograma con `presupuesto_id` interno y dejar que la BD genere
   `public_id`;
6. listar todos los rubros de esa versión en el orden presupuestario canónico;
7. insertar una actividad por cada rubro, con `rubro_id` interno único,
   `pesoPonderado` calculado y mapa vacío;
8. hacer flush, leer la proyección canónica y devolver el response.

La operación no acepta `actividad[]`, `rubroId`, pesos ni IDs enviados por el
cliente. El número de actividades resultante es exactamente el número de rubros
de la versión al comenzar la sección crítica. Si no hay rubros, crea un
cronograma válido con actividades vacías, sujeto al tratamiento de total cero
que 026 haya escrito.

Una segunda llamada POST no repara una fila parcial ni vuelve a importar: la
constraint 1:1 y la respuesta 409 son la autoridad. Una repetición de PUT con el
mismo body no crea filas; la semántica de cambios de mapa y revisión se hereda
de 026 y no se improvisa en este plan.

### Configuración y reducción

- `unidadTiempo` es independiente de `Proyecto.plazoUnidad`; solo admite
  `SEMANA`/`MES`.
- `numeroPeriodos` es un entero `SMALLINT` positivo dentro de los límites de
  026. No se duplica un máximo local en Java, DTO y SQL.
- Aumentar el número de períodos conserva el mapa existente y habilita nuevas
  claves, sin fabricar avances.
- Mantener el mismo número y unidad es idempotente según el contrato.
- Reducir sin claves/datos fuera del rango puede aplicarse si 026 lo permite.
- Reducir con avances fuera del nuevo rango requiere el procedimiento exacto que
  026 haya canonizado. El body de conflicto debe poder enumerar actividad,
  período y valor que se perderían; el segundo intento se valida otra vez bajo
  lock. Nunca se elimina el mapa antes de comprobar confirmación y nunca se
  interpreta la ausencia de `confirmarPerdida` como consentimiento.
- Si Plan 026 no define explícitamente la reducción, sus datos a perder, el
  nombre del campo de confirmación, el status/código de error o el caso de
  concurrente, se activa `STOP-028-REDUCCION`.

### Identidad y precisión

- Los `public_id` de cronograma, actividad, presupuesto y rubro son UUIDv7 en
  rutas/respuestas. Los `Long` solo viven en la transacción, repository, FK y
  SQL nativo.
- La respuesta usa strings decimales para dinero, peso y avance. Dinero conserva
  `NUMERIC(14,6)`; peso/avance conservan la escala canónica de 4; no se usa
  `double`, `float`, `parseFloat`, `toFixed` ni aritmética binaria.
- `pesoPonderado` se calcula con la fórmula de 026 a partir de
  `rubro.precioTotal` y `presupuesto.total`. El tratamiento de total cero,
  redondeo/residual y sumatoria global se consume del canon, no de una
  constante local.
- La creación no asigna avances automáticamente. Un mapa vacío puede ser
  `BORRADOR`; no se marca `COMPLETA` ni exportable solo porque la alta fue 201.
- `item`, `descripcion` y `precioTotal` se leen desde el rubro; no se aceptan ni
  persisten copias editables en la actividad. `avanceAcumulado` y segmentos se
  derivan en el read model posterior.
- La persistencia de un mapa vacío debe ser `{}` JSONB, no `NULL`, salvo que 026
  haya aprobado otra forma. Claves futuras no se generan por defecto.

### Copy/versionado

Al copiar una versión que ya tiene cronograma:

- la nueva versión recibe su propio cronograma 1:1;
- la configuración (`unidadTiempo`, `numeroPeriodos`, total revisado y fecha)
  se conserva según 026;
- cada actividad recibe UUIDv7 nuevo, `cronograma_id` nuevo y `rubro_id` del
  rubro copiado en la nueva versión;
- el mapa y los pesos se preservan según el contrato de copy, sin volver a
  importar sobre las actividades ya insertadas;
- ningún `public_id` del origen se usa en un INSERT y la mutación posterior de
  la copia no modifica origen;
- si el origen no tiene cronograma, no se crea uno por accidente.

El SQL actual de `VersionadoService` es el seam de copia y debe permanecer con
columnas explícitas. Si la lectura del builder/recalculo fuera necesaria para
recalcular pesos, se documenta como tarea de 029/guard separado; 028 no toca
`motor/` ni falsea la prueba usando fixtures incompletas.

## Contrato REST/DTO heredado de 026

028 implementa únicamente alta, lectura y configuración. La forma final de los
records se copia de 026 después de G0; la tabla siguiente no autoriza campos
alternativos.

| Método y path canónico | Request | Respuesta/códigos | Alcance de 028 |
|---|---|---|---|
| `GET /presupuestos/{presupuestoId}/cronograma` | Ninguno. `presupuestoId` UUIDv7. | 200 `CronogramaResponse`; 404 `no-encontrado` si no existe o es ajeno. | Sí. |
| `POST /presupuestos/{presupuestoId}/cronograma` | `CronogramaCrearRequest` canónico: unidad y número. | 201 `CronogramaResponse`; 400 validación; 404 owner; 409 1:1. | Sí. |
| `PUT /cronogramas/{cronogramaId}` | `CronogramaConfigurarRequest` canónico, incluida reducción/confirmación si 026 la aprobó. | 200; 400; 404; 409 de pérdida/configuración según canon. | Sí. |
| `PATCH /cronogramas/{cronogramaId}/actividades/{actividadId}` | `ActividadAvanceRequest`. | 200/400/404 según 026. | No: Plan 029. |
| `POST /cronogramas/{cronogramaId}/revisado` | Body según canon, normalmente ninguno. | 200/404. | No: Plan 030/guard. |

El response de lectura debe usar los nombres definitivos de 026 para
`id`, `presupuestoId`, `rubroId`, estado de distribución,
`desactualizado`, `actividades`, mapas, segmentos y totales. En ningún ejemplo
se escribe `id: 1`. Si 026 cambia el recurso a varios recursos por cohesión, se
adopta ese cambio antes del GREEN.

## Secuencia ejecutable futura

### Paso 0 — preflight y línea base

1. Leer y registrar la aprobación de 026 y la evidencia de 027.
2. Resolver el presupuesto propio de una fixture real por UUIDv7; contar rubros
   por `presupuesto.id` interno y guardar el conteo observado.
3. Confirmar que no existe cronograma en la fixture de alta y que una versión
   con cronograma existente tiene las actividades que espera el caso de copy.
4. Ejecutar los focales de presupuesto, validación, schema, identidad y
   recalculo; guardar salida y XML antes del cambio.
5. Verificar que el nombre/path de cada archivo candidato sigue dentro del
   contrato aprobado; si no, STOP antes de crear código.

### Paso 1 — RED de contrato y alta

Escribir primero tests de API/service que expresen:

- GET de presupuesto propio sin cronograma → 404;
- POST válido con 0 y con `n` rubros → 201, exactamente un cronograma y `n`
  actividades;
- segunda llamada POST → 409;
- unidad/número inválidos → 400 sin filas persistidas;
- respuesta sin `Long` y con UUIDv7 en las identidades;
- caller ajeno/inexistente → 404.

Antes del resource estos tests pueden fallar con 404 de ruta inexistente; ese
resultado se registra como RED para los casos de creación y como baseline para
el GET de ausencia. Si un caso de ausencia ya pasa, no se reclama GREEN de
implementación: se marca “baseline coincidente” y se verifica que el estado de
la base no cambió.

### Paso 2 — GREEN mínimo de alta y lectura

1. Resolver el presupuesto por la costura owner-scoped existente.
2. Bloquear la fila de presupuesto o aplicar el lock que 026/027 haya aprobado;
   no hacer `check-then-insert` sin serialización.
3. Persistir cronograma y flush.
4. Obtener rubros del presupuesto, ordenarlos según la regla canónica y crear
   todas las actividades dentro de la misma transacción.
5. Dejar que defaults de BD generen UUIDv7; no aceptar identidades del body.
6. Mapear la lectura mediante rubro + actividad, derivando campos no persistidos.
7. Repetir tests de alta/lectura y registrar GREEN.

Si ocurre una violación de unicidad por carrera, la excepción se traduce al 409
canónico después de limpiar la transacción; no se responde 500 ni se devuelve
la fila de otro caller.

### Paso 3 — RED/GREEN de configuración e idempotencia

**RED:** probar PUT con body válido, misma configuración, aumento de períodos,
reducción sin datos y reducción con datos. Probar dos PUT concurrentes sobre la
misma fila y una repetición tras respuesta perdida. Capturar status, body y
estado de la base.

**GREEN:** implementar el reemplazo completo bajo lock:

- validar todo el body antes de mutar;
- releer el cronograma y el mapa dentro de la sección crítica;
- no cambiar el mapa al aumentar períodos;
- aplicar el resultado exacto aprobado por 026 para reducción;
- hacer el resultado repetible para la misma entrada;
- devolver response fresco y no una entidad stale del contexto JPA.

No añadir `Idempotency-Key`, ETag ni un campo de versión si 026 no lo contempla.
Una repetición de POST sigue siendo el conflicto definido por la API; no se
inventa semántica de upsert.

### Paso 4 — RED/GREEN de copy/versionado

**RED:** sobre una versión con cronograma, ejecutar la copia de Plan 024 y
assertar las invariantes de 028: un nuevo cronograma, cantidad de actividades,
UUIDs distintos, FKs nuevas, mapas/configuración conservados y origen intacto.
El test debe detectar duplicación si el servicio llama por error a autoimport
además de ejecutar el SQL nativo.

**GREEN:** integrar solo lo necesario para que la operación existente y las
entidades de 027 cumplan el contrato:

- mantener `copiarCronogramaYActividad` como SQL nativa explícita;
- no copiar `public_id`;
- asegurar que el nuevo `rubro_id` se obtiene del rubro de destino;
- no ejecutar la importación inicial en la copia ya poblada;
- conservar la misma transacción y el flush/clear necesarios.

### Paso 5 — TRIANGULACIÓN

- Contar rubros antes y actividades después: `n = n`, también con cero rubros.
- Consultar directamente las constraints 1:1 y comprobar que una carrera no
  deja dos filas.
- Leer JSONB y comprobar `{}` inicial, claves no consecutivas preservadas por
  copy y escala decimal; no editar avance fino.
- Probar UUIDv7, owner-to-404 y roles en GET/POST/PUT.
- Comparar la respuesta con el DDL: derivados vienen del rubro, no de campos
  espejo de actividad.
- Ejecutar P-32 antes/después: el conjunto cubierto por cronograma coincide con
  las actividades reales, sin cambiar la query nativa.
- Comparar copia/origen por IDs internos y públicos, configuración, mapas y
  conteos; modificar la copia y releer origen.
- Verificar que una eventual alerta de desactualización no se transforma en
  bloqueo o cambio de estado durante la mera configuración; P-36 queda para su
  plan.
- Verificar que ningún diff toca `motor/`, V001–V008, frontend, Bruno o
  exportación.

### Paso 6 — REFACTOR

- Separar validación de body, resolución owner, lock, autoimport y mapping sin
  crear capas genéricas innecesarias.
- Mantener repositorios como acceso a datos y service como reglas de alta/
  configuración.
- Eliminar consultas duplicadas de existencia y conversiones UUID/Long,
  conservando el límite de la costura profunda.
- Dejar comentarios breves sobre por qué POST duplicado es 409, por qué la copia
  no autoimporta y qué condición de reducción proviene de 026.
- No cambiar el canon, fórmulas, escalas ni expected values de regresiones.

## Catálogo concreto de casos felices y negativos

| Caso | Precondición/entrada | Resultado verificable |
|---|---|---|
| TC-P33-01 alta con rubros | Presupuesto propio con `n > 0` rubros y sin cronograma. | 201; 1 cronograma; `n` actividades; UUIDv7; mapas vacíos; pesos según 026. |
| Caso complementario — alta vacía | Presupuesto propio sin rubros. | 201 con `actividades=[]`, estado/peso según la política de total cero; no se inventan rubros. |
| TC-P33-02 duplicado | POST de creación dos veces para la misma versión. | Primer 201; segundo 409 canónico; conteo de cronogramas y actividades no aumenta. |
| TC-P33-03 reducción segura | Configuración con reducción sin datos fuera del nuevo rango. | Resultado 200 solo si 026 lo permite; mapa no pierde claves válidas. |
| TC-P33-03 reducción peligrosa | Actividades con entradas en períodos que salen del rango. | 409 con lista determinista de actividad/período/valor a perder; no hay mutación. |
| Confirmación canónica | Reintento con el campo/valor de confirmación aprobado por 026. | 200; solo se eliminan los datos enumerados; el resto del mapa queda intacto. |
| Reducción sin confirmación | Mismo caso peligroso sin confirmación. | 409 y estado anterior intacto; nunca borrado silencioso. |
| Configuración válida | `SEMANA`, `MES`, mínimo, máximo y límites inclusivos del canon. | 200; valores persistidos sin coerción ni unidad extra. |
| Número inválido | Cero, negativo, decimal, ausente, texto o fuera de límite. | 400 `validacion`; ninguna fila o configuración parcialmente actualizada. |
| Unidad inválida | `DIA`, vacío, `null` o diferencia de mayúsculas no admitida. | 400; no se crea/configura. |
| GET ausente | Presupuesto propio sin cronograma. | 404 `no-encontrado`, no `200 {}`. |
| UUID inválido | Path malformado o UUIDv4 en presupuesto/cronograma. | 400 `validacion` antes del repository. |
| UUID inexistente | UUIDv7 correcto sin fila. | 404 `no-encontrado`. |
| Owner ajeno | UUIDv7 correcto de presupuesto/cronograma de otro owner. | 404 indistinguible de inexistente; no 403 ni filtración. |
| Roles | `USUARIO` y `SUPER_ADMIN` propios; anónimo/rol no permitido. | Solo roles canónicos; sin tercer rol. |
| Concurrencia alta | Dos POST simultáneos para el mismo presupuesto. | Una transacción 201, la otra 409 (o la forma canónica); exactamente 1/n filas. |
| Concurrencia config | Dos PUT simultáneos, uno con reducción peligrosa. | Revalidación bajo lock; no se pierde un mapa sin la política de 026. |
| Reintento PUT | Mismo body enviado dos veces. | Resultado idempotente según 026; no nuevas actividades ni cambios adicionales. |
| Aumento de períodos | Mapa existente y `numeroPeriodos` mayor. | Configuración actualizada; entradas previas y valores intactos; nuevas celdas ausentes. |
| No consecutivos | Mapa fixture `{"1":"…","3":"…"}` copiado. | Se conserva literalmente a escala canónica; no se rellena `"2"`; segmentos se difieren. |
| Autoimport cobertura | Rubros de varios capítulos, ordenados y con total cero/positivo. | Una actividad por cada rubro de esa versión, sin importar jerarquía como otra actividad. |
| Rubro duplicado imposible | Intento de dos actividades para el mismo rubro. | Constraint 1:1; servicio no crea ni conserva huérfana. |
| Rubro posterior al alta | Agregar/eliminar rubro luego de crear cronograma. | 028 no promete sincronía fina; el caso queda explícitamente diferido a 029, sin comportamiento inventado. |
| Copy con cronograma | Versión origen con configuración, mapas y actividades. | Copia 1:1, UUIDs nuevos, FKs nuevas, mapas/configuración preservados, no duplicación de autoimport. |
| Copy sin cronograma | Versión origen sin cronograma. | Copia no crea cronograma/actividad por accidente. |
| Aislamiento copy | Cambiar la configuración de la copia. | Origen no cambia; `presupuesto_id` y `cronograma_id` son distintos. |
| P-32 cobertura | Validar origen y copia después de crear/copy. | `findRubrosCubiertosPorCronograma` distingue conjuntos por presupuesto interno. |
| Read-only de GET | Leer varias veces sin mutar. | Mismos conteos/bytes semánticos; no se crean filas ni se actualiza revisión. |
| Exportabilidad | Cronograma recién creado/borrador. | 028 no exporta ni afirma conformidad; el estado queda disponible para 029–031. |

Los identificadores TC-P33/TC-P34 se conservan en el catálogo canónico; los casos
de locking y exactitud pueden mapearse allí con la convención vigente. No se
crea una colección Bruno en este plan.

## Owner-to-404 y roles

- GET/POST resuelven primero `presupuestoId` UUIDv7 + owner. Un presupuesto
  ajeno o inexistente es 404, antes de contar rubros.
- PUT resuelve `cronogramaId` UUIDv7 atravesando
  `Cronograma → Presupuesto → Proyecto → usuario`; no acepta un cronograma de
  otro owner aunque el caller conozca el UUID.
- La actividad no se edita en 028, pero toda proyección de actividades se limita
  al cronograma propio; no se mezclan rubros de otra versión.
- UUID malformado/no-v7 → 400 `validacion`; UUIDv7 inexistente/ajeno → 404
  `no-encontrado`; la conversión a `Long` es posterior y no visible.
- `@RolesAllowed({"USUARIO", "SUPER_ADMIN"})` es la única autorización
  funcional. Un usuario no puede inferir existencia por diferencias entre 404,
  conteos o mensajes.
- Segundo alta válida en el mismo presupuesto → 409 de la constraint 1:1; no se
  convierte el conflicto en actualización automática.

## STOP conditions

Detener y reportar evidencia, sin improvisar, si ocurre cualquiera de estas
condiciones:

- **STOP-028-GATE:** Plan 026 no está aprobado o 027 no validó migración,
  mapeo, constraints y ownership.
- **STOP-028-SHAPE:** el nombre final de un DTO, campo, estado, ruta o error no
  coincide con 026; no se escribe un alias temporal.
- **STOP-028-LIMITES:** 026 no fija límites de `numeroPeriodos`, unidad,
  comportamiento de total cero o regla para mapas vacíos.
- **STOP-028-REDUCCION:** reducir períodos con datos no tiene política canónica
  completa (código, body enumerado, confirmación, revalidación y efecto). No se
  inventa `confirmarPerdida` aunque aparezca en un documento antiguo.
- **STOP-028-1A:** alta concurrente puede dejar dos cronogramas, actividades
  duplicadas, huérfanas o una respuesta 200 falsa; no se compensa solo capturando
  una excepción.
- **STOP-028-AUTOIMPORT:** el conteo de actividades no coincide con el de rubros,
  se omite un capítulo/rubro, se aceptan actividades del body o la autoimportación
  se ejecuta dos veces durante copy.
- **STOP-028-ID:** un INSERT copia un `public_id` del origen, usa UUID como FK o
  filtra `BIGINT` en response/error/log.
- **STOP-028-OWNER:** ajeno e inexistente no son ambos 404, se consulta antes de
  parsear UUIDv7 o aparece un tercer rol.
- **STOP-028-SQL:** la solución cambia V001–V008, usa `SELECT *`, reemplaza el
  deep copy nativo sin autorización o rompe `findRubrosCubiertosPorCronograma`.
- **STOP-028-PRECISION:** se usa `double`/`float`, se redondea mapa/peso con una
  regla local, se altera total cero o se marca completo sin la igualdad canónica.
- **STOP-028-MOTOR:** se pretende tocar `Motor`, `Consolidador`, snapshots puros
  o agregar CPM/dependencias para completar este slice.
- **STOP-028-SYNC:** se intenta implementar la sincronización continua de rubros,
  PATCH de avances, segmentos o Gantt en lugar de dejarlo para 029/030.
- **STOP-028-COPY:** origen y copia comparten cronograma/actividad/UUID, el
  `rubro_id` no se remapea o el origen cambia al configurar la copia.
- **STOP-028-SCOPE:** aparece frontend, Bruno, export, SERCOP/MSPDI, SQL fuera
  de la migración de 027 o una modificación de `thesis-docs` no autorizada.

## Verificaciones focales, regresiones y cierre

Estas órdenes son para una sesión futura de implementación. No se ejecutan al
redactar este documento y sus resultados deben observarse, no presupuestarse.

### Focales del ciclo de vida

```bash
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.schema.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.identifier.*' --console=plain
```

La suite de cronograma debe cubrir GET ausente, alta 1:1, autoimport, DTO/UUID,
PUT, reducción, idempotencia y concurrencia. Schema/identifier son regresiones
de 027, no se sustituyen por mocks.

### Presupuesto, copy, cobertura y recalculo

```bash
./gradlew test --tests 'ec.uce.propuestas.presupuesto.resource.VersionadoResourceIT' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.resource.PresupuestoValidacionResourceIT' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' --console=plain
```

El motor permanece sin cambios. Si `VersionSnapshotBuilder` todavía pasa
cronograma `null`, se reporta como gap para 029/guard y no se modifica para
forzar un resultado verde de 028.

### Regresión completa, calidad, build, diff y Graphify

```bash
./gradlew test --console=plain
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

### Conteos reales desde XML

```bash
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path("build/test-results/test")
keys = ("tests", "failures", "errors", "skipped")
totals = {key: 0 for key in keys}
files = sorted(root.glob("TEST-*.xml"))
for path in files:
    suite = ET.parse(path).getroot()
    for key in keys:
        totals[key] += int(suite.attrib.get(key, 0))
print("files=", len(files))
print(" ".join(f"{key}={totals[key]}" for key in keys))
PY
```

Reportar además el conteo observado de rubros y actividades en cada caso de
alta/copy y la salida literal de los focales. No hardcodear un total de tests
futuro; los XML son la autoridad. Los residuales aceptados del motor se
reportan según Plan 014 y no se reabren en este plan.

## Criterios de aceptación

- [ ] El estado permanece `TODO — bloqueado por 026/027`; no se declara cerrado.
- [ ] G0/G1 están aprobados y todos los nombres/DTOs/rutas usados coinciden con
      Plan 026.
- [ ] GET sin configuración responde 404 owner-scoped y GET configurado devuelve
      el read model canónico sin IDs `BIGINT`.
- [ ] POST válido crea una sola fila 1:1 y autoimporta exactamente todos los
      rubros actuales como actividades, incluyendo el caso vacío.
- [ ] La alta es transaccional, no acepta actividades/IDs del cliente y no deja
      estado parcial ante validación, conflicto o error.
- [ ] `SEMANA`/`MES`, `numeroPeriodos` y sus límites se validan exactamente como
      026; unidad del cronograma no se confunde con plazo del proyecto.
- [ ] POST duplicado y altas concurrentes cumplen el 409/resultado canónico sin
      duplicar cronograma ni actividad.
- [ ] PUT es idempotente según 026, conserva mapas al ampliar y resuelve reducción
      con datos solo por la política canónica; si no existe, permanece STOP.
- [ ] Mapas iniciales, pesos, total cero y estados `BORRADOR`/completo se
      representan sin fórmulas o redondeos locales.
- [ ] Copy/versionado preserva lo aprobado, obtiene UUIDs nuevos, remapea FKs,
      no autoimporta dos veces y mantiene aislado el origen.
- [ ] Owner-to-404 y roles `USUARIO`/`SUPER_ADMIN` están probados para los tres
      endpoints de 028.
- [ ] No se implementan PATCH de avance, segmentos, Gantt, CPM, exportación,
      frontend, Bruno ni cambios al motor.
- [ ] Focales, regresiones, suite, Spotless, build, `git diff --check` y
      `graphify update .` tienen evidencia literal; conteos desde XML.
- [ ] No se ejecuta commit sin autorización explícita.

## Plantilla de evidencia — completar sin inventar resultados

```text
Plan: 028
Estado al iniciar: TODO — bloqueado por 026/027
Fecha/hora:
Ejecutor/revisor:

Aprobación Plan 026:
Evidencia Plan 027:
Nombres/rutas/DTOs canónicos usados:
Límites numeroPeriodos:
Política total cero:
Política reducción y confirmación:

RED contrato/alta/configuración observado:
-
GREEN alta/lectura observado:
-
GREEN configuración/idempotencia observado:
-
GREEN copy observado:
-
TRIANGULACIÓN/REFACTOR:
-

Conteos funcionales observados:
- rubros de fixture:
- actividades tras alta:
- cronogramas tras alta secuencial:
- cronogramas tras alta concurrente:
- actividades tras copy:

Owner-to-404/roles:
-

Archivos realmente modificados:
-
Archivos deliberadamente fuera de scope:
- motor/:
- V001–V008:
- frontend/Bruno/export:

Comandos focales y resultados:
- comando:
  resultado:

Regresiones/suite/Spotless/build/diff/Graphify:
- comando:
  resultado:

Conteo XML real:
- files=
- tests=
- failures=
- errors=
- skipped=

STOP activo (si aplica):
-

Estado de salida: TODO / bloqueado por ______
Commit: no realizado; requiere autorización explícita.
```

## Regla de no commit

Este plan no autoriza `git add`, `git commit`, merge, push, publicación ni cambio
de estado a concluido. Si falta una decisión canónica, la respuesta correcta es
STOP con evidencia y no una confirmación inventada de pérdida de datos.
