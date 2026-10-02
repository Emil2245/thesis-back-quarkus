# Dataset maestro Daule V1

Dos proyectos 2025 consolidados exclusivamente para biblioteca global de **insumos y APUs**.
No se crean ni fusionan plantillas de proyecto, presupuestos ni cronogramas.

## Política V1
- APUs de origen conservados: 1575.
- Insumos: se fusionan automáticamente solo coincidencias exactas tras normalización conservadora.
- APUs: se fusionan automáticamente solo si su estructura completa produce el mismo fingerprint.
- Coincidencias fuzzy se exportan como candidatos y **no se fusionan** sin revisión humana.
- Precios/costos originales se conservan en linaje; `precio_referencia_v1` es mediana ponderada documental, no precio de mercado actual.

## Importación sugerida
1. fuentes.csv / archivos_fuente.csv
2. insumos_v1.csv
3. precios_observados_v1.csv / insumo_aliases.csv
4. apu_conceptos_v1.csv / apus_v1.csv
5. apu_componentes_v1.csv
6. apu_origenes.csv / apu_detalles_origen.csv (auditoría)

Revisar `validaciones_consolidacion.csv`, `insumos_posibles_duplicados.csv` y `apus_posibles_equivalentes.csv` antes del seed definitivo.
