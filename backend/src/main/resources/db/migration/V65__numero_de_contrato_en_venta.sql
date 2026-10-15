-- Número de contrato de la venta: opcional, texto libre (hasta 50), y no puede repetirse entre ventas
-- (sin importar mayúsculas). Solo agrega una columna nullable y un índice parcial: no modifica datos existentes.
ALTER TABLE venta ADD COLUMN numero_contrato VARCHAR(50);
CREATE UNIQUE INDEX uq_venta_numero_contrato ON venta (LOWER(numero_contrato)) WHERE numero_contrato IS NOT NULL;
