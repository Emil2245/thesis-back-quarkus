# 03 — Ensayo de carga y recuperación

**Estado: ensayo DB ejecutado — dry-run ROLLBACK PASS.**
La carga local posterior y replay confirmaron 1/1372/937 y 0/0/0; dos verify
exactos PASS. Evidencia remitida por el padre en [SEGUIMIENTO](SEGUIMIENTO.md).
No se probaron runtime/API, concurrencia ni recuperación de commit incierto real.
Revisión independiente final de datos PASS, resultado global PARCIAL; no declarar
ensayo integral runtime completado. Los pasos siguientes siguen siendo instrucciones reutilizables.

## Objetivo y dependencia previa

Demostrar inserción, repetición NOOP y recuperación segura antes del destino real.
Depende de [02 sellado](02-mapeo-y-preparacion.md) y entorno desechable existente
con fixture de proyecto de usuario existente expresamente autorizado.

## Entradas

- [Preparación](02-mapeo-y-preparacion.md) y acta enlazada en [SEGUIMIENTO](SEGUIMIENTO.md).
- [V001](../../src/main/resources/db/migration/V001__baseline.sql)
  y [patrón V012](../../src/main/resources/db/migration/V012__seed_catalogo_plantillas_apu.sql).
- [Mapper](../../src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java)
  y [resolver](../../src/main/java/ec/uce/propuestas/plantilla/service/ResolverInsumoPlantillaService.java).
- Manifiesto, identidad, payload y operador externos sellados por 02.
  Sus rutas definitivas se registran antes de autorizar el ensayo.

## Superficies y acciones futuras permitidas

Únicamente tras permiso: sesión de carga en entorno desechable existente,
TEMP staging por sesión, inserciones en `base_insumos`, `insumo`, `plantilla_apu`
existentes y evidencias externas. Adopción runtime solo sobre fixture autorizado,
con efectos enumerados de antemano; no implica permiso en producción.

## Acciones prohibidas

Crear dummy proyectos/APUs/presupuestos o plantillas de proyecto; resetear entorno,
borrar seeds/datos, migrar, arrancar migrate-at-start como carga, alterar backend.
No probar mismatch cambiando filas existentes. No generar identidades nuevas
para reintentar la misma release; no `DO UPDATE` ni retries ciegos.

## Pasos

1. Confirmar hashes y durabilidad del mapa de UUID antes de abrir escritura.
2. Repetir preflight de instancia y revisar baseline de IESS/SISTEMA y datos
   ajenos. Registrar restricciones, conteos y contenido relevante sin PII.
3. Abrir transacción y tomar bloqueo transaccional exclusivo de operadores,
   con la misma clave/protocolo para todos los cargadores; mantenerlo desde la
   revalidación hasta commit/rollback. Coordinar otros escritores: ese bloqueo
   no serializa automáticamente escrituras ajenas que no lo usan.
4. Revalidar bajo bloqueo códigos/tipos y UUID/contenido. Usar UNIQUEs existentes
   como defensa adicional; no agregar constraints para la carga.
5. Introducir payload mediante COPY stdin a TEMP staging; comprobar conteos,
   referencias, longitudes y whitelist antes de INSERT/SELECT.
6. Admitir solo ausencia completa o replay completo exacto; estado parcial=STOP.
   Obtener BIGINT por defaults y resolver asociación por UUID durable;
   reconciliar presentes por `public_id` y contenido. Mantener base/plantillas
   aisladas, sin usar nombre como identidad.
7. Leer payload de vuelta dentro de transacción: pertenencia a base, códigos,
   tipos y JSONB estructural completo. Comparar arrays en su orden, sin depender
   del orden textual de claves JSONB. Discordancia implica rollback y STOP.
8. Confirmar commit; emitir recibo externo solo después. Si su resultado es
   desconocido, detener escritura y buscar UUIDs preparados/contenido en nueva
   lectura autorizada. Todo coincide=recuperar recibo; nada existe=decidir nuevo
   intento explícito; parcial/mismatch=STOP. Nunca reintentar a ciegas.
9. Repetir la misma release sellada: debe resultar NOOP sin nuevas filas.
   Cubrir mismatch mediante payload alternativo solo para validación/rechazo,
   no mutando datos ni redefiniendo el archivo de identidad original.
10. Verificar uso de plantillas en fixture autorizado: copia/reuso por código y
    tipo, orden, HM desde proyecto, CI y precios de proyecto. No exigir total
    histórico. Incluir E53/E54 distintos y variante HM al 1% si admitida.

## Comprobaciones

- [ ] Primera corrida coincide con conteos admitidos y contenido preparado.
- [ ] Segunda corrida idéntica NOOP; mismatch rechaza antes de mutación.
- [ ] Recuperación por UUID documentada, sin recibo previo a commit.
- [x] Baseline previo MD5 de nueve tablas confirmado independientemente;
  SHA worker de 27 tablas no reproducido por revisor (véase SEGUIMIENTO).
- [ ] Runtime no presenta resolución ajena ni campos de precio en snapshots.
- [ ] Efectos del fixture están dentro del permiso y se registraron.

## Condiciones STOP

Entorno/fixture no autorizado, mapa perdido, escritor concurrente no coordinado,
identidad/contenido incompatible, códigos ambiguos, readback desigual o commit
incierto sin reconciliación. No limpiar por DELETE como salida de emergencia.
Antes de commit: rollback; después: congelar, documentar y pedir decisión.

## Evidencia a registrar

ID de corrida, entorno sin secretos, release/hash, insertados/NOOP/cuarentena,
mapa de IDs retornados, readback, bloqueo, commit, recibo, errores y resolución.
No marcar ensayo aceptado si faltan casos de repetición/recuperación/runtime.

## Siguiente paso

[04 — Carga controlada](04-carga-controlada.md), tras aceptación humana del ensayo.
