-- PIN de 4 letras/números para que un asesor interno pueda cotizar y apartar desde el plano público.
ALTER TABLE usuario ADD COLUMN pin VARCHAR(4);
CREATE UNIQUE INDEX uq_usuario_pin ON usuario (pin) WHERE pin IS NOT NULL;
