# Backend — recálculo completo con lecturas por lote

Reducir las consultas repetidas al construir el snapshot de una versión, sin
alterar cálculos, orden, resultados, persistencia ni consistencia transaccional.
El usuario autorizó implementar y verificar la mejora; eligió la opción 1,
cadena sobre una rama de trabajo. La investigación anterior permanece histórica.

## Base, alcance y entrega

- Base auditada: `c6288c92cd7987abbcf7ade604b76673f33f6520`.
- Rama de seguimiento: `feat/backend-batch-recalculation`.
- Primera unidad: `feat/backend-batch-recalculation-tree`.
- Segunda unidad: `feat/backend-batch-recalculation-apu`, sobre la primera.
- Estrategia: `auto-chain`; `chain_strategy=feature-branch-chain`.
- Pronóstico: 400–600 líneas de código/pruebas y 20–40 de documentación;
  registrar el tamaño real por unidad. No reducir pruebas ni claridad por tamaño.
- Sin merge a `main`, push, PR ni escrituras paralelas.
- Solo builder, repositorios estrictamente necesarios, pruebas focalizadas y
  documentación de esta mejora. Reutilizar métodos batch existentes primero.
- Fuera de alcance: Motor, servicios de recálculo/persistencia, sincronización
  del cronograma, bloqueos, migraciones, configuración, dependencias, búsqueda,
  proyección documental, XLSX y refactor independiente de ApuCalculoService.

## Tareas y rutas

- [ ] B00 — PENDIENTE DE CIERRE CON B01: runner desbloqueado con puerto
      efímero, sin modificar servicios/configuración; formato corregido, comprobar
      Spotless del snapshot final junto con el GREEN de la implementación.
- [ ] B01 — EN CURSO: cargar capítulos y rubros por lote, preservando árbol y
      orden; pruebas primero y verificación independiente. Ruta: writer delegado
      por modificación multiarchivo; verifier para pruebas y conteo SQL real.
- [ ] B02 — PENDIENTE: cargar APUs, secciones, detalles e insumos referenciados
      por lote y reutilizar resolución de proyecto/parámetros dentro del build;
      conservar semántica y comprobar escala 300/1000. Misma ruta delegada.
- [ ] B03 — PENDIENTE: verificar propagación y regresión en base aislada,
      documentar resultados/limitaciones, actualizar grafo AST y cerrar mediante
      commit local. Verifier delegado; padre responsable de commits y seguimiento.

## Aceptación y controles

- Comparar snapshots completos y resultados del Motor, no solo conteos.
- Cubrir capítulos anidados, órdenes naturales/empates, APUs compartidos,
  referencias ausentes, insumos nulos y datos de otros presupuestos conforme
  a la semántica anterior, sin inventar reglas nuevas.
- Conteo SQL real: consultas de colecciones no crecen por capítulo/APU/sección;
  fixtures aislados de 300 y 1000 APUs. Registrar alcance y límites de la métrica.
- Test-first cuando la prueba es determinista: observar RED antes de cambiar
  producción; GREEN, casos alternativos y refactor con comprobaciones pasando.
- Documentación pasiva: formato, enlaces e integridad; sin RED ficticio.
- Cada unidad cierra con código, pruebas y documentación verificados en un
  commit Conventional Commit; registrar su identidad y frontera de rollback.
- El usuario pidió implementación directa, sin SDD/RDD ni auditorías adicionales;
  concentrar las comprobaciones completas al final. Mantener solo la prueba mínima
  previa aplicable y el GREEN focalizado por unidad. Sin review nativo para esta
  mejora; no modificar el interruptor global. Verificación funcional final obligatoria.
- Registrar fallos previos y nuevos; no convertir retries, cancelaciones ni
  resultados históricos en PASS. No dar una suite global por verde si no lo está.

## Seguridad de las pruebas

El proyecto usa Gradle 9.5.1, no Maven. La configuración test sin JDBC URL usa
Quarkus Dev Services. RecalculoServiceIT vacía tablas: ejecutar únicamente en
PostgreSQL aislado de pruebas, nunca en la base viva de Tulcán.

Prefijo obligatorio, desde el backend, para los comandos Gradle test:

```bash
env -u DB_URL -u DB_USER -u DB_PASSWORD \
  -u QUARKUS_DATASOURCE_JDBC_URL -u QUARKUS_DATASOURCE_USERNAME \
  -u QUARKUS_DATASOURCE_DEVSERVICES_ENABLED QUARKUS_PROFILE=test \
  QUARKUS_DATASOURCE_DEVSERVICES_ENABLED=true \
  QUARKUS_DATASOURCE_USERNAME=quarkus_test \
  QUARKUS_DATASOURCE_PASSWORD=quarkus_test QUARKUS_HTTP_TEST_PORT=0 \
  ./gradlew --no-daemon test --tests '<clase focalizada>'
```

Verificar otra vez el aislamiento antes de ejecutar; no imprimir credenciales.
No ejecutar recálculos ni fixtures contra el backend/DB del usuario. Permitir
solo contenedores efímeros del runner existente; no reiniciar sus servicios.

## Evidencia y próximo paso

Rama actual: `feat/backend-batch-recalculation-tree`; base de seguimiento
`768d3728df6f93ddff0847681f7aed5ce8f0cb51`.

Primer intento B01: dos pruebas nuevas (171 líneas), producción sin cambios.
El test compiló, pero Quarkus falló al enlazar 8081 antes de ejecutar fixtures
ni assertions: RED no establecido. Dev Services creó PostgreSQL 18 efímero,
separado de Tulcán. SpotlessCheck falló por formato en ambas pruebas nuevas.
No se ejecutaron GREEN, regresión ni refactor; no hubo commit de comportamiento.

ASSESS: RDD off; riesgo no evaluable porque las pruebas nuevas sin seguimiento
requieren declaración explícita. Se mantiene verificación independiente obligatoria;
no cambiar autoridad ni configuración para eludir el diagnóstico.

Runner corregido: HTTP efímero 45579, PostgreSQL 18 Testcontainers efímero
`df382c2ac036`, puerto JDBC 32773, guard verificado antes de escribir fixtures;
8081 siguió escuchando. Sin cambios persistentes de configuración o dependencias.
Se corrigieron referencia no disponible a Testcontainers en compileTestJava,
RETURNING de parametros_proyecto (clave proyecto_id) y escalas canónicas del test;
los tres intentos test se conservan, no son tres ejecuciones GREEN.

RED de comportamiento establecido: 3 capítulos / 16 statements frente a
33 capítulos / 76 statements; snapshots completos y assertions exactas del Motor
pasaron antes de fallar la cota SQL. Dos artefactos nuevos: 214 líneas (141/73).
SpotlessApply focalizado pasó; el último Check antecede las correcciones finales,
por lo que falta comprobar el formato del snapshot final. Producción sin cambios.

Próximo paso: implementar B01 directamente y obtener GREEN focalizado; sin nuevas
auditorías ni revisión RDD. Pruebas completas de regresión al final. Usar solo
el backend real, no copias scratch; no tocar la base ni servicios del usuario.
