-- Etapa 2 de abonos. Migración ADITIVA: no borra ni modifica filas existentes.
--
-- 1) Anulación de recibos: el recibo no se borra; queda marcado con motivo, quién y cuándo. Su dinero
--    deja de contar en el saldo de la venta y en las comisiones; en Finanzas se registra un
--    contra-asiento en la fecha de anulación (el mes original no cambia).
ALTER TABLE recibo_venta ADD COLUMN anulado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE recibo_venta ADD COLUMN anulado_motivo VARCHAR(500);
ALTER TABLE recibo_venta ADD COLUMN anulado_por_id BIGINT REFERENCES usuario(id);
ALTER TABLE recibo_venta ADD COLUMN anulado_fecha DATE;
ALTER TABLE recibo_venta ADD COLUMN anulado_en TIMESTAMP;

-- El folio de un recibo anulado queda libre para reutilizarse (el índice solo cubre los vigentes).
DROP INDEX uq_recibo_venta_folio;
CREATE UNIQUE INDEX uq_recibo_venta_folio ON recibo_venta (lower(btrim(folio))) WHERE folio IS NOT NULL AND anulado = FALSE;

-- 2) Bitácora de cada recibo: quién lo creó, editó o anuló, cuándo y qué cambió.
CREATE TABLE recibo_venta_historial (
    id BIGSERIAL PRIMARY KEY,
    recibo_id BIGINT NOT NULL REFERENCES recibo_venta(id),
    accion VARCHAR(20) NOT NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_hora TIMESTAMP NOT NULL DEFAULT now(),
    detalle VARCHAR(2000)
);
CREATE INDEX idx_recibo_historial_recibo ON recibo_venta_historial (recibo_id);

-- 3) Devolución de comisión: el asesor devuelve (total o parcialmente) una comisión ya entregada. Se
--    captura a mano y cuenta como ingreso en Finanzas; la entrega original no se toca.
CREATE TABLE venta_comision_devolucion (
    id BIGSERIAL PRIMARY KEY,
    comision_id BIGINT NOT NULL REFERENCES venta_comision(id),
    fecha DATE NOT NULL,
    monto NUMERIC(14, 2) NOT NULL CHECK (monto > 0),
    notas VARCHAR(500),
    registrada_por_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_comision_devolucion_comision ON venta_comision_devolucion (comision_id);
