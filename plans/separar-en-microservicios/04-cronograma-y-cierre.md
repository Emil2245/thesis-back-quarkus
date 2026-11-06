# 04 — Cronograma, garantías de consistencia y cierre

**Objetivo:** migrar la exportación XLSX/PDF/MSPDI de cronograma sin debilitar silenciosamente preflight, avisos, exactitud del archivo ni auditoría; demostrar al final dos servicios backend funcionales.

**Entrada:** [plan 03](03-s3-despliegue-y-cliente.md) cerrado. La ruta de cronograma puede permanecer **temporalmente en Core** durante las etapas anteriores; no debe considerarse migrada solo porque otros formatos ya usen S3.

## Decisión de producto/técnica antes de implementar

La descarga actual mantiene un bloqueo pesimista sobre presupuesto desde el preflight hasta que los bytes están generados dentro de una transacción. **No puede mantenerse la misma transacción durante una llamada HTTP, render remoto y subida S3 sin introducir otros riesgos.** Documentar y acordar una de estas garantías:

- **Instante de captura (propuesta):** Core valida, toma una proyección completa bajo la protección actual, libera el bloqueo y envía esos datos a Documentos. El archivo representa esa revisión aunque el presupuesto cambie después. Respuesta/advertencias deben corresponder a la misma captura.
- **Revalidación antes de entregar:** además de capturar, Core compara la revisión/fingerprint tras la subida y define qué hace si hubo cambios (rechazo, regeneración acotada, limpieza del objeto). No afirmar que esto equivale automáticamente al bloqueo actual.

Si la garantía no queda aprobada o demostrable, mantener cronograma en Core y reportar el bloqueo; no simular paridad.

## Acciones de esta etapa

1. Capturar en Core los resultados de preflight P-32, fingerprint, árbol de capítulos/rubros, actividades, períodos, pesos, importes y datos para MSPDI desde una vista coherente. Mantener validación por propietario, 404 para datos ajenos, bloqueos 409, advertencias stale por formato y resultados deterministas. Reusar las reglas actuales de proyección, no recalcularlas en los writers de Documentos.
2. Definir proyecciones por formato y versión de contrato con `BigDecimal` sin pérdida, orden, identidad pública y metadatos de nombre; Documentos genera XLSX/PDF/MSPDI **XML**, no `.mpp`. No inventar dependencias, lag u otros campos que no estén en el contrato vigente.
3. Renderizar y subir a S3 siguiendo el flujo de los formatos ya migrados. Core registra `DOCUMENTO_EXPORTADO` solo tras la subida confirmada, respetando el detalle de evento (`formato`, `bytes`, `stale`) y sin prometer entrega efectiva al navegador o exactamente una vez. Tratar carrera de cambios entre captura y entrega según la decisión adoptada, incluido el destino de un objeto producido que ya no sea válido.
4. Probar cambios concurrentes, presupuesto bloqueado y stale, respuestas de preflight frente a descarga, rechazos, auditoría, fallos del renderer y de S3, idempotencia y comportamiento del frontend. Conservar comprobaciones de contenido, hoja valorizada, PDF y parse-back de MSPDI/XML; no basta con `200` ni con bytes no vacíos.
5. Ejecutar las verificaciones focales y de regresión aplicables de Core, Documentos y frontend, construir ambas imágenes y probar en FLoCI: descarga real desde navegador, múltiples réplicas, fallos de instancia, limpieza de objetos, tiempos y conexiones. Registrar resultados, omisiones y problemas sin cambiar golden masters para hacer pasar el plan.
6. Reconciliar la documentación canónica de `thesis-docs`, manual de despliegue, contratos API y README de ambos servicios con el comportamiento final; separar explícitamente las afirmaciones verificadas en FLoCI de lo que sería una garantía de AWS real.

## Criterio de cierre

- Dos microservicios backend Quarkus desplegables, **una** PostgreSQL solo de Core y un S3 privado para los archivos; Gradle multiproyecto no cuenta como separación por sí solo.
- Todas las exportaciones acordadas pasan por la frontera Core → Documentos, con autorización, precisión, formatos, auditoría y garantía de cronograma decidida y comprobada. Si alguna queda fuera, registrarla como pendiente y no afirmar migración completa.
- Tests, evidencia de despliegue, descarga desde navegador y documentación actualizados. No se crea un tercer servicio para remediar una prueba fallida sin una nueva decisión.

**Referencias:** [índice](README.md), [plan 031 previo](../031-exportacion-cronograma.md), [`CronogramaDescargaService`](../../src/main/java/ec/uce/propuestas/cronograma/export/CronogramaDescargaService.java), [`CronogramaDocumentoResource`](../../src/main/java/ec/uce/propuestas/documento/CronogramaDocumentoResource.java), [contrato canónico](../../../thesis-docs/plan/architecture/07-api-contract.md).
