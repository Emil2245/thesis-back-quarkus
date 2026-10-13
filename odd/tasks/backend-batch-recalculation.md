# Backend — recálculo completo con lecturas por lote

Implementar la optimización autorizada, conservando cálculos, orden, snapshots,
persistencia y consistencia. El usuario eligió cadena sobre rama de trabajo y
pidió ejecución directa, sin SDD/RDD ni auditorías adicionales; comprobaciones
completas al final, con prueba mínima previa y GREEN focalizado por unidad.

## Alcance y cadena

- Base auditada: `c6288c92cd7987abbcf7ade604b76673f33f6520`.
- Seguimiento: `feat/backend-batch-recalculation`, commit
  `768d3728df6f93ddff0847681f7aed5ce8f0cb51`.
- Unidad 1: `feat/backend-batch-recalculation-tree`, commit
  `556c10e61f50f7f58555f0be19fa1f96bef2b286`.
- Unidad 2/checkout actual: `feat/backend-batch-recalculation-apu`, sobre unidad 1.
- Estrategia `auto-chain`, `chain_strategy=feature-branch-chain`; solo commits
  locales, sin merge a `main`, push, PR ni escrituras paralelas.
- Pronóstico original: 400–600 líneas código/pruebas + 20–40 documentación.
  Unidad 1 real: 343 líneas writer; 390 con seguimiento, cinco archivos.
  Registrar tamaño real restante; no recortar pruebas/claridad por el presupuesto.
- Editar solo builder, repositorios batch indispensables, pruebas y documentación.
  Motor, RecalculoService/persistencia, cronograma, bloqueos, migraciones,
  configuración/dependencias, búsqueda, proyección documental y XLSX fuera de alcance.
- No refactor independiente de ApuCalculoService ni cachés entre builds/versiones.
  La investigación anterior sigue histórica; su autorización no se reescribe.

## Tareas

- [x] B00 — CERRADO: runner HTTP efímero y PostgreSQL aislado comprobados;
      formato final PASS. Cierre con B01 en `556c10e`.
- [x] B01 — CERRADO: capítulos/rubros batch; 11 statements para 3 y 33 capítulos,
      snapshots completos y Motor exacto equivalentes. Commit `556c10e`.
- [ ] B02 — EN CURSO: APUs/secciones/detalles/insumos referenciados por lote y
      reutilización local de proyecto/parámetros; equivalencia y escala 300/1000.
- [ ] B03 — PENDIENTE: regresión funcional en DB aislada, evidencia final,
      actualización AST del grafo y cierre local verificado.

Rutas: un writer delegado por cambios multiarchivo, verificaciones focalizadas
propias y comprobación funcional final por verifier según ASSESS. El padre posee
commits, seguimiento, espejo y decisiones; cada tarea cierra con commit Conventional.

## Aceptación

- Igualdad de snapshot completo y resultados exactos del Motor, no solo conteos.
- Jerarquía/profundidad/orden natural/empates, APUs reutilizados, insumos nulos,
  referencias ausentes y otros presupuestos conforme a semántica previa y esquema.
- SQL real de colecciones no crece por capítulo/APU/sección: fixtures 300/1000.
  Métrica local, no promesa de latencia HTTP, heap ni concurrencia no medidos.
- Preservar parámetros por proyecto, BigDecimal, UUID/FK, filtros y transacciones.
- RED observado antes de producción, GREEN tras cambio; documentación pasiva
  con comprobación estructural, sin pruebas ficticias ni retries presentados como PASS.
- Sin revisión RDD para esta mejora por decisión del usuario; no cambiar interruptor.
  ASSESS solo determina verificación ordinaria, no consentimiento/aprobación nativa.
- Frontera de rollback por unidad: builder/repos modificados, pruebas y docs03,
  sin retirar cambios ajenos de Motor, servicios o configuración.

## Runner aislado

Gradle 9.5.1, Quarkus 3.37.4. RecalculoServiceIT vacía tablas: nunca ejecutar
fixtures, recálculos ni APIs mutantes contra Tulcán o servicios del usuario.
Quarkus Dev Services crea PostgreSQL efímero; comprobar URL/puerto del nuevo
contenedor antes de fixtures. No leer/imprimir secretos. `.env` mantiene valores;
por eso el proceso test fija perfil y credenciales exclusivas de la DB de pruebas.

```bash
env -u DB_URL -u DB_USER -u DB_PASSWORD \
  -u QUARKUS_DATASOURCE_JDBC_URL -u QUARKUS_DATASOURCE_DEVSERVICES_ENABLED \
  QUARKUS_PROFILE=test QUARKUS_DATASOURCE_DEVSERVICES_ENABLED=true \
  QUARKUS_DATASOURCE_USERNAME=quarkus_test \
  QUARKUS_DATASOURCE_PASSWORD=quarkus_test QUARKUS_HTTP_TEST_PORT=0 \
  ./gradlew --no-daemon test --tests '<clase focalizada>'
```

SpotlessApply solo sobre rutas autorizadas usando `spotlessIdeHook`; Check final.
No instalar dependencias, persistir overrides ni detener/reiniciar servicios.

## Evidencia observada y límites

1. Preflight: test sin JDBC URL, Gradle/Dev Services aislado. Reporte histórico
   DocumentoSnapshotIT 8/8 en PostgreSQL Testcontainers, no prueba actual del builder.
2. Primer intento B01: 171 líneas de pruebas, producción intacta; inicio falló
   por 8081 ocupado antes de fixtures/assertions, no RED. SpotlessCheck falló en
   dos archivos. DB efímera `4e5c107d54f7`, puerto 32769; sin escrituras en Tulcán.
3. B00: HTTP 0 funciona; 8081 siguió escuchando. DB `df382c2ac036`, JDBC 32773,
   HTTP 45579, 20 migraciones y guard pre-write comprobados. Tres intentos test:
   referencia Testcontainers no disponible en compileTestJava, RETURNING incorrecto
   en parametros_proyecto (su PK es proyecto_id), finalmente RED SQL esperado.
   Se corrigieron escalas del test y formato (Apply tres veces, Check dos veces);
   aquel último Check precedía las correcciones finales, no era cierre de formato.
4. RED real: 3 capítulos / 16 statements y 33 / 76; snapshots/Motor exactos
   pasaron antes de fallar la cota SQL. Pruebas: 214 líneas (141 test, 73 helper).
5. B01 GREEN: una ejecución, 1/1 PASS y 11/11 statements. SpotlessCheck final,
   formato docs03 y diff-check PASS. DB `04ff373875f2`, JDBC 32775, HTTP 34247;
   guard antes de fixtures. APU/servicios/Motor sin cambios en esta unidad.
6. ASSESS inicial no evaluable por archivos nuevos sin declaración; tras declarar
   los artefactos B01, medium/modelo grande: verificación propia suficiente,
   sin verifier separado requerido. RDD off; sin review ni autoridad aprobada.

B02/B03 siguen pendientes. Siguiente paso: batch APU directo y comprobaciones
completas al final. Usar solo backend real: el grafo incluye copias scratch antiguas.
