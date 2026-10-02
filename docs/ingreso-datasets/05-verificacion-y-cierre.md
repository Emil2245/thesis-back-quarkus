# 05 — Verificación independiente y cierre

**Estado: PARCIAL — carga verificada en BD; validación funcional de aplicación pendiente.**
Apply 1/1372/937, replay 0/0/0 y dos verify de payload exacto PASS. Totales locales
6 bases, 2140 insumos, 950 plantillas. Revisión final independiente **PASS** de
payload completo, precios originales seleccionados, UUIDv7, 15 hashes y recibos;
10 tests PASS. Nueve tablas MD5 preservadas confirmadas por revisor. SHA worker
27 tablas y fingerprint DDL previo completo no reproducidos independientemente.
Inventario actual 27 tablas/245 columnas/Flyway 17. [Recibos](SEGUIMIENTO.md).
Runtime/API, adopción de fixture y concurrencia NO PROBADOS; resultado global
PARCIAL, no PASS integral.

## Objetivo y dependencia previa

Cerrar únicamente lo demostrado: integridad de release y compatibilidad actual,
no equivalencia histórica. Depende de [04 reconciliado](04-carga-controlada.md)
y recibo confirmado/recuperado; revisión por persona distinta del operador.

## Entradas

- [Diagnóstico y límites](00-diagnostico-y-decisiones.md).
- [Preparación inmutable](02-mapeo-y-preparacion.md), [SEGUIMIENTO](SEGUIMIENTO.md).
- [Mapper](../../src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java)
  y [resolver](../../src/main/java/ec/uce/propuestas/plantilla/service/ResolverInsumoPlantillaService.java).
- Recibo, mapa UUID, readback y acta externos enlazados antes de iniciar.

## Superficies y acciones futuras permitidas

Lecturas del destino autorizado y evidencia externa. Runtime solo en entorno
existente desechable/fixture de proyecto de usuario existente autorizado;
registrar antes sus mutaciones permitidas. Si falta fixture, revisión estructural
puede registrarse, pero no sustituye adopción runtime pendiente.

## Acciones prohibidas

Crear dummy proyectos/APUs/presupuestos, plantillas de proyecto, modificar datos
para hacer coincidir totales, alterar CI/HM/precios de usuarios, tolerancias,
motor o resolver. No borrar cuarentenas, forzar cierre ni declarar historia nativa.

## Pasos

1. Verificar firmas/hash/versión y que archivo de identidad antecedía a escrituras.
   Confirmar que el recibo corresponde al commit, no solo a payload preparado.
2. Consultar por UUID esperado base, insumos y plantillas; comparar contenido,
   pertenencia a base, códigos/tipos y JSONB con orden de arrays preservado.
3. Reconciliar conteos por tipo/sección y cuarentena. Mantener 1374 recursos fuente,
   dos HM excluidos y 1372 ordinarios previos a cualquier reducción; 937 variantes
   antes de disposiciones HM. No exigir 937 cargadas si hubo cuarentena aprobada.
4. Revisar aislamiento: IESS y SISTEMA anteriores sin cambios, sin precio/ID en
   snapshot, sin tablas/migraciones nuevas ni mapeos BIGINT impuestos.
5. Revisar E53/E54: ambos canónicos válidos con códigos distintos; no alias como
   clave de resolución. No declarar candidatos fuzzy resueltos por la carga.
6. En fixture autorizado, adoptar muestra razonada: cuatro secciones, orden,
   MAT/TR sin rendimiento, EQ/MO positivos, metadato general cero, ambos códigos
   originales repetidos y variante HM al 1% si admitida.
7. Comprobar reuso PROYECTO antes de CENTRAL y precios actuales del fixture;
   HM usa porcentaje de proyecto y CI usa su semántica vigente. Registrar
   adaptación, no igualdad de totales históricos; no cambiar parámetros para
   ocultar diferencias. Ninguna advertencia inesperada de resolución ajena.
8. Revisar replay idéntico NOOP del ensayo y recuperación por UUID/contenido;
   no repetir escrituras en producción solo para generar prueba redundante.
9. Firmar cierre separado: integridad de datos, runtime y pendientes legales/
   reproducibilidad. Si falta prueba requerida, dejar cierre parcial/bloqueado
   con responsable y condición de salida, no marcar casillas completadas.

## Comprobaciones

- [x] Revisor independiente confirmó payload completo/conteos/precios/UUIDv7/
  hashes y recibos; resultado datos PASS, no cierre funcional integral.
- [x] Baseline de nueve tablas MD5 confirmado y payload/códigos reconciliados;
  resolución runtime de variantes aún no probada.
- [ ] Adopción runtime observada solo en fixture permitido y sin datos dummy.
- [ ] Diferencias HM/CI/precios aceptadas quedan visibles al consumidor.
- [ ] Recibo/hash/mapa externos accesibles para recuperación sin secretos.
- [ ] Pendientes restantes tienen responsable; nada se presenta como ejecutado sin evidencia.

## Condiciones STOP

Filas faltantes/sobrantes, snapshot fuera de contrato, resolución ambigua, baseline
alterado, recibo dudoso, fallo runtime o pretensión de exactitud histórica no
soportada. Congelar adopción/carga siguiente; no resolver con UPDATE o rediseño.

## Evidencia a registrar

Acta independiente con fecha/revisor, consultas y conteos reales, comparación
estructural, casos runtime y sus resultados, IDs públicos de fixture autorizados
sin datos personales, pendientes y aceptación humana. Registrar errores aun si
el cliente de carga reportó éxito en [SEGUIMIENTO](SEGUIMIENTO.md).

## Siguiente paso

[06 — Futuros datasets](06-futuros-datasets.md), únicamente tras cierre y nueva
aprobación; o volver al paso bloqueado indicado por el acta.
