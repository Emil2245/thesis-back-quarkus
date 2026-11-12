-- V015__seed_cmt_schedule_sequential.sql
-- Replace the generated uniform CMT schedule with sequential monthly batches.
-- No real schedule source is available; this is a generated planning pattern,
-- not a representation of verified construction dates or activity durations.
-- The completed-project state and each activity's existing weight are retained.

WITH cmt_schedule AS (
    SELECT a.id AS actividad_id,
           row_number() OVER (ORDER BY string_to_array(r.item, '.')::int[], r.id) AS activity_number,
           count(*) OVER () AS activity_count
    FROM actividad a
    JOIN cronograma cr ON cr.id = a.cronograma_id
    JOIN presupuesto p ON p.id = cr.presupuesto_id
    JOIN proyecto pr ON pr.id = p.proyecto_id
    JOIN rubro r ON r.id = a.rubro_id
    WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán'
      AND p.version = 1
      AND cr.unidad_tiempo = 'MES'
      AND cr.numero_periodos = 12
), assigned_periods AS (
    SELECT actividad_id,
           ((activity_number * 12 + activity_count - 1) / activity_count)::text AS period_key
    FROM cmt_schedule
)
UPDATE actividad a
SET avance_por_periodo = jsonb_build_object(assigned_periods.period_key, 1.0000::numeric)
FROM assigned_periods
WHERE a.id = assigned_periods.actividad_id;
