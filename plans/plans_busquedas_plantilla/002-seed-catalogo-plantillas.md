# Plan backend 002 — Seed representativo de plantillas APU

**Estado:** TODO · **Prioridad:** P1 · **Puede ejecutarse en paralelo con:** 001 y 003

## 01. Resultado observable

Una base limpia ofrece suficientes plantillas SISTEMA para que la primera página y la búsqueda sean demostrables, sin inventar precios ni depender de datos de un usuario específico. La plantilla PERSONAL existente de John Doe permanece owner-scoped.

## 02. Estado inicial

V004 siembra solo:

- 1 SISTEMA: retiro de pisos;
- 1 PERSONAL: replanteo y nivelación.

Dos filas no demuestran ranking, paginación, acentos, estados vacíos ni selección múltiple.

## 03. Alcance

Crear `V012__seed_catalogo_plantillas_apu.sql`; STOP si V012 está ocupada al ejecutar.

- Añadir al menos 12 plantillas SISTEMA totales, con nombres/descripciones representativos de obra pública ecuatoriana.
- Derivar los snapshots de APUs reales ya sembrados en V004/Cetro Médico Tulcán o de fixtures canónicos del motor; no inventar precios, fórmulas ni insumos inexistentes.
- Snapshot estrictamente price-free: códigos, cantidades y rendimientos permitidos; sin `insumoId`, precio efectivo, costo, subtotal, total ni enlace al APU origen.
- Incluir términos con tildes y familias útiles para validar FTS, por ejemplo `hormigón`, `nivelación`, `instalación`, sin duplicar nombres.
- UUIDv7 públicos fijos y no colisionantes dentro del rango reservado del seed.
- Idempotencia la administra Flyway; no usar `ON CONFLICT DO NOTHING` para esconder colisiones.

## 04. Fuera de alcance

- Crear plantillas PERSONAL para todos los usuarios.
- Cambiar V004.
- Añadir datos financieros o recalcular snapshots en SQL.
- Inflar el catálogo con cientos de filas repetidas para engañar un benchmark.

## 05. Pruebas

- Test de esquema/latest que confirme conteo, UUIDv7, ownership SISTEMA y snapshots price-free.
- Validar que cada `insumoCodigo` usado existe en las fuentes previstas o produce deliberadamente una advertencia documentada.
- Una plantilla controlada puede incluir un código faltante para probar advertencias, pero debe nombrarse claramente como escenario de prueba y no aparecer como opción normal si confunde al usuario.
- Confirmar que Ana no ve la PERSONAL de John y ambos ven todas las SISTEMA.

## 06. Verificación

```bash
./gradlew test --tests 'ec.uce.propuestas.schema.*' --tests 'ec.uce.propuestas.plantilla.*Seed*'
./gradlew test --tests 'ec.uce.propuestas.plantilla.*'
./gradlew spotlessCheck
./gradlew build -x test
git diff --check
```

Además, levantar PostgreSQL limpio y consultar la primera página cuando backend 001 esté integrado.

## 07. STOP y rollback

STOP si no puede trazarse un snapshot a datos canónicos o si se requiere inventar costos. Rollback: revertir únicamente V012 y sus tests; no tocar la infraestructura FTS de V011.
