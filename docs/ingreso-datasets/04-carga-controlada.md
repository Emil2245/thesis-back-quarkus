# 04 — Carga controlada en destino autorizado

**Estado: carga local EJECUTADA y commit confirmado.**
Primera apply: una CENTRAL, 1372 insumos, 937 plantillas; replay: 0/0/0.
Dos verify exactos PASS; totales base/insumo/plantilla **6/2140/950**. Baseline de
nueve tablas confirmado independientemente por MD5; SHA de 27 reportado por worker,
no reproducido por revisor. Inventario 27 tablas/245 columnas/Flyway 17; fingerprint
DDL previo completo no reproducido. Cuatro recibos en [SEGUIMIENTO](SEGUIMIENTO.md).
Revisión final datos/recibos PASS; global PARCIAL. Carga verificada en BD;
validación funcional de aplicación y concurrencia pendientes. Procedimiento siguiente reutilizable.

## Objetivo y dependencia previa

Añadir la release admitida sin modificar datos anteriores ni proyectos existentes.
Depende de [03 aceptado](03-ensayo-de-carga.md), permiso explícito de escritura,
ventana coordinada y versión sellada idéntica a la ensayada.

## Entradas

- [Procedimiento ensayado](03-ensayo-de-carga.md).
- [Identidad preparada](02-mapeo-y-preparacion.md) y [seguimiento](SEGUIMIENTO.md).
- [Esquema de referencia V001](../../src/main/resources/db/migration/V001__baseline.sql).
- Manifiesto, payload, operador y recibo de ensayo externos: rutas y hashes
  consignados en SEGUIMIENTO antes de abrir la ventana.

## Superficies y acciones futuras permitidas

Tras aprobación específica: destino designado, inserciones en las tres tablas
actuales `base_insumos`, `insumo`, `plantilla_apu`; TEMP staging de sesión,
lecturas de verificación y recibo externo. La autorización debe delimitar acceso,
respaldo operativo existente y responsable de recuperación; no incluir secretos.

## Acciones prohibidas

Modificar seeds V001–V017, ejecutar migraciones, schema/backend/dependencias nuevos,
UPDATE/DELETE/overwrite de datos previos, `DO UPDATE`, BIGINT explícitos o setval.
No introducir catálogo separado, APU ficticio, endpoint ni tablas permanentes.
No sincronizar precios/parámetros de proyectos ni utilizar startup como cargador.

## Pasos

1. Confirmar aprobación de precio, HM, unidades, legalidad, entorno y conteos.
   Verificar hash del mapa durable y archivos preparados; si difieren, volver a 02.
2. Capturar baseline autorizado de IESS, plantillas previas y datos ajenos;
   comprobar disponibilidad del mecanismo operativo de respaldo previamente
   aprobado, sin inventar ni ejecutar copias fuera de autorización.
3. Abrir transacción con bloqueo transaccional exclusivo de operadores y ventana
   coordinada. Todos los cargadores usarán la misma clave/protocolo desde la
   revalidación hasta commit/rollback; coordinar aparte escritores que no lo usan.
   Rehacer preflight bajo bloqueo frente a todos los códigos CENTRAL relevantes
   y tipos; revisar reuso PROYECTO conforme al alcance autorizado.
4. COPY por stdin a TEMP staging, validar antes de escribir. Insertar base
   CENTRAL adicional, luego insumos no-HM y plantillas SISTEMA admitidas mediante
   INSERT/SELECT en tablas existentes. Defaults para IDs internos; resolver base
   por UUID durable, sin BIGINT impuesto.
5. Reconciliar por UUID público almacenado: contenido exacto=NOOP;
   conjunto completamente ausente=INSERT; parcial/discrepante=STOP.
   Nombre repetido no demuestra igualdad.
   Verificar contenido completo esperado y asociación de insumos con la base.
6. Comparar readback de todo el conjunto antes de commit: conteos, referencias,
   códigos/tipos, cuatro secciones, orden y HM. Ningún precio/ID en snapshots.
7. Ante error antes de commit, rollback y registrar fallo sin recibo de éxito.
   Resolver mediante nueva aprobación; no cambiar el payload dentro de la ventana.
8. Tras commit confirmado, crear recibo externo con versión/hash, UUIDs,
   insertados/NOOP y momento de confirmación. Un recibo faltante no demuestra
   ausencia de filas: recuperar por UUID/contenido, no reinsertar.
9. Si commit queda incierto, suspender. En lectura autorizada comparar todas las
   identidades preparadas: conjunto exacto permite recuperar recibo; ausencia
   total requiere decisión explícita de reintento; parcial/mismatch queda STOP.
10. Entregar resultado reconciliado a 05. No declarar cierre solo por salida
    exitosa del cliente SQL ni emitir evidencia externa antes de commit.

## Comprobaciones

- [x] Base adicional=1; 1372/937 admitidos sin cuarentena.
- [x] Baseline previo de nueve tablas MD5 confirmado; proyectos no sincronizados.
  SHA worker de 27 tablas reportado, no reproducido independientemente.
- [x] UUID/payload readback exactos en dos verify.
- [x] Replay idéntico deja 0/0/0 inserciones.
- [x] Recibos posteriores a commit disponibles en SEGUIMIENTO.

## Condiciones STOP

Drift de instancia respecto al ensayo, colisión, aprobación ausente, mapa/hash
inconsistente, lock no obtenido, coordinación insuficiente, readback distinto,
resultado de commit incierto o datos inesperados. Sin borrar la release ni
sobrescribir datos como compensación: congelar y escalar al responsable.

## Evidencia a registrar

Corrida/destino, responsable, versión/hash, baseline, IDs retornados, conteos por
tabla/tipo, bloqueos, comprobaciones, confirmación de commit, recibo y errores.
Guardar evidencia sin credenciales/PII y actualizar [SEGUIMIENTO](SEGUIMIENTO.md).

## Siguiente paso

[05 — Verificación y cierre](05-verificacion-y-cierre.md), incluso si la corrida
fue NOOP; una discrepancia conserva el paquete bloqueado.
