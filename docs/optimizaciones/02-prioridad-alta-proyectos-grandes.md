# O-02 — Prioridad alta preventiva para proyectos grandes

**Una sola recomendación de prioridad ALTA:** reducir las lecturas repetidas de
colecciones mediante cargas por lote y reutilización en la consolidación de una
versión completa. La estructura del código y un experimento SQL acotado respaldan
esta prioridad de escalabilidad; **no demuestran un incumplimiento de SLA, un
incidente de latencia ni una corrección obligatoria**. No se autoriza implementar.

## Alcance y trazabilidad

- Backend: rama `feat/backend-optimization-audit`, HEAD
  `c5f317fa8867b02fa5a435802c3fd032cb847489`; referencia de producto `0757d9a`.
- Caso: «Cetro Médico Tulcán» en la BD (Centro Médico Tulcán en la solicitud),
  UUID público `0192f6c4-7c8a-7abc-8000-000000001103`.
- Único presupuesto, versión vigente 1, ID interno local **2**: 33 capítulos,
  298 rubros, 298 APUs vinculados distintos (también 298 APUs totales),
  1192 secciones, 2583 detalles y 298 actividades.
- Se observó **un snapshot cercano a 300 APUs**, no datos reales de 800 o 1000.
  El objetivo 300–1000 APUs es un escenario de escala, no una medición existente.
- Continuación de [O-01](01-revision-inicial.md): sus seis candidatos genéricos
  siguen siendo provisionales, salvo la corroboración estructural y SQL aquí
  delimitada. No todo GET construye este snapshot ni se probó throughput.

## Por qué priorizar esta ruta

En [VersionSnapshotBuilder, líneas 92–106 y 162–258](../../src/main/java/ec/uce/propuestas/recalculo/internal/VersionSnapshotBuilder.java#L92),
la lectura de raíces inicia la construcción; cada capítulo lee hijos y rubros,
cada APU lee secciones y cada sección lee detalles. Además se resuelve el proyecto
mediante una consulta nativa por APU.

Para C capítulos visitados, A APUs construidos y S secciones, la familia directa
de invocaciones de consultas de lista es **1 + 2C + A + S = 1557** en este caso.
Al incluir la consulta nativa de proyecto por APU: **1 + 2C + 2A + S = 1855**.
Son conteos derivados del código, **no una traza SQL observada del método entero**.
`findById` por rubro puede usar caché L1; su SQL efectivo es desconocido. Las
lecturas de presupuesto, parámetros, insumos, ancestros y cronograma no están
incluidas en estas fórmulas.

Con cuatro secciones por APU, únicamente secciones y detalles implican 1500
llamadas para 300 APUs y 5000 para 1000. Es una proyección estructural, no un
benchmark de 1000 APUs ni una hipótesis de crecimiento fijo de capítulos.

La ruta importa por su alcance, no por una frecuencia medida:

- [RecalculoService, líneas 122–199](../../src/main/java/ec/uce/propuestas/recalculo/RecalculoService.java#L122):
  un APU vinculado o un insumo afectado puede terminar consolidando toda la versión;
  la consolidación llama al builder y a `Motor.consolidar`.
- Contraevidencia: ya se filtran los APUs afectados por insumo y se deduplican las
  versiones antes de consolidar. Se debe preservar esa selección.
- [VersionadoService, líneas 203–207](../../src/main/java/ec/uce/propuestas/presupuesto/service/VersionadoService.java#L203):
  la copia recalcula la nueva versión para mantener sus totales.
- [ApuCalculoService, líneas 75–94 y 128–174](../../src/main/java/ec/uce/propuestas/apu/service/ApuCalculoService.java#L75):
  `calcular` y `recalcular` vuelven a leer secciones y detalles del mismo APU.
  Su reutilización es una extensión condicional de la misma recomendación,
  **no un ahorro medido**; requiere revisar consistencia y parámetros efectivos.

## Evidencia SQL: costo local, no latencia del servicio

Una conexión persistente de `psql` y una única transacción de lectura repetible
compararon proyecciones estrechas de IDs, tipo y orden, sin descripciones ni
hidratación ORM completa. Hubo un caso por alternativa, sin análisis de varianza,
concurrencia o SLA.

| Familia   | SELECT individuales | Tiempo del bucle | SELECT por lote | Tiempo del lote |
| --------- | ------------------: | ---------------: | --------------: | --------------: |
| Secciones |                 298 |        32.692 ms |               1 |        1.660 ms |
| Detalles  |                1192 |        95.999 ms |               1 |        3.291 ms |

Los intervalos de reloj del servidor alrededor de `gexec` incluyen viajes de ida
y vuelta. No son muestras de `EXPLAIN`, tiempos HTTP, del Motor, de escritura ni
de rehidratación/cálculo completo. No se suman ni multiplican para proyectar
latencia. El prototipo redujo esas dos familias de **1490 SELECT a 2**; no existe
una implementación de producción de ese cambio.

Los 2583 detalles coincidieron por digest de la proyección ordenada:
`550ad7cf5aa53f0d54dbb75843b6dc59af7f157aebf6db6fadef94b9bc84633c`.
La equivalencia del payload de **secciones quedó sin verificar**: el parser local
rechazó el enum. SELECT exitosos no sustituyen esa comprobación ni prueban la
equivalencia de snapshots del Motor.

### Protocolo y consultas reproducibles

Se usaron `PGOPTIONS='-c default_transaction_read_only=on -c statement_timeout=15000 -c lock_timeout=1000'`
y el siguiente protocolo; los valores observados fueron `on`, `on`,
`repeatable read`, `15s` y `1s`, respectivamente:

```sql
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SHOW default_transaction_read_only;
SHOW transaction_read_only;
SHOW transaction_isolation;
SHOW statement_timeout;
SHOW lock_timeout;
-- Ejecutar las lecturas acotadas y sus comparaciones.
ROLLBACK;
```

Pareja de detalles (el marcador `:section_id` requiere enlace de parámetro;
no es un literal ejecutable). **El presupuesto 2 es local**: antes de reutilizar
la consulta debe verificarse su correspondencia con proyecto y versión.

```sql
-- Individual: repetir por cada sección del alcance verificado.
SELECT d.id, d.seccion_id, d.insumo_id, d.orden
FROM public.apu_detalle d
WHERE d.seccion_id = :section_id
ORDER BY d.orden, d.id;

-- Lote: misma proyección y alcance de APUs vinculados.
SELECT d.id, d.seccion_id, d.insumo_id, d.orden
FROM public.apu_detalle d
JOIN public.apu_seccion s ON s.id = d.seccion_id
JOIN public.apu a ON a.id = s.apu_id
WHERE a.presupuesto_id = 2
  AND EXISTS (
    SELECT 1
    FROM public.rubro r
    JOIN public.capitulo c ON c.id = r.capitulo_id
    WHERE r.apu_id = a.id AND c.presupuesto_id = 2
  )
ORDER BY a.id, s.orden, s.id, d.orden, d.id;
```

Para secciones se proyectaron `s.id, s.apu_id, s.tipo, s.subtotal, s.orden`:
individualmente por `s.apu_id = :apu_id`, orden `s.orden, s.id`; por lote,
`apu_id IN` los APUs del presupuesto 2 con el mismo `EXISTS` rubro/capítulo,
orden `s.apu_id, s.orden, s.id`. Los desempates por ID son del experimento,
**no autorizan cambiar el orden de producción**. Esta descripción no constituye
un script íntegro del arnés ni un artefacto nuevo de ejecución.

### Salvaguardas y fallos conservados

La ejecución corregida de `psql` terminó con **EXIT 0**, sin timeout. La original
terminó con **EXIT 3** por un `if` externo de `psql` sin pareja, después de
`ROLLBACK`: fallo del arnés, no error SQL; no se oculta como ejecución exitosa.
El digest de fuentes antes/después fue idéntico:
`6dd7108795929ae1a2a8c8a1162f1dc296750cacb16252c6d4fbcb6f0992e19b`.
La referencia de producto `0757d9a` no cambió. No hubo pruebas de producto,
llamadas API, recálculos ni escrituras de datos/configuración; tampoco nuevos
artefactos experimentales. Esto no afirma que todo el árbol esté limpio:
la metadata ODD del padre tiene cambios esperados.

## Candidatos que no alcanzan prioridad alta

- **CPU de proyección documental:** el filtrado repetido es estructuralmente
  O(A×S + S×D), pero falta costo/frecuencia de extremo a extremo. Un JShell
  sintético, no las filas reales, usó cuatro secciones/APU y
  D = ceil(2583/298 × A), con `Objects.equals`, Long reencajados, filtro/orden y
  agrupación HashMap. Temurin 25.0.3, G1, heap 256 MiB; tres calentamientos y
  cinco mediciones intercaladas, con checksums iguales impresos:

  | A / S / D generados | Visitas filtro → agrupación | Mediana filtro → agrupación |
  | ------------------- | --------------------------- | --------------------------- |
  | 298 / 1192 / 2583   | 3434152 → 9040              | 12.373 → 1.114 ms           |
  | 300 / 1200 / 2601   | 3481200 → 9102              | 11.541 → 0.247 ms           |
  | 1000 / 4000 / 8668  | 38672000 → 30336            | 121.166 → 0.249 ms          |

  La agrupación de 298 varió entre 0.921–1.379 ms. El cierre produjo un error
  de preferencias relacionado con `/dev/null` y TIMEOUT: **la ejecución completa
  no fue PASS**; los resultados son provisionales, no prueba funcional completa.
  No hubo rerun, ORM, render ni endpoint. Véanse
  [ProyeccionDocumentoService, líneas 139–195 y 253–264](../../src/main/java/ec/uce/propuestas/documento/exportacion/ProyeccionDocumentoService.java#L139)
  y [DocumentoSnapshotIT, líneas 386–432](../../src/test/java/ec/uce/propuestas/documento/DocumentoSnapshotIT.java#L386):
  la prueba cubre SQL, no CPU, y no se ejecutó aquí.

- **Copia de versión/bloqueos:** no se midió contención; no eliminar `FOR UPDATE`.
  `flush` no es commit ni justifica cambios de consistencia.
- **Búsqueda substring, conteo e índices/paginación:** no se probó un cuello de
  botella con planes; FTS/GIN y el límite de página 200 son contraevidencia.
- **Heap de XLSX:** no se observaron OOM ni fugas.
- **Jerarquía de capítulos O(C²):** 33 capítulos no sustentan prioridad alta.

Esta lista delimita exclusiones; no constituye un plan de optimizaciones menores.

## Siguiente paso, solo con autorización separada

Evaluar cargas por lote del **mismo presupuesto**, mapas por claves y reutilización
local del snapshot, revisando también la resolución nativa de proyecto por APU.
Preservar Motor canónico, BigDecimal, jerarquía, órdenes, APUs compartidos,
insumos nulos, UUIDv7 públicos/FK BIGINT, consistencia y bloqueos; no introducir
cambios injustificados de `flush` ni cachés entre versiones/transacciones.

Antes de aceptar una implementación:

1. Comparar snapshots antiguos y por lote, incluidos huérfanos, APUs compartidos,
   insumos nulos y órdenes iguales; resolver la equivalencia pendiente de secciones.
2. Medir SQL real, tiempo total de recálculo, heap y pool antes/después en fixtures
   aislados de 300 y 1000 APUs, nunca modificando la BD viva del usuario.
3. Determinar frecuencia de la ruta y SLA; comprobar que el ahorro de lecturas
   mejora el servicio sin alterar resultados ni consistencia.

**Cierre:** investigación documental concluida con una prioridad preventiva
acotada y límites explícitos, no certificación de rendimiento ni implementación
automática. El costo real del servicio completo y su frecuencia siguen desconocidos.
