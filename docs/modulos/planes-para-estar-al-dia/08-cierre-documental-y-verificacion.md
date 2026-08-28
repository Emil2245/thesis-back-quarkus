# Plan 08 — Cerrar documentación, Bruno y verificación

## Resultado esperado

La documentación y las colecciones Bruno describen el backend realmente implementado, todas las suites relevantes pasan y no se introdujo ningún módulo nuevo ni deuda oculta fuera del alcance.

## Dependencias

Completar los planes 02–07. El Plan 01 ya debió reconciliar la decisión no-links.

## Alcance

### Incluye

- Actualizar documentación de módulos y estado general.
- Actualizar `plans/README.md` con resultados reales.
- Completar `api/bruno/09-i02-i06/` usando UUIDv7 públicos.
- Ejecutar verificaciones específicas y suite completa.
- Actualizar Graphify después de todos los cambios.

### No incluye

- Corregir funcionalidades nuevas descubiertas durante la verificación.
- Habilitar casos bloqueados por fixtures upstream sin una decisión explícita.
- Crear nuevos módulos para hacer pasar pruebas.
- Modificar expected values o tolerancias de golden masters.

## Documentos a sincronizar

- `docs/modulos/01-proyecto.md`;
- `docs/modulos/02-insumo.md`;
- `docs/modulos/03-apu.md`;
- `docs/modulos/04-apu-avanzado.md`;
- `docs/modulos/README.md`;
- `docs/00-ESTADO-ACTUAL.md`;
- `docs/modulos/estado-actual.md`;
- `plans/README.md`.

Cada documento debe distinguir claramente **DONE**, **DEFERRED** y cualquier bloqueo real. No conservar instrucciones que contradigan el código final.

## Casos mínimos Bruno

- rangos configurables;
- base PERSONAL y copia desde CENTRAL;
- plantilla APU y fallback con advertencias;
- reordenamiento de filas;
- cálculo a 3 dp;
- ET y títulos;
- archivar y borrar una base central;
- creación desde plantilla de proyecto;
- UUID inválido y recurso ajeno.

## Pasos

1. Comparar el resultado de cada plan con `estado-actual.md` y actualizar su matriz de capacidades.
2. Sincronizar los documentos listados sin reabrir decisiones funcionales cerradas.
3. Actualizar o crear requests Bruno con UUIDv7 y variables reutilizables; no fijar BIGINT de seeds.
4. Ejecutar formato, compilación y suites específicas.
5. Ejecutar la suite completa y clasificar cualquier fallo como regresión, bloqueo upstream o pendiente fuera de alcance.
6. Ejecutar las búsquedas de restricciones críticas.
7. Ejecutar `git diff --check` y revisar que los cambios correspondan a los planes.
8. Desde la raíz del proyecto de grado, ejecutar `graphify update .`.

## Verificación final

```bash
./gradlew spotlessCheck
./gradlew build -x test

./gradlew test --tests 'ec.uce.propuestas.schema.*'
./gradlew test --tests 'ec.uce.propuestas.identifier.*'
./gradlew test --tests 'ec.uce.propuestas.motor.*'
./gradlew test --tests 'ec.uce.propuestas.proyecto.*'
./gradlew test --tests 'ec.uce.propuestas.insumo.*'
./gradlew test --tests 'ec.uce.propuestas.apu.*'
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew test --tests 'ec.uce.propuestas.documento.*'
./gradlew test

! grep -RInE 'double|float' src/main/java/ec/uce/propuestas/motor/
! grep -RInE 'apu_auxiliar_id|apuAuxiliarId|CAMBIO_AUXILIAR|cdAuxiliar|esAuxiliar' \
  src/main src/test docs/modulos

git diff --check
git status --short

cd ..
graphify update .
```

Si existe una migración posterior a V004 autorizada por otro plan, no usar la antigua comprobación que prohíbe V005/V006; verificar en su lugar que cada migración nueva tenga autorización y no edite migraciones aplicadas.

## Criterios de terminado

- [ ] Documentación, código y Bruno usan los mismos contratos.
- [ ] La matriz de capacidades refleja resultados observados, no intenciones.
- [ ] Todas las suites pasan o existe un bloqueo upstream explícito y previo, sin regresiones nuevas.
- [ ] No hay vocabulario auxiliar vigente ni `double/float` en el motor.
- [ ] Ninguna colección Bruno depende de BIGINT públicos.
- [ ] Graphify fue actualizado desde la raíz correcta.
- [ ] No se creó un módulo nuevo de primer nivel.

## Condiciones de parada

Detener y reportar si:

- falla una prueba por una regresión de los planes 02–07;
- una documentación canónica contradice el comportamiento implementado;
- cerrar la suite requiere modificar golden masters, tolerancias o fixtures upstream;
- aparecen cambios de código fuera del alcance que no pueden atribuirse a los planes ejecutados.
