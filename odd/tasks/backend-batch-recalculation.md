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
- ASSESS nativo tras writer; seguir plan de verificación. Review nativo solo
  bajo el interruptor RDD del usuario, sin inventar consentimiento o aprobación.
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
  -u QUARKUS_DATASOURCE_PASSWORD QUARKUS_DATASOURCE_DEVSERVICES_ENABLED=true \
  ./gradlew --no-daemon test --tests '<clase focalizada>'
```

Verificar otra vez el aislamiento antes de ejecutar; no imprimir credenciales.
No ejecutar recálculos ni fixtures contra el backend/DB del usuario. Permitir
solo contenedores efímeros del runner existente; no reiniciar sus servicios.

## Evidencia y próximo paso

Preflight de solo lectura: configuración test, wrapper Gradle, repositorios y
reporte histórico de PostgreSQL Testcontainers verificados; ninguna prueba actual
se ha ejecutado todavía. El grafo mezcla rutas antiguas: usar exclusivamente el
backend real, no copias scratch. Próximo paso: B01, una única escritura delegada.
