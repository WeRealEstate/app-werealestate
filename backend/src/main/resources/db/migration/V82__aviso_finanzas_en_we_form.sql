-- Interruptor "Avisar a finanzas por correo" de cada formulario de We Forms: solo los formularios con el aviso
-- encendido mandan el correo "¡Es hora de realizar un contrato!" al recibir un registro de venta. Los formularios que
-- ya existen quedan APAGADOS (decisión de la empresa) hasta que alguien los encienda en la lista de We Forms.
-- Solo agrega una columna con valor por defecto: no toca datos existentes.
ALTER TABLE we_form ADD COLUMN aviso_finanzas BOOLEAN NOT NULL DEFAULT FALSE;
