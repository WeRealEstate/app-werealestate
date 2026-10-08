-- Precio por m² propio de un lote (null = el del desarrollo). Los macrolotes / hectáreas de SAMAI
-- (más de 8,000 m²) se cobran a $170/m², no al precio de lote normal.
ALTER TABLE lote ADD COLUMN precio_m2 NUMERIC(12, 2);
UPDATE lote SET precio_m2 = 170
WHERE superficie > 8000
  AND desarrollo_id IN (SELECT id FROM desarrollo WHERE lower(nombre) LIKE '%samai%');
