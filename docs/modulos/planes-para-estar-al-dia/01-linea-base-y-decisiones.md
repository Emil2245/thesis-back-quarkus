# Plan 01 — Cerrar la línea base y reconciliar decisiones

## Resultado esperado

El trabajo pendiente de `ParametrosProyectoCambio` queda resuelto, y las fuentes de verdad dejan de ordenar enlaces entre APUs. Al terminar, los planes posteriores parten de una línea base coherente.

## Contexto

[`../estado-actual.md`](../estado-actual.md) detectó una costura útil para un futuro write-through y documentación contradictoria sobre APUs auxiliares. La decisión más reciente establece que no existen enlaces entre APUs y que `recalculo` no se implementará en esta etapa.

## Alcance

### Incluye

- Confirmar que `ParametrosProyectoCambio` ya está commiteado en `main`
  (Plan 01 cerrado): la clase y su test viven en
  `proyecto/service/ParametrosProyectoCambio{Test}.java`.
- Mantener sus datos internos fuera del contrato REST: el resource
  devuelve solo `cambio.parametros()`. **No** se expone `Long proyectoId`,
  ni los flags `porcentajeIndirectoCambio` / `porcentajeHerramientaMenorCambio`
  por REST.
- La migración de los paths `proyectoId` y de la identidad interna
  expuesta en `ParametrosProyectoResponse` (que sigue como `Long`) a
  UUIDv7 público **queda diferida** al
  [Plan 07](./07-uuidv7-fronteras-rest.md); este Plan 01 no introduce
  ningún cambio de tipo `Long`→`UUID` en esa respuesta.
- Reconciliar `docs/modulos/04-apu-avanzado.md` con la decisión no-links.
- Sincronizar las fuentes globales enumeradas en `estado-actual.md` §2.2.
- Actualizar `plans/README.md` para reflejar el estado real de los planes 006 y 013.

### No incluye

- Implementar recálculo global.
- Crear `ApuValidacionService` o columnas auxiliares.
- Cambiar fórmulas del motor.
- Implementar capacidades de los planes 02–08.
- Migrar `ParametrosProyectoResponse.id` ni los paths `proyectoId` a
  UUIDv7 (esto es trabajo del Plan 07).

## Pasos

1. Revisar `git status` y los diffs de los cinco paths señalados en `estado-actual.md` §3. No sobrescribir cambios ajenos.
2. Confirmar que `ParametrosProyectoCambio` sigue representando:
   - identificador interno del proyecto;
   - cambio de `%CI`;
   - cambio de `%HM`;
   - respuesta final de parámetros.
3. Confirmar que el recurso `ParametrosProyectoResource` sigue
   exponiendo únicamente `cambio.parametros()` (ya validado en este Plan
   01: el endpoint devuelve `ParametrosProyectoResponse`, no el seam
   completo). La migración del `Long proyectoId` del path a UUIDv7, y
   la del campo `id` de `ParametrosProyectoResponse` a UUIDv7, están
   explícitamente diferidas a [Plan 07](./07-uuidv7-fronteras-rest.md).
4. En `docs/modulos/04-apu-avanzado.md`:
   - retirar P-25 auxiliar y sus DTOs/servicios/campos propuestos;
   - registrar que los casos `TC-P25-*` de enlaces quedaron obsoletos;
   - usar Apache POI como librería documental;
   - marcar lo ya implementado y dejar `recalculo` como diferido.
5. Sincronizar las fuentes de `thesis-docs` listadas en `estado-actual.md` §2.2. No reinterpretar decisiones de dominio distintas de no-links.
6. Corregir en `plans/README.md`:
   - Plan 013: estado parcial y alcance restante;
   - Plan 006: decisión funcional cerrada, implementación pendiente hasta el Plan 02.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.proyecto.*'

git diff --check

grep -RInE 'apu_auxiliar_id|apuAuxiliarId|CAMBIO_AUXILIAR|cdAuxiliar|esAuxiliar' \
  docs/modulos ../thesis-docs/plan ../thesis-docs/README.md
```

El último comando solo puede mostrar referencias históricas marcadas explícitamente como obsoletas o prohibidas.

## Criterios de terminado

- [ ] `ParametrosProyectoCambio` está completo o se documentó con evidencia que ya no era necesario.
- [ ] Ningún endpoint expone sus flags internos ni BIGINT del proyecto.
- [ ] Las fuentes vigentes no ordenan implementar enlaces entre APUs.
- [ ] `recalculo` continúa diferido y no existen stubs nuevos para simularlo.
- [ ] Las pruebas de `proyecto` pasan.

## Condiciones de parada

Detener y reportar si:

- aparece una decisión posterior a la entrevista N04 que restablece enlaces entre APUs;
- terminar la costura requiere crear el módulo `recalculo`;
- los cambios sin commit no coinciden con el propósito descrito y no se puede determinar su autoría.
