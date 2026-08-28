# Plan 03 — Cerrar el contrato APU actual

## Resultado esperado

Las filas de un APU pueden reordenarse mediante el contrato existente, incluida HM, y `GET /calculo` respeta el orden y la precisión definida por el motor sin filtrar identificadores internos ni vocabulario auxiliar.

## Dependencia

Completar primero el [Plan 02](./02-motor-precision-y-consolidacion.md).

## Alcance

### Incluye

- Completar P-23 en su contrato local set/clear.
- Añadir `orden` al PATCH de detalle mediante `JsonNullable<Integer>`.
- Persistir y devolver el orden real de las filas.
- Mantener HM protegida contra borrado, pero permitir reordenarla.
- Cerrar shape y precisión de P-27.
- Añadir pruebas de contrato e integración APU.

### No incluye

- Drag-and-drop del frontend.
- Propagación global de `%CI` o `%HM`.
- Plantillas de APU.
- Cambiar fórmulas del motor cerradas en el Plan 02.

## Pasos

1. Registrar el comportamiento actual de:
   - set/clear de `%CI` por APU;
   - PATCH de detalle;
   - orden en `GET /calculo`.
2. Añadir `JsonNullable<Integer> orden` a `ApuDetallePatchRequest` con validación coherente.
3. Actualizar `ApuCrudService.editarDetalle` para distinguir campo omitido, valor presente y valor inválido.
4. Permitir que la fila HM cambie de posición, sin permitir su borrado ni romper sus invariantes.
5. En `ApuCalculoService`:
   - respetar el orden persistido;
   - devolver resultados monetarios a `CALC_PRECISION`;
   - conservar operandos descriptivos de `operacion` a 6 dp cuando el contrato lo requiera.
6. Revisar `ApuCalculoResponse`, `ApuCalculoLinea` y `ApuCalculoResumen`:
   - sin BIGINT internos;
   - sin campos auxiliares;
   - shape estable y documentado.
7. Añadir o completar:
   - TC-P23 set y clear;
   - TC-P27-01 shape;
   - TC-P27-02 reordenamiento;
   - TC-DECIMALES-CALC3-DISP2 API.

## Pruebas y comprobaciones

```bash
./gradlew test --tests 'ec.uce.propuestas.apu.*'
./gradlew spotlessCheck
./gradlew build -x test

git diff --check
```

## Criterios de terminado

- [ ] PATCH diferencia correctamente `orden` omitido de `orden` enviado.
- [ ] HM puede reordenarse y continúa protegida contra borrado.
- [ ] `GET /calculo` respeta el orden persistido.
- [ ] Los resultados usan la precisión de cálculo vigente sin aplicar redondeo de exportación.
- [ ] La respuesta no contiene BIGINT ni vocabulario auxiliar.
- [ ] Las pruebas de `apu` pasan.

## Condiciones de parada

Detener y reportar si:

- reordenar requiere cambiar el modelo de datos canónico;
- la respuesta de cálculo vigente en `thesis-docs` contradice el shape descrito en `estado-actual.md`;
- completar P-23 exige propagación global o crear `recalculo`.
