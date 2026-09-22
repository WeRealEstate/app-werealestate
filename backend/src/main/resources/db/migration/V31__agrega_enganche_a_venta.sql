-- Mismo par de campos que ya usa "cotizacion" (engancheLabel/enganche) para el mismo concepto:
-- "Enganche" / "Pago inicial" / "Aportación anual", según el tipo de pago elegido en el formulario
-- (ver VentaFormComponent). Ambos nulos cuando no aplica (Sin enganche / Contado).
ALTER TABLE venta ADD COLUMN enganche_label VARCHAR(30);
ALTER TABLE venta ADD COLUMN enganche NUMERIC(14, 2);
