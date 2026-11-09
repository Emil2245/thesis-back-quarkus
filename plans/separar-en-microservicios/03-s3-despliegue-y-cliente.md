# 03 — S3 emulado, despliegue y descarga desde el cliente

**Objetivo:** integrar las primeras exportaciones con un bucket S3 privado en FLoCI, desplegar ambos servicios y permitir que el frontend descargue el archivo por el mecanismo validado en el plan 01.

**Entrada:** [plan 02](02-build-y-generacion.md) comprobado y decisión de descarga del [plan 01](01-factibilidad-y-contratos.md). El código de despliegue está en `thesis-deploy`, no en este backend: cualquier cambio en ese repositorio requiere trabajar allí con su propio contexto y permisos.

**Estado local (2026-11-09):** Core y Documentos implementan el POST para
presupuesto, APUs y especificaciones, subida privada confirmada, ticket de un
uso y proxy de Core; el frontend React usa POST/JSON y el enlace temporal, y
Core permite `Idempotency-Key` en CORS para los orígenes configurados. Los GET
existentes se conservan y el POST de cronograma sigue diferido. Pasaron 777
pruebas del frontend y cuatro pruebas CORS, pero la suite completa de Core
tiene dos fallos golden-master preexistentes (GM_19/GM_20), diferidos por el
usuario. El repo `thesis-deploy` ofrece `compose.stage03-local.yaml`: se
construyeron las tres imágenes desde los worktrees hermanos y arrancaron Core,
Documentos, frontend, PostgreSQL, FLoCI 2.4.0 aislado y Mailpit local para
activar una cuenta de prueba sin enviar correos. Core y Documentos respondieron
saludables, el presupuesto de prueba pasó preflight, POST devolvió 200 y una
descarga vía Core devolvió 200 (4289 bytes XLSX) y después 404 al reutilizar
el ticket. Playwright con Chromium del sistema verificó login y descarga real
desde el frontend (POST 200, ticket GET 200); cronograma conservó GET 200.
`HeadObject` del objeto en el bucket exclusivo confirmó 4289 bytes; nginx no
registró paths de tickets en su salida local. No hay CronJob, identidad IAM
separada, TLS/Ingress Kubernetes ni prueba de borrado físico tras 24 horas;
las pruebas locales/FLoCI no sustituyen esos controles.

## Acciones de esta etapa

1. Preparar en la infraestructura declarativa un **bucket exclusivo para documentos**, privado y distinto de `propuestas-backups`; definir permisos mínimos separados (`PutObject`/`HeadObject` para Documentos; `GetObject` para Core; borrado solo para la tarea de limpieza), cifrado si corresponde, retención/limpieza verificable e identidad de pod. Ninguno de los dos necesita publicar o firmar URL S3 para el navegador. No confundir vencimiento de URL con eliminación del objeto. Revisar límites IAM/KMS de la ejecución histórica con FLoCI 2.3.0 y verificar de nuevo el comportamiento de 2.4.0 en el despliegue; la [prueba local del plan 01](01-factibilidad-y-contratos.md) exige activar juntos `FLOCI_SERVICES_S3_ENFORCE_AUTH` y `FLOCI_SERVICES_IAM_ENFORCEMENT_ENABLED` para obtener rechazo anónimo y de host alterado, pero no prueba seguridad AWS real ni Pod Identity.
2. Implementar en Documentos subida S3 después de renderizar, confirmar resultado y responder referencia opaca, longitud/checksum, MIME y nombre; **Documentos no emite URL pública**. Core guarda la referencia y emite una URL temporal propia de 5 minutos/uso único para proxy de lectura; el objeto privado se elimina al cumplir 24 horas con prueba de limpieza efectiva. Tratar fallos y timeouts de subida como resultado incierto hasta comprobar el objeto; verificar reserva única, lease/fencing, escritura S3 condicional y recuperación por `HeadObject`/checksum con dos POST concurrentes antes de afirmar idempotencia. No almacenar capturas privadas ni tickets en logs ni usar el bucket de backups.
3. Hacer que Core devuelva al frontend la respuesta acordada **solo tras carga confirmada** y audite en su base lo que ya se audita (especificaciones; cronograma seguirá en Core por ahora). Si falla la auditoría después de cargar, no responder éxito: prever reconciliación o limpieza de huérfanos. Presupuesto/APU no ganan un evento `DOCUMENTO_EXPORTADO` sin una decisión separada.
4. Publicar dos imágenes y despliegues/Services Kubernetes separados, con readiness, límites y réplicas independientes; CI y GitOps identifican cada imagen. Mantener PostgreSQL y Flyway exclusivos de Core. Configurar Traefik para que POST y GET del ticket lleguen a Core, suprimir paths con tickets de access logs/APM y probar TLS público; la llamada interna Core → Documentos usa JWT de servicio dedicado, TLS interno y NetworkPolicy, nunca JWT de usuario, y no es pública; el Ingress documentado hoy dirige `/api` a una sola API. No incorporar Consul por defecto: Kubernetes ya ofrece Service/DNS.
5. Migrar el frontend de GET/`Blob` al nuevo POST/JSON y enlace temporal de **Core**. Verificar en navegador real emisión con JWT, un solo uso incluso con GET simultáneos/prefetch, caducidad de 5 minutos (acortada al `objectExpiresAt`), recuperación autenticada del mismo objeto antes de `uploadedAt`+24 horas, `Content-Disposition`, ausencia de S3 en URL/headers y errores 404/409; conservar los GET de bytes durante la transición. La navegación con ticket no envía JWT: el ticket bearer es un secreto y debe estar protegido contra logs, prefetch y referer.
6. Actualizar `thesis-docs`, el manual FLoCI, scripts de despliegue y smoke tests cuando se implemente, distinguiendo lo propuesto de lo verificado. Medir latencias, memoria, concurrencia y conexión con S3; una URL que funcione desde pods pero no desde el navegador no cierra esta etapa.

## Entregables / puerta para el plan 04

- Los tres primeros tipos de exportación usan Documentos y S3; se comprueban archivo, propiedad, fallos, auditoría existente y descarga desde navegador en el entorno emulado.
- Dos aplicaciones desplegables, con infraestructura de bucket y retención verificadas y ruta de recuperación si Documentos o S3 no están disponibles. El manual ya describe dos imágenes (`propuestas-api`, `propuestas-web`): el nuevo backend agrega una tercera imagen de aplicación, pero **solo un segundo microservicio backend**.
- Resultado literal de pruebas con SDK desde pod/identidad de servicio, privacidad S3 con controles 2.4.0, descarga por proxy desde navegador, ticket único, expiración y borrado efectivo de objetos. Nunca publicar el bucket: el fallback proxy ya es la ruta elegida en el plan 01. Pruebas con AWS real quedan fuera del emulador y no se infieren.

**Referencias:** [índice](README.md), [manual FLoCI (fuentes; PDF pendiente)](../../../thesis-docs/manual-despliegue-floci/README.md), [fuente de aplicación/Ingress](../../../thesis-docs/manual-despliegue-floci/capitulos/07-aplicacion.tex), [fuente de infraestructura](../../../thesis-docs/manual-despliegue-floci/capitulos/04-infraestructura.tex).
