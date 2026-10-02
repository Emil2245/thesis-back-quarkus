\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
SET LOCAL search_path = public, pg_temp;
-- Same family-wide constant, independent of release, base name and prefix.
SELECT pg_advisory_xact_lock(724619238501);
LOCK TABLE base_insumos, insumo, plantilla_apu IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE stage_payload (payload jsonb NOT NULL);
COPY stage_payload FROM STDIN;
-- PAYLOAD
CREATE TEMP VIEW expected_base AS
SELECT (payload->'base'->>'public_id')::uuid public_id,
       payload->'base'->>'nombre' nombre FROM stage_payload;
CREATE TEMP VIEW expected_insumo AS
SELECT (r->>'public_id')::uuid public_id, r->>'codigo' codigo, r->>'tipo' tipo,
       r->>'descripcion' descripcion, r->>'unidad' unidad,
       (r->>'precio_unitario')::numeric precio_unitario
FROM stage_payload, LATERAL jsonb_array_elements(payload->'insumos') r;
CREATE TEMP VIEW expected_template AS
SELECT (r->>'public_id')::uuid public_id, r->>'nombre' nombre,
       r->>'descripcion_rubro' descripcion_rubro, r->>'unidad' unidad,
       r->>'especificacion_tecnica' especificacion_tecnica,
       r->'snapshot_secciones' snapshot_secciones
FROM stage_payload, LATERAL jsonb_array_elements(payload->'plantillas') r;
CREATE TEMP VIEW actual_base AS
SELECT b.public_id, b.nombre FROM base_insumos b JOIN expected_base e USING(public_id)
WHERE b.tipo='CENTRAL' AND b.usuario_id IS NULL AND b.proyecto_id IS NULL AND NOT b.archivada;
CREATE TEMP VIEW actual_insumo AS
SELECT i.public_id, i.codigo, i.tipo, i.descripcion, i.unidad, i.precio_unitario
FROM insumo i JOIN expected_insumo e USING(public_id)
JOIN base_insumos b ON b.id=i.base_id JOIN expected_base eb ON eb.public_id=b.public_id;
CREATE TEMP VIEW actual_template AS
SELECT t.public_id, t.nombre, t.descripcion_rubro, t.unidad,
       t.especificacion_tecnica, t.snapshot_secciones
FROM plantilla_apu t JOIN expected_template e USING(public_id)
WHERE t.tipo='SISTEMA' AND t.usuario_id IS NULL;
-- Only digests and counts escape the session; no users/PII/raw existing content.
CREATE TEMP VIEW preservation AS
SELECT jsonb_build_object(
 'base_insumos', (SELECT md5(coalesce(string_agg(to_jsonb(b)::text,'|' ORDER BY b.id),''))
  FROM base_insumos b WHERE NOT EXISTS (SELECT 1 FROM expected_base e WHERE e.public_id=b.public_id)),
 'insumo', (SELECT md5(coalesce(string_agg(to_jsonb(i)::text,'|' ORDER BY i.id),''))
  FROM insumo i WHERE NOT EXISTS (SELECT 1 FROM expected_insumo e WHERE e.public_id=i.public_id)),
 'plantilla_apu', (SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'|' ORDER BY t.id),''))
  FROM plantilla_apu t WHERE NOT EXISTS (SELECT 1 FROM expected_template e WHERE e.public_id=t.public_id)),
 'proyecto', (SELECT md5(coalesce(string_agg(to_jsonb(p)::text,'|' ORDER BY p.id),'')) FROM proyecto p),
 'presupuesto', (SELECT md5(coalesce(string_agg(to_jsonb(p)::text,'|' ORDER BY p.id),'')) FROM presupuesto p),
 'apu', (SELECT md5(coalesce(string_agg(to_jsonb(a)::text,'|' ORDER BY a.id),'')) FROM apu a),
 'rubro', (SELECT md5(coalesce(string_agg(to_jsonb(r)::text,'|' ORDER BY r.id),'')) FROM rubro r),
 'parametros_sistema', (SELECT md5(coalesce(string_agg(to_jsonb(p)::text,'|' ORDER BY p.id),'')) FROM parametros_sistema p),
 'parametros_proyecto', (SELECT md5(coalesce(string_agg(to_jsonb(p)::text,'|' ORDER BY p.proyecto_id),'')) FROM parametros_proyecto p)
) hashes;
CREATE TEMP TABLE before_state AS
SELECT hashes,
 (SELECT count(*) FROM base_insumos b JOIN expected_base e USING(public_id)) bases,
 (SELECT count(*) FROM insumo i JOIN expected_insumo e USING(public_id)) insumos,
 (SELECT count(*) FROM plantilla_apu t JOIN expected_template e USING(public_id)) templates
FROM preservation;
DO $$
DECLARE present bigint; total bigint;
BEGIN
 IF current_database() <> 'propuestas' OR current_user <> 'postgres' THEN
  RAISE EXCEPTION 'Wrong local target';
 END IF;
 SELECT bases+insumos+templates INTO present FROM before_state;
 SELECT 1+(SELECT count(*) FROM expected_insumo)+(SELECT count(*) FROM expected_template) INTO total;
 IF present NOT IN (0,total) THEN RAISE EXCEPTION 'Partial prepared set: STOP'; END IF;
 -- All CENTRAL bases including archived; another base/public identity never adopted.
 IF EXISTS (SELECT 1 FROM insumo i JOIN base_insumos b ON b.id=i.base_id
            JOIN expected_insumo e ON e.codigo=i.codigo
            WHERE b.tipo='CENTRAL' AND
              (i.public_id<>e.public_id OR b.public_id<>(SELECT public_id FROM expected_base))) THEN
  RAISE EXCEPTION 'Global CENTRAL code collision';
 END IF;
 IF EXISTS (SELECT 1 FROM base_insumos b JOIN expected_base e ON e.nombre=b.nombre
            WHERE b.tipo='CENTRAL' AND b.public_id<>e.public_id) THEN
  RAISE EXCEPTION 'Base name occupied by different identity';
 END IF;
 IF present=total AND (
  EXISTS (SELECT * FROM expected_base EXCEPT SELECT * FROM actual_base) OR
  EXISTS (SELECT * FROM expected_insumo EXCEPT SELECT * FROM actual_insumo) OR
  EXISTS (SELECT * FROM expected_template EXCEPT SELECT * FROM actual_template) OR
  (SELECT count(*) FROM insumo WHERE base_id=(SELECT b.id FROM base_insumos b JOIN expected_base e USING(public_id)))
     <> (SELECT count(*) FROM expected_insumo)) THEN
  RAISE EXCEPTION 'Existing payload/ownership/archival mismatch';
 END IF;
END $$;
-- WRITE
DO $$
BEGIN
 IF EXISTS (SELECT * FROM expected_base EXCEPT SELECT * FROM actual_base) OR
    EXISTS (SELECT * FROM expected_insumo EXCEPT SELECT * FROM actual_insumo) OR
    EXISTS (SELECT * FROM expected_template EXCEPT SELECT * FROM actual_template) THEN
  RAISE EXCEPTION 'Full readback mismatch or release absent';
 END IF;
 IF (SELECT hashes FROM preservation) <> (SELECT hashes FROM before_state) THEN
  RAISE EXCEPTION 'Preservation mismatch/concurrent external modification';
 END IF;
END $$;
SELECT jsonb_build_object('release_sha256',payload->>'release_sha256',
 'source_hashes',payload->'source_hashes',
 'inserted',jsonb_build_object('base',1-bases,
  'insumos',(SELECT count(*) FROM expected_insumo)-insumos,
  'plantillas',(SELECT count(*) FROM expected_template)-templates),
 'noop',jsonb_build_object('base',bases,'insumos',insumos,'plantillas',templates),
 'before_preservation',hashes,'after_preservation',(SELECT hashes FROM preservation),
 'readback',jsonb_build_object('base',(SELECT count(*) FROM actual_base),
  'insumos',(SELECT count(*) FROM actual_insumo),'plantillas',(SELECT count(*) FROM actual_template)),
 'readback_md5',jsonb_build_object(
  'base',(SELECT md5(string_agg(row_to_json(a)::text,'|' ORDER BY public_id)) FROM actual_base a),
  'insumos',(SELECT md5(string_agg(row_to_json(a)::text,'|' ORDER BY public_id)) FROM actual_insumo a),
  'plantillas',(SELECT md5(string_agg(row_to_json(a)::text,'|' ORDER BY public_id)) FROM actual_template a)))
FROM stage_payload CROSS JOIN before_state;
-- END
