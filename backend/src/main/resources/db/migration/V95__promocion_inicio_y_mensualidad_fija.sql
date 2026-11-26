-- Fecha de inicio de una promoción (12.17.0). Las que ya existían no tienen: valen desde siempre (null = sin inicio).
ALTER TABLE promocion ADD COLUMN fecha_inicio TIMESTAMP;
-- false mientras una promoción programada espera su fecha de inicio: al llegar se activa sola
-- (PromocionService.actualizarVigencias). Las que ya existían ya empezaron.
ALTER TABLE promocion ADD COLUMN inicio_aplicado BOOLEAN NOT NULL DEFAULT TRUE;
-- Mensualidad fija de la promoción de la venta (copia, igual que promocion_nombre y promocion_bono): solo las de
-- modalidad MENSUALIDAD_FIJA, que se ponen desde «Modificar venta».
ALTER TABLE venta ADD COLUMN promocion_mensualidad_fija NUMERIC(14,2);
