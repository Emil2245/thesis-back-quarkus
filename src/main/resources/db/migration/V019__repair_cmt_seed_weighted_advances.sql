-- Corrige puntos porcentuales del presupuesto, no fracciones por actividad.
-- Solo el cronograma CMT v1 íntegramente idéntico al patrón V015 tras V018.
-- Una sola programación diferente protege el cronograma entero.
-- Los marcadores revisados describen el presupuesto, no los avances; al igual
-- que programarActividad, no se actualizan ni se fabrica una revisión.
WITH cmt_schedule AS (
    SELECT a.id AS actividad_id, a.cronograma_id, a.avance_por_periodo,
           a.peso_ponderado, r.item, r.codigo,
           row_number() OVER (
               PARTITION BY cr.id ORDER BY string_to_array(r.item, '.')::int[], r.id
           ) AS activity_number,
           count(*) OVER (PARTITION BY cr.id) AS activity_count
    FROM actividad a
    JOIN cronograma cr ON cr.id = a.cronograma_id
    JOIN presupuesto p ON p.id = cr.presupuesto_id
    JOIN proyecto pr ON pr.id = p.proyecto_id
    JOIN rubro r ON r.id = a.rubro_id
    JOIN capitulo ca ON ca.id = r.capitulo_id AND ca.presupuesto_id = p.id
    WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán'
      AND pr.codigo = 'CMT-2023'
      AND p.version = 1
      AND cr.unidad_tiempo = 'MES'
      AND cr.numero_periodos = 12
), assigned_periods AS (
    SELECT *, ((activity_number * 12 + activity_count - 1) / activity_count)::text AS period_key
    FROM cmt_schedule
), eligible AS (
    SELECT cronograma_id
    FROM assigned_periods
    GROUP BY cronograma_id
    HAVING count(*) = 298
       AND count(*) = (SELECT count(*) FROM actividad a WHERE a.cronograma_id = assigned_periods.cronograma_id)
       -- Huella de los 298 pares item:código de rubro de V004, orden textual C.
       -- Incluye repeticiones de códigos; no depende de IDs internos.
       AND md5(string_agg(item || ':' || codigo, '|' ORDER BY item COLLATE "C", codigo COLLATE "C"))
           = '1fdeebd12300d0ddd029ce2cc8a7ce8e'
       AND bool_and(avance_por_periodo = jsonb_build_object(period_key, '1.0000'::text))
)
UPDATE actividad a
SET avance_por_periodo = jsonb_build_object(ap.period_key, ap.peso_ponderado::text)
FROM assigned_periods ap
JOIN eligible e ON e.cronograma_id = ap.cronograma_id
WHERE a.id = ap.actividad_id;
