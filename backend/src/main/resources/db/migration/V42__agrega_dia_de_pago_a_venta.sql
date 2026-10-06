-- Día del mes en que el cliente paga su mensualidad (1-31; en meses más cortos se cobra el último
-- día del mes) y si el mes de la venta cuenta como la primera mensualidad o esta cae hasta el mes
-- siguiente. Base del calendario de ingresos esperados de Finanzas. Las ventas ya registradas
-- toman el día de su fecha de venta y la primera mensualidad el mes siguiente; ambos se pueden
-- editar en la venta.
ALTER TABLE venta
    ADD COLUMN dia_pago INTEGER,
    ADD COLUMN primera_mensualidad_mes_venta BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE venta SET dia_pago = EXTRACT(DAY FROM fecha_venta)::INTEGER;

ALTER TABLE venta
    ALTER COLUMN dia_pago SET NOT NULL,
    ADD CONSTRAINT ck_venta_dia_pago CHECK (dia_pago BETWEEN 1 AND 31);
