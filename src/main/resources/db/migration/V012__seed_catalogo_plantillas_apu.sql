-- V012__seed_catalogo_plantillas_apu.sql
-- Representative, price-free SISTEMA APU templates for public-works search.
-- All insumoCodigo values come from the canonical V003 IESS base.
INSERT INTO plantilla_apu
    (public_id, nombre, tipo, usuario_id, descripcion_rubro, unidad, snapshot_secciones)
VALUES
  ('0192f6c4-7c8a-7abc-8000-000000002010'::uuid, 'Replanteo y nivelación de terreno', 'SISTEMA', NULL,
   'Replanteo y nivelación de terreno para obras civiles', 'm2',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"esHerramientaMenor":true}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-001","cantidad":0.1,"rendimiento":0.1},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.1}]},{"tipo":"MATERIAL","lineas":[]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002011'::uuid, 'Contrapiso de hormigón simple', 'SISTEMA', NULL,
   'Contrapiso de hormigón para edificaciones públicas', 'm2',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"insumoCodigo":"EQ-011","cantidad":0.01,"rendimiento":0.1}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-001","cantidad":0.1,"rendimiento":0.2},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.2}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-035","cantidad":0.15,"rendimiento":1},{"insumoCodigo":"MA-036","cantidad":0.05,"rendimiento":1},{"insumoCodigo":"MA-003","cantidad":0.02,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002012'::uuid, 'Mampostería con bloque de 15 cm', 'SISTEMA', NULL,
   'Mampostería de bloque de hormigón para equipamiento comunitario', 'm2',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"esHerramientaMenor":true}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-001","cantidad":0.1,"rendimiento":0.8},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.8}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-035","cantidad":0.1,"rendimiento":1},{"insumoCodigo":"MA-036","cantidad":0.03,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002013'::uuid, 'Enlucido vertical interior', 'SISTEMA', NULL,
   'Enlucido vertical interior con acabado fino', 'm2',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"esHerramientaMenor":true}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-001","cantidad":0.1,"rendimiento":0.35},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.35}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-035","cantidad":0.08,"rendimiento":1},{"insumoCodigo":"MA-036","cantidad":0.025,"rendimiento":1},{"insumoCodigo":"MA-003","cantidad":0.01,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002014'::uuid, 'Instalación de porcelanato en piso', 'SISTEMA', NULL,
   'Instalación de porcelanato rectificado en pisos institucionales', 'm2',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"insumoCodigo":"EQ-004","cantidad":0.01,"rendimiento":0.2}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-005","cantidad":1,"rendimiento":0.45},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.45}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-001","cantidad":4,"rendimiento":1},{"insumoCodigo":"MA-004","cantidad":1.05,"rendimiento":1},{"insumoCodigo":"MA-002","cantidad":0.15,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002015'::uuid, 'Pintura de paredes interiores', 'SISTEMA', NULL,
   'Pintura de paredes interiores en centros de salud y educación', 'm2',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"esHerramientaMenor":true}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-001","cantidad":0.1,"rendimiento":0.2},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.2}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-055","cantidad":0.12,"rendimiento":1},{"insumoCodigo":"MA-014","cantidad":0.03,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002016'::uuid, 'Desmontaje de puertas y ventanas', 'SISTEMA', NULL,
   'Desmontaje de puertas y ventanas existentes, con retiro', 'u',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"insumoCodigo":"EQ-010","cantidad":0.05,"rendimiento":0.5}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-001","cantidad":0.1,"rendimiento":0.5},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.5}]},{"tipo":"MATERIAL","lineas":[]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002017'::uuid, 'Instalación eléctrica de tomacorrientes', 'SISTEMA', NULL,
   'Instalación eléctrica de tomacorrientes para edificio público', 'u',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"insumoCodigo":"EQ-006","cantidad":0.01,"rendimiento":0.3}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-008","cantidad":1,"rendimiento":0.3},{"insumoCodigo":"MO-009","cantidad":1,"rendimiento":0.3}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-021","cantidad":0.25,"rendimiento":1},{"insumoCodigo":"MA-022","cantidad":0.05,"rendimiento":1},{"insumoCodigo":"MA-024","cantidad":3,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002018'::uuid, 'Instalación de red hidrosanitaria', 'SISTEMA', NULL,
   'Instalación de red hidrosanitaria en batería sanitaria', 'u',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"esHerramientaMenor":true}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-012","cantidad":1,"rendimiento":0.6},{"insumoCodigo":"MO-013","cantidad":1,"rendimiento":0.6}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-037","cantidad":0.2,"rendimiento":1},{"insumoCodigo":"MA-038","cantidad":0.1,"rendimiento":1},{"insumoCodigo":"MA-039","cantidad":0.1,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002019'::uuid, 'Acero inoxidable para pasamanos', 'SISTEMA', NULL,
   'Fabricación e instalación de pasamanos de acero inoxidable', 'm',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"insumoCodigo":"EQ-008","cantidad":0.05,"rendimiento":0.4}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-015","cantidad":1,"rendimiento":0.4},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.4}]},{"tipo":"MATERIAL","lineas":[{"insumoCodigo":"MA-019","cantidad":1,"rendimiento":1},{"insumoCodigo":"MA-020","cantidad":2,"rendimiento":1},{"insumoCodigo":"MA-018","cantidad":0.02,"rendimiento":1}]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb),
  ('0192f6c4-7c8a-7abc-8000-000000002020'::uuid, 'Desalojo de escombros de obra', 'SISTEMA', NULL,
   'Desalojo de escombros y limpieza de obra pública', 'm3',
   '{"secciones":[{"tipo":"EQUIPO","lineas":[{"insumoCodigo":"EQ-001","cantidad":0.1,"rendimiento":0.3},{"insumoCodigo":"EQ-002","cantidad":0.05,"rendimiento":0.3}]},{"tipo":"MANO_OBRA","lineas":[{"insumoCodigo":"MO-003","cantidad":1,"rendimiento":0.3},{"insumoCodigo":"MO-002","cantidad":1,"rendimiento":0.3}]},{"tipo":"MATERIAL","lineas":[]},{"tipo":"TRANSPORTE","lineas":[]}]}'::jsonb);
