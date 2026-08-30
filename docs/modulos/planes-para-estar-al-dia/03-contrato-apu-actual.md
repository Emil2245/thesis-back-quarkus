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
   - respetar el orden persistido (sin override HM-primero en el response);
   - **NO** reintroducir `CALC_PRECISION=3 HALF_UP` — Plan 014 supersede 2026-08-28
     mantiene `BigDecimal` natural; el motor opera a la escala de persistencia
     6 (`NUMERIC(14,6)`) con `HALF_UP`; display/export rinden a `precisionDinero`
     desde `app.display.*` + `GET /api/v1/config/display`.
   - conservar operandos descriptivos de `operacion` a 6 dp (precisión natural).
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

- [x] PATCH diferencia correctamente `orden` omitido de `orden` enviado.
  (`ApuDetallePatchRequest.orden: JsonNullable<Integer>` — omitido = no-op;
  presente+null o fuera de `[1,count]` = 400 `validacion`; presente+válido =
  MOVE atómico.)
- [x] HM puede reordenarse y continúa protegida contra borrado.
  (HM acepta **solo** `orden`; cualquier otro campo editable sigue 409
  `fila-protegida`; DELETE HM sigue 409.)
- [x] `GET /calculo` respeta el orden persistido.
  (`ApuCalculoService.buildSecciones` casea cada `ApuDetalle` con su
  `FilaCalculada` por sección/posición/HM y ordena `lineas` por `orden`
  ascendente — sin HM-primero en el response.)
- [x] Los resultados usan la precisión de cálculo vigente sin aplicar redondeo
  de exportación. (BigDecimal natural; display a `precisionDinero` desde
  config global.)
- [x] La respuesta no contiene BIGINT ni vocabulario auxiliar.
- [x] Las pruebas de `apu` pasan.
  (`./gradlew test --tests 'ec.uce.propuestas.apu.*'`: ApuResourceIT 36/36,
  ApuCalculoServiceIT 2/2 — ver `validation` abajo.)

## Estado de cierre

**DONE 2026-08-28** — implementado en commit `feat(apu): Plan 03 reordenamiento atómico y HM order-only`. La cobertura nueva vive en `ApuResourceIT.TC_P21_03_orden_omitido…` … `TC_P21_08_orden_fuera_de_rango_rechaza_400` (parametrizado 0/-1/99) más las pre-existentes `TC_P27_01..04`. Sin migraciones ni cambios en el módulo `motor/`. Plan 04 (plantillas APU) queda como siguiente.

## Condiciones de parada

Detener y reportar si:

- reordenar requiere cambiar el modelo de datos canónico;
- la respuesta de cálculo vigente en `thesis-docs` contradice el shape descrito en `estado-actual.md`;
- completar P-23 exige propagación global o crear `recalculo`.
