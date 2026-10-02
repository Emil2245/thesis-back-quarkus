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
