-- V004 programó deliberadamente seis actividades al 100% y seis al 50%.
-- V018 cambió tipos JSON, no unidades: 0.125 sigue siendo una fracción.
-- Convertir a puntos porcentuales ponderados sin agregar períodos ni revisiones.
-- Una sola diferencia en el cohorte original protege el cronograma completo.
WITH original(item, codigo, peso, periodos) AS (
    VALUES ('1.1.1', 'RP-001', 0.7850, 8),
           ('1.1.2', 'ST-001', 19.5202, 8),
           ('1.1.3', 'ST-002', 5.9513, 8),
           ('1.1.4', 'ST-003', 2.2974, 8),
           ('1.2.1', 'CP-001', 7.8183, 8),
           ('1.2.2', 'ST-004', 6.2687, 8),
           ('2.1.1', 'ST-005', 9.2538, 4),
           ('2.1.2', 'ST-006', 6.1024, 4),
           ('2.1.3', 'ST-007', 5.4563, 4),
           ('2.2.1', 'ST-008', 7.2549, 4),
           ('2.2.2', 'ST-009', 12.1822, 4),
           ('2.2.3', 'ST-010', 17.1095, 4)
), seed AS (
    SELECT a.id, a.cronograma_id, a.peso_ponderado, a.avance_por_periodo,
           o.item, o.periodos,
           (SELECT jsonb_object_agg(n::text, '0.125'::text)
            FROM generate_series(1, o.periodos) n) AS mapa_original
    FROM actividad a
    JOIN cronograma cr ON cr.id = a.cronograma_id
    JOIN presupuesto p ON p.id = cr.presupuesto_id
    JOIN proyecto pr ON pr.id = p.proyecto_id
    JOIN rubro r ON r.id = a.rubro_id
    JOIN capitulo ca ON ca.id = r.capitulo_id AND ca.presupuesto_id = p.id
    JOIN apu ap ON ap.id = r.apu_id AND ap.presupuesto_id = p.id
    JOIN original o ON o.item = r.item AND o.codigo = r.codigo AND o.peso = a.peso_ponderado
    WHERE pr.codigo = 'UCE-CON-2026-B'
      AND p.version = 1
      AND cr.unidad_tiempo = 'MES'
      AND cr.numero_periodos = 8
), eligible AS (
    SELECT cronograma_id
    FROM seed
    GROUP BY cronograma_id
    HAVING count(*) = 12 AND count(DISTINCT item) = 12
       AND count(*) = (SELECT count(*) FROM actividad a WHERE a.cronograma_id = seed.cronograma_id)
       AND bool_and(avance_por_periodo = mapa_original)
), rounded AS (
    SELECT s.*, e.key,
           round(s.peso_ponderado * (e.value #>> '{}')::numeric, 4) AS valor,
           round(s.peso_ponderado * s.periodos * 0.125, 4) AS objetivo
    FROM seed s
    JOIN eligible el ON el.cronograma_id = s.cronograma_id
    CROSS JOIN LATERAL jsonb_each(s.avance_por_periodo) e
), allocated AS (
    -- Residuo exacto al último período existente (orden numérico estable).
    -- NUMERIC en cada operación; objetivo redondeado por actividad, no global.
    SELECT *, CASE WHEN key::int = periodos
                   THEN valor + objetivo - sum(valor) OVER (PARTITION BY id)
                   ELSE valor END AS ajustado
    FROM rounded
), converted AS (
    SELECT id, jsonb_object_agg(key, ajustado::text) AS mapa
    FROM allocated
    GROUP BY id
)
UPDATE actividad a
SET avance_por_periodo = c.mapa
FROM converted c
WHERE a.id = c.id;
