-- PIN de 4 caracteres (letras y números, guardado en mayúsculas) con el que el asesor externo entra al
-- botón "Asesor" de los planos públicos. Sin PIN asignado no puede entrar. Único sin importar mayúsculas.
ALTER TABLE asesor_externo ADD COLUMN pin VARCHAR(4);
CREATE UNIQUE INDEX uq_asesor_externo_pin ON asesor_externo (pin) WHERE pin IS NOT NULL;
