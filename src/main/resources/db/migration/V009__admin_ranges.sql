-- Plan 019: add configurable range columns to parametros_sistema
ALTER TABLE parametros_sistema ADD COLUMN rango_hm_min NUMERIC(5,4) DEFAULT 0;
ALTER TABLE parametros_sistema ADD COLUMN rango_hm_max NUMERIC(5,4) DEFAULT 0.2000;
ALTER TABLE parametros_sistema ADD COLUMN rango_ci_min NUMERIC(5,4) DEFAULT 0;
ALTER TABLE parametros_sistema ADD COLUMN rango_ci_max NUMERIC(5,4) DEFAULT 1.0000;
ALTER TABLE parametros_sistema ADD COLUMN rango_descuento_min NUMERIC(5,4) DEFAULT 0;
ALTER TABLE parametros_sistema ADD COLUMN rango_descuento_max NUMERIC(5,4) DEFAULT 0.5000;
ALTER TABLE parametros_sistema ADD COLUMN rango_iva_min NUMERIC(5,4) DEFAULT 0;
ALTER TABLE parametros_sistema ADD COLUMN rango_iva_max NUMERIC(5,4) DEFAULT 0.3000;

UPDATE parametros_sistema SET
  rango_hm_min = 0,
  rango_hm_max = 0.2000,
  rango_ci_min = 0,
  rango_ci_max = 1.0000,
  rango_descuento_min = 0,
  rango_descuento_max = 0.5000,
  rango_iva_min = 0,
  rango_iva_max = 0.3000
WHERE id = 1;
