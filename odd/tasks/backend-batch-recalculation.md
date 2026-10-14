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
- [ ] B04 — BLOQUEADO: resultado HISTÓRICO particionado anterior al fix V015,
      114/114 clases; 895 tests: 891 PASS/3 fallos/1 omitida. Ninguna clase pendiente.
      Fix posterior `88363f1`: ScheduleIT y reparación V018→V019, 2/2 PASS independientes.
      Bloqueos ACTUALES: dos aserciones de Motor y OOM monolítico sin resolver.
      Sin nueva suite completa; cobertura histórica no significa aprobación global.

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

16. El usuario autoriza continuar el pendiente B04. Registro original recuperado:
    /tmp/pi-bash-c8aa68b345a9e241.log, 545 líneas/53850 bytes, mtime
    2026-10-13T22:36:58Z, vinculado al comando global agotado a los 1200 s.
    Primer OOM reportado: ApuDescargaConcurrencyIT vía QuarkusTestExtension:668;
    los otros dos OOM y el worker se reportan después, sin causalidad demostrada.
    NPE de Motor anterior en consola, sin demostrar fallo de test o relación al OOM.
    Sin límites JVM, secuencia de perfiles, puerto DB ni total final originales.
    org.gradle.parallel afecta tareas de proyectos, no demuestra tests paralelos;
    settings declara un proyecto. Fuente sin fuga/resource leak confirmado.
17. Reproducción controlada de tres métodos: 3/3 PASS en 2m05s, XML fresco;
    probe Gradle help PASS en 14 s. Test min/maxHeapSize UNSET, maxParallelForks 1,
    forkEvery 0, JUnit parallel UNSET. Sin modificar esas políticas ni el heap.
    PG18 nuevos por perfil: 32799/32801/32803; HTTP 35439/46467/42969.
    Init diagnóstico ignorado build/b04-diagnostics/oom-repro.init.gradle;
    GC build/b04-diagnostics/gc-722355.log (267511 bytes). Source protegido sin
    cambios, HEAD ab3c36c y main 0757d9a, estado Git limpio tras la reproducción.
18. GC real de ese worker: máximo 536870912 bytes (512 MiB), antes de collection
    hasta 520093696 (496 MiB), después de GC hasta 507510784 (484 MiB).
    No confundir un valor tras young GC con memoria viva tras full GC. Fallo de
    evacuación 494M→244M recuperado durante el último método; el test pasó.
    Metaspace 448467K usado/452736K committed está fuera del cap del heap.
    Presión observada, no prueba de fuga ni causa original. Manifest de evidencia
    anterior fechado 2026-10-06 no prueba esta reproducción ni la corrida original;
    sus samples/JMX lifetime peaks no se atribuyen a allocations de una tarea.

19. Una ejecución global diagnóstica sin filtros, sin retry, falló en unos 14m20s:
    Gradle Test Executor 1 salió 3 por java.lang.OutOfMemoryError: Java heap space.
    Startup de clase/perfil ApuDescargaConcurrencyIT, 03:43:38.930–03:44:32.090Z
    del 2026-10-14; ningún método de esa clase llegó a empezar. 75 XML frescos:
    650 PASS/1 skipped, sin assertion failures/errors reportados antes de la salida.
    Son resultados PARCIALES, no total final ni aprobación de toda la suite.
    Artefactos ignorados: build/b04-diagnostics/full-20261014T033012Z-751782/;
    init full-diagnostic.init.gradle y GC full-gc-752338.log. G1 máximo 512 MiB;
    full compaction cerca de la salida 510M→510M(512M), 442.553 ms.
    Metaspace usado 1085625344 bytes, fuera del heap. Fallo de evacuación anterior
    en ventana Cmt. Presión acumulada compatible con contextos/bootstrap; no prueba
    de fuga ni objetos retenidos identificados. No se obtuvo histograma antes de salir.
    9 eventos de PG18 con puertos generados y 8 inicios test/HTTP registrados;
    no asignar puerto al perfil fallido sin evidencia de su clase.
    ExitOnOutOfMemoryError solo en el nuevo worker evitó hang; heap/forks intactos.
    Prettier de docs03/seguimiento y diff-check PASS antes de este registro.
    HEAD ab3c36c/main 0757d9a y nueve hashes protegidos iguales; solo seguimiento dirty.

20. El scout posterior no recuperó timeline en la carpeta diagnóstica: solo init
    y GC. Orden exacto de perfiles/fixtures batch previas al OOM no demostrado;
    fuente Quarkus 3.37.4 no accesible a ese scout, no prueba de fuga ni ausencia
    pública del tag. Ningún test ni cambio de código en ese análisis.
    El usuario ahora pide intentar completar las verificaciones finales después
    de ofrecer suite por grupos/JVMs independientes. Autoriza una ejecución
    particionada completa: forkEvery=1 temporal, maxParallelForks permanece 1,
    min/maxHeap sin modificar. Es un cambio de topología de verificación,
    no una corrección de la retención ni equivalencia de interacción entre clases.
    Mantener useJUnitPlatform y **/*Test.class / **/*IT.class sin filtros/exclusiones,
    assertions y timeouts originales; ningún ignoreFailures/retry/skip añadido.
    Inventario de candidatos compilados y XML/eventos frescos para cobertura real;
    resultados incompletos no se presentan como totales globales. Solo archivos
    ignorados build/b04-diagnostics para init, GC, inventario y salida guardada.
    Cada JVM Quarkus usa PG generado/perfil test/HTTP 0, nunca servicios del usuario.

21. Dos bloqueos del host, no resultados de tests: preflight PID 860940 incierto
    y posterior stall de cuatro minutos generando argumentos. PID ausente en
    snapshot 05:15:43Z, sin señales; conciliación 05:24:31Z sin carpetas particionadas
    ni worker test. No inferir propietario/cause de salida; warning codemode no
    demuestra causa y no se cambió Pi. Verificador nuevo con bash directo sí lanzó
    el intento; supervisor expiró tras 30 minutos con bash aún ejecutándose.
22. Recuperación read-only del intento partitioned-20261014T052854Z-880993:
    inventario 114 clases; 63 con resultado, subtotal 515 tests: 512 PASS/2 FAIL/1 skip.
    64 logs GC con máximo 512M, forkEvery=1/maxForks=1 reales. Última clase
    SnapshotSinAuxiliaresTest comenzó 05:58:54Z sin resultado; 51 candidatos pendientes.
    Sin XML, exit/root RUN_TOTAL ni BUILD final: no total global/completitud demostrada.
    Consola 172–176: MotorConsolidacionTest.GM_20_totales_capitulos_raiz_tulcan
    AssertionFailedError en línea 87; GM_19_total_general_tulcan en línea 50.
    Causa/mismatch y método omitido aún sin recuperar; no atribuirlos al batch.
    Timeline 05:58:55Z y consola 05:58:49Z; snapshot posterior sin testworker activo,
    solo desarrollo. Sin nuevas señales/fixtures/código durante recuperación.
    Logs/resultado binario propios conservados; no sustituirlos por XML antiguo.
23. El usuario confirma que la invocación SDD fue accidental: continuar sin SDD.
    Al reanudar, el ID del verificador anterior ya no aparece en el registro.
    Conciliación read-only: ningún directorio de continuación/lotes nuevos;
    66 registros de clase incluyen 3 duplicados, manteniendo 63 clases únicas
    y el mismo subtotal 512 PASS/2 FAIL/1 skip; 51 pendientes sin cambios.
    Snapshot acotado 2026-10-14T14:08:59Z sin Java con cwd del backend;
    no prueba propietario ni salida normal del proceso histórico. Sin señales.
    Continuación autorizada en verificador mv86zo58-2-b7xg, solo pendientes,
    con lotes/artefactos propios y límites anteriores. No repetir Motor fallido,
    corregir código/configuración ni aumentar heap; aún sin resultado final.
24. El verificador expiró por stall del host de cuatro minutos después de bash.
    El warning codemode no demuestra causa; no se modificó Pi. Recuperación
    read-only comprobó que sí ejecutó cinco lotes completos de ocho clases:
    continuación continuation-20261014T141927Z-21874, batch-01..05.
    Timeline, XML recursivos y exits/BUILD finales: 48/109/82/27/53 tests,
    exits 0/0/0/1/0. Son 40 clases nuevas, 319 tests: 318 PASS/1 fallo/0 skip.
    Total entonces: 103/114 clases, 834 tests: 830 PASS/3 fallos/1 skip.
    El scout inicial contó incompletamente XML/receipts; conciliación exacta
    posterior confirma ocho suites por lote. No usar sus conteos provisionales.
    Nuevo fallo real, no setup ni el NPE de consola: CentroMedicoTulcanScheduleIT.
    v015_assigns_cmt_activities_to_sequential_completed_monthly_batches_only(),
    AssertionFailedError en línea 31: expected 0, actual 298; cada actividad CMT
    debía tener una clave de período con avance completo. Causa no establecida.
    Batch APU/Tree, RecalculoServiceIT y ProyectoCiRecalculoIT PASS en batch-04.
    ENV_PROOF antes de clases; PG/HTTP públicos confirmados en XML system-out,
    no console.log. Cada lote conservó PostgreSQL efímero/perfil test/HTTP 0.
    Quedaron solo once clases sin resultados, ocho y tres de batch-06/07.
25. Últimos once ejecutados sin replay: batch-06 ocho clases/29 PASS, exit 0,
    14:49:35–14:52:37Z; batch-07 tres clases/32 PASS, exit 0,
    14:53:15–14:54:52Z del 2026-10-14. Artefactos propios:
    build/b04-diagnostics/continuation-20261014T141927Z-21874-final11-20261014T144935Z-57663/.
    XML/timelines/exits finales y PG/HTTP locales nuevos comprobados.
    Unión disjunta 63 originales + 51 continuación = 114/114 candidatos;
    895 tests, 891 PASS/3 FAIL/1 skip. Ninguna clase pendiente de ejecución.
    Los dos fallos Motor originales no se repitieron ni corrigieron; detalle
    de mismatch y método/motivo de skip siguen sin recuperar. El nuevo fallo
    de cronograma también conservado, sin atribución al código base/candidato.
    Cobertura completa CON FALLOS, no PASS global ni B04 resuelto; OOM intacto.
    SpotlessCheck, Prettier docs03/seguimiento y diff-check PASS antes de añadir
    esta evidencia. El verifier incluyó por error el seguimiento entre hashes;
    comprobación directa del padre confirma nueve/nueve hashes protegidos B02
    idénticos. HEAD ab3c36c/main 0757d9a intactos; solo seguimiento dirty.
    Sin código/configuración/heap modificados ni stage/commit/merge/push/PR.

Próximo paso: aclarar las dos aserciones de Motor pendientes, conservando resultados
históricos y el OOM monolítico por separado. No lanzar más tests ni cambiar Motor,
cronograma, fixtures, configuración o heap sin alcance acotado. El skip GM-24 ya
está explicado: falta el fixture APU upstream de EMELNORTE. La optimización y su
validación dirigida están completas; la regresión global no está aprobada.
Formato final de docs03/registro y diff-check PASS después de añadir la evidencia
histórica anterior. Sin merge/push/PR ni review RDD en aquel alcance.

### Autorización posterior de publicación — 2026-10-14

El usuario autoriza guardar ambos registros pendientes, integrar backend en `main`
por fast-forward y publicar normalmente a `origin` ambos repositorios (backend y
frontend), aceptando documentados los dos fallos baseline de Motor y el OOM.
Esta concesión posterior sustituye el límite histórico de solo feature/sin merge;
no convierte las pruebas globales en PASS. Commit/integración/push aún no se
acreditan aquí; el padre registrará los recibos después de ejecutarlos.
