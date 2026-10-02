-- Complete absence or complete exact replay only. Defaults own internal IDs.
INSERT INTO base_insumos(public_id,nombre,tipo,usuario_id,proyecto_id,archivada)
SELECT public_id,nombre,'CENTRAL',NULL,NULL,false FROM expected_base
WHERE (SELECT bases FROM before_state)=0;
INSERT INTO insumo(public_id,base_id,codigo,tipo,descripcion,unidad,precio_unitario)
SELECT e.public_id,b.id,e.codigo,e.tipo,e.descripcion,e.unidad,e.precio_unitario
FROM expected_insumo e CROSS JOIN expected_base eb
JOIN base_insumos b ON b.public_id=eb.public_id
WHERE (SELECT bases FROM before_state)=0;
INSERT INTO plantilla_apu(public_id,nombre,tipo,usuario_id,descripcion_rubro,unidad,
                          especificacion_tecnica,snapshot_secciones)
SELECT public_id,nombre,'SISTEMA',NULL,descripcion_rubro,unidad,
       especificacion_tecnica,snapshot_secciones FROM expected_template
WHERE (SELECT bases FROM before_state)=0;
