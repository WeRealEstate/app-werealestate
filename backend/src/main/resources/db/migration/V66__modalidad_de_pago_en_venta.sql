-- Modalidad de pago de la venta: EFECTIVO o TRANSFERENCIA (una sola). Opcional: las ventas existentes quedan
-- sin definir. Solo agrega una columna nullable: no modifica ningún dato existente.
ALTER TABLE venta ADD COLUMN modalidad_pago VARCHAR(20);
