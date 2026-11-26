-- Código de 6 dígitos de una promoción (12.18.0): los asesores lo escriben en el registro de venta (We Forms y panel)
-- para aplicarla. Opcional (las promociones de antes se quedan sin código hasta que el admin lo genere) y único.
ALTER TABLE promocion ADD COLUMN codigo VARCHAR(6);
CREATE UNIQUE INDEX ux_promocion_codigo ON promocion (codigo) WHERE codigo IS NOT NULL;
