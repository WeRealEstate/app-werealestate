-- Recibos de abono: un recibo (folio, fecha, nota, quién lo registró) con una o más líneas (pago_venta),
-- cada una con su tipo (MENSUALIDAD / ENGANCHE / APARTADO / APORTACION), los meses que cubre una
-- mensualidad y la aportación programada que paga una aportación.
--
-- Migración ADITIVA: no borra ni modifica datos existentes. Crea tablas y columnas nuevas, copia el
-- folio, nota y registrante de cada abono existente a su propio recibo, y deja tipo/meses/aportación
-- VACÍOS (por completar a mano; no se autocompletan). Las columnas viejas de pago_venta (folio, notas,
-- registrado_por_id, fecha_creacion) se CONSERVAN con sus datos, ya sin uso por la aplicación; solo se
-- relaja el NOT NULL de registrado_por_id para que los abonos nuevos no tengan que llenarla.
CREATE TABLE recibo_venta (
    id BIGSERIAL PRIMARY KEY,
    venta_id BIGINT NOT NULL REFERENCES venta(id),
    folio VARCHAR(50),
    fecha DATE NOT NULL,
    notas VARCHAR(500),
    registrado_por_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT now(),
    pago_origen_id BIGINT
);
CREATE INDEX idx_recibo_venta_venta ON recibo_venta (venta_id);

INSERT INTO recibo_venta (venta_id, folio, fecha, notas, registrado_por_id, fecha_creacion, pago_origen_id)
SELECT venta_id, folio, fecha, notas, registrado_por_id, fecha_creacion, id FROM pago_venta;

ALTER TABLE pago_venta ADD COLUMN recibo_id BIGINT REFERENCES recibo_venta(id);
ALTER TABLE pago_venta ADD COLUMN tipo VARCHAR(20);
ALTER TABLE pago_venta ADD COLUMN aportacion_id BIGINT REFERENCES venta_aportacion(id);

UPDATE pago_venta p SET recibo_id = r.id FROM recibo_venta r WHERE r.pago_origen_id = p.id;
ALTER TABLE pago_venta ALTER COLUMN recibo_id SET NOT NULL;
ALTER TABLE recibo_venta DROP COLUMN pago_origen_id;

-- Desde ahora el folio, la nota y quién registró viven en el recibo; los abonos nuevos ya no llenan
-- registrado_por_id en pago_venta.
ALTER TABLE pago_venta ALTER COLUMN registrado_por_id DROP NOT NULL;
CREATE UNIQUE INDEX uq_recibo_venta_folio ON recibo_venta (lower(btrim(folio))) WHERE folio IS NOT NULL;
CREATE INDEX idx_pago_venta_recibo ON pago_venta (recibo_id);

CREATE TABLE pago_venta_mes (
    pago_id BIGINT NOT NULL REFERENCES pago_venta(id) ON DELETE CASCADE,
    anio INT NOT NULL,
    mes INT NOT NULL CHECK (mes BETWEEN 1 AND 12),
    PRIMARY KEY (pago_id, anio, mes)
);
