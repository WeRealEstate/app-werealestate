-- Número de venta por desarrollo, igual que el de clientes (V72): SAMAI (S-1, S-2…) y Aldea Nanuu (N-1, N-2…) llevan cada
-- uno su numeración consecutiva, sin saltos. Es aditiva: el número global de siempre (venta.numero) se conserva intacto
-- como respaldo interno y ya no se muestra.
--
--   desarrollo_id: el desarrollo con el que cuenta la venta = el de su primer lote (en una venta con lotes de dos
--                  desarrollos manda el primero).
--   numero_desarrollo: su número dentro de ese desarrollo. Las ventas existentes se numeran por su número global actual
--                  (el orden en que se registraron), de 1 en adelante en cada desarrollo.
ALTER TABLE venta ADD COLUMN desarrollo_id BIGINT REFERENCES desarrollo (id);
ALTER TABLE venta ADD COLUMN numero_desarrollo BIGINT;

UPDATE venta v
SET desarrollo_id = (
    SELECT l.desarrollo_id
    FROM venta_lote vl
    JOIN lote l ON l.id = vl.lote_id
    WHERE vl.venta_id = v.id
    ORDER BY vl.id
    LIMIT 1);

UPDATE venta v
SET numero_desarrollo = r.n
FROM (
    SELECT id, row_number() OVER (PARTITION BY desarrollo_id ORDER BY numero, id) AS n
    FROM venta
    WHERE desarrollo_id IS NOT NULL) r
WHERE r.id = v.id;

-- Diferido, como el del número global: recorrer o bajar números no choca a media operación.
ALTER TABLE venta ADD CONSTRAINT uk_venta_desarrollo_numero UNIQUE (desarrollo_id, numero_desarrollo) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX idx_venta_desarrollo ON venta (desarrollo_id);

-- La comisión de una venta eliminada conserva, además del número global viejo, la etiqueta que tenía ("S-12").
ALTER TABLE venta_comision ADD COLUMN venta_etiqueta VARCHAR(20);
