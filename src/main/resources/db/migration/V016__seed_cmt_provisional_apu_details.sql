-- These ten missing APU compositions are invented provisional placeholders, not technical specifications. Replace them with verified real source data; they must not be treated as real costs.

WITH provisional_apus(codigo, costo_directo) AS (
    VALUES
    -- 500BKC provisional APU source-gap placeholder.
    ('500BKC', 7.796610::numeric),
    -- 500COH provisional APU source-gap placeholder.
    ('500COH', 10.016949::numeric),
    -- 501DS8 provisional APU source-gap placeholder.
    ('501DS8', 150.813559::numeric),
    -- 501DS9 provisional APU source-gap placeholder.
    ('501DS9', 1.822034::numeric),
    -- 501B50 provisional APU source-gap placeholder.
    ('501B50', 3.567797::numeric),
    -- 501DIH provisional APU source-gap placeholder.
    ('501DIH', 2.186441::numeric),
    -- 501067 provisional APU source-gap placeholder.
    ('501067', 53.847458::numeric),
    -- 500BFU provisional APU source-gap placeholder.
    ('500BFU', 219.771186::numeric),
    -- 500BFV provisional APU source-gap placeholder.
    ('500BFV', 163.372881::numeric),
    -- 504239 provisional APU source-gap placeholder.
    ('504239', 3125.186441::numeric)
),
section_amounts AS (
    SELECT p.codigo, s.tipo, s.orden,
           CASE s.orden
               WHEN 0 THEN round(p.costo_directo * 0.10, 6)
               WHEN 1 THEN round(p.costo_directo * 0.30, 6)
               WHEN 2 THEN round(p.costo_directo * 0.50, 6)
               ELSE p.costo_directo
                    - round(p.costo_directo * 0.10, 6)
                    - round(p.costo_directo * 0.30, 6)
                    - round(p.costo_directo * 0.50, 6)
           END::numeric(14,6) AS subtotal
    FROM provisional_apus p
    CROSS JOIN (VALUES
        ('EQUIPO', 0), ('MANO_OBRA', 1), ('MATERIAL', 2), ('TRANSPORTE', 3)
    ) AS s(tipo, orden)
),
cmt AS (
    SELECT pr.id AS proyecto_id, p.id AS presupuesto_id, b.id AS base_id
    FROM proyecto pr
    JOIN presupuesto p ON p.proyecto_id = pr.id AND p.version = 1
    JOIN base_insumos b ON b.proyecto_id = pr.id AND b.tipo = 'PROYECTO'
    WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán'
)
INSERT INTO insumo (public_id, base_id, codigo, tipo, descripcion, unidad, precio_unitario)
SELECT overlay(overlay(md5('cmt-provisional:' || s.codigo || '-' || s.tipo)
                       placing '7' from 13 for 1)
                       placing '8' from 17 for 1)::uuid,
       c.base_id,
       'CMT-PROV-' || s.codigo || '-' || CASE s.tipo
           WHEN 'EQUIPO' THEN 'EQ'
           WHEN 'MANO_OBRA' THEN 'MO'
           WHEN 'MATERIAL' THEN 'MAT'
           ELSE 'TR'
       END,
       s.tipo,
       'PROVISIONAL accounting/UI placeholder input for APU ' || s.codigo || ' (' || s.tipo || ')',
       CASE WHEN s.tipo IN ('EQUIPO', 'MANO_OBRA') THEN 'h' ELSE 'u' END,
       1.000000
FROM section_amounts s
CROSS JOIN cmt c
ON CONFLICT (base_id, codigo) DO NOTHING;

WITH provisional_apus(codigo, costo_directo) AS (
    VALUES
    -- 500BKC provisional section tuple group.
    ('500BKC', 7.796610::numeric),
    -- 500COH provisional section tuple group.
    ('500COH', 10.016949::numeric),
    -- 501DS8 provisional section tuple group.
    ('501DS8', 150.813559::numeric),
    -- 501DS9 provisional section tuple group.
    ('501DS9', 1.822034::numeric),
    -- 501B50 provisional section tuple group.
    ('501B50', 3.567797::numeric),
    -- 501DIH provisional section tuple group.
    ('501DIH', 2.186441::numeric),
    -- 501067 provisional section tuple group.
    ('501067', 53.847458::numeric),
    -- 500BFU provisional section tuple group.
    ('500BFU', 219.771186::numeric),
    -- 500BFV provisional section tuple group.
    ('500BFV', 163.372881::numeric),
    -- 504239 provisional section tuple group.
    ('504239', 3125.186441::numeric)
),
section_amounts AS (
    SELECT p.codigo, s.tipo, s.orden,
           CASE s.orden
               WHEN 0 THEN round(p.costo_directo * 0.10, 6)
               WHEN 1 THEN round(p.costo_directo * 0.30, 6)
               WHEN 2 THEN round(p.costo_directo * 0.50, 6)
               ELSE p.costo_directo
                    - round(p.costo_directo * 0.10, 6)
                    - round(p.costo_directo * 0.30, 6)
                    - round(p.costo_directo * 0.50, 6)
           END::numeric(14,6) AS subtotal
    FROM provisional_apus p
    CROSS JOIN (VALUES
        ('EQUIPO', 0), ('MANO_OBRA', 1), ('MATERIAL', 2), ('TRANSPORTE', 3)
    ) AS s(tipo, orden)
)
INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden)
SELECT a.id, s.tipo, s.subtotal, s.orden
FROM section_amounts s
JOIN apu a ON a.codigo = s.codigo
JOIN presupuesto p ON p.id = a.presupuesto_id AND p.version = 1
JOIN proyecto pr ON pr.id = p.proyecto_id AND pr.nombre_proyecto = 'Cetro Médico Tulcán';

WITH provisional_apus(codigo, costo_directo) AS (
    VALUES
    -- 500BKC provisional detail tuple group.
    ('500BKC', 7.796610::numeric),
    -- 500COH provisional detail tuple group.
    ('500COH', 10.016949::numeric),
    -- 501DS8 provisional detail tuple group.
    ('501DS8', 150.813559::numeric),
    -- 501DS9 provisional detail tuple group.
    ('501DS9', 1.822034::numeric),
    -- 501B50 provisional detail tuple group.
    ('501B50', 3.567797::numeric),
    -- 501DIH provisional detail tuple group.
    ('501DIH', 2.186441::numeric),
    -- 501067 provisional detail tuple group.
    ('501067', 53.847458::numeric),
    -- 500BFU provisional detail tuple group.
    ('500BFU', 219.771186::numeric),
    -- 500BFV provisional detail tuple group.
    ('500BFV', 163.372881::numeric),
    -- 504239 provisional detail tuple group.
    ('504239', 3125.186441::numeric)
),
section_amounts AS (
    SELECT p.codigo, s.tipo, s.orden,
           CASE s.orden
               WHEN 0 THEN round(p.costo_directo * 0.10, 6)
               WHEN 1 THEN round(p.costo_directo * 0.30, 6)
               WHEN 2 THEN round(p.costo_directo * 0.50, 6)
               ELSE p.costo_directo
                    - round(p.costo_directo * 0.10, 6)
                    - round(p.costo_directo * 0.30, 6)
                    - round(p.costo_directo * 0.50, 6)
           END::numeric(14,6) AS subtotal
    FROM provisional_apus p
    CROSS JOIN (VALUES
        ('EQUIPO', 0), ('MANO_OBRA', 1), ('MATERIAL', 2), ('TRANSPORTE', 3)
    ) AS s(tipo, orden)
)
INSERT INTO apu_detalle (public_id, seccion_id, insumo_id, descripcion, orden, es_herramienta_menor,
                         cantidad, costo_hora, rendimiento, unidad, precio_unitario_tarifa, costo)
SELECT overlay(overlay(md5('cmt-provisional-detail:' || a.codigo || '-' || s.tipo)
                       placing '7' from 13 for 1)
                       placing '8' from 17 for 1)::uuid,
       s.id, i.id,
       'PROVISIONAL accounting/UI placeholder for APU ' || a.codigo || ' - ' || s.tipo
           || '; replace with verified source detail',
       1, FALSE, s.subtotal, 1.000000, 1.000000,
       CASE WHEN s.tipo IN ('EQUIPO', 'MANO_OBRA') THEN 'h' ELSE 'u' END,
       1.000000, s.subtotal
FROM section_amounts amounts
JOIN apu a ON a.codigo = amounts.codigo
JOIN presupuesto p ON p.id = a.presupuesto_id AND p.version = 1
JOIN proyecto pr ON pr.id = p.proyecto_id AND pr.nombre_proyecto = 'Cetro Médico Tulcán'
JOIN apu_seccion s ON s.apu_id = a.id AND s.tipo = amounts.tipo
JOIN base_insumos b ON b.proyecto_id = pr.id AND b.tipo = 'PROYECTO'
JOIN insumo i ON i.base_id = b.id
 AND i.codigo = 'CMT-PROV-' || a.codigo || '-' || CASE s.tipo
     WHEN 'EQUIPO' THEN 'EQ'
     WHEN 'MANO_OBRA' THEN 'MO'
     WHEN 'MATERIAL' THEN 'MAT'
     ELSE 'TR'
 END;

DO $$
DECLARE
    provisional_count integer;
    invalid_count integer;
BEGIN
    SELECT count(*) INTO provisional_count
    FROM apu a
    JOIN presupuesto p ON p.id = a.presupuesto_id AND p.version = 1
    JOIN proyecto pr ON pr.id = p.proyecto_id
    WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán'
      AND a.codigo IN ('500BKC','500COH','501DS8','501DS9','501B50','501DIH','501067','500BFU','500BFV','504239');

    IF provisional_count <> 10 THEN
        RAISE EXCEPTION 'Expected exactly ten CMT provisional APUs, found %', provisional_count;
    END IF;

    SELECT count(*) INTO invalid_count
    FROM (
        SELECT a.id, a.costo_directo, count(s.id) AS section_count, coalesce(sum(s.subtotal), 0) AS section_sum
        FROM apu a
        JOIN presupuesto p ON p.id = a.presupuesto_id AND p.version = 1
        JOIN proyecto pr ON pr.id = p.proyecto_id
        LEFT JOIN apu_seccion s ON s.apu_id = a.id
        WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán'
          AND a.codigo IN ('500BKC','500COH','501DS8','501DS9','501B50','501DIH','501067','500BFU','500BFV','504239')
        GROUP BY a.id, a.costo_directo
        HAVING count(s.id) <> 4 OR coalesce(sum(s.subtotal), 0) <> a.costo_directo
    ) invalid_sections;

    IF invalid_count <> 0 THEN
        RAISE EXCEPTION 'CMT provisional section subtotals do not equal the pre-existing APU costo_directo';
    END IF;
END $$;
