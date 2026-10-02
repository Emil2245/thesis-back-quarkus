# 06 — Releases futuras sin rediseño

**Estado: NO EJECUTADO; bloqueado por cierre de 05 y nueva autorización.**
La carga local V1 ya está reconciliada; no hay V2 aprobada y no se hereda permiso
para otra release. Revisión final datos V1 PASS, cierre global PARCIAL; runtime/API y concurrencia
no probados, SHA worker de 27 tablas y DDL previo completo no reproducidos
independientemente.

## Objetivo y dependencia previa

Reutilizar el flujo de datos sin prometer deduplicación/historia que el modelo no
ofrece. Depende de [05 cerrado](05-verificacion-y-cierre.md), fuente identificada
y nueva autorización humana para cada dataset o versión.

## Entradas

- [Alcance y límites](README.md), [diagnóstico V1](00-diagnostico-y-decisiones.md).
- [Preflight](01-preflight-y-aprobaciones.md), [mapa de preparación](02-mapeo-y-preparacion.md).
- [Contrato de BD](../03-BASE-DATOS.md), [mapper vigente](../../src/main/java/ec/uce/propuestas/plantilla/service/SnapshotApuMapper.java).
- [Registro de releases y recibos](SEGUIMIENTO.md).
- Nueva fuente: ruta, licencia, manifiesto y hashes deben aprobarse y quedar
  enlazados en el registro; no se presume que existen aquí.

## Superficies y acciones futuras permitidas

Tras nueva aprobación: depósito externo versionado de fuente/evidencia/mapa y
plan data-only; lectura de instancia. Escrituras solo por 03/04 autorizados en
las tablas actuales. Las rutas de otra fuente requieren permiso antes de leerlas
si no forman parte de evidencia autorizada. No ampliar este alcance tácitamente.

## Acciones prohibidas

Usar V1 como permiso perpetuo, mutar payload sellado, reciclar UUID/prefijo para
contenido diferente, overwrite/delete de releases, migraciones/catalogo_* o
schema versionado. No sincronizar precios a proyectos, nuevo formato snapshot,
procedencia nativa inventada ni merge automático por similitud fuzzy.

## Pasos

1. Determinar si es replay o release nueva: mismos bytes/versiones/identidades
   y contenido esperado = replay NOOP; cualquier cambio sustantivo = propuesta
   nueva. Ausencia de recibo se trata como recuperación, no nueva identidad.
2. Auditar fuente y legalidad, conteos/IDs/FKs, normalización, precios y fechas,
   unidades, HM, CI y orden; repetir 00/01 sin heredar aprobaciones semánticas.
3. Para V2 aprobar nuevo prefijo fijo (por ejemplo DV2), base CENTRAL adicional
   y UUIDv7 nuevos de base/insumos/plantillas. Coexistencia con V1; nunca alterar
   códigos que ya están referenciados por snapshots o copias de proyecto.
4. Preflight global frente a todas las releases CENTRAL/códigos/tipos y reuso
   PROYECTO. No confiar en aislamiento por nombre de base ni nombre de plantilla.
5. Conservar aliases/precios/orígenes/candidatos externamente por versión;
   mantener política de exactos/fuzzy y decisiones de cuarentena explícitas.
6. Explicar duplicados semánticos entre versiones: códigos nuevos evitan
   ambigüedad de resolver pero no eliminan duplicación de recursos/conceptos.
   El modelo actual no impone identidad canónica global; no prometer dedup global.
7. Si se piden nuevos precios, abrir acción separada con aprobación de columnas,
   fuente/fecha y destino. Nunca interpretar release nueva como actualización
   automática de proyectos existentes ni como historia de precios en BD.
8. Repetir 02–05 con manifiesto/mapa durable, ensayo y bloqueo transaccional
   exclusivo de operadores: misma clave/protocolo para todos los cargadores,
   desde revalidación hasta commit/rollback. Coordinar escritores ajenos;
   comparar UUID/contenido, hacer readback y emitir recibo después de commit.
9. Mantener V1 accesible como evidencia externa e inventario de base/plantillas;
   archivar/borrar no es parte de este paquete. Toda política de retiro necesita
   acción explícita compatible con datos de usuarios y referencias existentes.

## Comprobaciones

- [ ] Replay usa identidad original; release nueva usa prefijo/UUID nuevos.
- [ ] No se editó contenido V1 ni se sincronizaron proyectos.
- [ ] Nuevas decisiones de precio/HM/legalidad/unidades documentadas.
- [ ] Duplicados semánticos y límites de historia/procedencia reconocidos.
- [ ] 01–05 tienen permisos y evidencias propios para la nueva release.

## Condiciones STOP

Fuente sin permiso, prefijo reutilizado con contenido distinto, mapa de identidad
perdido, deduplicación global obligatoria sin soporte actual, requerimiento de
historia nativa o cambio de contrato. Registrar necesidad fuera de alcance;
no ampliar esquema ni backend por conveniencia del cargador.

## Evidencia a registrar

Comparación entre releases, versiones/hashes, decisiones nuevas, colisiones,
conteos admitidos/cuarentenas, mapa UUID, ensayo y recibos propios. Actualizar
[SEGUIMIENTO](SEGUIMIENTO.md) sin sustituir registros anteriores.

## Siguiente paso

Volver a [01 — Preflight](01-preflight-y-aprobaciones.md) para cada release nueva.
