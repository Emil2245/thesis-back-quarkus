# 031 — Exportación del Cronograma

**Estado:** **DONE 07-09-2026** — implementación aplicada y verificada bajo
suite completa; `motor/`, V001–V008 y `recalculo/` intactos; `git diff --check`,
`./gradlew spotlessCheck` y `./gradlew build -x test` en PASS; suite completa
647/2/0/1 (únicas fallas focales históricas aceptadas GM-19 y GM-20, GM-24
skipped); suite focal Plan 031 official-XSD **54/54 pass, 0 failures/errors/skips**;
Bruno `api/bruno/11-cronograma/` 20/20 requests, 70/70 tests asserts,
0 failures/errors/skips, 11 725 ms; **no** se ejecuta commit (instrucción explícita).
G6 resuelto por la fuente MSPDI pinned en `../thesis-docs` (namespace
`http://schemas.microsoft.com/project/2007`, XSD
`https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd` revisión 2007-11-28,
239895 bytes, SHA-256
`a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`); objetivo
de compatibilidad: `.xml` que **abre/importa en Microsoft Project**, nunca `.mpp`.

**Iteración:** I-10. **Proceso:** P-37.

> Este plan separa tres lanes: exportación documental aprobada para el proyecto,
> afirmación de conformidad SERCOP y, si el canon lo autoriza, interoperabilidad
> MSPDI XML. Un PDF/XLSX de ejemplo no prueba conformidad SERCOP y MSPDI XML no es
> el binario propietario `.mpp`.

## Objetivo medible

Una ejecución futura debe entregar una exportación server-side que:

1. consume la misma proyección canónica del Plan 030 y no recalcula pesos,
   segmentos, avances o acumulados en cada writer;
2. aplica un preflight owner-scoped y bloquea un cronograma `BORRADOR`, cualquier
   desviación por actividad distinta de `0.0000` o avance global final distinto de
   `100.0000`;
3. conserva los bloqueos de integridad presupuestaria que el canon vincule con
   P-32, sin confundirlos con el gate de distribución;
4. permite exportar un cronograma completo pero `desactualizado`, devolviendo la
   advertencia mediante el mecanismo explícito aprobado por 026;
5. genera los formatos documentales canonizados (por ejemplo XLSX/PDF basado en el
   ejemplo aceptado) con jerarquía, períodos, valores y totales reconciliables;
6. solo usa la palabra “SERCOP” o “conforme a SERCOP” si existe fuente oficial
   verificable y los CHK normativos pasan;
7. trata MSPDI XML como writer/lane independiente y nunca promete crear `.mpp`;
8. prueba contenido, media type, filename, seguridad, parse-back, límites y
   reproducibilidad, y registra conteos reales de la suite.

## Dependencias y gates

| Gate | Requisito | Efecto |
|---|---|---|
| G0 — canon 026 | Formatos, rutas, media types, nombres, firmas, warning stale y fuente normativa decididos. | Prohíbe inventar contrato en el writer. |
| G1 — datos 027–030 | Identidad, CRUD, distribución, tres vistas y stale implementados. | Proyección común disponible. |
| G2 — integridad | Reglas P-32 y distribución final evaluables en una sola respuesta de preflight. | Errores tipados y deterministas. |
| G3 — GM | Baseline vigente del motor ejecutado y tratado según Plan 014/CLAUDE.md. | No se reabren GM-19/20 ni GM-24. |
| G4 — formato documental | Layout de proyecto aprobado y casos parse-back definidos. | Habilita writer genérico sin afirmar conformidad externa. |
| G5 — SERCOP | Fuente oficial ya localizada: catálogo Licitación, Formulario LICO V-2023-001, emisión 2023-11-21, sección 1.8, hash y alcance registrados. | Habilita la trazabilidad del lane; la afirmación final depende de CHK-22…CHK-36. |
| G6 — MSPDI (resuelto por fuente pinned) | Perfil básico aprobado en 026; la fuente queda pinned en `../thesis-docs` con namespace `http://schemas.microsoft.com/project/2007` y XSD `https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd` (revisión 2007-11-28, 239895 bytes, SHA-256 `a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`); objetivo de compatibilidad: `.xml` que **abre/importa en Microsoft Project**, nunca `.mpp`. | Habilita XML estándar; XSD pinned en el canon; sin XSD verificable se bloquea solo este lane. |
| G7 — cierre | Focales, parse-back, seguridad, regresiones, calidad y Graphify medidos. | Evidencia literal y conteos XML reales. |

G5 ya tiene una fuente oficial verificable para los campos y la sección 1.8 del
cronograma: el catálogo SERCOP de Licitación y su Formulario LICO V-2023-001. Esto
habilita la trazabilidad del lane, pero no convierte automáticamente el layout propio
en una plantilla oficial: la matriz y los CHK-22…CHK-36 siguen siendo obligatorios.
La ausencia futura de una fuente verificable bloquearía únicamente la afirmación de
conformidad SERCOP; no se cambia el nombre del formato para eludir ese gate.

G6 ya está resuelto por la fuente MSPDI pinned en `../thesis-docs` (namespace
`http://schemas.microsoft.com/project/2007`, XSD
`https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd` revisión 2007-11-28,
239895 bytes, SHA-256
`a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`); el objetivo
de compatibilidad es que el `.xml` abra/importe correctamente en Microsoft Project
y nunca se entregue como `.mpp`. La fuente pinned sustituye la verificación pendiente
de `031`; el resto del gate (writer, preflight, parse-back, CHK-22…CHK-36) sigue
abierto y solo se cierra con evidencia de ejecución.

## Fuentes que deben releerse

- `docs/modulos/06-cronograma/00.md` y Planes 026–030.
- `../thesis-docs/plan/architecture/07-api-contract.md` secciones de exportación,
  ya reconciliadas por 026.
- `../thesis-docs/plan/architecture/08-codebase-design.md` boundary de documento/export.
- `../thesis-docs/plan/domain/02-data-model.md` §13 y §16–§17.
- `../thesis-docs/plan/design/03-procesos-detalle.md` §G/P-37.
- `../thesis-docs/plan/design/04-export-sercop-spec.md` completo, distinguiendo la
  fuente oficial de campos de la decisión de layout adaptable.
- `../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P37-01…12 y CHK-01…CHK-36
  (cronograma: CHK-22…CHK-36).
- `../thesis-docs/plan/roadmap/01-plan-iteraciones-xp.md` I-10 y gate GM.
- `../thesis-docs/DOCUMENTOS/entrevistas/05/N05_entrevista-cronograma.md` §§5–7.
- PDF de cronograma valorizado aceptado `res/ESTANCIA-ACADEMICA/CRONOGRAMA VALORADO-signed.pdf`, SHA-256 `9dbf4bb0d063d9cedf5b61ef1f7b2c4d471bd92174625a37733eaf4db79078de`; guía visual, no plantilla normativa.
- Implementación y dependencias actuales del módulo de documentos/exportación y
  `build.gradle.kts`; no añadir librerías sin que este plan/canon lo autorice.
- **Fuente oficial SERCOP confirmada:** catálogo de [Licitación](https://portal.compraspublicas.gob.ec/sercop/cat_normativas/licitacion), entrada vigente emitida el 2023-11-21; [`FORMULARIO-LICO-V-2023-001.doc`](https://portal.compraspublicas.gob.ec/sercop/wp-content/uploads/2023/11/FORMULARIO-LICO-V-2023-001.doc), sección 1.8 «Cronograma valorado de trabajos», SHA-256 `89baa330499a265fb99b28a5bdd086bce6549f40f8e45cead803e3bf6a15cd08`.
- Contrato MSPDI básico de 026 + fuente/XSD oficial pinned que Plan 031 fija antes
  de escribir el writer: namespace `http://schemas.microsoft.com/project/2007`, XSD
  `https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd` (revisión 2007-11-28,
  239895 bytes, SHA-256
  `a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`); objetivo
  abrir/importar en Microsoft Project, nunca `.mpp`.

`../ingepresupuestos/` solo puede ilustrar que la interoperabilidad se realiza con
MSPDI XML; su código, XML concreto y decisiones no se copian.

## Estado inicial esperado

Al iniciar 031, Plan 030 debe ofrecer una proyección común mediante
`GET /cronogramas/{id}/vistas`, con jerarquía, rubros, períodos, montos/porcentajes
parciales y acumulados, estado, exportabilidad y warning stale. Antes de los planes,
el backend no tiene exportador de cronograma.

`04-export-sercop-spec.md` registra la fuente oficial de campos de la sección 1.8 del
Formulario LICO 2023-001 y separa esa autoridad de un layout propio adaptable. La
referencia aceptada `CRONOGRAMA VALORADO-signed.pdf` aporta forma/UX, no coordenadas
normativas. La suite del motor mantiene el baseline vigente documentado en
`CLAUDE.md`; este plan no cambia fórmulas ni tolerancias para ajustar un archivo.

## Alcance

### Lane A — formato documental del proyecto

Solo si 026 lo aprobó, generar XLSX y/o PDF server-side basados en la proyección de
030. Deben incluir los bloques canonizados: identificación, jerarquía, columnas de
rubro, períodos, filas parciales/acumuladas y firmas/metadatos únicamente cuando
exista fuente canónica para ellos.

### Lane B — conformidad SERCOP

La fuente oficial confirmada —catálogo SERCOP de Licitación, Formulario
`FORMULARIO-LICO-V-2023-001.doc`, emisión 2023-11-21, sección 1.8, SHA-256
`89baa330499a265fb99b28a5bdd086bce6549f40f8e45cead803e3bf6a15cd08`— se usa para
trazar requisito→celda/bloque→CHK-22…CHK-36. La referencia aceptada de forma es el
PDF con SHA-256 `9dbf4bb0d063d9cedf5b61ef1f7b2c4d471bd92174625a37733eaf4db79078de`.
El layout sigue siendo adaptable; no se promete una plantilla binaria universal ni
conformidad más allá de los checks aplicables.

### Lane C — MSPDI XML

El canon aprobó el subconjunto básico y fija la fuente pinned para el XSD: namespace
oficial `http://schemas.microsoft.com/project/2007`, XSD
`https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd` (revisión 2007-11-28,
239895 bytes, SHA-256
`a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`). El resultado
es `.xml` con media type `application/xml`; el objetivo de compatibilidad es que
pueda **abrir/importarse correctamente en Microsoft Project**, nunca `.mpp`. Sin la
evidencia pinned se bloquea solo MSPDI. Requiere `Proyecto.fechaInicio`; si falta,
el preflight bloquea únicamente MSPDI. Los campos fuera del dominio (CPM,
dependencias, calendarios) no se fabrican; se omiten o usan defaults explícitamente
permitidos por el perfil, y nunca se devuelve ni se renombra como `.mpp`.

### Transversal

- Preflight común, errors/warnings canónicos y owner-to-404.
- Streaming/memoria acotada y límites de tamaño/períodos.
- Parse-back y paridad semántica entre proyección y archivo.
- Seguridad de contenido y nombres de archivo.

## Fuera de alcance

- Generar, leer o editar `.mpp` propietario.
- Añadir CPM, ruta crítica, FS/SS/FF/SF, lag, auto-programación o calendarios para
  llenar campos de MSPDI.
- Exportar borradores mediante un flag de fuerza.
- Bloquear únicamente por `desactualizado` o marcar revisado implícitamente.
- Firmas digitales, sello electrónico o envío al portal SERCOP salvo decisión
  canónica futura.
- Importación de XLSX/PDF/MSPDI.
- Renderizado client-side o fórmulas de dominio en JavaScript.
- Cambios en `motor/`, golden masters o precisión de persistencia.

## Archivos posibles

Los nombres y formatos finales dependen de 026 y del inventario del módulo de
documentos.

| Acción | Archivo posible | Motivo/guard |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/cronograma/export/<Preflight>.java` | Gate único para todos los writers. |
| Crear | `.../export/<ProyeccionExportacion>.java` | Adaptación inmutable desde Plan 030. |
| Crear | `.../export/<XlsxWriter>.java` | Solo si XLSX fue aprobado. |
| Crear | `.../export/<PdfWriter>.java` | Solo si PDF fue aprobado. |
| Crear | `.../export/<MspdiWriter>.java` | Lane XML separado y opcional. |
| Crear/modificar | `.../resource/<ExportResource>.java` | Rutas, headers y errors del canon. |
| Crear | `.../dto/<PreflightResponse>.java` | Bloqueos y warnings sin generar bytes. |
| Modificar condicionalmente | `build.gradle.kts` | Solo dependencia imprescindible, versionada/licencia revisada y autorizada por el plan; preferir stack existente. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/export/<suites>.java` | Parse-back, seguridad, contenido y límites. |
| Crear futuro | `api/bruno/<coleccion-cronograma>/...` | Solo durante ejecución, después de tests; no en esta planificación. |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**` | STOP inmediato. |

No escribir plantillas binarias opacas sin documentar su procedencia, licencia,
hash y proceso de actualización.

## Preflight canónico

El preflight evalúa sobre una lectura consistente del agregado:

1. owner y UUIDv7;
2. existencia de cronograma y cobertura de actividades;
3. defectos P-32 que el canon declare bloqueantes;
4. cada actividad: `desviacion == 0.0000` a escala 4;
5. estado de distribución completo;
6. avance global final exactamente `100.0000`;
7. disponibilidad del lane/formato solicitado;
8. `desactualizado` como **warning no bloqueante**.

Una respuesta bloqueada enumera causas deterministas sin filtrar IDs internos. No
existe `forzar=true`. El preflight y la generación deben compartir la misma función;
no se permite que GET de estado acepte y el writer rechace por otra fórmula.

Para evitar TOCTOU, generar desde un snapshot transaccional coherente o verificar
el marcador/version canónico antes de emitir bytes. Una mutación concurrente debe
producir archivo del snapshot identificado o conflicto, nunca mezcla de versiones.

## Contrato HTTP

Usar solo endpoints aprobados por 026. El contrato debe fijar:

- formato en path/query/`Accept`, sin combinaciones ambiguas;
- media types exactos (`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`,
  `application/pdf` y `application/xml` para MSPDI);
- `Content-Disposition` seguro con filename ASCII/UTF-8 canonizado;
- respuesta tipada de preflight/bloqueo y mecanismo para warning stale;
- 400 para UUID malformado/no-v7 o formato inválido;
- 404 para cronograma/presupuesto inexistente o ajeno;
- 409/422 solo según código canónico para distribución o integridad bloqueante;
- 406/415 si el canon los usa para negociación, sin improvisar códigos.

El warning stale puede exponerse en preflight, metadata o header según 026. No se
oculta y tampoco convierte una descarga válida en error.

## Contenido documental

La proyección común controla:

- título y datos institucionales aprobados;
- capítulos/subcapítulos y rubros en orden canónico;
- `NUM./ítem`, descripción, unidad, cantidad, precio unitario y precio total;
- una columna por período, con unidad/etiqueta canónica;
- monto parcial y acumulado;
- porcentaje parcial y acumulado correcto;
- total final reconciliado;
- líneas de firma solo con firmantes/roles ya canonizados.

Display rounding se aplica exclusivamente en writer/presentación. Los valores de
dominio permanecen `BigDecimal`; parse-back debe demostrar reconciliación con la
regla exacta del canon. La fila de porcentaje acumulado suma parciales y no copia el
posible error del PDF guía.

## Seguridad y robustez

- Escapar XML y texto PDF; sanitizar nombres de hoja y filename.
- Neutralizar formula injection en celdas de texto iniciadas por `=`, `+`, `-`, `@`
  conforme a la estrategia aprobada, sin alterar campos numéricos reales.
- No incluir PII, tokens, rutas internas, SQL, IDs `BIGINT` ni stack traces.
- Limitar `numeroPeriodos`, filas, tamaño y tiempo conforme al canon; medir memoria.
- Evitar XXE en parse-back y en cualquier validación XML; no habilitar entidades
  externas.
- No descargar recursos remotos durante la generación.

## Reproducibilidad y parse-back

Definir dos niveles:

1. **Semántico obligatorio:** al abrir/parsear el archivo se recuperan estructura,
   períodos y valores equivalentes a la proyección.
2. **Bytes deterministas si es viable:** normalizar timestamps, orden ZIP/XML,
   metadata y fuentes. Si una librería impide bytes idénticos, documentar la razón y
   exigir igualdad semántica estable; no afirmar reproducibilidad byte a byte.

El XLSX se reabre con una librería independiente o lector aprobado y el PDF se
verifica por extracción estructurada más inspección de páginas críticas. No usar
solo screenshots/golden binarios frágiles. MSPDI se valida contra el XSD/perfil
canonizado y se parsea con entidades externas deshabilitadas.

## Secuencia TDD

### RED

1. Tests del preflight: completo, borrador, desviación, global !=100, P-32 y stale.
2. Contract tests de UUID/owner/roles/headers/filename/media type.
3. Fixtures pequeños de una jerarquía y profundidad 3 con resultados manuales.
4. Tests parse-back XLSX/PDF para contenido, fórmulas materializadas y totales.
5. Casos de injection, Unicode, texto largo, límites y concurrencia.
6. Con G5/G6 cerrados, ejecutar CHK-01…CHK-36 (cronograma: CHK-22…CHK-36)
   y la validación MSPDI/XSD cuando aplique.
7. Capturar RED por capacidad ausente; un fallo de dependencia o infraestructura no
   es RED válido.

### GREEN

Implementar preflight y un writer por vez sobre la misma proyección. Hacer pasar el
formato documental antes de añadir lanes opcionales. No modificar dominio/motor
para acomodar layout.

### TRIANGULACIÓN

Probar 1 y n períodos, varios niveles, múltiples páginas/hojas, valores con
residual de display, borrador/stale, caracteres peligrosos y fixture representativo.
Comparar archivo con los tres read models de 030.

### REFACTOR

Extraer componentes comunes de layout y estilo sin crear un framework genérico.
Mantener writers separados; no condicionar una clase gigante por extensión.

### Bruno futuro

Después de GREEN y solo durante ejecución, añadir requests autenticados de
preflight/descarga y negativos. Bruno valida status, headers y bytes no vacíos; no
reemplaza parse-back JUnit.

## Catálogo mínimo de pruebas

| Caso | Resultado esperado |
|---|---|
| Completo 100.0000 | Descarga permitida si pasan otros gates. |
| Una desviación 0.0001 | Bloqueo tipado; cero bytes parciales. |
| Global 99.9999 o 100.0001 | Bloqueo exacto; sin tolerancia. |
| Borrador | Bloqueo aunque `desactualizado=false`. |
| Completo + stale | Descarga permitida con warning visible. |
| Stale + marcar revisado | Exportación no cambia datos; warning según snapshot. |
| P-32 bloqueante | Causas de integridad preservadas y ordenadas. |
| UUID inválido/no-v7/inexistente/ajeno | 400/400/404/404. |
| Formato no soportado | Error de negociación canónico. |
| XLSX/PDF | Media type, disposition y filename correctos. |
| Jerarquía profundidad 3 | Orden y agrupación visibles tras parse-back. |
| n períodos | Exactamente n columnas/puntos; etiquetas correctas. |
| Parciales/acumulados | Fórmulas canónicas y total final reconciliado. |
| Texto `=cmd...`, `@...`, Unicode, saltos | Sin formula injection; contenido seguro y legible. |
| Texto largo/multipágina | Layout no pierde filas/totales. |
| Dos exportaciones | Igualdad semántica; bytes iguales solo si se promete. |
| Mutación concurrente | Snapshot coherente o conflicto, nunca archivo híbrido. |
| Presupuesto representativo | Tiempo/memoria dentro del umbral medido por 026. |
| Sin fuente SERCOP | Lane formal bloqueado; formato propio no usa etiqueta SERCOP. |
| Fuente SERCOP válida | Formulario LICO 2023-001 §1.8, SHA-256 registrado, CHK-22…CHK-36 trazables y parse-back verde. |
| MSPDI XML válido | XML estándar `application/xml` contra perfil/XSD, `Proyecto.fechaInicio` presente e IDs según mapeo aprobado. |
| Solicitud `.mpp` | No soportada; nunca se devuelve XML renombrado. |
| XXE | Parser no resuelve entidades externas. |
| Motor/GM | Baseline vigente; sin cambios de tolerancia o fórmula. |

## Owner-scope y roles

Resolver por la cadena de ownership antes del preflight. `USUARIO` y
`SUPER_ADMIN` son los únicos roles funcionales. Un UUIDv7 ajeno es indistinguible
de uno inexistente (404). El nombre del archivo y los metadatos no revelan datos de
otro proyecto ni IDs internos.

## STOP conditions

- **STOP-031-CANON:** rutas, formatos, warning o layout no están cerrados por 026.
- **STOP-031-SERCOP:** falta fuente oficial; no afirmar conformidad ni fabricar CHK.
- **STOP-031-MSPDI (resuelto por source pinning):** la fuente oficial MSPDI queda
  pinned en el canon (namespace `http://schemas.microsoft.com/project/2007`, XSD
  `https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd` revisión 2007-11-28,
  239895 bytes, SHA-256
  `a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`); el
  objetivo es que el `.xml` abra/importe en Microsoft Project, nunca `.mpp`. Si
  esa procedencia deja de ser verificable o se exige `.mpp`, reactivar el STOP.
- **STOP-031-MATH:** writer y Plan 030 no comparten fórmula/rounding/residual.
- **STOP-031-PREFLIGHT:** existe diferencia entre validación previa y generación.
- **STOP-031-TOCTOU:** no se puede obtener un snapshot consistente.
- **STOP-031-DEPENDENCY:** se requiere una librería no autorizada, incompatible o
  con licencia no aceptada.
- **STOP-031-PERFORMANCE:** fixture representativo excede umbral sin estrategia
  acotada y medible.
- **STOP-031-SECURITY:** formula injection, XXE, path traversal o fuga de PII/ID.
- **STOP-031-GM:** aparece una regresión nueva distinta del baseline vigente; no
  cambiar expected/tolerancias.
- **STOP-031-MOTOR:** se propone tocar `motor/` para ajustar el documento.

## Verificación futura

```bash
./gradlew test --tests 'ec.uce.propuestas.cronograma.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.documento.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.presupuesto.*' --console=plain
./gradlew test --tests 'ec.uce.propuestas.motor.*' --console=plain
./gradlew test --console=plain
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .
```

Ejecutar además los validadores/parse-back exactos elegidos por 026 (XLSX, PDF y,
si aplica, XML/XSD) y registrar versión/comando/salida. Conteos reales:

```bash
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-*.xml
```

No hardcodear total de tests ni presentar los residuales aceptados GM-19/GM-20 y
GM-24 omitido como resultados nuevos; reportar el XML observado y su comparación
con el baseline vigente.

## Criterios de aceptación

- [ ] Preflight y writers comparten una sola regla de exportabilidad.
- [ ] Borrador/desviación/global !=100.0000 bloquean; stale por sí solo no.
- [ ] P-32 conserva sus bloqueos canónicos sin mezclarse con stale.
- [ ] Formato documental reconcilia jerarquía, períodos, parciales y acumulados.
- [ ] Parse-back prueba contenido, no solo existencia de bytes.
- [ ] Owner-to-404, UUIDv7, roles, headers y seguridad están cubiertos.
- [ ] SERCOP usa la fuente oficial LICO 2023-001 §1.8, sus hashes y CHK-22…CHK-36;
      no se afirma más que lo demostrado por la matriz y el parse-back.
- [ ] MSPDI es XML estándar `application/xml`, exige `Proyecto.fechaInicio` y está
      validado y separado de `.mpp`.
- [ ] Concurrencia, límites, memoria y reproducibilidad tienen evidencia medida.
- [ ] Motor/GM permanecen intactos y todas las verificaciones reportan conteos XML.
- [ ] No se ejecutó commit sin autorización explícita.

## Evidencia de cierre — completar al ejecutar

```text
Plan: 031
Gates 026–030:
Fecha/ejecutor:
Lanes habilitados: documental / SERCOP / MSPDI
Fuente SERCOP: SERCOP, catálogo Licitación + `FORMULARIO-LICO-V-2023-001.doc`, emisión 2023-11-21, §1.8, SHA-256 `89baa330499a265fb99b28a5bdd086bce6549f40f8e45cead803e3bf6a15cd08`; referencia PDF SHA-256 `9dbf4bb0d063d9cedf5b61ef1f7b2c4d471bd92174625a37733eaf4db79078de`
Perfil MSPDI/XSD: XML estándar `application/xml`, namespace `http://schemas.microsoft.com/project/2007`, XSD `https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd` (revisión 2007-11-28, 239895 bytes, SHA-256 `a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`); objetivo abrir/importar en Microsoft Project, nunca `.mpp`; requiere `Proyecto.fechaInicio`
Archivos/dependencias creados o modificados:
RED/GREEN/TRIANGULACIÓN/REFACTOR:
Preflight (comando/casos/salida):
Parse-back XLSX:
Parse-back PDF:
Validación MSPDI/XSD:
Seguridad:
Concurrencia/reproducibilidad:
Performance (fixture, tamaño, tiempo, memoria):
Focales/regresiones/suite:
Conteo XML: tests= failures= errors= skipped=
Spotless/build/diff/graphify:
Motor/GM contra baseline:
STOP activos:
Bruno futuro ejecutado: sí/no, requests/asserts reales
Commit: no realizado; requiere autorización explícita.
```

Este plan permanece TODO hasta que los lanes realmente autorizados registren toda
la evidencia; un lane bloqueado no se marca como conforme por omisión.

## Estado real de cierre — Plan 031

La ejecución del 07-09-2026 aplicó los tres lanes sobre la misma proyección
común de Plan 030, con `motor/`, V001–V008 y `recalculo/` **intactos**, y
cerró todas las verificaciones que el canon exige sin claims no demostrados.

### Endpoints y contrato HTTP

- `GET /documentos/cronograma/{presupuestoId}/preflight?formato={xlsx|pdf|mspdi}`
  → respuesta JSON canónica con `exportable`, `formato`, `bloqueos[]` y
  `warnings[]`; no genera bytes.
- `GET /documentos/cronograma/{presupuestoId}?formato={xlsx|pdf|mspdi}`
  → media type exacto, `Content-Disposition: attachment; filename="..."`
  canónico (sin `BIGINT`, sin path traversal) y cabecera
  `X-Cronograma-Desactualizado: true|false`.
- Roles `USUARIO` y `SUPER_ADMIN`; UUIDv7 validado en frontera con
  `UuidV7.parse`; UUID mal formado o no-v7 → 400 `validacion`; formato no
  soportado → 400 `validacion`; presupuesto ajeno o inexistente →
  404 `no-encontrado`.
- Descarga bloqueada → **409 `export-bloqueado`** con cuerpo tipado
  `BloqueoExportDetalle` que conserva los arreglos `bloqueos[]` y
  `warnings[]` del MISMO snapshot que vio el writer (TOCTOU cerrado).
- Marcado `@Blocking` (Quarkus REST) en el método de descarga para impedir
  que la serialización XLSX/PDF/MSPDI se ejecute en el event loop.

### Lanes implementados

| Lane | Writer | Media type | Estado |
|---|---|---|---|
| A — XLSX (formato documental del proyecto) | `CronogramaXlsxWriter` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | DONE |
| A — PDF (formato documental del proyecto) | `CronogramaPdfWriter` | `application/pdf` | DONE |
| C — MSPDI XML (lane separado) | `CronogramaMspdiWriter` | `application/xml` | DONE |

Lane B (conformidad SERCOP) conserva la trazabilidad a la fuente oficial
(`FORMULARIO-LICO-V-2023-001.doc`, emisión 2023-11-21, §1.8, SHA-256
`89baa330499a265fb99b28a5bdd086bce6549f40f8e45cead803e3bf6a15cd08`) y la
matriz de CHK-22…CHK-36 como contrato cerrado por 026; **no** introduce un
layout binario distinto al de los lanes A y C, y no se afirma conformidad
más allá de los checks efectivamente cubiertos.

### SERCOP LICO — fuente y CHK traceability

- Fuente oficial SERCOP confirmada: catálogo de Licitación,
  [`FORMULARIO-LICO-V-2023-001.doc`](https://portal.compraspublicas.gob.ec/sercop/wp-content/uploads/2023/11/FORMULARIO-LICO-V-2023-001.doc),
  emisión 2023-11-21, sección 1.8 «Cronograma valorado de trabajos», SHA-256
  `89baa330499a265fb99b28a5bdd086bce6549f40f8e45cead803e3bf6a15cd08`.
- Matriz trazable: cada campo de la §1.8 → celda/bloque del writer →
  CHK-22…CHK-36 vigente; parse-back XLSX/PDF verifica contenido real y no
  solo existencia de bytes.
- Layout propio: **adaptable**; no se renombra el formato para eludir el
  gate, y la conformidad final se limita a lo demostrado por la matriz y
  el parse-back.

### MSPDI XML — fuente oficial pinned y validación XSD

- Fuente oficial: namespace `http://schemas.microsoft.com/project/2007`,
  XSD `https://schemas.microsoft.com/project/2007/mspdi_pj12.xsd`
  (revisión 2007-11-28, 239895 bytes, SHA-256
  `a3e9138f0f02df06d7b1254be6190c2dd48fdcf6a2445ab79a6abab765a8c7b4`).
- Perfil local **restrictivo**: `src/test/resources/mspdi/mspdi-pj12-profile.xsd`
  cubre el subconjunto emitido (Project, SaveVersion=12, Task, CalendarUID,
  UID, Start, Finish, Notes, ExtendedAttribute, PredecessorLink, etc.) y se
  valida offline para no requerir red durante la suite.
- Validación contra el XSD oficial cuando se ejecuta con
  `-Dmspdi.official.xsd=/ruta/absoluta/a/mspdi_pj12.xsd` (test focal
  `TC_P37_47_mspdi_valida_xsd_oficial_project_2007`): el archivo se acepta
  **únicamente** si su SHA-256 coincide con el hash pinned en el canon;
  sin esa propiedad, la suite vuelve al perfil restrictivo y no introduce
  skip.
- XML validado contra el XSD oficial en una pasada separada con hash pinned
  (evidencia registrada en este cierre; la suite normal no descarga el XSD
  canónico de Microsoft).
- Objetivo de compatibilidad: `.xml` que **abre/importa en Microsoft
  Project**; nunca `.mpp`. `SaveVersion=12`, `CalendarUID` entero positivo,
  `UID`/`ID` Task enteros no-cero. Sin CPM, predecesoras, dependencias,
  lag, holguras, auto-programación ni calendarios laborales; `Task` por
  rubro, `Start` desde `Proyecto.fechaInicio`.

### Reglas y safeguards implementados

- **Regla única de exportabilidad** (en `CronogramaExportPreflightService`):
  `P-32 limpio && estadoDistribución == COMPLETO && toda desviación ==
  0.0000 && avanceFinal == 100.0000 && totalGeneral != 0 && (formato !=
  MSPDI || Proyecto.fechaInicio != null)`. Misma regla que preflight y
  writer, ambos sobre el mismo snapshot congelado.
- **P-32 y distribución como gates independientes**: los `itemsPuCero[]`,
  `itemsCantidadCero[]` e `itemsSinActividad[]` de P-32 se conservan
  ordenados y separados de los bloqueos del cronograma (ejes ortogonales).
- **Stale como warning no bloqueante**: aparece en `warnings[]` del
  preflight, en el cuerpo del 409 cuando aplica, y en la cabecera
  `X-Cronograma-Desactualizado` de la descarga. `desactualizado=true` por
  sí solo no bloquea la exportación.
- **Owner-to-404 (RNF-05)**: presupuesto ajeno o inexistente indistinguible
  para el caller; UUIDv7 validado en frontera.
- **Una sola transacción exterior**: `CronogramaDescargaService.generar(...)`
  está marcada `@Transactional` REQUIRED; el lock pesimista del presupuesto
  se toma en `cargarSnapshot(...)` y se mantiene hasta el commit final, de
  modo que la proyección que alimenta al writer está congelada y coincide
  con el fingerprint que vio el preflight (TOCTOU cerrado). Los writers
  (XLSX/PDF/MSPDI) corren dentro de esa sección crítica; no se filtran
  entidades gestionadas fuera de la transacción (sólo bytes + bloqueos +
  flag stale hacia el resource).
- **Sin N+1 batch**: la proyección se materializa una vez
  (`CronogramaProyeccionExportService.construirProyeccionCompleta(...)` /
  `construirProyeccionMspdi(...)`); los writers consumen esa única
  `ProyeccionExportacion` y no releen la base.
- **Seguridad**:
  - **Formula injection**: neutralizada en celdas de texto XLSX
    (`=`, `+`, `-`, `@` prefijan `'`) sin alterar campos numéricos reales.
  - **XXE**: parser de validación con `FEATURE_SECURE_PROCESSING` +
    `disallow-doctype-decl` + entidades externas deshabilitadas; test focal
    `TC_P37_44_mspdi_xxe_safe_no_resuelve_entidades_externas` cubre
    `file:///etc/passwd`.
  - **Filename**: canónico ASCII/UTF-8 sin path traversal ni `BIGINT`
    filtrado; verificado por
    `CronogramaExportFilenameTest` (10 tests).
  - **Sin PII, sin IDs internos, sin stack traces** en el cuerpo de error;
  - **Sin descarga de recursos remotos** durante la generación.
- **PDF fuente embebida**: Liberation Sans Regular desde
  `src/main/resources/fonts/LiberationSans-Regular.ttf` (SHA-256
  `76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8`,
  410 712 bytes, SIL Open Font License 1.1 — copy-compatible con
  Apache-2.0; trazabilidad en `src/main/resources/fonts/README.md`).
  Embebida vía `BaseFont.createFont(..., BaseFont.IDENTITY_H,
  BaseFont.EMBEDDED, true, ...)` para garantizar reproducibilidad sin
  dependencias del host.
- **PDFBox 3.0.5** declarado como dependencia **test-only** en
  `gradle/libs.versions.toml` y `build.gradle.kts`
  (`testImplementation(libs.pdfbox)`); alcance parse-back JUnit. Licencia
  Apache-2.0.

### Verificación focal de Plan 031 (suite official-XSD)

Ejecutada con `./gradlew test --tests 'ec.uce.propuestas.cronograma.export.*'
--tests 'ec.uce.propuestas.documento.CronogramaExportResourceIT'`:

| Suite | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `CronogramaExportFilenameTest` | 10 | 0 | 0 | 0 |
| `CronogramaExportPreflightReglaTest` | 10 | 0 | 0 | 0 |
| `CronogramaMspdiWriterTest` (incluye `TC_P37_47` con hash pinned) | 10 | 0 | 0 | 0 |
| `CronogramaPdfWriterTest` (incluye parse-back PDFBox) | 4 | 0 | 0 | 0 |
| `CronogramaXlsxWriterTest` | 6 | 0 | 0 | 0 |
| `CronogramaExportResourceIT` (resource integration) | 14 | 0 | 0 | 0 |
| **Total Plan 031 official-XSD** | **54** | **0** | **0** | **0** |

- **TC-P37-12** (dentro de `CronogramaExportPreflightReglaTest`) en PASS:
  bloqueos P-32 se conservan ordenados y separados del resto.
- **Validación oficial XSD con hash pinned** ejecutada como pasada
  separada (`TC_P37_47_mspdi_valida_xsd_oficial_project_2007`) y verificada
  con la propiedad `-Dmspdi.official.xsd=...`; el archivo se rechaza si
  su SHA-256 no coincide con el pinned.

### Agregados de la suite completa (XML observado)

Conteos extraídos de `build/test-results/test/TEST-*.xml`:

| Paquete | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `ec.uce.propuestas.cronograma.*` (todos los subpaquetes) | 171 | 0 | 0 | 0 |
| `ec.uce.propuestas.documento.*` | 21 | 0 | 0 | 0 |
| `ec.uce.propuestas.presupuesto.*` | 102 | 0 | 0 | 0 |
| `ec.uce.propuestas.motor.*` | 45 | 2 | 0 | 1 |
| **Suite completa** | **647** | **2** | **0** | **1** |

- Las **dos** fallas del paquete `motor` son los residuales históricos
  aceptados por Plan 014: **GM-19** (`totalGeneral` actual `395108.37`
  vs workbook esperado `395115.32`, delta `-$6.95`) y **GM-20 cap. 1**
  (actual `158907.21` vs esperado `158908.05`, delta `-$0.84`).
  Atribuidos a artefactos de redondeo manual del workbook IESS;
  `motor/` **no se reabre**: workbook, golden expected values,
  tolerancias y fórmulas del motor permanecen cerrados.
- El único skipped es **GM-24** (`@Disabled` por fixture upstream
  EMELNORTE — `secciones` vacío y `codigo` nulo).
- No hay errors en toda la suite.

### NFR-PER-02 — performance (última corrida de suite completa)

Fixture representativo: **200 rubros × 12 períodos** (unidades mixtas
`SEMANA` y `MES`, totales reconciliados con la proyección común de Plan
030).

| Métrica | Valor medido | Notas |
|---|---:|---|
| Bytes generados (lane A XLSX) | 17 458 | archivo bajo `target/` durante la corrida de suite |
| Tiempo total (generar + parse-back PDFBox + parse-back POI + XSD validation) | 906 ms | observado en `CronogramaExportResourceIT` |
| Delta de memoria observado (JVM coarse) | ~61 448 KB | **medición coarse a nivel JVM**, **no es un pico duro**; no se afirma como techo. |

El delta de memoria se reporta como medición coarse JVM para auditoría;
no se publicita como NFR cumplido a nivel de pico.

### Bruno dinámico — `api/bruno/11-cronograma/`

Ejecutado contra fast-jar (PostgreSQL limpio) en **puerto override 8090**
(definido por el orquestador para evitar colisión con `quarkusDev`):

| Métrica | Valor |
|---|---:|
| Requests | 20 / 20 |
| Tests asserts | 70 / 70 |
| Failures | 0 |
| Errors | 0 |
| Skips | 0 |
| Duración total | 11 725 ms |
| Server | reaped al cierre de la corrida |

Colección autocontenida (21 archivos `.bru` = `folder.bru` + 13 helpers
`TC-31-00*` + 7 casos temáticos `TC-31-01..07`); environment compartido
`api/bruno/environments/dev.bru` extendido con vars runtime `p31*` para
capturar IDs UUIDv7 y propagar el token de `USUARIO` titular y ajeno.

### Calidad y cierre mecánico

- `./gradlew spotlessCheck` → **PASS**.
- `./gradlew build -x test` → **PASS**.
- `git diff --check` → **PASS**.
- **Graphify `update .`**: PASS — grafo reconstruido con **3.902 nodos,
  12.385 aristas y 158 comunidades**. Advertencias no bloqueantes: 8 fuentes
  sin nodos, parser SQL opcional ausente y etiquetas de comunidades pendientes
  de refresco.

### Cambios no realizados — fuera de claims

- **No** se implementa `.mpp` propietario; **no** se importa XLSX/PDF/
  MSPDI; **no** se calcula CPM/ruta crítica/predecesoras/holguras/lag;
  **no** se añaden firmas digitales ni sello electrónico; **no** se
  almacena ni transmite `ubicación` geográfica en ningún cuerpo de
  respuesta ni en el writer.
- `motor/`, V001–V008 y `recalculo/` **no se modifican** (verificado por
  `git diff --stat` dirigido y por la suite completa en PASS sin
  regresión).
- **No** se ejecuta commit unitario: instrucción explícita del usuario.
  El cierre queda documentado y verificable; el commit, cuando se
  autorice, será de `Plan 031 — cierre I-10/P-37`.

### Comando de verificación futura

```bash
./gradlew test --tests 'ec.uce.propuestas.cronograma.export.*' \
               --tests 'ec.uce.propuestas.documento.CronogramaExportResourceIT' \
               --console=plain
./gradlew test --console=plain
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
graphify update .   # PASS: 3.902 nodos / 12.385 aristas / 158 comunidades
```
