# 030 — Vistas de cronograma y desactualización

**Estado:** TODO — bloqueado por los Planes 026–029; no es una implementación.

**Iteración:** I-09. **Procesos:** P-35 y P-36.

> I-09 entrega tres proyecciones distintas sobre una sola programación: Gantt
> editable, cronograma valorizado y curva S. No crea tres modelos persistentes ni
> recalcula el dominio en el frontend.

## Objetivo medible

Una ejecución futura debe demostrar que:

1. el backend expone, mediante las rutas y DTOs aprobados en 026, read models
   separados para Gantt, cronograma valorizado y curva S;
2. las tres vistas usan la misma actividad, el mismo mapa de avances y los mismos
   totales, con orden estable y jerarquía recursiva de capítulos visible;
3. el cronograma valorizado presenta los datos de rubro y valores por período,
   además de monto/porcentaje parcial y acumulado, con fórmulas canónicas exactas;
4. la curva S devuelve una serie ordenada de avance acumulado y no persiste puntos
   duplicados;
5. mover/redimensionar desde Gantt reutiliza los comandos semánticos de 029 y nunca
   envía coordenadas de píxeles;
6. un cambio relevante del presupuesto activa una alerta `desactualizado`, sin
   borrar la programación ni bloquear por sí solo la exportación;
7. marcar revisado es owner-scoped, idempotente, registra el snapshot canónico y no
   corrige ni completa distribuciones;
8. precisión, query count y tamaño de respuesta se verifican sobre un presupuesto
   representativo, sin consultas N+1.

## Dependencias y gates

| Gate | Condición | Resultado requerido |
|---|---|---|
| G0 — 026 | Contrato canónico aprobado para rutas, DTOs, fórmulas, jerarquía, revisión y exportabilidad. | Ningún shape o cálculo pendiente. |
| G1 — 027/028 | Identidad/persistencia y ciclo de vida operativos. | Cronograma owner-scoped legible. |
| G2 — 029 | Mapas, segmentos, estados, sincronización y snapshot persistente implementados. | Una fuente de verdad estable. |
| G3 — stale | El canon detecta todos los cambios relevantes, incluso cambios compensados que conservan el total general. | Mecanismo de revisión suficiente. |
| G4 — dinero | La conversión avance↔monto y su rounding de presentación están definidos una sola vez. | Paridad exacta entre tabla, curva y export. |
| G5 — cierre | Focales, regresiones, performance, calidad y Graphify medidos. | Evidencia literal y conteos XML reales. |

`total_general_revisado` por sí solo no detecta dos cambios compensados que mantienen
el mismo total pero alteran los pesos. Si 026 no resuelve esta brecha con un
marcador/revisión canónica suficiente, activar `STOP-030-STALE` antes de codificar.

## Fuentes que deben releerse

- `docs/modulos/06-cronograma/00.md` y Planes 026–029.
- `../thesis-docs/plan/architecture/06-database-schema.md` §2.13 y §17.
- `../thesis-docs/plan/architecture/07-api-contract.md` §7 y Apéndice B, ya
  reconciliado por 026.
- `../thesis-docs/plan/architecture/08-codebase-design.md` §3–§6 y frontend §8.
- `../thesis-docs/plan/domain/02-data-model.md` §13 y §16–§17.
- `../thesis-docs/plan/design/03-procesos-detalle.md` §F/P-35/P-36.
- `../thesis-docs/plan/quality/02-catalogo-pruebas.md` TC-P35/TC-P36 y casos de 026.
- `../thesis-docs/DOCUMENTOS/entrevistas/05/N05_entrevista-cronograma.md` §§2, 4–6.
- `../thesis-docs/res/ESTANCIA-ACADEMICA/CRONOGRAMA VALORADO-signed.pdf` o la
  ubicación que el canon confirme, solo como ejemplo aceptado de presentación.
- `src/main/java/ec/uce/propuestas/motor/VersionCalculada.java` y snapshots de
  cronograma, únicamente para usar el resultado existente.
- Implementación final de `recalculo`, presupuesto y cronograma resultante de 029.

La referencia `../ingepresupuestos/views/cronograma_view.py` puede aclarar UX de
vistas, pero no define API, arquitectura, esquema, fórmula ni funcionalidad CPM.

## Estado inicial esperado

Al iniciar 030 deben existir cronogramas owner-scoped, actividades 1:1, mapas de
avance, segmentos derivados y estados de distribución. El backend actual previo a
esos planes no contiene recursos de vista. El esquema baseline conserva
`total_general_revisado` y `fecha_revision`, pero el Plan 026 debe haber decidido si
son suficientes o si se requiere una revisión/fingerprint adicional mediante una
migración planificada.

El PDF aceptado evidencia una hoja valorizada con jerarquía visible, columnas
`NUM.`, descripción, unidad, cantidad, precio unitario, precio total y períodos,
más filas de monto/porcentaje parcial y acumulado. La extracción parece repetir los
porcentajes parciales en la fila acumulada; ese posible error de ejemplo no se
convierte en fórmula.

## Alcance

- Proyección base común y tres DTOs/read models diferenciados.
- Jerarquía recursiva de capítulos/subcapítulos con actividades/rubros en orden.
- Gantt con segmentos y metadatos para aplicar comandos de 029.
- Tabla valorizada con valores por período y filas parciales/acumuladas.
- Curva S separada con serie acumulada.
- Estado de distribución, desviaciones, exportabilidad cuantitativa y alerta de
  desactualización visibles en las vistas relevantes.
- Acción canonizada de marcar revisado y su auditoría mínima.
- Performance, read-only e igualdad entre proyecciones.

## Fuera de alcance

- Persistir vistas, puntos de curva, acumulados, segmentos o totales de display.
- Implementar componentes frontend, drag-and-drop o gráficos.
- Exportar archivos (Plan 031).
- Corregir automáticamente una distribución desactualizada.
- CPM, dependencias, lag, ruta crítica, fechas e hitos.
- Cambiar aritmética o records de `motor/`.
- Convertir `desactualizado` en sinónimo de `BORRADOR` o en bloqueo de exportación.

## Archivos posibles

| Acción | Archivo posible | Condición |
|---|---|---|
| Crear | `src/main/java/ec/uce/propuestas/cronograma/dto/<GanttResponse>.java` | Shape exacto de 026. |
| Crear | `.../dto/<CronogramaValorizadoResponse>.java` | Jerarquía y valores canónicos. |
| Crear | `.../dto/<CurvaSResponse>.java` | Serie parcial/acumulada separada. |
| Crear/modificar | `.../service/<ProyeccionCronogramaService>.java` | Una consulta/proyección común, sin duplicar fórmula. |
| Crear/modificar | `.../resource/<recursos-canónicos>.java` | Solo rutas aprobadas. |
| Modificar | Repositorios del módulo cronograma | Fetch ordenado y owner-scoped sin N+1. |
| Modificar | DTO base de cronograma | Solo campos comunes aprobados. |
| Crear/modificar condicionalmente | Migración/entidad de revisión | Únicamente si 026 determinó que el snapshot actual es insuficiente; de lo contrario STOP y plan previo. |
| Crear | `src/test/java/ec/uce/propuestas/cronograma/<suites-vistas>.java` | Fórmulas, shapes, seguridad y performance. |
| No previsto | `src/main/java/ec/uce/propuestas/motor/**` | Cualquier cambio activa STOP. |

No se inventan nombres definitivos ni endpoints. Si el Plan 026 no aprobó las tres
rutas o una ruta con tres representaciones negociadas, primero se corrige el canon.

## Modelo de lectura

### Proyección común

Debe contener al menos:

- IDs públicos UUIDv7 de presupuesto, cronograma, actividad y rubro;
- `unidadTiempo`, `numeroPeriodos` y períodos 1..n;
- jerarquía recursiva de capítulos con `item`, descripción y orden;
- por rubro: item, descripción, unidad, cantidad, precio unitario y precio total;
- por actividad: peso, mapa de avances, desviación, estado y segmentos derivados;
- totales parciales/acumulados, estado global, exportabilidad y alerta de revisión.

Los FK `BIGINT` nunca atraviesan el mapper. Los datos de rubro se leen del
presupuesto; no se duplican como columnas editables en actividad.

### Gantt

La respuesta expone filas jerárquicas y segmentos en unidades de período, no fechas
ni píxeles. El frontend traduce drag/resize al comando semántico definido por 026 y
probado en 029. Leer Gantt no escribe ni marca revisado.

### Cronograma valorizado

El Plan 026 debe fijar una fórmula única para convertir el avance ponderado global
en monto por actividad/período. La fórmula no puede inferirse solo del PDF. Debe
cumplir simultáneamente:

- para una actividad completa, la suma monetaria por períodos reconcilia con el
  precio total del rubro según la regla de display/export;
- el monto parcial del período es la suma de sus actividades;
- el monto acumulado es la suma de montos parciales 1..t;
- el porcentaje parcial es `Σ avance_i,t`;
- el porcentaje acumulado es `Σ porcentajeParcial_1..t`, no una repetición del
  parcial;
- el último acumulado completo reconcilia con el total del presupuesto y
  `100.0000` bajo las reglas canónicas.

El cálculo usa `BigDecimal` y precisión natural. `app.display.precision` y
`precision-porcentaje` afectan serialización/presentación cuando el contrato lo
indique, no almacenamiento ni estado de completitud. Cualquier residual monetario
debe tener asignación determinista documentada.

### Curva S

La serie contiene un punto por período 1..n con porcentaje parcial/acumulado y,
si el canon lo autoriza, monto parcial/acumulado. El acumulado es monótono cuando
los avances son no negativos. La curva usa la misma proyección que la tabla; no
recalcula en otra clase ni acepta puntos enviados por el cliente.

## Desactualización y revisión

Son dos ejes independientes:

- `estadoDistribucion`: borrador/completo según 029;
- `desactualizado`: el presupuesto cambió después de la revisión canónica.

La detección debe cubrir cambios de cantidad, precio, alta/baja/reordenamiento de
rubros u otra mutación que altere los pesos o la proyección, incluso si el total
general final coincide con el revisado. El Plan 026 debe definir una revisión
confiable (por ejemplo, revisión/version/fingerprint canónico); este plan no escoge
una solución fuera del canon.

Marcar revisado:

1. resuelve owner y bloquea el agregado;
2. captura el marcador actual aprobado;
3. actualiza fecha/marcador de revisión;
4. devuelve la proyección fresca;
5. no cambia pesos, mapas, segmentos, borrador ni completitud.

Repetir el comando sin cambios es idempotente semánticamente según 026. Una carrera
con una mutación presupuestaria no puede limpiar una alerta sobre datos distintos a
los realmente revisados.

Una distribución completa y desactualizada puede exportarse mostrando warning. Una
distribución borrador no puede exportarse aunque esté revisada.

## Contrato REST y seguridad

Usar únicamente las rutas aprobadas por 026, incluido
`POST /cronogramas/{id}/revisado` si fue conservado. Cada read model es explícito;
no usar un query parameter ambiguo que cambie silenciosamente el shape salvo que
así lo haya canonizado 026.

- UUID malformado/no-v7 → 400 `validacion`.
- UUIDv7 inexistente o de otro owner → 404 `no-encontrado`.
- actividad/rubro cross-presupuesto → 404.
- roles funcionales: `USUARIO`, `SUPER_ADMIN`.
- las lecturas son read-only y no actualizan `fechaRevision`.

## Secuencia TDD

### RED

1. Escribir contract tests separados para Gantt, valorizado y curva S.
2. Escribir tests de jerarquía profunda, orden y arrays exactamente de longitud n.
3. Fijar fixtures pequeños con cálculos manuales exactos y un caso con residual.
4. Añadir borrador, completo, desactualizado y sus combinaciones.
5. Añadir cambio compensado de rubros que conserva total pero cambia pesos.
6. Añadir revisado idempotente, carrera revisión/mutación y owner-to-404.
7. Añadir guard de query count/tiempo con fixture representativo.

### GREEN

Construir una proyección común owner-scoped y mappers finos para las tres
respuestas. Implementar revisión solo con el mecanismo aprobado. No persistir
derivados ni tocar el motor.

### TRIANGULACIÓN

Comparar los tres read models período por período; usar un capítulo, profundidad 3,
rubros en huecos, 1 período y límite máximo; verificar presupuesto vacío/total cero,
completo, borrador y stale con total igual.

### REFACTOR

Eliminar cálculos duplicados y N+1. Mantener una función interna por concepto
canónico; mappers solo transforman. No exponer helpers profundos como API pública.

## Catálogo mínimo de pruebas

| Caso | Resultado |
|---|---|
| Jerarquía profundidad 3 | Capítulos/subcapítulos/rubros en orden canónico. |
| Gantt no consecutivo | Segmentos iguales a 029; huecos conservados. |
| Read Gantt repetido | Misma respuesta; cero escrituras. |
| Valorizado completo | Rubro y total reconcilian según fórmula canónica. |
| Porcentaje parcial/acumulado | Parcial por período; acumulado suma 1..t. |
| Monto parcial/acumulado | Misma regla y total final canónico. |
| PDF con fila acumulada errónea | La implementación sigue fórmula, no el error visual. |
| Curva S | n puntos ordenados, acumulado monótono y final coherente. |
| Paridad de vistas | Mismos avances/totales para el mismo snapshot. |
| Borrador | Valores y desviaciones visibles; no se finge 100.0000. |
| Completo | Todas las desviaciones 0.0000 y final 100.0000. |
| Cambio de total | `desactualizado=true`; avances conservados. |
| Cambio compensado con mismo total | También desactualizado; prueba el mecanismo robusto. |
| Alta/baja/reordenamiento de rubro | Alerta según canon y jerarquía fresca. |
| Marcar revisado | Limpia solo alerta del snapshot realmente revisado. |
| Marcar revisado dos veces | Idempotencia canónica, sin cambiar distribución. |
| Carrera revisar/mutar | No pierde alerta ni captura snapshot incorrecto. |
| Completo + stale | Exportabilidad cuantitativa true con warning. |
| Borrador + revisado | Exportabilidad false. |
| Total cero/presupuesto vacío | Respuesta/error exactamente canonizado; sin división por cero. |
| UUID inválido/no-v7/inexistente/ajeno | 400/400/404/404. |
| Query count representativo | Dentro del umbral medido y aprobado; sin N+1. |
| Campos JSON | Ningún `BIGINT`, decimal binario o campo de otra vista. |

## STOP conditions

- **STOP-030-CONTRACT:** 026 no fija tres vistas, shapes o rutas.
- **STOP-030-STALE:** el mecanismo solo compara total y pierde cambios compensados.
- **STOP-030-MATH:** no existe fórmula canónica avance↔monto, acumulado o residual.
- **STOP-030-ZERO:** total cero/presupuesto vacío no tiene resultado definido.
- **STOP-030-MOTOR:** se propone modificar el motor o duplicar su cálculo.
- **STOP-030-NPLUS1:** el fixture representativo revela consultas por fila y no hay
  estrategia compatible con el diseño aprobado.
- **STOP-030-SCHEMA:** revisión robusta requiere migración no prevista; volver a 026
  y crear/ajustar el plan de persistencia antes de continuar.
- **STOP-030-SCOPE:** se intenta implementar frontend, exportación o CPM.

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
graphify update .
```

Conteos reales:

```bash
grep -ho 'tests="[0-9]*"\|failures="[0-9]*"\|errors="[0-9]*"\|skipped="[0-9]*"' \
  build/test-results/test/TEST-*.xml
```

Registrar también el método y resultado del query-count/performance; no fijar en
este plan un número inventado sin baseline representativo.

## Criterios de aceptación

- [ ] Hay tres read models separados sobre una sola proyección.
- [ ] Jerarquía, orden, segmentos y n períodos son deterministas.
- [ ] Fórmulas monetarias, parciales y acumuladas son canónicas y exactas.
- [ ] Curva S y valorizado coinciden período por período.
- [ ] La detección stale cubre cambios compensados con total igual.
- [ ] Revisado es owner-scoped/idempotente y no modifica distribución.
- [ ] Stale no bloquea exportación; borrador sí.
- [ ] No hay N+1, persistencia de derivados, `BIGINT` público ni cambio en motor.
- [ ] Suite/calidad/Graphify y conteos XML tienen evidencia real.
- [ ] No se ejecutó commit sin autorización explícita.

## Evidencia de cierre — completar al ejecutar

```text
Plan: 030
Gates 026–029:
Fecha/ejecutor:
Archivos creados/modificados:
RED/GREEN/TRIANGULACIÓN/REFACTOR:
Fórmulas canónicas verificadas:
Paridad Gantt/valorizado/curva:
Stale con total distinto:
Stale con total compensado:
Revisado/concurrencia:
Performance/query count (fixture, comando, resultado):
Focales y regresiones:
Conteo XML: tests= failures= errors= skipped=
Spotless/build/diff/graphify:
STOP activos:
Motor diff: sin cambios / STOP
Commit: no realizado; requiere autorización explícita.
```

Este plan permanece TODO hasta registrar la evidencia completa.
