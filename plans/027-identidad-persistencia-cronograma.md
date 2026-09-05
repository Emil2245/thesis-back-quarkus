# 027 — Identidad pública y persistencia de Cronograma/Actividad

**Estado:** DONE 05-09-2026 — identidad, fingerprint, constraints, copia y
verificación integral entregados; Plan 028 habilitado.

**Iteración:** I-08 (persistencia base del cronograma).

**Procesos:** P-33 y P-34 parcial; habilita la base técnica para P-35/P-36 y el
lane de exportación de P-37, sin implementarlos.

> Plan 026 dejó el contrato canónico aprobado. Este plan es la siguiente unidad
> ejecutable; no autoriza adelantar CRUD/vistas/export ni tocar `thesis-docs`.

## Objetivo medible

Al finalizar una futura ejecución autorizada, la base y el modelo de aplicación
deben demostrar, sin cambiar las tablas ya aplicadas ni el motor, que:

1. Existe una migración Flyway **aditiva posterior a V008**, con el número real
   elegido al ejecutar, que añade `public_id UUIDv7` inmutable a `cronograma` y
   `actividad`, y `cronograma.presupuesto_fingerprint_revisado CHAR(64)` nullable.
2. Se mantienen intactos los PK/FK `BIGINT`, nombres, FKs y unicidades. La misma
   migración reemplaza únicamente el CHECK histórico `numero_periodos > 0` por
   el límite canónico `SEMANA 1..520 | MES 1..120` y añade el CHECK lowercase
   SHA-256 del fingerprint.
3. Las entidades JPA y repositorios siguen el patrón vigente
   `PanacheEntityBase` + `IDENTITY`, mapean la identidad pública generada por la
   BD sin permitir insertarla o actualizarla desde Java y no filtran el
   `BIGINT` a la frontera.
4. La resolución pública de cronograma y actividad atraviesa ownership y
   devuelve 404 para inexistente o ajeno; el contrato de entrada rechaza UUID
   malformado/no-v7 con 400 y solo permite `USUARIO` y `SUPER_ADMIN`.
5. `VersionadoService.copiarCronogramaYActividad` conserva el seam de SQL
   nativo: sus `INSERT` omiten `public_id`, copian el fingerprint junto con los
   marcadores ya existentes, remapean los FK internos y la copia recibe UUIDs
   nuevos, sin compartir identidad ni filas con el origen.
6. `findRubrosCubiertosPorCronograma` sigue resolviendo por `BIGINT` y sus
   resultados no cambian por la nueva identidad pública.
7. El límite profundo queda claro: el módulo de cronograma traduce UUIDv7 ↔
   `BIGINT` en su frontera; `recalculo` adapta datos persistidos a snapshots del
   motor; el motor no conoce JPA, REST ni UUIDs.
8. Las pruebas de esquema, mapeo, identidad, ownership, constraints y copia se
   escriben primero y sus conteos se reportan desde XML. No se implementa CRUD
   funcional completo en este plan.

## Dependencias y gates

| Gate | Entrada | Condición de salida |
|---|---|---|
| G0 — 026 | Plan 026 en estado de gate cerrado y contrato aprobado. | Nombres finales de entidades, DTOs, rutas, campos y errores disponibles. |
| G1 — V008 | `src/main/resources/db/migration/` inspeccionado y `V008__capitulo_rubro_public_id.sql` confirmado como última migración aplicada en el árbol. | El número siguiente se determina en la sesión; no se reserva ni se adivina. |
| G2 — esquema | Base de test levantada con V001…V008 y snapshot de constraints antes del cambio. | RED capturado; migración nueva aditiva y reversible por procedimiento de despliegue, sin editar histórico. |
| G3 — ORM | Entidades/repositories compilables bajo `hibernate-orm.database.generation: validate`. | Mapeo JPA validado y sin `ALTER` implícito de PK/FK/columnas existentes. |
| G4 — deep copy | SQL nativo actual de `VersionadoService` y query de cobertura revisados. | Copia con UUIDs frescos, FK remapeados y cobertura regresada intacta. |
| G5 — ownership | Rutas/DTOs finales del Plan 026 disponibles aunque el CRUD siga fuera. | Repositorios/servicios no filtran existencia ajena y conservan 404. |
| G6 — cierre | Focales, regresiones, Spotless, build, diff y Graphify ejecutados. | Evidencia literal, conteos XML reales y plan aún `TODO` hasta autorización de la siguiente implementación. |

**Regla de bloqueo:** si G0 no está cerrado, no se eligen nombres finales ni se
crea una migración. Si el número Flyway siguiente no puede determinarse de forma
segura en el árbol y en `flyway_schema_history`, se activa `STOP-027-NUMBER`.

## Fuentes exactas

| Fuente | Uso |
|---|---|
| `docs/modulos/06-cronograma/00.md` | Mapa de secuencia 026→027→028, invariantes, precisión, ownership y STOP. |
| `plans/026-sincronizar-contrato-canonico-cronograma.md` | Gate de nombres, shapes, rutas, estados, fórmulas y formatos que este plan hereda; no se redecide aquí. |
| `../thesis-docs/plan/architecture/06-database-schema.md` §2.13, §3–§5, §17 | DDL de `cronograma`/`actividad`, cardinalidades, FK, JSONB, escalas e índices. |
| `../thesis-docs/plan/architecture/07-api-contract.md` §1, §7, Apéndice B | Identidad y errores REST que deben heredar las entidades, aunque el CRUD completo sea posterior. |
| `../thesis-docs/plan/architecture/08-codebase-design.md` §3, §5, §6 y §8 | Costura `recalculo`, deep copy, módulos profundos, frontera frontend/backend y prohibición de fórmulas en el motor. |
| `../thesis-docs/plan/domain/02-data-model.md` §3, §13, §16–§17 | Deep copy, campos derivados, avance JSONB, fórmulas y precisión. |
| `../thesis-docs/plan/design/03-procesos-detalle.md` §F, §G y §J/D-09/D-10 | P-33/P-34, copia de versiones, 1:1 y confirmación de pérdida una vez canonizada. |
| `../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P31-01…04, TC-P32-01, TC-P33-01…10 y TC-P34-01…12 | Casos de copia, cobertura, alta, conflicto, sincronía y períodos. Se amplía la evidencia de identidad sin cambiar el motor. |
| `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` | Orden I-08/I-09/I-10 y gate de pruebas. |
| `src/main/resources/db/migration/V001__baseline.sql` | Patrón `uuidv7()`, función `fn_assert_public_id_immutable()`, PK/FK y DDL baseline. No se edita. |
| `src/main/resources/db/migration/V008__capitulo_rubro_public_id.sql` | Patrón real de migración aditiva y triggers por tabla. No se edita. |
| `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | Método `copiarCronogramaYActividad`, SQL nativo y remapeo por `rubro.codigo`. |
| `src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java` | `findRubrosCubiertosPorCronograma`, join interno por IDs `BIGINT`. |
| `src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java` | Adaptador que hoy devuelve `VersionSnapshot(..., null)`; no autoriza cambiar `motor/`. |
| `src/main/java/ec/uce/propuestas/motor/CronogramaSnapshot.java` y `ActividadSnapshot.java` | Contrato puro actual del snapshot; se conserva el límite, sujeto a un plan/guard si el canon exige otra forma. |
| `src/main/java/ec/uce/propuestas/presupuesto/entity/Capitulo.java` y `Rubro.java` | Convención JPA vigente para `publicId`, `PanacheEntityBase`, `IDENTITY` y FK `Long`. |

## Estado inicial

- V008 es la última migración versionada en el árbol actual. Añade
  `public_id` a `capitulo` y `rubro` con `DEFAULT uuidv7()` y el trigger común;
  no toca `cronograma` ni `actividad`.
- V001 ya declara `cronograma` y `actividad` con PK/FK `BIGINT`, la unicidad de
  `presupuesto_id` y `rubro_id`, el mapa JSONB de avances y la función común de
  inmutabilidad. También conserva `cronograma_actividad`, seam histórico inerte
  que I-08 no usa. V001–V008 son historia aplicada: no se reescriben.
- El backend no contiene `Cronograma.java`, `Actividad.java`,
  `CronogramaRepository.java` ni `ActividadRepository.java`. Los únicos tipos
  de cronograma en Java son los snapshots puros del paquete `motor`.
- `VersionadoService.copiarCronogramaYActividad` ya inserta con listas de
  columnas explícitas: copia configuración, `peso_ponderado` y
  `avance_por_periodo`; remapea `rubro_id` por código y omite toda identidad
  pública. El nuevo mapeo JPA no debe obligar a convertir ese método en un
  CRUD ni en una copia por entidades.
- `PresupuestoRepository.findRubrosCubiertosPorCronograma` selecciona
  `a.rubro_id` mediante `actividad → cronograma`; su contrato es un conjunto de
  `Long` interno usado por P-32. No se reemplaza la consulta por UUID.
- `VersionSnapshotBuilder.build(Long)` crea `ParametrosCalculo`, capítulos y
  rubros, pero entrega cronograma `null`. El gap se reporta para Plan 029/otro
  guard; este plan no modifica `motor/` ni duplica la aritmética.
- `Capitulo` y `Rubro` muestran la convención vigente: `public UUID publicId`
  generado por la BD (`@Generated(event = INSERT)`, `insertable=false`,
  `updatable=false`) y campos FK `Long`. La nueva entidad debe seguirla salvo
  que Plan 026 apruebe una diferencia explícita.

## Alcance y fuera de alcance

### Alcance futuro de 027

- Una migración nueva, aditiva y posterior a V008, con `public_id` para ambas
  tablas, defaults UUIDv7 y triggers de inmutabilidad reutilizando la función de
  V001; `presupuesto_fingerprint_revisado` nullable con CHECK SHA-256 lowercase;
  y reemplazo focal del CHECK de períodos por los límites 520/120.
- Entidades `Cronograma` y `Actividad`, repositorios separados y el mapeo
  JSONB/decimal necesario para que `hibernate-orm` valide el esquema real.
- Métodos de repositorio para resolución por UUID público, por presupuesto o
  cronograma interno y por owner; la forma final de los nombres públicos se
  hereda de 026.
- El adaptador mínimo de la frontera profunda, si hace falta para que
  `recalculo`/versionado consuman las entidades sin filtrar JPA al motor.
- Pruebas de migración, mapeo, identidad, inmutabilidad, constraints,
  ownership, deep copy y regresiones del query de cobertura.

### Fuera de alcance

- Cualquier edición de V001–V008, cambio de PK/FK, reemplazo de `BIGINT` por
  UUID, migración destructiva, reseed de negocio o limpieza de datos.
- CRUD completo, recursos REST, DTOs funcionales, alta/configuración, lectura
  completa, importación de rubros o edición de avances; pertenecen a 028/029 y
  heredan los nombres de 026.
- Cambiar `Motor`, `Consolidador`, `CronogramaSnapshot` o
  `ActividadSnapshot` por una decisión no autorizada. El motor permanece puro.
- CPM, dependencias, lag, ruta crítica, auto-programación, calendarios,
  fechas, hitos y cualquier esquema del proyecto `ingepresupuestos`.
- Segmentos editables, distribución uniforme, reducción de períodos o
  operaciones de Gantt; son contrato 026 y ejecución posterior.
- Exportación SERCOP, MSPDI XML, `.mpp`, frontend, Bruno, cambios de
  `thesis-docs`, commits, publicación o migraciones reales en esta sesión.

## Archivos posibles de una ejecución autorizada

> Los nombres públicos quedaron congelados por Plan 026. La lista mantiene
> superficies condicionales para no ampliar el cambio durante la implementación.

| Acción posible | Archivo | Condición |
|---|---|---|
| Crear | `src/main/resources/db/migration/V<next>__cronograma_persistencia.sql` | `<next>` se determina después de V008; añade identidades, fingerprint y CHECKs canónicos. El nombre puede variar sin reducir el alcance. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/entity/Cronograma.java` | Entidad cuyo nombre final fue cerrado por 026. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/entity/Actividad.java` | Entidad cuyo nombre final fue cerrado por 026. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/repository/CronogramaRepository.java` | Resolución 1:1, UUID y owner sin exponer IDs internos. |
| Crear | `src/main/java/ec/uce/propuestas/cronograma/repository/ActividadRepository.java` | Resolución por cronograma/UUID, cobertura y lectura interna ordenada. |
| Modificar | `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | Incluir el fingerprint en la copia explícita, mantener omisión de `public_id`, marcadores y remapeo BIGINT; no usar `SELECT *`. |
| Modificar condicionalmente | `src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java` | Preferencia: **no modificar**; solo adaptar tipos internos si no cambia `findRubrosCubiertosPorCronograma`. |
| Modificar condicionalmente | `src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java` | Solo si el alcance aprobado de 027 lo exige para leer persistencia; no tocar `src/main/java/ec/uce/propuestas/motor/`. |
| Crear/modificar | `src/test/java/ec/uce/propuestas/schema/<siguiente>SchemaIT.java` | El nombre final depende de la numeración de migración y del patrón de suites existente. |
| Crear/modificar | `src/test/java/ec/uce/propuestas/cronograma/<contratos-de-persistencia>.java` | Tests focales, no CRUD completo. |
| Modificar | `src/test/java/ec/uce/propuestas/identifier/PublicIdPersistenceTest.java` | Solo si el catálogo común de identidad es el lugar natural; agregar cronograma/actividad sin romper los casos de capitulo/rubro. |
| Modificar | `src/test/java/ec/uce/propuestas/presupuesto/resource/VersionadoResourceIT.java` | Reforzar UUIDs frescos y aislamiento de copia; no cambiar rutas fuera del canon 026. |
| Modificar condicionalmente | `src/test/java/ec/uce/propuestas/presupuesto/resource/PresupuestoValidacionResourceIT.java` | Solo para preservar el caso P-32 de cobertura; no convertirlo en CRUD de cronograma. |

## Datos, identidad y precisión

### DDL y migración aditiva

La migración futura debe seguir estas invariantes, sin escribir el SQL aquí ni
editar migraciones aplicadas:

- Añadir `public_id UUID NOT NULL UNIQUE DEFAULT uuidv7()` a `cronograma` y
  `actividad`.
- Añadir `cronograma.presupuesto_fingerprint_revisado CHAR(64) NULL` y CHECK
  `NULL OR ~ '^[0-9a-f]{64}$'`; las filas históricas quedan NULL/stale hasta revisión.
- Reemplazar el CHECK histórico de períodos por `SEMANA 1..520 | MES 1..120`,
  verificando primero que los datos existentes lo satisfacen; si no, STOP.
- Crear por tabla el trigger `BEFORE UPDATE OF public_id` que reutiliza
  `fn_assert_public_id_immutable()` de V001. No crear una función paralela ni
  una generación Java.
- El default debe poblar filas existentes con UUIDv7 fresco al aplicar la
  migración y filas nuevas al insertarlas. Verificar versión y variante de cada
  UUID, no solo que sea parseable.
- No cambiar `id`, `presupuesto_id`, `cronograma_id`, `rubro_id`, FKs,
  `ON DELETE`, unicidades ni JSONB. No tocar ni empezar a usar la tabla inerte
  `cronograma_actividad`.
- No agregar `public_id` a las tablas relacionadas como condición de este plan;
  `presupuesto` y `rubro` ya tienen identidad pública vigente.
- No agregar una segunda constraint para simular 1:1: la unicidad existente es
  la autoridad. `cronograma_id` puede repetirse en varias actividades; solo
  `rubro_id` es único en `actividad`.
- La migración se valida en una base con datos existentes y en una base vacía;
  ambos casos deben producir identidades nuevas y no colisiones.

### Entidades y mapeo

Las entidades candidatas deben seguir la convención comprobada en `Capitulo` y
`Rubro`:

- `@Entity` y `@Table(name = "cronograma"|"actividad")`.
- `PanacheEntityBase`, `@Id` y
  `@GeneratedValue(strategy = GenerationType.IDENTITY)` para el PK `Long`.
- `public UUID publicId` con `@Generated(event = EventType.INSERT)` y
  `@Column(name = "public_id", insertable = false, updatable = false)`.
- FKs como `Long` (`presupuestoId`, `cronogramaId`, `rubroId`) y sin
  `@ManyToOne`/cascadas JPA nuevas si el patrón del módulo no las usa; el
  comportamiento de borrado sigue en PostgreSQL.
- `unidadTiempo`, `numeroPeriodos`, `totalGeneralRevisado`, `fechaRevision`,
  `pesoPonderado` y `avancePorPeriodo` mapean exactamente las columnas y escalas
  del DDL que 026 haya canonizado. No se agregan columnas derivadas como
  `item`, `descripcion`, `precioTotal` o `avanceAcumulado` a `actividad`.
- Para JSONB se usa el patrón vigente de Hibernate (`@JdbcTypeCode(SqlTypes.JSON)`
  o la forma exacta que el código aprobado determine), con una representación
  de persistencia que conserve decimal strings/escala y permita validar las
  claves 1-based. No se convierte el dominio en `Map<Integer, Double>`.
- Los setters/servicios no permiten cambiar `publicId`; cualquier intento de
  update debe ser rechazado por la BD y cubierto por prueba.

### Precisión y dominio persistido

- Dinero: `NUMERIC(14,6)` y `BigDecimal`; porcentajes y pesos con la escala que
  fija 026 (`peso_ponderado` es `NUMERIC(7,4)` en el DDL actual).
- `numeroPeriodos` corresponde a `SMALLINT`; la validación de rango pertenece al
  contrato 026/028, no se duplica como una regla distinta en la entidad.
- El mapa se persiste con claves string de períodos 1..n y valores decimales de
  escala 4. La validación de suma, segmentos, residual y exportabilidad no se
  implementa aquí.
- `totalGeneralRevisado` y `fechaRevision` conservan `NULL` cuando aún no hay
  revisión, según la semántica aprobada en 026. No se reemplaza `NULL` por cero
  como atajo.
- `item`, `descripcion`, `precioTotal` y `avanceAcumulado` son derivados o de
  consulta. El repository puede obtenerlos mediante join/proyección, pero no
  inventa columnas espejo que puedan desincronizarse.

## Contrato REST/DTO heredado

027 no habilita CRUD REST. Solo fija las obligaciones que las entidades y
repositorios deben permitir a los planes siguientes:

- Los paths y campos públicos usan exactamente los nombres aprobados por 026;
  cualquier `id`, `presupuestoId` o `rubroId` de cronograma/actividad es UUIDv7
  serializado como string.
- `GET /presupuestos/{id}/cronograma` y
  `POST /presupuestos/{id}/cronograma` resolverán el presupuesto público; la
  ausencia o ajenidad será 404 y la constraint 1:1 será 409.
- `PUT /cronogramas/{id}/configuracion`, `PATCH /cronogramas/{id}/actividades/{aid}` y
  `POST /cronogramas/{id}/revisado` resolverán ambos UUID públicos con
  ownership; no reciben `Long` en DTOs.
- La proyección agregada de Gantt, valorizado y curva S se leerá únicamente mediante
  `GET /cronogramas/{id}/vistas`, fuera del alcance de persistencia de este plan.
- El mapeo interno UUID→`Long` ocurre en repository/service, después del parse
  de frontera y del ownership. El motor y las queries nativas continúan con
  `Long`.
- DTOs, campos de estado, `segmentos`, operaciones de Gantt y el cuerpo de
  reducción no se reescriben aquí: si un nombre de este plan contradice 026,
  se detiene antes de codificar.

## Secuencia ejecutable futura

### Paso 0 — preflight y baseline

1. Leer Plan 026, verificar su aprobación y registrar el contrato exacto.
2. Resolver el root y confirmar que el árbol de migraciones termina en V008;
   contrastar archivos con `flyway_schema_history` en la base de test.
3. Capturar el esquema/constraints de `cronograma` y `actividad` antes del
   cambio, sin modificar datos.
4. Ejecutar los tests de regresión de identidad, schema, presupuesto y validación
   para guardar la línea base literal.

### Paso 1 — RED de migración y constraints

Escribir primero un test de esquema aislado que espere:

- `cronograma.public_id` y `actividad.public_id` UUID, NOT NULL, UNIQUE y con
  default UUIDv7;
- `presupuesto_fingerprint_revisado` nullable + CHECK SHA-256 lowercase;
- CHECK de períodos dependiente de unidad (520/120);
- trigger de inmutabilidad por tabla;
- PK/FK BIGINT y unicidades 1:1 intactas;
- `cronograma_actividad` intacta e inerte;
- una fila existente y una fila nueva con UUIDv7 distinto.

Ejecutar el test contra V001…V008 antes de crear la migración nueva. El RED debe
ser observado y guardado literalmente: faltan identidades, fingerprint, CHECKs y
triggers; no se simula el resultado con mocks ni con una base ya migrada.

### Paso 2 — GREEN de migración

1. Elegir el número siguiente real y crear una única migración aditiva con nombre
   conforme a la convención, sin editar V001–V008.
2. Añadir identidades/defaults/triggers, fingerprint y CHECKs; ejecutar sobre base
   vacía y base con filas existentes válidas.
3. Verificar UUIDv7 únicos/inmutables, fingerprint/límites y que `BIGINT`, FKs,
   unicidades, mapas y seam histórico permanecen semánticamente equivalentes.
4. Repetir el test de esquema y registrar GREEN.

No se agrega backfill manual con UUIDs inventados, no se reutilizan IDs de otra
entidad y no se modifica el nombre de la función de V001.

### Paso 3 — RED de mapeo JPA

Crear primero el test de mapeo/reflexión que compruebe, para cada entidad:

- `@Entity`/`@Table` correctos;
- PK `Long` con `IDENTITY`;
- `publicId` de tipo `UUID`, generado por INSERT, no insertable/no actualizable;
- FKs `Long` y columnas de negocio con nombres y escalas del DDL;
- JSONB no representado como `double`/`float`.

Ejecutar `hibernate`/test focal antes de las entidades. El fallo esperado se
captura como RED (clases ausentes o mapeo inexistente).

### Paso 4 — GREEN de entidades y repositorios

1. Crear `Cronograma` y `Actividad` siguiendo el patrón de las entidades
   existentes, sin asociaciones JPA que cambien cascadas.
2. Crear repositories separados. Como responsabilidades mínimas, sujetos a
   nombres de 026:
   - cronograma: resolver por `publicId`, por `presupuestoId`, comprobar 1:1 y
     buscar con owner `presupuesto → proyecto → usuario`;
   - actividad: resolver por `publicId` dentro de un cronograma, listar por
     `cronogramaId` en orden canónico y comprobar owner atravesando cronograma y
     presupuesto;
   - ambas: permitir consultas internas por `Long` para `recalculo`, copia y
     `findRubrosCubiertosPorCronograma` sin hacer públicos esos IDs.
3. Mapear JSONB y `BigDecimal` con las anotaciones y escalas exactas del contrato.
4. Ejecutar el test focal de mapeo y un arranque con `generation: validate`.

### Paso 5 — RED/GREEN de identidad e inmutabilidad

Primero agregar casos que fallen contra una entidad sin el mapping completo:

- UUID de fila existente es v7 y no coincide con PK ni con otra fila;
- insert de dos cronogramas para un presupuesto falla por la unicidad 1:1;
- insert de dos actividades para un rubro falla por la unicidad 1:1;
- actividad con cronograma inexistente falla por FK;
- intento de cambiar `public_id` falla por trigger;
- nueva fila obtiene un UUID distinto del origen.

Después de GREEN, repetir en una transacción real y comprobar que el rollback no
consume una identidad de forma que se confunda con la identidad de negocio (los
huecos de identity no son un defecto; la unicidad y frescura sí son obligatorias).

### Paso 6 — RED/GREEN de ownership y límite profundo

Sin crear endpoints CRUD completos, probar los métodos de resolución:

1. UUIDv7 del owner devuelve la entidad.
2. UUIDv7 inexistente devuelve `Optional.empty()`/la excepción interna
   prevista, que la futura frontera traducirá a 404.
3. UUIDv7 existente en otro proyecto devuelve la misma ausencia, sin filtrar
   datos ni IDs.
4. Un UUIDv4/malformado se rechaza antes del repository en el adaptador de
   frontera; el repository no acepta `String` como sustituto de parseo.
5. El adaptador interno entrega `Long` solo después de resolver ownership y el
   snapshot resultante no expone JPA al motor.

### Paso 7 — RED/GREEN de deep copy

Reforzar la prueba de copia de versión antes de ajustar el seam:

- origen con cronograma y varias actividades, incluyendo mapa de períodos
  no consecutivos, peso y configuración revisada;
- `VersionadoService` copia dentro de la misma transacción;
- nuevo cronograma tiene `presupuesto_id` nuevo y `public_id` nuevo;
- cada actividad tiene `public_id` nuevo, `cronograma_id` nuevo y `rubro_id` del
  rubro copiado, nunca el `rubro_id` de origen;
- mapas, pesos, unidad, número y revisión se conservan según 026;
- origen y copia pueden modificarse/consultarse sin afectar la otra;
- `findRubrosCubiertosPorCronograma` devuelve el conjunto correcto para cada
  versión;
- origen sin cronograma no crea un hijo accidental.

Si el SQL actual falla por una columna nueva, ajustar solo la lista explícita
para seguir omitiendo `public_id`; no sustituir el deep copy por `SELECT *`, no
copiar UUIDs del origen y no crear un nuevo módulo de persistencia dentro de
`motor`.

### Paso 8 — TRIANGULACIÓN

- Ejecutar el test de esquema después de aplicar la migración en base vacía y
  poblada.
- Comparar las anotaciones de las nuevas entidades con `Capitulo`/`Rubro` y con
  el DDL, incluyendo precisión y JSONB.
- Comparar la huella de deep copy antes/después y verificar cardinalidades 1:1.
- Verificar que P-32 sigue usando `findRubrosCubiertosPorCronograma` y que la
  query continúa seleccionando `Long` internos.
- Verificar que no aparece ninguna referencia a `public_id` dentro de fórmulas
  del motor ni ningún `double`/`float` en la ruta de persistencia de avance.
- Ejecutar casos de owner-to-404 y roles con los adaptadores previstos por 026,
  sin adelantar recursos de 028.

### Paso 9 — REFACTOR

- Consolidar el mapeo UUIDv7 en una sola convención igual a V008; no crear un
  parser alternativo.
- Mantener repositorios finos: consultas de datos allí, reglas de negocio en el
  futuro service de cronograma.
- Eliminar helpers duplicados de conversión UUID/Long, conservar la query nativa
  de cobertura y dejar comentarios que expliquen el límite profundo.
- No cambiar nombres aprobados, fórmulas, tests existentes ni expected values.

## Catálogo concreto de casos

| Caso | Entrada | Resultado verificable |
|---|---|---|
| M-027-01 migración poblada | Base V008 con cronograma/actividad válidos. | `public_id` poblados; fingerprint NULL; CHECKs nuevos activos; PK/FK/mapas intactos. |
| M-027-02 migración vacía | Base limpia hasta V008. | Inserts posteriores reciben UUIDv7; fingerprint válido/null; límites 520/120 se aplican. |
| M-027-03 trigger cronograma | UPDATE del `public_id` de una fila. | Error de inmutabilidad; valor anterior permanece. |
| M-027-04 trigger actividad | UPDATE del `public_id` de una fila. | Error de inmutabilidad; valor anterior permanece. |
| M-027-05 1:1 presupuesto | Dos cronogramas para el mismo presupuesto. | Violación de `UNIQUE (presupuesto_id)`; no se relaja con repository. |
| M-027-06 1:1 rubro | Dos actividades para el mismo rubro. | Violación de `UNIQUE (rubro_id)`; no se crea huérfana. |
| M-027-07 FK composición | Actividad con `cronograma_id` inexistente o borrado. | Violación/cascade según DDL; no se reemplaza por validación solo Java. |
| M-027-08 mapeo | Arranque con `hibernate-orm.database.generation: validate`. | Sin alteraciones implícitas; tipos y columnas coinciden. |
| M-027-09 UUID correcto | UUIDv7 de cronograma/actividad del caller. | Repository obtiene la fila y conserva el `Long` solo internamente. |
| M-027-10 UUID v4/malformado | Entrada de frontera de versión incorrecta o texto inválido. | 400 `validacion` antes de BD; no se intenta `UUID.fromString` sin validar v7. |
| M-027-11 UUID ajeno | UUIDv7 válido de otro owner/proyecto. | 404 `no-encontrado`; mismo comportamiento observable que inexistente. |
| M-027-12 ausencia | UUIDv7 no existente. | 404 en la futura frontera; repository vacío. |
| M-027-13 roles | `USUARIO` y `SUPER_ADMIN` propios; caller anónimo/rol no permitido. | Solo los dos roles canónicos; ownership se aplica igual y no aparece un tercer rol. |
| M-027-14 JSONB válido | Mapa `{"1":"2.5000","3":"2.5000"}` dentro de n. | Persistencia/lectura conserva claves no consecutivas y decimales; no se ordena como string. |
| M-027-15 JSONB inválido | Clave 0, n+1, clave no numérica o valor no decimal/negativo. | Rechazo según 026/028; este plan no normaliza silenciosamente. |
| M-027-16 copia fresca | Origen con configuración, pesos, mapa y fecha de revisión. | Cronograma/actividades copiados con UUIDs nuevos y FKs remapeados; mapa conservado. |
| M-027-17 copia aislada | Mutar copia y releer origen. | Origen intacto; no hay identidad compartida. |
| M-027-18 copia sin cronograma | Versión origen sin fila de cronograma. | No se inserta cronograma/actividad hijo por accidente. |
| M-027-19 cobertura P-32 | Dos versiones con actividades distintas. | `findRubrosCubiertosPorCronograma` distingue cada `presupuesto_id` usando IDs internos. |
| M-027-20 precisión | Peso/avance y totales revisados con escala del DDL. | `BigDecimal`/JSONB sin `double`/`float`; no se agrega redondeo de negocio. |
| M-027-21 fingerprint | NULL, hash lowercase válido y variantes inválidas. | NULL/válido aceptados; longitud, mayúscula o no-hex rechazados por CHECK. |
| M-027-22 límites por unidad | SEMANA 520/521 y MES 120/121. | bordes válidos aceptados; excedentes rechazados por CHECK. |
| M-027-23 seam histórico | `cronograma_actividad` poblada o vacía. | migración no altera ni usa la tabla; agregado canónico opera solo con `actividad`. |

Los casos P-31, P-32, P-33 y P-34 existentes se conservan y se cruzan con
estos casos de persistencia. Los nombres finales de DTO/endpoint y la forma de
`segmentos` se toman de Plan 026, nunca de este inventario provisional.

## Owner-to-404 y roles

- Repository de cronograma: `publicId → presupuestoId → proyectoId → usuarioId`
  para resolver ownership. Una fila ajena no se retorna.
- Repository de actividad: `publicId → cronogramaId → presupuestoId → proyectoId
  → usuarioId`; además puede exigir que el cronograma del path sea el mismo
  agregado para evitar cross-cronograma.
- El futuro resource parsea `UuidV7` en la frontera; 400 para malformado/no-v7,
  404 para inexistente/ajeno. El servicio no construye mensajes con `Long`.
- `@RolesAllowed({"USUARIO", "SUPER_ADMIN"})` es la única autorización
  funcional. No se crea otro rol ni una ruta administrativa para cronograma.
- La constraint 1:1 que concurre es 409 con el código que 026 haya canonizado;
  no se convierte una carrera en 200 fingiendo idempotencia.
- Fallos de ownership, FK interno no visible o fila ausente no filtran si el
  caller pudo adivinar el UUID.

## STOP conditions

Detener y devolver evidencia al autor si ocurre una de estas condiciones:

- **STOP-027-GATE:** Plan 026 no está aprobado o cambian sus nombres/shapes
  durante esta ejecución.
- **STOP-027-NUMBER:** no se puede determinar con seguridad la versión Flyway
  posterior a V008; no se crea V009 por intuición ni se edita V008.
- **STOP-027-UUID:** la función `uuidv7()` no existe, produce otra versión,
  devuelve colisiones o no puede poblar filas existentes; no se genera UUID en
  Java como sustituto.
- **STOP-027-IMMUTABLE:** el trigger común no puede reutilizarse o Hibernate
  intenta actualizar `public_id`; detener antes de aceptar el mapeo.
- **STOP-027-SCHEMA:** para mapear las entidades es necesario cambiar PK/FK,
  `ON DELETE`, `UNIQUE (presupuesto_id)`, `UNIQUE (rubro_id)` o una migración
  aplicada.
- **STOP-027-SQL:** el deep copy requiere `SELECT *`, copia el UUID de origen,
  pierde el remapeo de `rubro_id` o rompe `findRubrosCubiertosPorCronograma`.
- **STOP-027-OWNER:** una consulta de UUID público devuelve una fila ajena,
  distingue ajeno de inexistente o expone el `BIGINT` en DTO/log/error.
- **STOP-027-ORM:** `generation: validate` falla por una discrepancia que exige
  alterar el DDL histórico, añadir asociaciones JPA con cascada distinta o
  tocar `motor/`.
- **STOP-027-PRECISION:** JSONB se mapea con `double`/`float`, se redondea avance
  fuera de la política 026 o se cambia la escala monetaria/porcentual sin gate.
- **STOP-027-SCOPE:** aparece CRUD, DTO, Bruno, frontend, export, CPM,
  segmentos editables o una migración real fuera de la lista candidata.
- **STOP-027-MOTOR:** una solución exige cambiar `Motor`, `Consolidador`,
  `CronogramaSnapshot` o `ActividadSnapshot`; abrir guard/plan específico.
- **STOP-027-COPY:** el deep copy no puede obtener UUIDs nuevos para cronograma y
  actividad, o no puede conservar los mapas/configuración sin compartir filas.

## Verificaciones focales, regresiones y calidad

Las órdenes siguientes se ejecutan en una futura sesión autorizada. No se
corrieron al redactar este plan y no deben llenarse con resultados inventados.

### Migración, esquema e identidad

```bash
./gradlew test --tests 'ec.uce.propuestas.schema.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.identifier.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' --console=plain
```

El test de schema debe verificar columnas, defaults, triggers, tipos, PK/FK y
unicidades contra una base V008 poblada y una base limpia. El test de identidad
debe cubrir mapeo `UUIDv7`, inmutabilidad y no fuga de `Long`.

### Copy, cobertura y recalculo

```bash
./gradlew test --tests 'ec.uce.propuestas.presupuesto.resource.VersionadoResourceIT' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.resource.PresupuestoValidacionResourceIT' --console=plain
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
```

El motor no se modifica. Si una regresión muestra que el builder aún pasa
cronograma `null`, se registra como limitación para 029/otro plan y no se tapa
con una fixture distinta.

### Suite, Spotless, build, diff y Graphify

```bash
./gradlew test --console=plain
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

### Conteos reales desde XML

Usar los reportes generados por Gradle y reportar los atributos observados, no
un total presupuestado por el plan:

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

Para el reporte se conserva además la salida de cada patrón focal y se
identifican los XML correspondientes. Ninguna cifra de este plan es una
predicción de la suite final.

## Criterios de aceptación

- [x] El estado es `DONE`; no se ejecutó CRUD funcional y Plan 028 quedó habilitado.
- [x] El gate 026 y los nombres canónicos fueron respetados.
- [x] V009 es aditiva, determinada tras confirmar Flyway v008; V001–V008 están intactas.
- [x] Ambos `public_id` son UUIDv7 NOT NULL UNIQUE con default y trigger compartido.
- [x] Fingerprint nullable/lowercase y límites SEMANA 520/MES 120 están activos.
- [x] `cronograma_actividad` permanece inerte e intacta.
- [x] PK/FK `BIGINT` y las dos unicidades 1:1 permanecen.
- [x] ORM valida y el JSONB/BigDecimal no usa `double`/`float`.
- [x] Repositories resuelven UUID con owner y scope del cronograma anidado.
- [x] Deep copy omite `public_id`, remapea FKs y conserva fingerprint/mapa.
- [x] `findRubrosCubiertosPorCronograma` permanece sobre IDs internos.
- [x] Tests de migración poblada/vacía, mapping, identidad, constraints, owner y copia pasan.
- [x] Suite, Spotless, build, diff y Graphify tienen evidencia literal.
- [x] Conteos extraídos de 48 XML: 465 tests, 2 fallos aceptados, 0 errores, 1 omitido.
- [x] El commit unitario fue autorizado explícitamente por el usuario.

## Plantilla de evidencia — sin resultados inventados

```text
Plan: 027
Estado actual: DONE — gate final verificado; Plan 028 habilitado
Fecha/hora: 05-09-2026 (ejecución y commit autorizados por el usuario)
Ejecutor/revisor: parent-orchestrator / Gentle-AI writer

Gate 026 aprobado por / referencia: commit backend 078eb11 / plan 026
Número Flyway real posterior a V008: V009
Nombre de migración: `V009__cronograma_persistencia.sql`
Base de prueba: PostgreSQL 18 (Quarkus Dev Services) — V008 con seed V004 (poblada)

RED migración observado (salida literal):
- Compile-time RED: 75 errores de compilación en
  `CronogramaJpaIT`/`ActividadJpaIT`/`PublicIdPersistenceTest`
  (`cannot find symbol: class Cronograma / Actividad /
  CronogramaRepository / ActividadRepository`).
- La BD previa a V009 no tiene `cronograma.public_id` /
  `actividad.public_id`, lo que el validador Hibernate confirma.

GREEN focal observado: ver comando único y conteo XML de 65/65 más abajo.

RED mapeo/identidad observado:
- `SchemaManagementException: wrong column type encountered in column
  [presupuesto_fingerprint_revisado] in table [cronograma]; found [bpchar
  (Types#CHAR)], but expecting [varchar(64) (Types#VARCHAR)]`.
- Solucionado con `@JdbcTypeCode(SqlTypes.CHAR) + columnDefinition = "char(64)"`
  en `Cronograma.presupuestoFingerprintRevisado`.

GREEN mapeo/identidad observado:
- `hibernate-orm.database.generation=validate` no detecta diferencias.
- Las entidades `Cronograma` y `Actividad` pasan validación
  contra el esquema físico V001..V009.

TRIANGULACIÓN/REFACTOR:
- Los 13 tests previos limpian y crean su propio grafo mínimo con sufijo UUID;
  no dependen de filas dejadas por otra clase.
- El replay test #14 (único test que ejercita el camino pre-V009 → V009 con
  población real) usa un esquema temporal PostgreSQL dedicado
  (`v009_replay_<nanoTime>`) que se crea al inicio y se elimina en `finally` con
  `DROP SCHEMA ... CASCADE`; migra ese esquema hasta V008, conserva la fila inerte
  sembrada por V004 e inserta cronograma/actividad antes de V009; luego captura IDs
  y el `avance_por_periodo` JSONB y aplica V009 sobre esa población,
  y vuelve a `public` antes de cerrar cada conexión (vía `resetSchemaQuietly`)
  para no contaminar el pool ni a los demás tests.
- `bpchar` vs `character` (PostgreSQL ≥ 16): el assert acepta ambos nombres.
- Fingerprint CHAR(64): el CHECK rechaza longitudes distintas de 64 (CHAR padding)
  y mayúsculas, conservando lowercase exact semantics.

Archivos de implementación realmente tocados tras autorización:
- src/main/resources/db/migration/V009__cronograma_persistencia.sql (nuevo)
- src/main/java/ec/uce/propuestas/cronograma/entity/Cronograma.java (nuevo)
- src/main/java/ec/uce/propuestas/cronograma/entity/Actividad.java (nuevo)
- src/main/java/ec/uce/propuestas/cronograma/repository/CronogramaRepository.java (nuevo)
- src/main/java/ec/uce/propuestas/cronograma/repository/ActividadRepository.java (nuevo)
- src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java (SQL del deep copy preserva fingerprint y omite public_id)
- src/test/java/ec/uce/propuestas/schema/V009SchemaIT.java (nuevo, 14 tests; el #14 es `v009_applies_over_populated_v008_state_assigning_distinct_uuidv7_public_ids`)
- src/test/java/ec/uce/propuestas/cronograma/CronogramaJpaIT.java (nuevo, 5 tests)
- src/test/java/ec/uce/propuestas/cronograma/ActividadJpaIT.java (nuevo, 5 tests)
- src/test/java/ec/uce/propuestas/identifier/PublicIdPersistenceTest.java (extendido para cronograma/actividad)
- src/test/java/ec/uce/propuestas/presupuesto/resource/VersionadoResourceIT.java (TC_P31_27 fingerprint + UUID fresh)
- plans/027-identidad-persistencia-cronograma.md (status DONE + evidencia)
- docs/modulos/06-cronograma/00.md (índice refleja 027 DONE / 028 habilitado)

Archivos deliberadamente no tocados:
- V001–V008: intactos (verificado por `./gradlew test --tests 'ec.uce.propuestas.schema.SchemaBaselineIT'`).
- motor/: sin cambios.
- CRUD/DTO/Bruno/frontend: sin cambios.
- `PresupuestoRepository`: sin cambios (`findRubrosCubiertosPorCronograma` sigue usando BIGINT).
- `recalculo`/`VersionSnapshotBuilder`: sin cambios (gap conocido para 029).
- `PresupuestoRepository.java` (no fue necesario crear `PresupuestoRepository` nuevo — ya existía).

Evidencia de esquema:
- public_id cronograma: UUID NOT NULL UNIQUE DEFAULT uuidv7() — verificado
  en `flyway_schema_history` (success=009) y por los 14 tests de V009SchemaIT (incluido el replay test #14).
- public_id actividad: mismo contrato.
- defaults UUIDv7 sobre filas existentes: evidencia empírica del replay test
  `v009_applies_over_populated_v008_state_assigning_distinct_uuidv7_public_ids`,
  que migra un esquema temporal únicamente a través de V008, inserta un
  cronograma + actividad (cronograma `SEMANA 12` y actividad con
  `avance_por_periodo = {"1":"0.5000","3":"0.5000"}` no consecutivo), aplica V009
  sobre esa población y comprueba que cada fila de cronograma/actividad recibe
  un `public_id` UUIDv7 distinto, `presupuesto_fingerprint_revisado` permanece
  NULL en todas las filas pre-existentes, los PK/FKs BIGINT y el JSONB del mapa
  de avance no cambian, y la tabla inerte `cronograma_actividad` mantiene su
  conteo y la constraint `UNIQUE(presupuesto_id)` intactas. Los 13 tests previos
  NO cubrían este camino: truncaban en `@BeforeEach` y solo insertaban filas
  nuevas, por lo que la afirmación anterior sobre el DEFAULT rellenando filas
  V001–V007 carecía de evidencia observada.
- triggers: `trg_public_id_immutable` (BEFORE UPDATE OF public_id) en
  `cronograma` y `actividad`, ambos usando la función compartida
  `fn_assert_public_id_immutable()` de V001 §5.
- fingerprint + CHECK: CHAR(64) NULL con regex anclada
  `^[0-9a-f]{64}$`; acepta lowercase 64-hex y NULL; rechaza uppercase,
  longitudes distintas y no-hex (cubierto por `cronograma_fingerprint_check_accepts_lowercase_sha256_and_rejects_invalid_forms`).
- límites SEMANA/MES: CHECK `(SEMANA 1..520) | (MES 1..120)` reemplaza el
  histórico `numero_periodos > 0`; bordes aceptados, excedentes rechazados.
- seam `cronograma_actividad` intacta: BIGINT PK IDENTITY, UNIQUE(presupuesto_id, rubro_id)
  preservados.
- PK/FK/UNIQUE preservadas: cronograma.id/rubro_id/cronograma_id BIGINT;
  UNIQUE(cronograma.presupuesto_id), UNIQUE(actividad.rubro_id),
  ON DELETE CASCADE.

Evidencia de copy/ownership:
- UUIDs nuevos: TC_P31_27 verifica que el cronograma/actividad copiados
  reciben UUIDs frescos, distintos del origen, ambos UUIDv7.
- FKs remapeados: TC_P31_01 mantiene la verificación de
  `actividad.rubro_id → rubro copiado` (no el rubro origen).
- origen aislado: TC_P31_27 confirma que el origen conserva su `public_id`
  y `presupuesto_fingerprint_revisado` intactos tras el deep copy.
- owner-to-404: `cronograma_owner_scope_returns_row_for_owner_and_hides_from_foreign`
  + `actividad_owner_scope_returns_row_for_owner_and_hides_from_foreign` +
  `cronograma_repository_resolves_by_presupuesto_and_hides_from_foreign_owner`.
- roles: el contrato canónico (USUARIO + SUPER_ADMIN) sigue vigente — no
  se introdujeron nuevos roles ni rutas administrativas para cronograma.

Comando focal y resultado observado:
- `./gradlew test --tests 'ec.uce.propuestas.schema.V009SchemaIT' --tests
  'ec.uce.propuestas.identifier.PublicIdPersistenceTest' --tests
  'ec.uce.propuestas.cronograma.*' --tests
  'ec.uce.propuestas.presupuesto.resource.VersionadoResourceIT' --tests
  'ec.uce.propuestas.presupuesto.resource.PresupuestoValidacionResourceIT'
  -Dquarkus.http.test-port=0 --console=plain`
  → BUILD SUCCESSFUL; XML: 65 tests, 0 failures, 0 errors, 0 skipped.
- `./gradlew spotlessApply` → BUILD SUCCESSFUL.
- `./gradlew spotlessCheck --console=plain` → BUILD SUCCESSFUL.
- `git diff --check` → salida vacía, exit 0.

Gate final:
- `./gradlew test -Dquarkus.http.test-port=0 --console=plain` → 465 tests,
  2 fallos aceptados (GM-19/GM-20), 0 errores y 1 omitido (GM-24).
- `./gradlew spotlessCheck --console=plain` → BUILD SUCCESSFUL.
- `./gradlew build -x test --console=plain` → BUILD SUCCESSFUL.
- `git diff --check` → limpio.
- `graphify update .` → 3277 nodos, 9677 aristas.
- El primer intento focal sin puerto aleatorio encontró 8081 ocupado; no ejecutó
  casos. La repetición con puerto efímero produjo 65/65.

Conteo XML real de la suite final:
- files=48
- tests=465
- failures=2 (GM-19/GM-20, baseline aceptado)
- errors=0
- skipped=1 (GM-24)

STOP activo (si aplica):
- STOP-027-NUMBER: no aplicable (V009 determinado por inspección del árbol).
- STOP-027-IMMUTABLE: no aplicable (trigger compartido y `@Column(updatable=false)`).
- STOP-027-OWNER: no aplicable (repositorios devuelven `Optional.empty()` para ajeno).
- STOP-027-SCHEMA: no aplicable (PK/FK/UNIQUE preservadas, sin `ALTER` implícito).
- STOP-027-SQL: no aplicable (deep copy omite `public_id`, conserva fingerprint).
- STOP-027-MOTOR: no aplicable (no se tocó `motor/`).
- STOP-027-SCOPE: no aplicable (sin CRUD/DTO/Bruno/frontend/export).

Estado de salida: DONE — Plan 028 habilitado.
Commit: autorizado; se materializa en el commit unitario que contiene este cierre.
```

## Regla de no commit

Este plan no autoriza `git add`, `git commit`, merge, push, publicación ni
cambio de estado a concluido. Si la migración, ORM o copia requieren una
decisión no presente en Plan 026, se detiene y se reporta antes de escribir.
