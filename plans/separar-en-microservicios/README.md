# Ruta de separación: Core + Documentos

**Estado: plan 01 cerrado como factibilidad y contrato; plan 02 cerrado con la limitación local de verificación aceptada; planes 03–04 verificados en local (FLoCI), sin prueba de producción; plan 05 (retiro de los GET de bytes) implementado en local.** Ejecutar los planes siguientes en orden, con un resultado comprobable por etapa. Son **dos servicios Quarkus en un build Gradle multiproyecto**, no dos repositorios: Core conserva PostgreSQL y Flyway; Documentos recibe datos autorizados y proyectados, genera archivos y los almacena en un bucket privado S3 emulado. No hay acceso a la base desde Documentos.

| Orden | Plan | Resultado esperado |
|---|---|---|
| 1 | [Factibilidad y contratos](01-factibilidad-y-contratos.md) | Prueba de descarga temporal en FLoCI, decisión del mecanismo de descarga y contrato canónico aprobado. |
| 2 | [Separación Gradle y generación](02-build-y-generacion.md) | Dos aplicaciones construibles; primeras exportaciones con capturas de Core, sin SQL en Documentos. |
| 3 | [S3, despliegue y cliente](03-s3-despliegue-y-cliente.md) | Archivos privados en S3, ruta de descarga comprobada, frontend adaptado y dos imágenes/despliegues. |
| 4 | [Cronograma y cierre](04-cronograma-y-cierre.md) | Exportación de cronograma migrada sin pérdida silenciosa de sus garantías; pruebas finales y documentación actualizada. |
| 5 | [Retiro de descargas legadas](05-retiro-descargas-legadas.md) | Sin GET de bytes ni *writers* en Core; solo POST + ticket. Registro: [`odd/tasks/microservices-stage05.md`](../../odd/tasks/microservices-stage05.md). |

## Decisiones ya tomadas

- Se mantienen **dos servicios** y **una base PostgreSQL**. Core es el único dueño de las tablas, las migraciones, las reglas de negocio y la autorización del usuario.
- Documentos **no consulta la base**: recibe un contrato interno versionado con proyecciones inmutables, renderiza y usa S3. Puede tener varias réplicas sin convertirse en un tercer servicio.
- Traefik enruta las peticiones; Kubernetes ofrece descubrimiento y réplicas. No se agrega Consul solo para estos dos servicios.
- El bucket de respaldo existente **no se reutiliza** para archivos de usuarios.
- Los contratos HTTP públicos y el frontend pueden refactorizarse manteniendo la funcionalidad; el POST/JSON y el enlace temporal de Core están implementados (etapas 03–04) y documentados en `thesis-docs/plan/architecture/07-api-contract.md`. Los GET de bytes se conservaron durante la transición y se retiraron en la etapa 05 (2026-11).

## Condiciones abiertas, no asumidas

1. **Decisión del plan 01 tomada:** ante la falta de prueba de navegador/EKS para URL S3 directa, se escogió un enlace de **Core** de un solo uso y **5 minutos**, que transmite el objeto S3 privado mediante proxy. El objeto se retiene **24 horas**. La [prueba local de S3 2.4.0](01-factibilidad-y-contratos.md#ejecución-acotada-con-floci-240-solo-s3-local) encontró que el modo por defecto permite GET anónimo; con S3+IAM activados juntos rechazó anonimato, firma y host alterados. No se probó aún el proxy, el pod ni el navegador desplegado.
2. **Pendiente para plan 04:** resolver la garantía temporal del cronograma: actualmente se bloquea el presupuesto hasta terminar de generar bytes; liberar el bloqueo antes de renderizar y subir a S3 cambia esa garantía.
3. **Pendiente para plan 03:** implementar y verificar idempotencia del POST, consumo atómico/reemisión del ticket, identidad desde pod, limpieza efectiva a las 24 horas, TLS y descarga desde navegador. En plan 02 se probó la identidad Core→Documentos y la auditoría DOCX localmente; no extrapolarlas al despliegue.

## Fuentes que se deben releer antes de cada etapa

- [Análisis de módulos y tablas](../../../separacion_microservicios.md): alternativa inicial de renderer HTTP, sin S3.
- [Análisis de autonomía documental](../../../arquitectura_documentos_cloud_native.md): compara SQL directo, descartado **para esta ruta** porque se decidió no conceder acceso a la base a Documentos. Contiene la auditoría real por tipo de documento.
- [Fuentes del manual FLoCI](../../../thesis-docs/manual-despliegue-floci/README.md): la ejecución integral histórica es 2.3.0; la fuente `.tex` incluye la prueba S3 local 2.4.0 y el PDF versionado se regeneró el 2026-11-11 (XeLaTeX del sistema; `thesis-docs` 73988f5). El despliegue documentado aún tiene una sola API y el único bucket configurado es de respaldos. El código de despliegue vive en el repositorio `thesis-deploy`, que no está incluido aquí.
- [Contrato canónico API](../../../thesis-docs/plan/architecture/07-api-contract.md), [diseño de módulos](../../../thesis-docs/plan/architecture/08-codebase-design.md), [calidad](../../../thesis-docs/plan/quality/02-catalogo-pruebas.md) y planes [026–031](../031-exportacion-cronograma.md) de cronograma. Si el contrato vigente contradice una propuesta de estos planes, detenerse y acordar la actualización en `thesis-docs`; no convertir estos planes en nueva fuente normativa.

**Alcance de la etapa 01:** documentación y prueba aislada S3 ya realizadas; los dos buckets de prueba creados en contenedores propios no son infraestructura de la aplicación. No se crean módulos, despliegues, rutas públicas ni tests de aplicación en esta etapa. La implementación y verificación funcional pertenecen a los planes 02–04.
