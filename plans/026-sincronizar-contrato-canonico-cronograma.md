# 026 — Sincronizar el contrato canónico de Cronograma

**Estado:** DONE — 04-09-2026; gate documental cerrado, sin implementación.

**Iteración:** I-08 (configuración y avance), I-09 (vistas y sincronía) e I-10
(exportación).

**Procesos:** P-33, P-34, P-35, P-36 y P-37.

> Ejecutado antes de escribir entidades, migraciones, recursos REST, pruebas de
> integración, Bruno o frontend. El producto es una decisión documental única y
> trazable en `../thesis-docs/`; no se modificó código de aplicación.

## Resultado medible del gate

El gate se cierra únicamente cuando una revisión de los documentos candidatos
puede comprobar, sin interpretar ni completar huecos, que:

1. `Cronograma` y `Actividad` usan UUIDv7 como identidad pública en HTTP/JSON;
   los `BIGINT` de la base siguen siendo internos y no aparecen en ejemplos,
   DTOs, rutas ni respuestas.
2. La relación es inequívoca: una versión de `Presupuesto` tiene cero o un
   `Cronograma`, y cada `Rubro` de la versión tiene cero o una `Actividad` dentro
   del cronograma; el alta inicial crea todas las actividades de los rubros
   existentes.
3. `SEMANA`, `MES`, `numeroPeriodos`, sus límites, la indexación 1-based, la
   representación de mapas y la semántica de reemplazo atómico tienen un solo
   nombre y una sola regla en todos los documentos.
4. Las rutas legacy se evaluaron sin aliases. El contrato activo queda en
   `GET/POST /presupuestos/{id}/cronograma`,
   `PUT /cronogramas/{id}/configuracion`,
   `PATCH /cronogramas/{id}/actividades/{aid}`,
   `GET /cronogramas/{id}/vistas` y
   `POST /cronogramas/{id}/revisado`.
5. El contrato distingue tres conceptos que no se pueden colapsar: distribución
   `BORRADOR`, distribución `COMPLETO` y alerta `desactualizado`. La alerta
   detecta los cambios presupuestarios relevantes para pesos/proyección —incluidos
   cambios compensados que conserven el total general— y no bloquea por sí sola la
   exportación. El gate decidió complementar el total revisado con un fingerprint
   SHA-256 canónico.
6. Las fórmulas y la precisión son ejecutables: por actividad,
   `Σ avances de períodos = peso ponderado` exactamente a escala 4 y
   `desviacion = 0.0000`; el total global requerido para exportar es
   `100.0000`. El algoritmo de cuantización y cualquier residual se documentan,
   no se dejan como “aproximado”.
7. `segmentos` es una proyección derivada de los períodos activos, admite grupos
   no consecutivos y nunca se convierte silenciosamente en una segunda fuente de
   verdad. Si el Gantt es editable, mover y redimensionar tienen operaciones
   semánticas, validaciones y efectos atómicos definidos por el canon.
8. La exportación genérica y el lane de conformidad SERCOP quedan separados. Se
   registra la procedencia, versión y hash de cualquier formato oficial; si no
   existe una fuente oficial verificable, solo se bloquea el lane SERCOP/I-10 y
   no se afirma conformidad. MSPDI XML y el binario propietario `.mpp` se tratan
   como formatos distintos.
9. La coordinación frontend queda explícita: tipos derivados de la fuente
   OpenAPI, períodos 1-based, ninguna fórmula en TypeScript, una clave de
   TanStack Query para el read model y comandos de Gantt alineados con el backend.
10. Los Planes 027–031 citan una forma estable sin reabrir estas decisiones.
    El cierre de este gate desbloquea 027; los demás conservan su dependencia
    secuencial.

## Dependencias y gates de ejecución

| Gate | Comprobación | Salida exigida |
|---|---|---|
| G0 — mapa | Leer `docs/modulos/06-cronograma/00.md` y este plan; verificar que el árbol de planes sigue sin implementación de cronograma. | Lista de fuentes y decisiones vinculantes, sin investigación duplicada. |
| G1 — fuentes | Leer únicamente las secciones exactas de la tabla de fuentes de abajo y contrastarlas con el estado del backend. | Matriz de contradicciones con ubicación y decisión adoptada. |
| G2 — identidad y rutas | Revisar UUIDv7 público, `BIGINT` interno, ownership, roles y las cinco rutas. | Tabla API/DTO/errores sin IDs numéricos ni aliases implícitos. |
| G3 — dominio y precisión | Cerrar períodos, estados, segmentos, fórmulas, escala, residuales y exportabilidad. | Fórmulas reproducibles y casos límite con resultado esperado. |
| G4 — formatos | Buscar la fuente oficial del formato de cronograma SERCOP y separar MSPDI XML de `.mpp`. | Procedencia verificable o STOP solo para SERCOP/I-10. |
| G5 — reconciliación | Actualizar coordinadamente arquitectura DB/API/codebase, dominio, procesos, roadmap y catálogo de pruebas en `../thesis-docs/`. | Diff documental coherente, enlaces válidos y ningún término alternativo activo. |
| G6 — revisión | Revisión humana del ledger y de los diffs en ambos repositorios. | Aprobación explícita del gate; sin ella no inicia 027/028. |

**Precondiciones conocidas:** V008 es la última migración del backend y las tablas
baseline `cronograma`/`actividad` ya existen. La identidad de esas dos tablas se
implementará después, en el plan de persistencia. Los planes posteriores no
pueden usar una forma provisional para “avanzar”.

## Fuentes exactas y autoridad

| Fuente | Sección/uso en este gate |
|---|---|
| `docs/modulos/06-cronograma/00.md` | Mapa ya redactado: invariantes, secuencia 026→028, riesgos, límites, STOP register y comandos de cierre. |
| `../thesis-docs/plan/architecture/06-database-schema.md` | §2.13, §3–§5 y §17: tablas, cardinalidades, FK, `NUMERIC`, JSONB, índices y reglas de identidad. |
| `../thesis-docs/plan/architecture/07-api-contract.md` | §1, §7, §8 y Apéndice B: errores, roles, rutas, DTOs y shapes candidatos. El bloque de Cronograma debe quedar reconciliado, no copiado a ciegas. |
| `../thesis-docs/plan/architecture/08-codebase-design.md` | §3–§6 y §8: costura `recalculo`, deep copy, separación del motor, módulo `cronograma` del frontend y regla de no duplicar fórmulas. |
| `../thesis-docs/plan/domain/02-data-model.md` | §3, §5, §13 y §16–§17: modelo, copia de versión, fórmulas, precisión y campos derivados. |
| `../thesis-docs/plan/design/03-procesos-detalle.md` | §F (P-33…P-36), §G (P-37), §J (decisiones D-01…D-13) y las notas de S-33/S-34/S-35. |
| `../thesis-docs/plan/design/04-export-sercop-spec.md` | §4, §6 y §8: layout adaptable, fuente LICO oficial, MSPDI y CHK-22…CHK-36. |
| `../thesis-docs/plan/quality/02-catalogo-pruebas.md` | TC-P33-01…10, TC-P34-01…12, TC-P35-01…04, TC-P36-01…05, TC-P37-01…12 y CHK-22…CHK-36. |
| `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` | Orden I-08/I-09/I-10, hitos y gate GM previo a exportación. |
| `../thesis-docs/DOCUMENTOS/entrevistas/05/N05_entrevista-cronograma.md` | Aclaraciones de negocio del cronograma; solo se usa como fuente humana si el canon remite a ella y se conserva la trazabilidad. |
| `src/main/resources/db/migration/V001__baseline.sql` | DDL vigente de las tablas y función `fn_assert_public_id_immutable()`. No se edita. |
| `src/main/resources/db/migration/V008__capitulo_rubro_public_id.sql` | Patrón vigente de migración aditiva de UUIDv7 posterior al baseline. No se edita. |
| `src/main/java/ec/uce/propuestas/motor/CronogramaSnapshot.java` y `ActividadSnapshot.java` | Seam de lectura existente; no autoriza ampliar el motor ni convertir snapshots en entidades. |
| `src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java` | Estado del adaptador: actualmente construye `VersionSnapshot(..., null)` para cronograma. Se documenta como gap, no se corrige en este gate. |
| `src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java` | `copiarCronogramaYActividad`: deep copy por SQL nativo, con remapeo de `rubro_id`; la identidad nueva debe generarse por defaults. |
| `src/main/java/ec/uce/propuestas/presupuesto/repository/PresupuestoRepository.java` | `findRubrosCubiertosPorCronograma`: consulta nativa que une `actividad` y `cronograma`; debe seguir usando IDs internos. |
| `../ingepresupuestos/core/cronograma.py` y `../ingepresupuestos/views/cronograma_view.py` | Referencia funcional/UX únicamente: no son autoridad de nombres, arquitectura, esquema, CPM, calendario ni formato. |

## Estado inicial verificado

- El DDL de V001 contiene las tablas de negocio de cronograma y actividad con
  PK/FK `BIGINT`, `UNIQUE (cronograma.presupuesto_id)` y
  `UNIQUE (actividad.rubro_id)`. V008 solo añade `public_id` a `capitulo` y
  `rubro`; no añade identidad pública a cronograma ni actividad.
- No existen `Cronograma.java`, `Actividad.java`, repositorios JPA ni CRUD de
  cronograma en el árbol actual. Sí existe el record puro
  `CronogramaSnapshot(int numeroPeriodos, List<ActividadSnapshot>)`; el record de
  actividad usa `Map<Integer, BigDecimal>` y documenta períodos 1-based.
- `VersionSnapshotBuilder.build(Long)` devuelve actualmente
  `new VersionSnapshot(motorParams, raices, null)`. Por tanto, el builder todavía
  no carga el cronograma de la base; no se puede presentar esa integración como
  existente ni tocar `motor/` para ocultar el gap.
- `VersionadoService.copiarCronogramaYActividad` usa SQL nativo porque las
  entidades aún no existen. Copia unidad, número de períodos, total revisado,
  fecha de revisión, peso y mapa JSONB, y remapea el rubro por código. Los
  `INSERT` omiten la identidad pública, de modo que el futuro default debe
  generar UUIDs nuevos.
- `PresupuestoRepository.findRubrosCubiertosPorCronograma` hace el join interno
  por `a.rubro_id` y `c.presupuesto_id`. No se cambia a UUID ni se convierte el
  UUID público en FK.
- El bloque API actual contiene ejemplos contradictorios: el `CronogramaResponse`
  muestra `id: 1` y `Actividad` muestra `id: 1`, `rubroId: 1`, aunque otras
  fronteras del backend ya exigen UUIDv7. El gate debe retirar la contradicción;
  aceptar IDs numéricos de compatibilidad no es una solución.
- El contrato vigente dice `Σ ≈ peso`, pero el objetivo de este módulo exige
  igualdad exacta a escala 4 para poder exportar. El texto aproximado queda
  superseded por la regla canónica que el gate documente.
- `design/04-export-sercop-spec.md` declara un layout propuesto y no demuestra
  que exista un workbook oficial de cronograma. No se puede convertir esa
  propuesta, un PDF de ejemplo o el proyecto de referencia en una afirmación de
  conformidad.

## Alcance y fuera de alcance

### Alcance de este plan cuando se ejecute

- Reconciliar documentos canónicos en el repositorio `../thesis-docs/`.
- Fijar el vocabulario y la forma pública de cronograma, actividad, período,
  avance, segmento, distribución y desactualización.
- Resolver las cinco rutas indicadas, sus códigos HTTP, cuerpos de error, roles y
  ownership.
- Registrar la política exacta de pesos, avances, residuales, exportabilidad,
  revisión y copia de versiones.
- Especificar el contrato que heredarán 027, 028, 029, 030 y 031, incluyendo la
  coordinación frontend y el lane separado de SERCOP/MSPDI.
- Actualizar el catálogo de pruebas con casos concretos y trazabilidad a las
  decisiones, sin ejecutar todavía la implementación.

### Fuera de alcance

- Crear o modificar entidades, repositorios, servicios, recursos, DTOs,
  migraciones Flyway, consultas SQL, tests, fixtures, Bruno o frontend.
- Modificar `src/main/java/ec/uce/propuestas/motor/` o duplicar en otro módulo las
  fórmulas del motor. Si el contrato requiere cambiar `Motor`,
  `Consolidador`, `VersionSnapshot` o records del paquete `motor`, se abre un
  guard/plan específico.
- Ejecutar Plan 027/028 o declarar disponible cualquier endpoint.
- Implementar CPM, ruta crítica, dependencias, lag, auto-programación,
  calendarios laborales, hitos, fechas de inicio/fin o subpresupuestos.
- Escribir `.mpp` propietario. MSPDI XML, si se autoriza, es un writer y lane
  independiente; no es un alias de `.mpp`.
- Afirmar que un xlsx, pdf o XML cumple SERCOP sin fuente oficial versionada.

## Archivos candidatos para la reconciliación documental

> Superficies ejecutadas. Durante la reconciliación se comprobó que los índices,
> requisitos, pantallas, diagramas y trazabilidad contenían referencias activas al
> contrato anterior; se incorporaron al mismo gate documental. El usuario autorizó
> la ejecución secuencial y la inclusión de N05; `.atl/*` quedó excluido.

| Repositorio | Archivo posible | Motivo |
|---|---|---|
| `../thesis-docs` | `README.md`, `PROJECT_SPEC.md`, `plan/README.md` | Mantener los índices y el scope canónico sincronizados. |
| `../thesis-docs` | `DOCUMENTOS/entrevistas/05/N05_entrevista-cronograma.md` | Fuente humana autorizada; conservar respuestas y corregir solo formato. |
| `../thesis-docs` | `DOCUMENTOS/requerimientos/v1.3-functional-requirements.md` | Propagar N05 al requisito funcional acumulativo vigente. |
| `../thesis-docs` | `plan/architecture/06-database-schema.md` | Ajustar identidad pública, tipos y notas de `cronograma`/`actividad`. |
| `../thesis-docs` | `plan/architecture/07-api-contract.md` | Dejar una tabla de rutas, DTOs, errores y shapes sin IDs numéricos. |
| `../thesis-docs` | `plan/architecture/08-codebase-design.md` | Fijar la costura `recalculo`, el deep copy y el módulo frontend sin fórmulas duplicadas. |
| `../thesis-docs` | `plan/domain/02-data-model.md` | Reconciliar §13, §16–§17, estados, segmentos y precisión. |
| `../thesis-docs` | `plan/design/02-pantallas-flujos.md` | Alinear S-33/S-34/S-35, tres vistas y Gantt editable. |
| `../thesis-docs` | `plan/design/03-procesos-detalle.md` | Reconciliar P-33…P-37 y decisiones de reducción/revisión. |
| `../thesis-docs` | `plan/design/04-export-sercop-spec.md` | Trazar layout oficial/adaptable y separar MSPDI XML. |
| `../thesis-docs` | `plan/design/05-diagramas.md`, `plan/design/06-casos-de-uso.md` | Actualizar secuencias, estados y referencias CHK. |
| `../thesis-docs` | `plan/quality/02-catalogo-pruebas.md`, `plan/quality/03-trazabilidad.md` | Alinear TC/CHK, igualdad a escala 4, rutas y matriz. |
| `../thesis-docs` | `plan/roadmap/01-plan-iteraciones-xp.md` | Alinear dependencias 026→027→028→029→030→031 y el gate GM. |

## Ledger de decisiones que el gate debe dejar cerrado

### 1. Identidad, ownership y roles

- La identidad pública de `Cronograma` y `Actividad` es `public_id UUIDv7`.
  En JSON se conserva el nombre semántico que el canon elija (`id`,
  `presupuestoId`, `rubroId`), pero el valor siempre es string UUIDv7. No se
  exponen columnas `id BIGINT`, ni se acepta un número como compatibilidad.
- Los joins, FKs, locks y consultas de repositorio continúan con `BIGINT`. El
  UUID público no es FK, no se usa para autorizar y no reemplaza el ID interno.
- El parser de frontera debe rechazar UUID malformado, UUIDv1–v6 y UUIDv8 con
  400 `validacion`; un UUIDv7 inexistente o perteneciente a otro owner se
  comporta como 404 `no-encontrado`.
- La frontera solo permite `USUARIO` y `SUPER_ADMIN`. Un caller autenticado no
  puede inferir la existencia de un proyecto, presupuesto, cronograma o
  actividad ajena: el resultado es 404, no 403.
- La resolución de una actividad debe atravesar
  `Actividad → Cronograma → Presupuesto → Proyecto → usuario` y la resolución
  del cronograma debe atravesar `Cronograma → Presupuesto → Proyecto → usuario`.
  Los métodos internos pueden recibir el `Long` ya resuelto, pero esa
  conversión no cruza el contrato HTTP.

### 2. Períodos y configuración

- `unidadTiempo` solo puede ser `SEMANA` o `MES`, con los nombres exactos que
  defina el enum canónico. No se agrega `DIA`, `TRIMESTRE`, `AÑO` ni una unidad
  derivada del proyecto.
- `numeroPeriodos` es entero positivo y sus límites mínimo/máximo deben quedar
  escritos en una única fuente. Si el canon no fija el máximo, el gate no puede
  inventarlo: queda `STOP-026-LIMITES` y 028 no implementa validación parcial.
- Las claves de `avancePorPeriodo` son strings de enteros 1-based dentro de
  `1..numeroPeriodos`. El orden numérico, no el orden lexicográfico, gobierna
  sumas, acumulados, segmentos y respuesta.
- Un mapa enviado por el cliente reemplaza atómicamente el mapa de la
  actividad. Omitir una clave significa que ese período no está activo; no se
  conserva una clave previa por merge implícito. La política de `null`, mapa
  vacío y valores repetidos debe quedar explícita en el DTO y en los casos
  negativos.
- No se persiste `avanceAcumulado`; se deriva en la lectura como suma de los
  períodos 1..t. No se persisten `item`, `descripcion` ni `precioTotal` dentro
  de `actividad` si el DDL canónico los deriva del `rubro`.

### 3. Rutas y contrato REST/DTO

El gate cerró el inventario siguiente. No existen aliases legacy:

| Operación canónica | Contrato | Resultado mínimo |
|---|---|---|
| `GET /presupuestos/{id}/cronograma` | presupuesto UUIDv7; agregado completo; sin fila = 404 | 200/400/404; `USUARIO|SUPER_ADMIN` + owner-to-404 |
| `POST /presupuestos/{id}/cronograma` | `CronogramaCrearRequest`; crea 1:1 e importa rubros | 201; duplicado 409 `cronograma-ya-existe` |
| `PUT /cronogramas/{id}/configuracion` | reemplaza unidad/número; `confirmarPerdida` solo para el reintento | 200/400/404; pérdida/cambio de unidad 409 |
| `PATCH /cronogramas/{id}/actividades/{aid}` | `ActividadProgramarRequest`; cuatro operaciones semánticas | 200/400/404/409; mapa atómico y actividad cross-cronograma 404 |
| `GET /cronogramas/{id}/vistas` | una proyección con `gantt`, `valorizado`, `curvaS` | 200/400/404; ninguna fórmula cliente |
| `POST /cronogramas/{id}/revisado` | sin body; captura total+fingerprint+fecha | 200/400/404; no toca avances |

Los records usan camelCase, decimales string y UUIDv7. `CronogramaResponse` contiene
configuración, marcadores visibles pertinentes, `BORRADOR|COMPLETO`, stale,
actividades, avance parcial/acumulado y segmentos derivados. La actividad expone
`rubroId`, derivados del rubro, peso, mapa y desviación. `CronogramaVistasResponse`
agrupa las tres vistas sin crear tres endpoints ni tres fuentes de verdad. No queda
ningún ejemplo numérico de identidad pública.

### 4. Estados de distribución y desactualización

El contrato tiene dos ejes independientes:

- **Distribución:** `BORRADOR` cuando falta asignación o alguna actividad tiene
  desviación distinta de cero; `COMPLETO` solo cuando todas satisfacen igualdad a
  escala 4 y el avance final es `100.0000`.
- **Desactualización:** compara total y fingerprint SHA-256 del snapshot canónico.
  Detecta cambios compensados con igual total; sin marcador (`NULL`) es `true`.
  Puede coexistir con cualquier distribución y revisar no cambia avances.

Guardar un borrador es válido. La lectura debe mostrarlo como borrador y no
fingir conformidad. La exportación de cronograma solo puede pasar el gate
cuantitativo cuando la distribución global es `100.0000` y las desviaciones son
`0.0000`; la alerta de desactualización, por sí sola, no la bloquea. Los bloqueos
independientes de P-32 (PU/cantidad/sin actividad) conservan su contrato y no se
redefinen aquí.

### 5. Segmentos y operaciones semánticas del Gantt

La fuente de verdad es el mapa `avancePorPeriodo`. Para un mapa como
`{"1":"1.2500", "3":"1.2500", "4":"2.5000", "7":"0.0000"}`:

- se ordenan las claves numéricamente;
- los períodos activos no consecutivos se conservan, no se rellenan con claves
  ficticias;
- `segmentos` se deriva como runs máximos de períodos consecutivos (por ejemplo,
  `[1,1]`, `[3,4]`, `[7,7]` si el canon considera activo también el cero);
- al volver a leer, derivar segmentos desde el mapa debe producir la misma
  proyección; no se persiste un segundo intervalo que pueda divergir.

El gate debe fijar la representación exacta de un segmento y trasladar la
 decisión vigente de N05: el Gantt es editable. El contrato debe describir
 operaciones semánticas de movimiento y redimensionamiento, aunque se ejecuten por
 el `PATCH` existente o por una ruta que el canon apruebe:

- **Mover:** desplaza el conjunto de períodos de una actividad por un delta
  entero, conserva la forma y la suma de los valores, valida el rango completo y
  reemplaza el mapa en una sola transacción. Un desplazamiento que sale de
  `1..numeroPeriodos` devuelve 400 y no deja cambios.
- **Redimensionar:** modifica el intervalo o conjunto de períodos según el
  comando canónico, redistribuye los valores con el algoritmo determinista de
  escala 4 y conserva la suma objetivo `pesoPonderado`; si la operación no puede
  conservarla exactamente, devuelve el error definido por el canon en lugar de
  introducir un residual silencioso.
- Ambos comandos deben usar el nombre, payload y ruta aprobados por 026. No se
  inventa `POST /mover`, `POST /redimensionar`, `segmentos` editable ni un
  `delta` ambiguo solo para satisfacer al frontend. Un Gantt read-only contradice
  N05 y activa STOP; no es una alternativa abierta de este gate.

### 6. Fórmulas y precisión exactas

El gate dejó fórmulas únicas y ejecutables, conservando escalas y sin añadir
redondeo al motor:

- Dinero: `NUMERIC(14,6)`, transportado como string decimal; nunca `double`,
  `float`, `parseFloat` ni `toFixed` para dominio.
- Peso por actividad `i`: base del motor a escala 4. Fuera del motor se convierte
  a unidades `0.0001`; residual positivo se suma a la primera actividad ordenada
  por precio descendente/orden presupuestario y residual negativo se consume sin
  bajar de cero siguiendo ese orden. Total cero conserva pesos cero.
- Avance por actividad:
  `avanceTotal_i = Σ_{p=1..numeroPeriodos} avance_{i,p}`.
- Desviación: el canon debe fijar si es firmada o absoluta y su forma JSON; la
  comprobación de completitud debe ser inequívoca. La regla de este módulo es
  que una actividad completa satisface exactamente
  `avanceTotal_i = peso_i` a escala 4 y expone `desviacion = "0.0000"`.
- Total final:
  `avanceFinal = Σ_i avanceTotal_i`; exportable por cronograma exige
  `avanceFinal = 100.0000` exactamente, no una tolerancia.
- Totales de período:
  `avancePeriodo_t = Σ_i avance_{i,t}` y
  `avanceAcumulado_t = Σ_{k=1..t} avancePeriodo_k`; ambos se derivan en
  lectura, ordenados por período 1..n.
- Montos: racional entero con dinero scale-6 y porcentaje scale-4; target HALF_UP,
  bases floor y micro-unidades restantes por residuo fraccionario descendente.
  Peso cero con precio positivo exige clave activa y reparte su precio entre esas
  claves; nunca se usa división decimal sin escala.

Si el reparto de residuales, el signo de `desviacion`, el tratamiento de un
presupuesto sin rubros o el tratamiento de total cero no queda escrito en todas
las fuentes, se activa `STOP-026-MATH`. No se puede “resolver” con una
comparación aproximada en el endpoint ni con un redondeo diferente en el writer.

### 7. Exportación y procedencia SERCOP

Durante G4 se debe buscar, registrar y revisar la fuente oficial del formato de
cronograma SERCOP: organismo emisor, URL o ubicación documental, versión/fecha,
hash o identificador estable, alcance (xlsx, pdf, XML u otro) y permisos de uso.

- Si se encuentra la fuente, `04-export-sercop-spec.md` la cita y cada CHK
  aplicable indica qué parte es normativa y qué parte es decisión de layout.
- Si no se encuentra una fuente oficial verificable, se marca únicamente el
  lane SERCOP/I-10 como bloqueado. Se puede seguir documentando el contrato
  genérico de datos y los lanes no formales sin escribir “SERCOP-compatible”.
- El formato de interoperabilidad **MSPDI XML** se documenta como XML estándar,
  con su media type y extensión propios. No se crea ni se promete un writer de
  `.mpp`; un `.mpp` propietario no es equivalente a MSPDI XML.
- La exportación server-side consume una proyección común, aplica redondeo de
  presentación solo en el writer y no recalcula pesos o avances. Un cronograma
  en borrador no pasa el gate cuantitativo; `desactualizado=true` no lo bloquea
  por sí solo.

## Pruebas primero: RED → GREEN → triangulación/refactor

Este plan es un gate documental. No tiene una prueba de comportamiento de
producción significativa antes de que exista el contrato; se deja constancia
de la excepción y se usan comprobaciones de consistencia como evidencia.

### RED — contradicciones observables

Antes de editar `../thesis-docs/`, el ejecutor debe capturar el estado de las
fuentes con búsquedas focales, al menos:

- ejemplos numéricos de `id`/`rubroId` en el bloque de Cronograma;
- usos de `≈`, 0-based, claves numéricas y nombres alternativos de estado;
- ausencia de fórmula de residuales, reducción y segmentos;
- afirmaciones de formato SERCOP sin procedencia verificable;
- diferencias entre API, dominio, procesos, roadmap y catálogo.

El resultado RED es la lista literal de hallazgos y rutas; no se convierte en un
resultado de tests inventado.

### GREEN — una fuente por decisión

Actualizar los documentos candidatos en el orden DB → dominio → API → codebase →
procesos → roadmap → calidad → exportación. Cada decisión se escribe una vez en
su fuente natural y las demás fuentes enlazan o resumen sin crear variantes.
Repetir las búsquedas RED y comprobar que:

- todos los IDs activos son UUIDv7;
- las fórmulas usan igualdad a escala 4 y `100.0000`;
- la tabla de rutas y los DTOs tienen los mismos nombres;
- el estado de distribución no se confunde con `desactualizado`;
- la falta de fuente SERCOP deja solo su lane bloqueado.

### TRIANGULACIÓN

- Releer el DDL y el contrato API lado a lado: cada campo persistido, derivado y
  JSONB tiene dueño definido.
- Comparar el catálogo TC/CHK con P-33…P-37: cada caso tiene ruta, actor,
  precondición, código y resultado verificable.
- Contrastar el contrato con `CronogramaSnapshot`/`ActividadSnapshot` y
  `VersionSnapshotBuilder`: cualquier cambio requerido en `motor/` abre STOP.
- Contrastar deep copy y ownership con `VersionadoService` y
  `findRubrosCubiertosPorCronograma`: UUID público en la frontera, BIGINT interno.
- Validar la coordinación frontend: no hay cálculo duplicado ni supuesto
  lexicográfico sobre períodos.

### REFACTOR documental

Eliminar duplicados o ejemplos activos que contradigan la fuente natural,
conservar notas históricas claramente rotuladas y dejar enlaces relativos
resolubles. El refactor no cambia una decisión; solo reduce ambigüedad y el
número de lugares que un ejecutor debe memorizar.

## Catálogo concreto de casos a reconciliar

| Caso | Precondición/entrada | Resultado exigido por el canon |
|---|---|---|
| P33-01 alta | Presupuesto propio con `n` rubros y sin cronograma. | POST 201; exactamente un cronograma y `n` actividades, pesos calculados, mapas `{}` y UUIDv7 públicos. |
| P33-02 conflicto 1:1 | Segundo POST para la misma versión. | 409 tipado; no se duplica cronograma ni actividad. |
| P33-03 reducción | Cronograma con avances en períodos que quedarían fuera. | Respuesta y body de pérdida exactamente los definidos por 026; sin borrado silencioso; reintento solo con la confirmación canónica o STOP. |
| Configuración válida | `SEMANA`, `MES`, mínimo, máximo y valores límite del canon. | 200; persistencia exacta; operación repetida no crea filas ni altera avances sin orden explícita. |
| Configuración negativa | Unidad desconocida, número cero, negativo, decimal, ausente o fuera de límite. | 400 `validacion`; no se toca el agregado. |
| GET inexistente | Presupuesto propio sin cronograma. | 404 `no-encontrado`, no 200 vacío, salvo que el canon cambie expresamente esta decisión. |
| UUID de frontera | UUID malformado, UUIDv4, UUIDv7 inexistente, UUIDv7 ajeno. | Respectivamente 400, 400, 404 y 404; sin filtrar IDs internos. |
| Avance 1-based | Mapa con claves `"1"`, `"3"`, `"4"` y `numeroPeriodos >= 4`. | Se conservan períodos no consecutivos; orden numérico; segmentos derivados `[1]` y `[3,4]`. |
| Avance fuera de rango | Clave `"0"`, `"n+1"`, decimal, negativa o no numérica. | 400 `validacion`; mapa anterior intacto. |
| Avance con precisión | Valores con más de cuatro decimales, signo negativo o formato no decimal. | Resultado exacto decidido por el DTO (rechazo o normalización canónica); nunca `double` ni truncado silencioso. |
| Distribución borrador | Alguna actividad sin períodos o suma distinta del peso. | Guarda; estado `BORRADOR`; desviación no cero visible; export cuantitativo bloqueado. |
| Distribución completa | Cada actividad suma su peso exactamente a escala 4 y suma global 100.0000. | Estado completo; cada desviación `0.0000`; export cuantitativo habilitado si pasan los demás gates. |
| Cambio de presupuesto | Se modifica un rubro después de revisar el cronograma. | Pesos recalculados; alerta `desactualizado=true`; no se borran avances; export no se bloquea por esa alerta. |
| Cambios compensados | Dos rubros cambian y el total general final coincide con el revisado, pero cambian sus pesos. | `desactualizado=true`; demuestra que comparar solo el total no es suficiente. |
| Marcar revisado | Cronograma propio con datos actuales. | POST 200; marcador/snapshot y fecha actualizados; alerta limpia según regla canónica. |
| Ownership actividad | `aid` válido de otro presupuesto/owner. | 404 indistinguible de inexistente; no 403. |
| Roles | Caller `USUARIO`, `SUPER_ADMIN`, anónimo y cualquier rol no permitido. | Los dos roles permitidos comparten ownership; anónimo/no permitido sigue la política global, sin tercer rol. |
| Copia de versión | Origen con configuración, avances, segmentos derivados y actividades. | Copia conserva configuración y mapa según canon, remapea rubros, genera UUIDs nuevos y no comparte filas; origen intacto. |
| Copia sin cronograma | Origen sin cronograma. | No se crea cronograma hijo por accidente; el resultado coincide con la decisión de deep copy. |
| Export SERCOP | Fuente oficial localizada y distribución completa. | Solo el lane formal puede afirmar conformidad; CHK aplicables trazables. |
| Export sin fuente | No se encuentra fuente oficial verificable. | STOP solo SERCOP/I-10; no se presenta maqueta como conforme. |
| Interoperabilidad | Solicitud MSPDI XML o `.mpp`. | MSPDI XML se evalúa como XML estándar; `.mpp` queda explícitamente fuera, nunca se confunden. |

Los casos existentes conservaron sus identificadores y el catálogo se amplió a
TC-P33-01…10, TC-P34-01…12, TC-P35-01…04, TC-P36-01…05 y TC-P37-01…12;
Gantt, precisión, procedencia y MSPDI viven allí, no en una lista privada.

## Owner-to-404 y autorización

- Recursos y servicios deben resolver el owner antes de devolver un objeto de
  cronograma o actividad.
- `USUARIO` y `SUPER_ADMIN` son los únicos roles funcionales de este módulo.
- Un UUIDv7 correcto pero ajeno, un presupuesto ajeno, una actividad ajena o un
  cronograma inexistente responden 404 `no-encontrado` con el mismo principio de
  no filtración. El mensaje no incluye el `BIGINT` interno.
- UUID malformado o de versión incorrecta se rechaza en la frontera con 400
  `validacion`, antes de tocar repositorios.
- Un conflicto de cardinalidad 1:1 o pérdida confirmable de datos usa 409 con el
  código y payload que 026 deje en el canon; no se reutiliza 400 por comodidad.

## STOP conditions

Detener el gate y reportar sin editar más si ocurre cualquiera de estas
condiciones:

- **STOP-026-DOC:** arquitectura, dominio, API, procesos, roadmap o calidad
  siguen usando shapes distintos después de la reconciliación.
- **STOP-026-ID:** alguna fuente activa expone `id`, `rubroId` o
  `presupuestoId` numérico, propone aceptar IDs legacy o usa UUID sin exigir v7.
- **STOP-026-LIMITES:** `numeroPeriodos`, unidad, claves 1-based o la política de
  reemplazo del mapa no tienen límites y errores canónicos.
- **STOP-026-MATH:** no hay algoritmo exacto de escala 4, residual,
  `desviacion`, total cero o `100.0000`; no se acepta `≈` como contrato.
- **STOP-026-STATE:** `BORRADOR`, completo y `desactualizado` se representan
  con un mismo campo, no se decide el caso sin revisión previa o el mecanismo no
  detecta cambios compensados que conservan el total general.
- **STOP-026-SEGMENTS:** `segmentos` aparece como fuente de escritura duplicada,
  se asume continuidad, el Gantt editable no tiene mover/redimensionar semánticos
  definidos o se intenta convertirlo en read-only contra la decisión N05.
- **STOP-026-LEGACY:** una ruta legacy se mantiene por intuición, se agrega un
  alias o se cambia el significado de PUT/PATCH sin una fila canónica y un caso
  del catálogo.
- **STOP-026-ENGINE:** cerrar el contrato exige modificar `motor/`, introducir
  CPM/dependencias/lag/auto-programación o copiar fórmulas fuera del motor.
- **STOP-026-SERCOP:** no existe fuente oficial verificable. El STOP afecta solo
  SERCOP/I-10; no se sortea afirmando conformidad por la propuesta de layout.
- **STOP-026-MPP:** un requisito pretende generar o leer `.mpp` como si fuera
  MSPDI XML.
- **STOP-026-FRONT:** la coordinación frontend requiere motor o reglas distintas
  en TypeScript, o consume un endpoint que no está en el canon.
- **STOP-026-SCOPE:** se propone editar el índice, código, migraciones, Bruno,
  tests, frontend o cualquier archivo no incluido en la tabla de candidatos.

## Verificaciones focales, regresiones y cierre

Estas órdenes se ejecutaron para cerrar el gate; las focales de cronograma que aún
no existen quedan reservadas a los planes posteriores. Solo se registran cifras
observadas.

### Documentación y diff

```bash
git -C ../thesis-docs diff --check
git diff --check
git -C ../thesis-docs status --short
git status --short
```

Revisión focal de consistencia (ajustar la ruta solo si el repositorio canónico
la reorganiza durante el gate):

```bash
grep -RInE '"id"[[:space:]]*:[[:space:]]*[0-9]+|"rubroId"[[:space:]]*:[[:space:]]*[0-9]+|≈|0-based' \
  ../thesis-docs/plan/architecture ../thesis-docs/plan/domain \
  ../thesis-docs/plan/design ../thesis-docs/plan/quality \
  ../thesis-docs/plan/roadmap
```

Los hits históricos deben estar marcados como tales; ningún hit activo se
acepta. Revisar también que las cinco rutas aparezcan una sola vez en la tabla
API y que todos los links relativos resuelvan.

### Focales backend reservadas

```bash
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.recalculo.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.identifier.*' --console=plain
```

### Regresión, suite, calidad y Graphify

```bash
./gradlew test --console=plain
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

### Conteos reales desde XML

Reportar los conteos observados, no los números que el plan o una estimación
anticipen:

```bash
python3 - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path("build/test-results/test")
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
files = sorted(root.glob("TEST-*.xml"))
for path in files:
    suite = ET.parse(path).getroot()
    for key in totals:
        totals[key] += int(suite.attrib.get(key, 0))
print("files=", len(files))
print(" ".join(f"{key}={value}" for key, value in totals.items()))
PY
```

La evidencia debe conservar los XML o la salida literal y separar pass, failures,
errors y skipped. Los dos residuales aceptados del motor (GM-19/GM-20) siguen la
política de Plan 014; no se corrigen ni se cuentan como una señal para reabrir
`motor/` en este plan.

## Criterios de aceptación — cierre 04-09-2026

- [x] Estado `DONE`; fuentes y ledger identifican autoridad y procedencia.
- [x] Rutas, DTOs, UUIDv7/ownership, períodos 1-based y reemplazo atómico tienen
      un único contrato; ningún `BIGINT` cruza HTTP/JSON.
- [x] Peso, residual, avance, desviación, parciales/acumulados y `100.0000` son
      reproducibles a escala 4.
- [x] `BORRADOR|COMPLETO` y `desactualizado` son ejes ortogonales; stale no bloquea.
- [x] Segmentos no consecutivos y Gantt editable usan comandos semánticos con
      negativos de rango/solape.
- [x] Deep copy, SQL nativo, `cronograma_actividad` inerte y builder nullable están
      trazados sin modificar `motor/`.
- [x] Fuente SERCOP oficial localizada; XLSX/PDF adaptables y MSPDI XML separado
      de `.mpp`.
- [x] Requerimientos, DB, API, codebase, dominio, pantallas, procesos, export,
      diagramas, calidad, trazabilidad, roadmap e índices quedaron alineados.
- [x] Verificaciones documentales/backend, Spotless, build, XML y Graphify tienen
      salida observada.
- [x] Commits autorizados explícitamente por el usuario, uno por plan y repositorio.

## Evidencia de ejecución

```text
Plan: 026
Estado al iniciar: TODO — gate documental bloqueante
Fecha: 04-09-2026

Fuente oficial SERCOP:
- Catálogo: https://portal.compraspublicas.gob.ec/sercop/cat_normativas/licitacion
- Documento: FORMULARIO-LICO-V-2023-001.doc
- Emisión/estado: 2023-11-21 / Vigente
- Sección: 1.8 Cronograma valorado de trabajos
- SHA-256: 89baa330499a265fb99b28a5bdd086bce6549f40f8e45cead803e3bf6a15cd08
- Descarga revalidada: Composite Document File V2
- PDF aceptado SHA-256: 9dbf4bb0d063d9cedf5b61ef1f7b2c4d471bd92174625a37733eaf4db79078de

Documentación:
- git diff --check (backend y thesis-docs): PASS
- enlaces relativos de 23 Markdown modificados: 0 rotos
- catálogo: 209 TC únicos, 25 GM únicos, 36 CHK; sin TC/CHK duplicados
- búsqueda de legacy activa: sin ≈/tolerancia, total-only stale, rutas retiradas,
  Gantt read-only, S-curve opcional ni bloqueo por stale
- revisión adversarial: 3 hallazgos de residual/división/peso-cero corregidos y
  re-juzgados como verified

Backend (sin cambios de aplicación):
- focal presupuesto + recalculo + identifier: PASS
- suite: 438 tests, 2 failures aceptados (GM-19/GM-20), 0 errors, 1 skipped (GM-24)
- XML: files=45 tests=438 failures=2 errors=0 skipped=1
- spotlessCheck: PASS
- build -x test: PASS
- graphify update .: 3185 nodos, 9287 aristas, 134 comunidades

STOP activo: ninguno
Siguiente plan: 027
```

## Commit

El usuario autorizó ejecutar secuencialmente los planes y crear un commit por cada
avance. Plan 026 se entrega con stage selectivo: N05 incluido por autorización;
`.atl/*` excluido; sin push ni publicación.
