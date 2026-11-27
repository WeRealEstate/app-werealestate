-- Ajustes de datos que deben correr una sola vez al arrancar (12.30.1). Solo agrega una tabla.
CREATE TABLE ajuste_unico (
    clave VARCHAR(80) PRIMARY KEY,
    aplicado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    detalle VARCHAR(500)
);
