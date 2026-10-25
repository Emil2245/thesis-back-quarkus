-- Plan 021: actividades con mapa de avance vacio ('{}') reciben distribucion
-- uniforme en 1..numero_periodos con total = peso_ponderado (residuo firmado al
-- ultimo periodo). Los mapas NO vacios no se tocan: una migracion no distingue
-- un mapa desactualizado de una programacion parcial intencional (p. ej. las
-- seis actividades UCE programadas al 50%).
WITH vacias AS (
    SELECT a.id, a.peso_ponderado AS peso, cr.numero_periodos AS n
    FROM actividad a
    JOIN cronograma cr ON cr.id = a.cronograma_id
    WHERE (a.avance_por_periodo IS NULL OR a.avance_por_periodo = '{}'::jsonb)
      AND cr.numero_periodos >= 1
), filas AS (
    SELECT v.id, g AS periodo, v.n, round(v.peso / v.n, 4) AS base, v.peso
    FROM vacias v
    CROSS JOIN LATERAL generate_series(1, v.n) g
), ajustado AS (
    SELECT id, periodo,
           CASE WHEN periodo = n
                THEN base + round(peso, 4) - sum(base) OVER (PARTITION BY id)
                ELSE base END AS final
    FROM filas
), nuevo AS (
    SELECT id, jsonb_object_agg(periodo::text, to_char(final, 'FM999999990.0000') ORDER BY periodo) AS mapa
    FROM ajustado
    GROUP BY id
)
UPDATE actividad a
SET avance_por_periodo = n.mapa
FROM nuevo n
WHERE a.id = n.id;
