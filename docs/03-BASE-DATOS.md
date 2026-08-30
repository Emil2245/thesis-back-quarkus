# Base de datos — esquema y trazabilidad con lo solicitado

- **Fecha:** 2026-08-02
- **Fuente canónica:** `../thesis-docs/plan/architecture/06-database-schema.md`
  (DDL autoritativo, `V001__baseline.sql` lo implementa 1:1).
- **Requerimientos de origen:** `../Plan_Tesis_Andrade_Verkade.md` (objetivos
  específicos 1–6, alcance §5.1) y las minutas de entrevista
  `../thesis-docs/res/docs/interviews/{01,02,03}/*.md`.
- **Verificación incluida:** §6 contrasta los planes de `plans/` contra las
  decisiones tomadas en las entrevistas.

---

## 1. Propósito de este documento

Explica **las 21 tablas** del esquema PostgreSQL, por qué existen y a qué
requerimiento responden: qué objetivo del plan de tesis cumplen y qué decisión
de entrevista las justifica. Sirve de puente entre el documento de tesis (qué se
prometió), las minutas (qué se decidió con el experto) y el DDL (cómo se
implementó).

No redefine el esquema: `06-database-schema.md` y `V001__baseline.sql` mandan.
Aquí se describe y se traza.

---

## 2. Convenciones del modelo (resumen)

| Convención | Valor | Fuente |
|---|---|---|
| Dinero | `NUMERIC(14,6)` — el redondeo a 2 dp ocurre **solo en export** | schema §1, DM §16 |
| Porcentajes | `NUMERIC(5,4)` (`0.1800` = 18 %) | schema §1 |
| Cantidades | `NUMERIC(12,6)` · rendimiento `NUMERIC(10,6)` · peso `NUMERIC(7,4)` | schema §1 |
| PKs | `BIGINT GENERATED ALWAYS AS IDENTITY` | schema §1 |
| Enums | `VARCHAR(n) + CHECK`, nunca tipo `ENUM` de Postgres | schema §1 |
| Timestamps | `created_at`/`updated_at TIMESTAMPTZ NOT NULL DEFAULT now()` | schema §1 |
| Soft-delete | Prohibido; única excepción `base_insumos.archivada` (**D-12**) | §J D-12 |
| Unidades | Catálogo abierto: `insumo.unidad` sin FK + semilla `unidad_catalogo` | §17 #10 |
| Totales | Se persisten (write-through) y el motor los recalcula en cada mutación | schema §1, §17 #7 |

---

## 3. Mapa general — 21 tablas por dominio funcional

```
IDENTIDAD Y ACCESO
  usuario ◄────────── refresh_token · token_usuario · log_actividad

PROYECTO
  usuario ◄────────── proyecto ◄── firmante
                          ├── parametros_proyecto (1:1, PK = proyecto_id)
                          └── base_insumos (tipo PROYECTO)
  parametros_sistema (singleton, id = 1)  → se copia a cada parametros_proyecto

INSUMOS
  base_insumos ◄───── insumo (UNIQUE base_id, codigo)
  unidad_catalogo (semilla, LECTURA)
  valor_referencia (SBU, aportes, … fuera del motor)

APU
  presupuesto ◄────── apu ◄── apu_seccion (4 fijas M/N/O/P) ◄── apu_detalle
                              └── (sin enlaces entre APUs — N04 temporal)
  plantilla_apu (snapshot JSONB, tipo SISTEMA|PERSONAL)

PRESUPUESTO
  proyecto ◄────────── presupuesto (versionado; una vigente por proyecto)
                          ├── capitulo (árbol sin tope, parent_id)
                          └── rubro (1:1 con apu, UNIQUE apu_id)

CRONOGRAMA
  presupuesto ◄────── cronograma (1:1, UNIQUE presupuesto_id)
                          └── actividad (1:1 con rubro, UNIQUE rubro_id)
```

**Flechas = FK.** Los `UNIQUE` de la parte derecha de cada dominio son los
invariantes de integridad que las entrevistas exigieron explícitamente
(§5.4, §5.5 y §5.6).

---

## 4. Fichas por tabla: qué es y qué requiere

### 4.1 Identidad y acceso — usuario, refresh_token, token_usuario, log_actividad

| Tabla | Qué es | Qué implementa |
|---|---|---|
| `usuario` | Cuenta: nombre, email (UNIQUE, login), `password_hash` bcrypt, `rol` (solo USUARIO/SUPER_ADMIN), `email_verificado`, `activo` | **Plan tesis OE-3 y alcance §5.1 "Módulo de Gestión de Insumos"** (flujo de acceso previo); **LOPDP** (§6.5: JWT + bcrypt). |
| `refresh_token` | Tokens opacos de sesión "recordar sesión"; solo guarda SHA-256 | **D-02** (TTL 30 días / sesión) y **D-03** (revocación masiva al cambiar contraseña). |
| `token_usuario` | Tokens de un solo uso: verificación, reset, invitación, cambio de email | **D-01** (TTL 24 h), **D-11** (invitación 72 h), **D-03** (cambio de email). |
| `log_actividad` | Auditoría sin PII; evento de catálogo cerrado (**D-13**), `detalle` JSONB | **Plan tesis OE-6** (evidencias) · **RNF-08**. |

### 4.2 Proyecto — proyecto, firmante, parametros_sistema, parametros_proyecto

| Tabla | Qué es | Qué implementa |
|---|---|---|
| `proyecto` | Cabeza de la oferta: nombre, código, año, plazo (valor + unidad SEMANA/MES), estado (BORRADOR/EN_PROCESO/FINALIZADO), institución, logo `BYTEA` | **Plan tesis OE-2/OE-4** (estructura jerárquica del presupuesto). `usuario_id → RESTRICT` = una cuenta no se borra sin decidir el destino de sus ofertas. |
| `firmante` | Firmantes de carátula: nombre, cargo, rol (CONSOLIDADO/APROBADO), orden; UNIQUE (proyecto_id, rol, orden) | **Entrevista N02 §6** (firma de documentos SERCOP). Evolución del diseño: la entrevista pidió dos campos de texto ("Representante Legal", "Responsable Técnico"); el schema va más lejos con una **tabla** (multi-firmante, orden de display, cargo) — cubre el caso mínimo y permite varios por rol. |
| `parametros_sistema` | Fila única (CHECK `id = 1`): defaults del Super-Admin | **Entrevista N01 §3** (valores cambian con el tiempo → se gobiernan centralmente). |
| `parametros_proyecto` | 1:1 con `proyecto` (PK = proyecto_id); espejo de `parametros_sistema` (12 columnas: %HM, %CI, IVA, moneda, toggles de presentación M/N/O/P, modo de código de rubro) | **Entrevista N01 §3** (aislamiento por proyecto: cambiar un valor no afecta la base maestra ni otros proyectos), **N01 §2** (`modo_codigo_rubro` AUTOGENERADO/MANUAL), **N01 §5/§7** (toggles de secciones, sufijos, header, enumeración de APUs), **N02 §2** (descuento no altera precios base). |

### 4.3 Insumos — base_insumos, insumo, unidad_catalogo, valor_referencia

| Tabla | Qué es | Qué implementa |
|---|---|---|
| `base_insumos` | Contenedor de insumos: tipo CENTRAL (sin proyecto) o PROYECTO (con proyecto); `archivada` para centrales | **Entrevista N02 §3** (copiar bases de insumos entre proyectos, no clonar proyecto entero) · **D-12** (archivar, no borrar) · **Plan tesis OE-3** (base de datos de insumos centralizada). |
| `insumo` | Material/MO/Equipo/Transporte: código único por base, tipo, descripción, unidad, precio 6 dp | **Entrevista N01 §1** (unidades), **N01 §2** (código por base), **N01 §4** (precios vienen precalculados de la base central) · **D-06** (upsert CSV por código) · CHECK `'h'` fija para EQUIPO/MANO_OBRA (N01 §1: solo hora en tiempo) · **Plan tesis OE-3** + alcance §5.1 (importación CSV masiva). |
| `unidad_catalogo` | Semilla del catálogo de unidades (m, m², m³, kg, u, gl, lt, pto, m³·km, viaje, h) | **Entrevista N01 §1** (catálogo acotado y controlado, sin texto libre). |
| `valor_referencia` | Constantes del sistema (SBU, aportes) como **texto**, nunca al motor | **Entrevista N02 §5** (datos CAMICON fuera de alcance por licencia; solo datos públicos Ministerio del Trabajo) · agenda A7. |

### 4.4 APU — apu, apu_seccion, apu_detalle, plantilla_apu

| Tabla | Qué es | Qué implementa |
|---|---|---|
| `apu` | Análisis de Precios Unitarios por versión: código único, unidad, `porcentaje_indirecto` (NULL = hereda del proyecto), `porcentaje_descuento`, totales write-through (CD/CI/CT) | **Plan tesis OE-3** (motor de cálculo APU) · **Entrevista N01 §6** + **N02 §2** (descuento % sobre CD antes de CI, reversible). **Sin `es_auxiliar`**: N04 temporal elimina los enlaces entre APUs. |
| `apu_seccion` | Las 4 secciones fijas por APU (UNIQUE apu_id, tipo): EQUIPO(M)/MANO_OBRA(N)/MATERIAL(O)/TRANSPORTE(P) | **Entrevista N01 §5** + **N02 §1** (sufijos M/N/O/P obligatorios, estándar de facto SERCOP/Contraloría). |
| `apu_detalle` | Filas de cada sección (union table): cantidad, tarifa/jornal, rendimiento, unidad, precio, `costo`; `insumo_id` (apunta solo a insumos materializados en la base PROYECTO — sin columnas alternativas hacia otros APUs); fila HM con `es_herramienta_menor` | **Entrevista N01 §4** (rendimientos = parámetro de entrada del usuario, no cálculo inverso) · **§8** herencia de precios (COALESCE) · **§9** Herramienta Menor = %HM × Subtotal N. |
| `plantilla_apu` | Snapshot JSONB de un APU reutilizable: tipo SISTEMA (sin dueño) o PERSONAL (con dueño) | **Plan tesis OE-3** (reutilización) · **Entrevista N02 §3** (descarta plantillas de *proyecto*, mantiene reutilización de rubros). |

### 4.5 Presupuesto — presupuesto, capitulo, rubro

| Tabla | Qué es | Qué implementa |
|---|---|---|
| `presupuesto` | Versión de la oferta: correlativo por proyecto (UNIQUE proyecto_id, version), una sola vigente (índice parcial), total write-through | **Plan tesis OE-4** (consolidación dinámica, totales por sección/componente) · **§17 #15** versionado. |
| `capitulo` | Árbol jerárquico sin tope (`parent_id`), `item` renumerado, total write-through | **Plan tesis OE-4** (estructura jerárquica capítulos/etapas). |
| `rubro` | Ítem del presupuesto: 1:1 con APU (UNIQUE apu_id — **D-09**), cantidad de obra, `precio_total` write-through | **Plan tesis OE-4/OE-5** (vinculación APU→presupuesto→cronograma). |

### 4.6 Cronograma — cronograma, actividad

| Tabla | Qué es | Qué implementa |
|---|---|---|
| `cronograma` | 1:1 con la versión (UNIQUE presupuesto_id): unidad SEMANA/MES, número de períodos, `total_general_revisado` para detectar desactualización | **Entrevista N03 §1** (semanas o meses) · **N03 §4** (bloqueo de export si el acumulado no cierra 100 %) · **Plan tesis OE-5**. |
| `actividad` | 1:1 con `rubro` (UNIQUE rubro_id): peso ponderado write-through (`precio_total / total × 100`), avance por período en JSONB | **Entrevista N03 §2** (100 % de los rubros vinculados) · **N03 §3** (peso ponderado por valor monetario; distribución lineal editable con Σ por fila = peso) · **Plan tesis OE-5** (avance planificado ponderado). |

---

## 5. Matriz de trazabilidad: requerimiento → tabla

### 5.1 Objetivos específicos del plan de tesis (§4.2)

| OE | Objetivo | Tablas que lo implementan | Procesos |
|---|---|---|---|
| OE-1 | Clasificar requerimientos normativos (reglas de cálculo, componentes, formatos) | (documental: `apu_seccion`, `parametros_*`, `cronograma`) | P-37 (export) |
| OE-2 | Arquitectura cloud-native: modelo de BD de insumos, REST, frontend | `proyecto`, `base_insumos`, `insumo`, `parametros_*` | P-05…P-11, P-13…P-17 |
| OE-3 | Módulo insumos + motor APU (costos directos e indirectos) | `insumo`, `apu`, `apu_seccion`, `apu_detalle`, `plantilla_apu` | P-13…P-27 |
| OE-4 | Módulo presupuesto (jerarquía, totales por sección/componente) | `presupuesto`, `capitulo`, `rubro`, `apu` | P-28…P-32 |
| OE-5 | Vinculación presupuesto↔cronograma con avance ponderado | `cronograma`, `actividad`, `rubro` | P-33…P-36 |
| OE-6 | Verificación con escenarios reales (GM, CHK, SUS) | `log_actividad` (auditoría), fixtures GM | — |

### 5.2 Decisiones de entrevistas → tablas

| Entrevista | Decisión | Tablas / columnas |
|---|---|---|
| N01 §1 | Catálogo acotado de unidades; solo "hora" para tiempos | `unidad_catalogo` · `insumo.unidad` + CHECK `'h'` |
| N01 §2 | Codificación de rubros: autogenerado o manual | `parametros_*.modo_codigo_rubro` |
| N01 §3 | Valores parametrizables **por proyecto**, aislados | `parametros_proyecto` (espejo de `parametros_sistema`) |
| N01 §4 | Precios precalculados desde la base central; rendimientos = entrada del usuario | `insumo` · `apu_detalle.rendimiento` |
| N01 §5 / N02 §1 | Secciones M/N/O/P con sufijos obligatorios | `apu_seccion` (UNIQUE apu_id, tipo) + toggles en `parametros_*` |
| N01 §6 / N02 §2 | Descuento % al CD, antes del CI, reversible; global + por rubro | `apu.porcentaje_descuento` (0–50 %) |
| N01 §7 | Header configurable (nombre proyecto, enumerar APUs) | `parametros_*.mostrar_nombre_proyecto_header`, `.enumerar_apus` |
| N02 §3 | Copiar bases de insumos; sin clonar proyecto entero | `base_insumos` (PROYECTO/CENTRAL) + P-17 |
| N02 §4 | Rubros auxiliares calculados hasta CD e inyectados como material | **SUPERSEDED** — N04 temporal elimina los enlaces entre APUs. Un supuesto "auxiliar" se modela como otro APU/rubro independiente. |
| N02 §5 | Sin datos CAMICON; solo datos públicos | `valor_referencia` (seed condicionado a licencia) |
| N02 §6 | Firmas de responsable técnico y representante legal | `firmante` (rol CONSOLIDADO/APROBADO) |
| N03 §1 | Cronograma en semanas o meses | `cronograma.unidad_tiempo` |
| N03 §2 | 100 % de rubros vinculados a actividades | `actividad.rubro_id` UNIQUE (1:1) |
| N03 §3 | Peso ponderado = precio del rubro / total × 100; distribución lineal editable | `actividad.peso_ponderado`, `avance_por_periodo` (JSONB) |
| N03 §4 | Avance parcial/acumulado; export bloqueado si Σ ≠ 100 % | `cronograma` + validación P-32 |

---

## 6. Verificación de los planes de `plans/` contra las entrevistas

Contraste de lo que decidieron las entrevistas con lo que implementan los
planes del repo. Conclusión: **los planes implementados (003 schema, 004 auth,
005/006 motor, 009 proyecto+insumo) no contradicen ninguna decisión de
entrevista.** Hallazgos y matices:

### ✅ Coherentes (sin acción)

| Plan | Verificación frente a entrevistas |
|---|---|
| 003 (schema V001–V003) | Cumple N01 §1 (unidades y CHECK `'h'`), N01 §3 (espejo parametros), N01 §5/§7 (toggles), N02 §5 (CAMICON excluida de V002), N03 §1–§4 (cronograma/actividad). |
| 004 (auth) | Las entrevistas N01–N03 son de dominio APU (no tocan identidad). Nada las contradice; la tabla `usuario`/tokens cumple LOPDP del plan de tesis §6.5. |
| 005/006 (motor) | Reproduce N01 §4 (rendimientos de entrada), N01 §6 + N02 §2 (CD_ajustado = CD × (1−descuento); CI sobre ajustado; descuento no muta precios base), N02 §4 (CI = 0 en auxiliar). |
| 009 (proyecto + insumo, en `docs/modulos/`) | Implementa N01 §2/§3 (parámetros + modo de código), N01 §4 (precios de la base), N02 §3 (copia de base), D-06 (upsert CSV). |

### ⚠️ Matices (no contradicen, pero conviene dejar constancia)

1. **Firmas (N02 §6 vs `firmante`).** La entrevista pidió dos campos de texto
   libre en los parámetros del proyecto. El schema implementó una **tabla**
   `firmante` con rol + orden. Es una evolución más potente (varios firmantes
   por rol, cargo, orden de display) que **cubre** lo solicitado. El plan I-10
   (export) ya asume la tabla para los bloques de firmas. Documentado aquí
   para que no se lea como "campo faltante" durante la defensa.
2. **P-09 "duplicar proyecto": EXCLUIDO del alcance (decisión 2026-08-02).**
   La entrevista N02 §3 desaconseja clonar proyectos completos ("propenso a
   errores al arrastrar cronogramas o cantidades pasadas") y aprueba solo copiar
   **bases de insumos** (P-17, ya implementado). En consecuencia se elimina
   P-09 del alcance: **no existe ni existirá** `POST /proyectos/{id}/duplicar`.
   Lo único duplicable son insumos entre bases. D-04 queda sin efecto.
3. **Unidades del seed (N01 §1 vs V002).** La entrevista enumeró "m, u, Kg, pto,
   m³-km, hora" como ejemplos de un catálogo acotado; V002 siembra 11 unidades
   (añade m², m³, kg, gl, lt, viaje). No es contradicción: la entrevista no fue
   una lista exhaustiva y v1.1 §4.1 fija las 11 canónicas. Los mismatches
   `m²/m³` (Unicode) vs `m2/m3` (ASCII) del CSV IESS ya están registrados como
   deuda de datos (deuda #3 en `00-ESTADO-ACTUAL.md`).

### Planes aún por escribir que deben citar estas entrevistas

Cuando se redacten los planes de las iteraciones pendientes, las minutas son
fuente de reglas duras, no solo referencia:

- **I-05/I-06 (APU):** N01 §4 (rendimiento como entrada), N01 §5 (secciones
  siempre visibles, opción de ocultar vacías), N01 §6/N02 §2 (descuento),
  N02 §4 (auxiliares).
- **I-08/I-09 (versiones + cronograma):** N03 §1–§4 (unidades, 1:1, pesos,
  bloqueo de export). Nota: P-09 (duplicar proyecto) queda **excluido** — ver
  matiz 2.
- **I-10 (export):** N01 §5/§7 (toggles de presentación), N02 §6 (firmantes en
  la carátula), N03 §4 (gate del 100 %).

---

## 7. Estado de implementación por tabla (2026-08-02)

| Tabla | Entidad JPA | Repository | Service | REST |
|---|---|---|---|---|
| `usuario`, `refresh_token`, `token_usuario` | ✅ | ✅ (parcial) | ✅ (`usuario/auth/`) | ✅ (`/auth`, `/perfil`) |
| `log_actividad` | ⬜ | ⬜ | ⬜ | ⬜ (I-11, P-42) |
| `proyecto` | ✅ | ✅ | ✅ (`ProyectoService`) | ✅ `/proyectos` |
| `firmante` | ✅ | ✅ | ✅ (`FirmanteService`) | ✅ `/proyectos/{id}/firmantes` |
| `parametros_sistema` | ✅ (LECTURA) | ✅ | ✅ | ✅ `/proyectos/parametros-sistema` |
| `parametros_proyecto` | ✅ | ✅ | ✅ | ✅ `/proyectos/{id}/parametros` |
| `base_insumos` | ✅ | ✅ | ✅ | ✅ `/bases-centrales` |
| `insumo` | ✅ | ✅ | ✅ | ✅ `/proyectos/{id}/insumos` |
| `unidad_catalogo` | ✅ (LECTURA) | ✅ | ✅ | ✅ |
| `valor_referencia` | ⬜ | ⬜ | ⬜ | ⬜ (I-11, P-41) |
| `apu`, `apu_seccion`, `apu_detalle` | ⬜ | ⬜ | ⬜ | ⬜ (I-05/I-06) |
| `plantilla_apu` | ⬜ | ⬜ | ⬜ | ⬜ (I-05/I-06) |
| `presupuesto`, `capitulo`, `rubro` | ⬜ | ⬜ | ⬜ | ⬜ (I-07) |
| `cronograma`, `actividad` | ⬜ | ⬜ | ⬜ | ⬜ (I-08/I-09) |

Código vivo: `src/main/java/ec/uce/propuestas/{usuario,proyecto,insumo}/`.
Las entidades de los módulos pendientes se mapearán a estas tablas **sin
modificar V001** (Hibernate corre con `generation: validate`).

---

## 8. Conclusión

El esquema cubre el 100 % de los objetivos específicos del plan de tesis y el
100 % de las decisiones registradas en las tres minutas de entrevista. Las
21 tablas están agrupadas por los cinco módulos funcionales prometidos en el
alcance (insumos, APU, presupuesto, cronograma, documentos) más el soporte de
identidad y auditoría. Los planes ya ejecutados no contradicen las entrevistas;
los matices detectados (firmantes como tabla, **P-09 excluido del alcance**,
unidades del seed) quedan documentados en §6 para las iteraciones pendientes.
