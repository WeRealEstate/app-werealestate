-- Pausa de mensualidades: el cliente deja de pagar un tiempo y su mensualidad N pasa a una fecha posterior; las
-- siguientes (y sus aportaciones) se recorren mes a mes desde ahí y el plazo sigue completo. Los meses de la pausa no
-- tienen renglón ni cuentan como atraso. Se deshace la más reciente (queda en la bitácora, no se borra).
-- Solo agrega una tabla: no toca datos existentes.
CREATE TABLE venta_pausa (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta(id),
    -- Plan al que pertenece: la reestructuración que lo creó (null = el plan original de la venta), igual que en
    -- venta_aportacion. Sin llave foránea para no depender del orden de borrado al eliminar la venta.
    reestructuracion_id BIGINT,
    desde_numero INTEGER NOT NULL,
    fecha_anterior DATE NOT NULL,
    fecha_nueva DATE NOT NULL,
    motivo VARCHAR(500) NOT NULL,
    usuario_id BIGINT REFERENCES usuario(id),
    fecha_registro TIMESTAMP NOT NULL DEFAULT now(),
    deshecha_en TIMESTAMP,
    deshecha_por_id BIGINT REFERENCES usuario(id),
    motivo_deshacer VARCHAR(500)
);

CREATE INDEX idx_venta_pausa_venta ON venta_pausa(venta_id);
