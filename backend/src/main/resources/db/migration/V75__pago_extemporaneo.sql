-- Pago extemporáneo: una línea de mensualidad registrada después de que ya se cubrieron todos los meses del plazo
-- (el cliente no terminó de pagar a tiempo). No lleva meses; solo abona al saldo. Aditiva: las líneas existentes
-- quedan como no extemporáneas.
ALTER TABLE pago_venta ADD COLUMN extemporaneo BOOLEAN NOT NULL DEFAULT FALSE;
