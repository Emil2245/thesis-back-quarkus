# 04 — Cronograma, garantías de consistencia y cierre

**Objetivo:** migrar la exportación XLSX/PDF/MSPDI de cronograma sin debilitar silenciosamente preflight, avisos, exactitud del archivo ni auditoría; demostrar al final dos servicios backend funcionales.

**Entrada:** [plan 03](03-s3-despliegue-y-cliente.md) verificado en el entorno local Core–Documentos–frontend/FLoCI. El usuario aceptó esa evidencia local por ahora; IAM, TLS, limpieza programada y pruebas de despliegue siguen pendientes, no aprobadas. La ruta de cronograma puede permanecer **temporalmente en Core** durante las etapas anteriores; no debe considerarse migrada solo porque otros formatos ya usen S3.

## Garantía de consistencia elegida para esta etapa

La descarga GET actual mantiene un bloqueo pesimista sobre presupuesto desde el preflight hasta que los bytes están generados dentro de una transacción. **No puede mantenerse la misma transacción durante una llamada HTTP, render remoto y subida S3 sin introducir otros riesgos.** El usuario eligió **captura consistente**: Core valida y calcula preflight, advertencias y proyección completa bajo el bloqueo vigente; libera el bloqueo antes de llamar a Documentos. El archivo y la respuesta corresponden a esa captura aunque el presupuesto cambie después. No se promete revalidación tras la subida ni equivalencia con el bloqueo mantenido hasta generar los bytes.

El GET de bytes y su preflight existentes se conservan durante la transición. El nuevo POST de cronograma devolverá un ticket de Core mediante el protocolo de la etapa 03, sin convertir silenciosamente el GET en JSON. Si la captura coherente no puede demostrarse en un formato, ese formato permanece en Core y se informa como pendiente.

## Acciones de esta etapa

1. Capturar en Core los resultados de preflight P-32, fingerprint, árbol de capítulos/rubros, actividades, períodos, pesos, importes y datos para MSPDI desde una vista coherente. Mantener validación por propietario, 404 para datos ajenos, bloqueos 409, advertencias stale por formato y resultados deterministas. Reusar las reglas actuales de proyección, no recalcularlas en los writers de Documentos.
2. Definir proyecciones por formato y versión de contrato con `BigDecimal` sin pérdida, orden, identidad pública y metadatos de nombre; Documentos genera XLSX/PDF/MSPDI **XML**, no `.mpp`. No inventar dependencias, lag u otros campos que no estén en el contrato vigente.
3. Renderizar y subir a S3 siguiendo el flujo de los formatos ya migrados. Core registra `DOCUMENTO_EXPORTADO` solo tras la subida confirmada, respetando el detalle de evento (`formato`, `bytes`, `stale`) y sin prometer entrega efectiva al navegador o exactamente una vez. Tratar carrera de cambios entre captura y entrega según la decisión adoptada, incluido el destino de un objeto producido que ya no sea válido.
4. Probar cambios concurrentes, presupuesto bloqueado y stale, respuestas de preflight frente a descarga, rechazos, auditoría, fallos del renderer y de S3, idempotencia y comportamiento del frontend. Conservar comprobaciones de contenido, hoja valorizada, PDF y parse-back de MSPDI/XML; no basta con `200` ni con bytes no vacíos.
5. Ejecutar las verificaciones focales y de regresión aplicables de Core, Documentos y frontend, construir ambas imágenes y probar en FLoCI: descarga real desde navegador, múltiples réplicas, fallos de instancia, limpieza de objetos, tiempos y conexiones. Registrar resultados, omisiones y problemas sin cambiar golden masters para hacer pasar el plan.
6. Reconciliar la documentación canónica de `thesis-docs`, manual de despliegue, contratos API y README de ambos servicios con el comportamiento final; separar explícitamente las afirmaciones verificadas en FLoCI de lo que sería una garantía de AWS real.

## Estado de verificación local (11 de noviembre de 2026)

La [evidencia local de etapa 04](../../../thesis-deploy/docs/evidencias/stage04-cronograma-local-2026-11.md)
registra Core, Documentos y FLoCI en la composición aislada: preflight, POST,
XLSX/PDF/MSPDI, advertencia stale, ticket 200 seguido de 404 y GET heredado
funcionaron por HTTP. El frontend pasó 781 pruebas, typecheck, lint y build.
**El criterio de descarga desde navegador permanece pendiente:** Playwright no
encontró su ejecutable Chromium y no se intentó reemplazarlo ni instalarlo.
Tampoco se probaron réplicas, fallos de instancia, limpieza física a las 24 h,
IAM/TLS desplegados o la suite completa de Core (GM_19/GM_20 diferidos).

## Criterio de cierre

- Dos microservicios backend Quarkus desplegables, **una** PostgreSQL solo de Core y un S3 privado para los archivos; Gradle multiproyecto no cuenta como separación por sí solo.
- Todas las exportaciones acordadas pasan por la frontera Core → Documentos, con autorización, precisión, formatos, auditoría y garantía de cronograma decidida y comprobada. Si alguna queda fuera, registrarla como pendiente y no afirmar migración completa.
- Tests, evidencia de despliegue, descarga desde navegador y documentación actualizados. No se crea un tercer servicio para remediar una prueba fallida sin una nueva decisión.

**Referencias:** [índice](README.md), [plan 031 previo](../031-exportacion-cronograma.md), [`CronogramaDescargaService`](../../src/main/java/ec/uce/propuestas/cronograma/export/CronogramaDescargaService.java), [`CronogramaDocumentoResource`](../../src/main/java/ec/uce/propuestas/documento/CronogramaDocumentoResource.java), [contrato canónico](../../../thesis-docs/plan/architecture/07-api-contract.md).
