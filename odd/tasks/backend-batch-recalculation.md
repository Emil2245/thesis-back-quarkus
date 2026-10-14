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
- Unidad 2/checkout actual: `feat/backend-batch-recalculation-apu`, sobre unidad 1;
  commit `1c400a3c8c1b0e0fa52c30fc35765d4a80b66635`.
- Estrategia `auto-chain`, `chain_strategy=feature-branch-chain`; solo commits
  locales, sin merge a `main`, push, PR ni escrituras paralelas.
- Pronóstico original: 400–600 líneas código/pruebas + 20–40 documentación.
  Unidad 1 real: 343 líneas writer; 390 con seguimiento, cinco archivos.
  Unidad 2 real: 400 líneas writer; 585 con seguimiento, nueve archivos.
  La recuperación documental añade carga de revisión; conservar unidades separadas
  de la cadena elegida, sin recortar pruebas/claridad por el presupuesto.
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
- [x] B02 — CERRADO: grafo APU referenciado por lote, parámetros locales;
      300/1000 APUs con 11/11 statements y equivalencia exacta. Commit `1c400a3`.
- [x] B03 — CERRADO en alcance funcional dirigido: 18/18 PASS en 41 s;
      evidencia/límites en `46c8d0ba5dd5d318f5d22ce24bad3d45b9e65621`.
      Suite global incompleta: no afirmar validación global.
- [ ] B04 — EN CURSO, BLOQUEADO: resolver el bloqueo de la suite global (OOM/timeout),
      con causa aún desconocida; sin cambios de configuración ni fixes ajenos
      automáticos. No cerrar como PASS con XML histórico o sin resumen final.

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

7. B02 RED: 2110/7010 statements para 300/1000 APUs; snapshot/Motor exactos
   pasaron antes de fallar cota SQL. GREEN combinado APU/Tree 2/2 PASS:
   11/11 para 300/1000 APUs, 7/7 para 3/33 capítulos con APUs vacíos.
   Órdenes, precios heredados/override/null, HM 5%/7% por proyecto, CI alterno,
   insumos compartidos y APU no vinculado excluido comprobados. DB efímera JDBC
   32779, HTTP 35841, 20 migraciones y guard pre-write. Spotless final/docs03/diff
   PASS; test precede formato mecánico. Motor/servicios/schema/config sin cambios.
8. ASSESS B02 medium/modelo grande, verificación propia suficiente; riesgo de
   revisión por 585 líneas registrado, sin review RDD por decisión del usuario.

9. Grafo AST actualizado una vez: 27398 nodos, 63686 aristas, 1109 comunidades;
   backup graphify-out/2026-10-13. Límites: 84 fuentes sin nodos, parser SQL ausente
   para 109 archivos, etiquetas/hubs semánticos desactualizados. Sin instalación,
   relabel con API ni staging del repositorio exterior.
10. B03 global: SpotlessCheck y compilación completaron; test --rerun-tasks
    agotó 1200 s con OutOfMemoryError en tres métodos y un thread del worker:
    ApuDescargaConcurrencyIT.blockersNeverInvokeEitherRealWriter,
    ApuDocumentoEvidenceIT.fullSeedCapturePureOutputsAndServiceMeasurements y
    PresupuestoDescargaConcurrencyIT.blockersNeverInvokeEitherRealWriter.
    Sin resumen final ni XML nuevo: conteos frescos y cinco clases focalizadas
    no verificables en esa corrida. XML anteriores de 2/2 no prueban este intento.
    No se estableció causa ni atribución al código base/candidato; no hubo retry.
    Prueba de puerto de la DB de esa corrida no disponible en artefactos finales.
    Docs03/seguimiento, diff-check y nueve hashes B02 PASS; source sin cambios.

11. Diagnóstico posterior: Java Gradle PID 101773/101811 y test PID 102138
    activos en backend, creación reportada 2026-10-13 12:13:18–28 -05:00.
    No se probó propiedad de nuestra ejecución; no se terminaron procesos ni
    se inició la cohorte de cinco clases. Memoria disponible 19473392 kB no
    explica el OOM previo. Listeners 8081 preservados, sin conexión o modificación.
    No hubo nuevos formatter/Git/mirror checks en ese intento bloqueado.

12. Decisión explícita del usuario: no detener el proceso de pruebas y dejar la
    validación pendiente (preserve_active_tests). No se interrumpe ningún proceso
    ni se inicia otra comprobación. B03/B04 no cerrados; sin nuevo commit de cierre.

13. El usuario solicita continuar. La identificación directa posterior corrige
    la clasificación anterior: PID 101773 ejecuta gradle-wrapper.jar/quarkusDev,
    101811 es GradleDaemon y 102138 ejecuta propuestas-api-dev.jar (start_ticks
    1178034). Es el servidor de desarrollo, no un ejecutor de pruebas independiente.
    Inventario Java del backend: esos tres procesos; ningún test executor identificado.
    No se detuvo ningún proceso. La ausencia de perfil test en argv no prueba
    nada sobre variables de entorno; la identificación anterior era insuficiente.

14. Cohorte dirigida final: BUILD SUCCESSFUL en 41 s; test ejecutado, XML fresco
    18 pruebas, 0 fallos, 0 errores, 0 omitidas. VersionSnapshotApuBatchIT 1/1,
    VersionSnapshotTreeBatchIT 1/1, RecalculoServiceIT 7/7, ProyectoCiRecalculoIT 1/1,
    DocumentoSnapshotIT 8/8 (ec.uce.propuestas.documento.exportacion).
    SQL: 11/11 para 300/1000 APUs; 7/7 para 3/33 capítulos con APUs vacíos.
    Snapshot/Motor exactos y assertions de servicio, CI y documentos PASS.
    PostgreSQL 18 Dev Services: 32797 (guard batch de puerto generado antes de
    fixtures), 32795 (documentos); HTTP efímero 35145/39087. Sin conexión a 8081,
    señales, escrituras en DB del usuario ni cambios de heap/configuración.
    SQLState 25006 en consola (UPDATE en transacción read-only); XML sin fallos.
    Prettier/docs03/seguimiento y diff-check PASS antes de añadir esta evidencia;
    nueve hashes B02 coinciden; solo seguimiento dirty. SHA previo del seguimiento:
    3ea5476727a54680c3c413a258e639b232015ed00e67690bc8e9b29c61702986.
    Una ejecución filtrada, sin retry ni suite global. Resultado global anterior
    preservado como incompleto; esta cohorte no prueba heap, latencia ni concurrencia.

15. Cierre funcional dirigido B03: evidencia/límites preservados en commit local
    46c8d0ba5dd5d318f5d22ce24bad3d45b9e65621, solo seguimiento (78+/8-).
    Formato de la evidencia y diff-check nuevamente PASS antes del commit.
    Código y documentación de implementación sin cambios respecto de la cohorte.
    El cierre no incluye la suite global ni declara resuelto su OOM; B04 bloqueado.

Próximo paso: decidir por separado la investigación del bloqueo global B04;
no repetirla ni cambiar heap/configuración automáticamente. Implementación y
comprobación funcional dirigida terminadas en la cadena local, sin merge a main,
push o PR. Conservar servidor/daemons/PostgreSQL del usuario activos.
B04 conserva el fallo global y su causa desconocida; no rebajar aceptación ni
atribuirlo al código base. No nuevas auditorías ni review RDD.
