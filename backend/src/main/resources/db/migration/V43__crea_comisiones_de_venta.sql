-- Comisión de cada venta (5% del valor de la venta por defecto, editable). Se acumula con los
-- abonos de la venta (venta_comision_devengo) y se entrega los sábados (venta_comision_entrega, que
-- además genera un gasto de tipo "Comisiones"). Si la venta se elimina, la comisión se queda en el
-- historial (venta_id pasa a NULL); por eso guarda su propio cliente.
CREATE TABLE venta_comision (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT REFERENCES venta(id) ON DELETE SET NULL,
    venta_numero BIGINT,
    cliente VARCHAR(200) NOT NULL,
    usuario_asesor_id BIGINT REFERENCES usuario(id),
    asesor_externo_id BIGINT REFERENCES asesor_externo(id),
    base NUMERIC(14, 2) NOT NULL,
    porcentaje NUMERIC(9, 4) NOT NULL,
    monto NUMERIC(14, 2) NOT NULL CHECK (monto >= 0),
    monto_manual BOOLEAN NOT NULL DEFAULT FALSE,
    modalidad VARCHAR(20) NOT NULL,
    cancelada BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_venta_comision_asesor CHECK (usuario_asesor_id IS NOT NULL OR asesor_externo_id IS NOT NULL)
);
CREATE UNIQUE INDEX uk_venta_comision_venta ON venta_comision(venta_id) WHERE venta_id IS NOT NULL;

-- Lo que la comisión ya ganó por cada abono (la mitad) o de golpe (una exhibición), y el sábado
-- en que toca entregarlo.
CREATE TABLE venta_comision_devengo (
    id BIGSERIAL PRIMARY KEY,
    comision_id BIGINT NOT NULL REFERENCES venta_comision(id) ON DELETE CASCADE,
    pago_venta_id BIGINT REFERENCES pago_venta(id) ON DELETE SET NULL,
    fecha_origen DATE NOT NULL,
    fecha_entrega DATE NOT NULL,
    monto NUMERIC(14, 2) NOT NULL CHECK (monto > 0)
);
CREATE INDEX idx_venta_comision_devengo_comision ON venta_comision_devengo(comision_id);

CREATE TABLE venta_comision_entrega (
    id BIGSERIAL PRIMARY KEY,
    comision_id BIGINT NOT NULL REFERENCES venta_comision(id),
    fecha DATE NOT NULL,
    monto NUMERIC(14, 2) NOT NULL CHECK (monto > 0),
    notas VARCHAR(500),
    gasto_id BIGINT REFERENCES gasto(id),
    registrada_por_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_venta_comision_entrega_comision ON venta_comision_entrega(comision_id);
