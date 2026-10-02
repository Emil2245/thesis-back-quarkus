-- Normaliza únicamente números JSON de mapas legacy; no modifica otros valores.
-- JSONB conserva la precisión/escala decimal de PostgreSQL, sin paso por float.
UPDATE actividad AS a
SET avance_por_periodo = (
    SELECT jsonb_object_agg(e.key,
        CASE WHEN jsonb_typeof(e.value) = 'number'
             THEN to_jsonb((e.value::text)::numeric::text)
             ELSE e.value
        END)
    FROM jsonb_each(a.avance_por_periodo) AS e
)
WHERE jsonb_typeof(a.avance_por_periodo) = 'object'
  AND EXISTS (
      SELECT 1
      FROM jsonb_each(
          CASE WHEN jsonb_typeof(a.avance_por_periodo) = 'object'
               THEN a.avance_por_periodo ELSE '{}'::jsonb END
      ) AS e
      WHERE jsonb_typeof(e.value) = 'number'
  );
