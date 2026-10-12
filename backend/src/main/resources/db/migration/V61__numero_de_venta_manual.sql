-- El número de venta deja de recalcularse por fecha: se asigna una vez (el siguiente = mayor + 1, o el
-- que se escriba a mano; si ya está ocupado, VentaService recorre +1 las consecutivas) y queda fijo.
-- Los números que ya existen se conservan tal cual. Se quita el valor provisional de la secuencia para
-- que ningún camino que olvide asignar el número termine con uno inventado.
ALTER TABLE venta ALTER COLUMN numero DROP DEFAULT;
DROP SEQUENCE IF EXISTS venta_numero_provisional_seq;
