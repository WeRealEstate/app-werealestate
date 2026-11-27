-- Amplía el espacio para códigos de 5 a 7 caracteres sin cambiar los existentes.
ALTER TABLE promocion ALTER COLUMN codigo TYPE VARCHAR(7);
