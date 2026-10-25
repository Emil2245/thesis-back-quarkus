-- Plan 021: invariante sum(avance_por_periodo) = peso_ponderado (escala 4).
-- Mapas no vacios con suma distinta del peso: re-escalado proporcional
-- (v' = v * peso / suma, escala 4 HALF_UP) con el residuo firmado en el ultimo
-- periodo con valor no cero; si la suma es 0, reparto uniforme entre las claves.
-- Mapas vacios: reparto uniforme en 1..numero_periodos (residuo al ultimo).
WITH base AS (
    SELECT a.id, a.peso_ponderado AS peso, cr.numero_periodos AS n,
           a.avance_por_periodo AS mapa,
           COALESCE((SELECT sum((e.value #>> '{}')::numeric) FROM jsonb_each(a.avance_por_periodo) e), 0) AS suma,
           (SELECT count(*) FROM jsonb_each(a.avance_por_periodo)) AS claves
    FROM actividad a
    JOIN cronograma cr ON cr.id = a.cronograma_id
), objetivo AS (
    SELECT * FROM base
    WHERE (claves = 0 AND n >= 1)
       OR (claves > 0 AND round(suma, 4) <> round(peso, 4))
), llaves AS (
    -- Claves destino: existentes, o 1..n si el mapa esta vacio.
    SELECT o.id, o.peso, o.suma, k.key::int AS periodo,
           CASE WHEN o.claves = 0 THEN 0 ELSE (o.mapa ->> k.key)::numeric END AS valor,
           (o.claves = 0 OR o.suma = 0) AS uniforme
    FROM objetivo o
    CROSS JOIN LATERAL (
        SELECT key FROM jsonb_object_keys(o.mapa) key WHERE o.claves > 0
        UNION ALL
        SELECT g::text FROM generate_series(1, o.n) g WHERE o.claves = 0
    ) k
), conteo AS (
    SELECT l.*,
           count(*) OVER (PARTITION BY id) AS total,
           row_number() OVER (PARTITION BY id ORDER BY periodo) AS pos,
           max(CASE WHEN valor <> 0 THEN periodo END) OVER (PARTITION BY id) AS ultimo_no_cero
    FROM llaves l
), calculo AS (
    SELECT c.*,
           CASE WHEN uniforme THEN round(peso / total, 4)
                ELSE round(valor * peso / suma, 4) END AS base_valor,
           CASE WHEN uniforme THEN periodo = (max(periodo) OVER (PARTITION BY id))
                ELSE periodo = ultimo_no_cero END AS recibe_residuo
    FROM conteo c
), ajustado AS (
    SELECT id, periodo,
           CASE WHEN recibe_residuo
                THEN base_valor + round(peso, 4) - sum(base_valor) OVER (PARTITION BY id)
                ELSE base_valor END AS final
    FROM calculo
), nuevo AS (
    SELECT id, jsonb_object_agg(periodo::text, to_char(final, 'FM999999990.0000') ORDER BY periodo) AS mapa
    FROM ajustado
    GROUP BY id
)
UPDATE actividad a
SET avance_por_periodo = n.mapa
FROM nuevo n
WHERE a.id = n.id;
