-- Número de cliente: consecutivo simple que se muestra en la app (el id interno no se expone como número).
-- Los clientes existentes lo reciben en orden de alta; los nuevos lo toman de la secuencia.
-- Solo agrega una columna: no modifica ningún dato existente.
ALTER TABLE cliente ADD COLUMN numero BIGINT;

UPDATE cliente c
SET numero = o.rn
FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY fecha_creacion, id) AS rn FROM cliente) o
WHERE c.id = o.id;

CREATE SEQUENCE cliente_numero_seq OWNED BY cliente.numero;
SELECT setval('cliente_numero_seq', COALESCE((SELECT MAX(numero) FROM cliente), 0) + 1, false);

ALTER TABLE cliente ALTER COLUMN numero SET DEFAULT nextval('cliente_numero_seq');
ALTER TABLE cliente ALTER COLUMN numero SET NOT NULL;
CREATE UNIQUE INDEX uq_cliente_numero ON cliente (numero);
