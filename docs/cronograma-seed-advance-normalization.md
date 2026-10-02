# Normalización de avances heredados (V018)

El contrato canónico transporta y persiste los valores de `avancePorPeriodo`
como strings decimales. El cálculo sigue usando `BigDecimal`; el parser estricto
normaliza la escala y no acepta números JSON como entrada API.

V004/V015 generaron números JSON en datos heredados. V018 es una migración
forward, exclusivamente de datos, para todos los mapas de `actividad`, no solo
CMT/UCE. Convierte únicamente valores numéricos de objetos JSONB a strings
mediante `numeric::text` de PostgreSQL: conserva precisión y escala sin redondear
ni pasar por `double`. Conserva claves, strings existentes y entradas inválidas;
no agrega padding ni corrige errores de dominio. Objetos vacíos, SQL NULL y
valores no objeto quedan intactos. Los datos malformados siguen sujetos al
rechazo del parser.

No modifica pesos, UUIDs, totales ni otros campos. Flyway ejecuta el UPDATE
atómicamente; repetirlo es un no-op semántico. V001–V017 permanecen intactas,
conservando sus checksums históricos.

`AvanceSeedMigrationTest` usa PostgreSQL aislado de Dev Services, aplica primero
V017 y luego V018, verifica precisión/preservación/idempotencia y ejecuta el
recálculo de APUs vinculadas de los seeds reales CMT/UCE, atravesando snapshot y
consolidación. La aplicación local de Flyway corresponde al operador después
de capturar el baseline; no se debe arrancar quarkusDev para validar este cambio.

## Aplicación local verificada (ADV02)

El fast-jar construido con `build -x test` aplicó V018 mediante Flyway al iniciar
contra PostgreSQL Compose (5436), después del baseline de solo lectura. Flyway
validó 18 migraciones y avanzó de 017 a 018; `/q/health` respondió `UP` en 8090.
Se conservaron los checksums V001–V017, los conteos de bases/insumos/plantillas/
actividades (7/2142/950/310) y sus digests de preservación. Los 370 valores de
avance quedaron como strings, sin números JSON; el digest completo de avances
fue `e4a1e5b87804a1c4cfa41cc9861e60e8` y el de los demás campos de actividad
permaneció `83f9ec35a5147a9baa6d8b1570bda259`.

Evidencia en `build/advance-normalization-{before,after,startup,health}.log`.
No hubo reset, reseed ni UPDATE manual. Se detuvo únicamente la instancia de
verificación mediante SIGTERM; Quarkus confirmó el cierre. El backend queda
apagado, como antes de la operación. Las 17 pruebas focales y Spotless pasaron.

## Corrección ponderada CMT (V019, CMT-01)

V018 corrige el tipo JSON, no el significado: V015 asignaba `1.0000` a cada
una de las 298 actividades (suma 298), aunque sus pesos suman `100.0000`.
V019 conserva cada período asignado y escribe el `peso_ponderado::text`
almacenado como avance. No recalcula pesos, precios, costos ni fechas.
V015 y las demás migraciones previas permanecen inmutables.

La selección exige nombre `Cetro Médico Tulcán`, código `CMT-2023`, versión 1,
MES/12 y exactamente 298 actividades. La huella MD5 de los pares `item:código`
de los 298 rubros V004 (separador `|`, orden textual C) protege la pertenencia
sin depender de BIGINT. Además, **todos** los mapas deben coincidir exactamente
con V015 tras V018: un solo string `1.0000` en el período calculado por
`ceil(rank * 12 / count)`, ordenando item numérico e ID de rubro. Si un mapa
difiere, no se modifica ninguna actividad del cronograma. Una programación
manual idéntica al patrón sí se corrige, conforme a la autorización explícita.
UCE y cualquier otra programación quedan intactos.

La revisión canónica describe el presupuesto (total y fingerprint de rubros),
no los avances. `CronogramaService.programarActividad` no invalida esos campos;
V019 tampoco los cambia ni fabrica fecha, fingerprint o aprobación. Sin una
revisión vigente, el preflight real devuelve `cronograma-desactualizado` como
**warning no bloqueante**; marcar revisado requiere la acción explícita del
usuario, no SQL de la migración.

`CmtSeedWeightedAdvanceMigrationTest` aplica Flyway 018→019 en Dev Services y
verifica 298 valores exactos, suma 100, períodos/pesos/revisiones/presupuesto
preservados, UCE intacto, idempotencia y no-op integral ante un mapa diferente
o una identidad/configuración distinta. El preflight real del seed limpio
queda exportable en XLSX/PDF/MSPDI, con warning stale y sin desviaciones.
Esto no verifica descargas, interoperabilidad ni el estado de la base local:
otros datos pueden conservar bloqueos P-32, total cero o fecha de inicio
faltante para MSPDI. El patrón temporal sigue siendo generado, **no fechas de
construcción verificadas**.

TDD focal: RED observado antes de crear V019 (18 pruebas, 1 fallo: Flyway no
encuentra target 19); GREEN posterior (18 pruebas, 0 fallos). La aplicación
local/reconstrucción Compose queda fuera de CMT-01 y se registra abajo.

## CMT-02: reconstrucción local verificada (2026-10-02)

Tras reset explícitamente autorizado y ejecutado por el padre, se levantó
PostgreSQL Compose y se confirmó `propuestas|0` tablas públicas. El fast-jar
aplicó realmente las **19 migraciones** sobre la base vacía; versión 019,
todas success. Instancia propia PID **958963** en **8090**, health HTTP 200/UP.

CMT quedó en MES/12 con **298** actividades: cada mapa conserva el período
V015 y contiene exactamente el peso almacenado como string decimal. Pesos y
avances suman **100.0000**, **cero desviaciones** y ninguna entrada numérica JSON
en los cronogramas. No se modificaron pesos ni costos. La huella revisada sigue
NULL; no se fabricó revisión. La revisión explícita del usuario elimina el
warning stale, que por contrato no bloquea exportación.

SELECT local confirmó precio unitario/cantidad distintos de cero, cobertura
completa, total positivo y fecha inicial presente en CMT. El preflight real
fue probado en Dev Services en CMT-01; **no se ejecutó preflight HTTP autenticado,
descarga, UI ni interoperabilidad local**. UCE conserva avance `9.000` y sus
12 desviaciones: borrador esperado, fuera del alcance de V019.

Daule se recargó mediante el operador existente: validación de release sin
regeneración, ensayo ROLLBACK, apply **1/1372/937**, verify, replay **0/0/0** y
verify final PASS. Los conteos y MD5 seed de 27 tablas permanecieron iguales
antes/después de esta carga; catálogos finales **6/2140/950**. Recibos y detalle:
[Seguimiento CMT-02](ingreso-datasets/SEGUIMIENTO.md#cmt-02-reconstrucción-con-v019-y-recarga-daule-2026-10-02).

Evidencia en `build/cmt-seed-{baseline,final}.json`,
`build/cmt-seed-local-preconditions.json` y logs `build/cmt-seed-rebuild-*`.
La instancia propia terminó mediante SIGTERM tras los checks; backend apagado,
PostgreSQL Compose levantado. Sin UPDATE manual, cambios al operador ni commits.
La reconstrucción elimina las ediciones locales previas conforme a autorización;
V019 por sí sola habría protegido el cronograma con una actividad editada.
