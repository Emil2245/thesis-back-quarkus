# Plan 02 — Corregir precisión y consolidación del motor

## Resultado esperado

El motor aplica `CALC_PRECISION=3` con `HALF_UP` después de cada operación, mientras la consolidación APU→Rubro usa precio unitario y total a 2 decimales con `RoundingMode.DOWN`. Los golden masters pasan sin tolerancias nuevas.

## Dependencia

Completar primero el [Plan 01](./01-linea-base-y-decisiones.md).

## Alcance

### Incluye

- Crear el plan obligatorio `plans/014-motor-precision-no-links.md` antes de tocar `motor/`.
- Actualizar las notas de modificación del motor en `CLAUDE.md` y `../thesis-docs/CLAUDE.md`.
- Configuración explícita de precisión de cálculo y presentación.
- Eliminar del modelo puro las ramas relacionadas con APUs auxiliares.
- Implementar realmente la decisión de consolidación de `plans/006`.
- Adaptar fixtures y pruebas sin cambiar tolerancias ni resultados canónicos.

### No incluye

- Redondear la persistencia a 2 decimales.
- Aplicar `DISPLAY_PRECISION` dentro del motor.
- Recálculo transaccional de APUs persistidos.
- Cambios de fórmula no respaldados por requisitos vigentes.

## Pasos

1. Registrar baseline de la suite del motor y de GM-19/GM-20.
2. Crear `plans/014-motor-precision-no-links.md` siguiendo el patrón de `plans/006` y documentar el cambio funcional que autoriza tocar el motor.
3. Añadir o ajustar `PrecisionConfig`:
   - `calcPrecision=3`;
   - `displayPrecision=2`;
   - valores configurables por MicroProfile/env.
4. Incorporar la precisión inmutable en `ParametrosCalculo`, conservando compatibilidad donde esté justificada.
5. Aplicar `HALF_UP` tras cada producto, porcentaje, suma, resta y acumulación del cálculo APU.
6. Eliminar `esAuxiliar` y `cdAuxiliar` de snapshots/resultados y ramas internas. Un APU representa siempre un análisis ordinario.
7. En `Consolidador`:
   - `precioUnitario = costoTotal.setScale(2, DOWN)`;
   - `precioTotal = cantidad × precioUnitario` y luego `setScale(2, DOWN)`.
8. Adaptar fixtures y propiedades. No editar assertions ni tolerancias de golden masters.
9. Confirmar que `DISPLAY_PRECISION` solo se usa en fronteras de presentación/exportación.
10. Actualizar las notas obligatorias de `CLAUDE.md` en ambos repositorios.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.motor.*'
./gradlew build -x test

! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'apu_auxiliar_id|apuAuxiliarId|CAMBIO_AUXILIAR|cdAuxiliar|esAuxiliar' \
  src/main/java/ec/uce/propuestas/motor src/test/java/ec/uce/propuestas/motor

git diff --check
```

## Criterios de terminado

- [ ] Existe `plans/014-motor-precision-no-links.md` y autoriza exactamente el cambio aplicado.
- [ ] Los 25 golden masters habilitados pasan con tolerancia original.
- [ ] GM-19 y GM-20 pasan por la frontera 2 dp `DOWN`, no por alterar expected values.
- [ ] No quedan ramas auxiliares en el motor.
- [ ] La persistencia conserva `NUMERIC(14,6)` y la presentación a 2 dp queda fuera del motor.
- [ ] No hay `double` ni `float` en `motor/`.

## Condiciones de parada

Detener y reportar si:

- un golden master requiere cambiar su valor esperado o tolerancia;
- la precisión de 3 decimales contradice una decisión funcional posterior;
- el cambio exige incorporar framework, logging o persistencia dentro de `motor/`;
- GM-24 continúa bloqueado por el fixture upstream: documentarlo, no repararlo silenciosamente.
