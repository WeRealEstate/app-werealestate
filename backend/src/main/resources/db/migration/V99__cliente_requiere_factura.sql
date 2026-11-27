-- Si el cliente pide factura. Todos quedan «sin factura»; solo admin y Administración lo cambian desde la ficha.
ALTER TABLE cliente ADD COLUMN requiere_factura BOOLEAN NOT NULL DEFAULT FALSE;
