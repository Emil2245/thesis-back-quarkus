-- Plan 016: add especificacion_tecnica to apu, titulo_et fields to proyecto
ALTER TABLE apu ADD COLUMN especificacion_tecnica TEXT;
ALTER TABLE proyecto ADD COLUMN titulo_et_1 TEXT;
ALTER TABLE proyecto ADD COLUMN titulo_et_2 TEXT;
