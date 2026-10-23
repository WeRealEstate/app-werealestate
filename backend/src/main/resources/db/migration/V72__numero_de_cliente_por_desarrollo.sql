-- Número de cliente por desarrollo: SAMAI (S-1, S-2…) y Aldea Nanuu (N-1, N-2…) llevan cada uno su propia
-- numeración. Un cliente recibe número en un desarrollo con su primera venta ahí (como titular o copropietario):
-- sin venta no hay número. Una persona tiene un solo número por desarrollo aunque compre varias veces, y los
-- copropietarios de una venta toman el número del titular (por eso un número puede repetirse dentro de un
-- desarrollo, solo entre los clientes de esa compra). El desarrollo "Otro" no numera.
--
-- Solo agrega una tabla y la llena: el número consecutivo de siempre (cliente.numero) se queda intacto como
-- respaldo; no se modifica ni se borra ningún dato existente.
CREATE TABLE cliente_numero (
    id BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES cliente (id),
    desarrollo_id BIGINT NOT NULL REFERENCES desarrollo (id),
    numero BIGINT NOT NULL CHECK (numero > 0),
    CONSTRAINT uq_cliente_numero_cliente_desarrollo UNIQUE (cliente_id, desarrollo_id)
);

CREATE INDEX idx_cliente_numero_desarrollo ON cliente_numero (desarrollo_id, numero);

-- Numeración inicial, desarrollo por desarrollo, recorriendo a los titulares en el orden de su número de cliente de
-- siempre: el titular que aún no tiene número recibe el siguiente; los copropietarios de sus ventas en ese desarrollo
-- que aún no tienen número reciben el del titular. Quien ya tiene número (por ser copropietario de una compra
-- anterior en el recorrido) lo conserva: una persona, un número por desarrollo.
DO $$
DECLARE
    d RECORD;
    t RECORD;
    cp RECORD;
    siguiente BIGINT;
    n BIGINT;
BEGIN
    FOR d IN SELECT id FROM desarrollo WHERE nombre IN ('SAMAI Campestre', 'Aldea Nanuu') ORDER BY id LOOP
        siguiente := 0;
        FOR t IN
            SELECT DISTINCT c.id AS cliente_id, c.numero AS numero_actual
            FROM venta v
            JOIN cliente c ON c.id = v.cliente_id
            JOIN venta_lote vl ON vl.venta_id = v.id
            JOIN lote l ON l.id = vl.lote_id
            WHERE l.desarrollo_id = d.id
            ORDER BY c.numero, c.id
        LOOP
            SELECT numero INTO n FROM cliente_numero WHERE cliente_id = t.cliente_id AND desarrollo_id = d.id;
            IF n IS NULL THEN
                siguiente := siguiente + 1;
                n := siguiente;
                INSERT INTO cliente_numero (cliente_id, desarrollo_id, numero) VALUES (t.cliente_id, d.id, n);
            END IF;
            FOR cp IN
                SELECT DISTINCT vc.cliente_id
                FROM venta_copropietario vc
                JOIN venta v ON v.id = vc.venta_id
                WHERE v.cliente_id = t.cliente_id
                  AND EXISTS (SELECT 1 FROM venta_lote vl JOIN lote l ON l.id = vl.lote_id
                              WHERE vl.venta_id = v.id AND l.desarrollo_id = d.id)
                  AND NOT EXISTS (SELECT 1 FROM cliente_numero x
                                  WHERE x.cliente_id = vc.cliente_id AND x.desarrollo_id = d.id)
            LOOP
                INSERT INTO cliente_numero (cliente_id, desarrollo_id, numero) VALUES (cp.cliente_id, d.id, n);
            END LOOP;
        END LOOP;
    END LOOP;
END $$;
