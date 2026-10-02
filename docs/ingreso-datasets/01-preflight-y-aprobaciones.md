# 01 — Preflight y aprobaciones

**Estado: preflight local ejecutado; carga posterior confirmada.**
La autorización posterior consta en [tarea LOAD](../../odd/tasks/dataset-daule-data-only-load.md).
El padre verificó SELECT-only 17 migraciones, 27 tablas, bases=5, insumos=768,
plantillas=13 (12 SISTEMA/1 PERSONAL), proyectos=3, presupuestos=2, APUs=311,
rubros=310 y cero colisiones CENTRAL DV1. Sistema y tres proyectos HM=0.0500.
Este fue el baseline previo. Preservación MD5 de nueve tablas confirmada
independientemente; SHA de 27 tablas reportada por worker, no reproducida por
revisor. Inventario actual 27 tablas/245 columnas/Flyway 17; fingerprint DDL
previo completo no reproducido. Totales posteriores 6/2140/950 en base/insumo/
plantilla. Ensayo ROLLBACK, apply, replay y dos verify PASS; revisión independiente
de datos PASS, global PARCIAL: aplicación/concurrencia pendientes.
[SEGUIMIENTO](SEGUIMIENTO.md).
Para una nueva corrida, recapturar baseline: no queda congelado permanentemente.

## Objetivo y dependencia previa

Cerrar decisiones humanas y límites del entorno antes de preparar datos.
Dependencia: lectura y revisión de [00](00-diagnostico-y-decisiones.md).

## Entradas

- [Export y política V1](../../../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1/README.md).
- [Manifiesto fuente](../../../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1/manifiesto.json).
- [Contrato de BD](../03-BASE-DATOS.md), [V001](../../src/main/resources/db/migration/V001__baseline.sql).
- [Resolver vigente](../../src/main/java/ec/uce/propuestas/plantilla/service/ResolverInsumoPlantillaService.java).
- [Registro de decisiones](SEGUIMIENTO.md).

## Superficies y acciones futuras permitidas

Solo tras autorización específica: lectura de la instancia designada, export y
contratos; depósito externo de acta, inventario y hashes. El acta debe nombrar
ruta del depósito, entorno, responsable, permisos y ventana. No registrar
credenciales ni datos personales. No hay escritura de BD en este paso.

## Acciones prohibidas

Cargar datos, cambiar esquema/backend, ejecutar notebooks, migrar al arrancar,
crear fixtures ficticios, actualizar parámetros del proyecto o sembrar unidades
sin necesidad demostrada. No confundir permiso de lectura con permiso de escritura.

## Pasos

1. Confirmar versión/hash del export y mantener huecos legales y de fuente de 00.
2. Resolver uso legal: autorización de uso o bloqueo por NO_VERIFICADA;
   documentar responsable y alcance, sin atribuir licencias no comprobadas.
3. Aprobar o rechazar política de precios para las columnas actuales del insumo.
   Decisión aprobada: menor observación positiva ORIGINAL ordenada por
   `(source_id,dataset_origen,precio_observado_id)`, sin mediana/promedio.
   Precio documental con fecha exacta desconocida, no precio vigente de mercado.
4. Resolver HM: aceptar adaptación a porcentaje del proyecto para variantes
   enumeradas o ponerlas en cuarentena. Las 935 al 5% y dos al 1% requieren
   disposición explícita. Usuario aprobó todas con adopción actual 5%; conservar
   ambas originales 1% externas. [Notas canónicas](../../../thesis-docs/res/datasets_presupuestos/exports/dataset_maestro_daule_v1/NOTAS_INTEGRACION_BACKEND.md).
5. Aceptar que CI histórico 20% y precios originales no viajan en plantilla;
   totales futuros son del proyecto y no una reproducción del workbook.
6. Medir las 24 unidades: longitud real ≤10, EQ/MO `h`, consumidores soportados.
   Aprobar normalización externa reversible o cuarentena solo donde sea necesaria.
7. Tras permiso de lectura, inventariar constraints/defaults/columnas vigentes,
   seeds y códigos de TODAS las bases CENTRAL relevantes, incluidas archivadas.
   Comparar código/tipo y detectar ambigüedades; reservar prefijo DV1 global.
8. Revisar códigos en bases PROYECTO donde se autorice adopción: el resolver
   reutiliza primero el insumo del proyecto. Incluir posibles fuentes PERSONAL
   visibles en el análisis, sin recopilar datos ajenos no autorizados.
9. Designar entorno desechable existente y fixture de proyecto de usuario
   existente para 03/05; delimitar mutaciones permitidas, sin crear proyectos,
   APUs ni presupuestos dummy. Si no existe fixture autorizado, bloquear runtime.
10. Aprobar futura herramienta de operador de datos, depósito externo durable,
    revisión de payload, bloqueo transaccional exclusivo de operadores y recuperación.
    Todos los cargadores usarán la misma clave/protocolo desde la revalidación
    hasta commit/rollback. Documentar la coordinación de otros escritores.

## Comprobaciones de salida

- [ ] Precio, HM, unidades, uso legal y pérdidas semánticas tienen decisión firmada.
- [ ] Instancia inventariada solo con autorización; sin afirmar inspección previa.
- [ ] Código DV1 no puede resolver otro CENTRAL/tipo ni reutilizar contenido ajeno.
- [ ] Entorno/fixture existente y superficies externas están identificados.
- [ ] Permisos de preparación y ensayo están separados de permiso de producción.
- [ ] Conteos admitidos y cuarentenas explican toda diferencia de 1372/937.

## Condiciones STOP

Decisión sin aprobación, uso fuera de autorización local (licencia NO_VERIFICADA), unidad incompatible sin disposición,
colisión operativa, estado de instancia desconocido, fixture no autorizado o
necesidad de rediseño. Una colisión ORIGINAL E53/E54 por sí sola no es STOP de
las filas canónicas: comprobar códigos operativos distintos antes de disponerlas.

## Evidencia a registrar

Acta por decisión con responsable/fecha/alcance; hashes reales, inventario de
constraints y colisiones, cantidades candidatas/admitidas/cuarentena, permisos
para etapa siguiente. En [SEGUIMIENTO](SEGUIMIENTO.md) dejar bloqueos abiertos
si falta evidencia. No poner secretos ni afirmar precio vigente de mercado.

## Siguiente paso

[02 — Mapeo y preparación](02-mapeo-y-preparacion.md), solo con puertas cerradas.
