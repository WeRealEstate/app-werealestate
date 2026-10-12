-- Etapa 3 de abonos. Migración ADITIVA: solo agrega columnas con valor por defecto; no cambia el
-- significado de ninguna fila existente.
--
-- 1) Contrato firmado de la venta (Sí/No y fecha de firma). Las ventas existentes quedan "sin firmar".
ALTER TABLE venta ADD COLUMN contrato_firmado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE venta ADD COLUMN contrato_fecha_firma DATE;

-- 2) Regla de comisión por tipo de abono (enganche completo, mensualidad y aportación a la mitad,
--    apartado sin comisión; entrega frenada hasta que el contrato esté firmado). Solo aplica a las
--    comisiones de las ventas creadas DESPUÉS de desplegar esto: las existentes quedan en FALSE y
--    conservan exactamente su cálculo actual.
ALTER TABLE venta_comision ADD COLUMN regla_nueva BOOLEAN NOT NULL DEFAULT FALSE;
