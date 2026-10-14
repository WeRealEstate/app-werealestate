-- País de residencia del cliente: se llena desde el precontrato de We Forms (pregunta tipo "País") o a mano
-- en Clientes. Solo agrega una columna nullable: no modifica ningún dato existente.
ALTER TABLE cliente ADD COLUMN pais_residencia VARCHAR(80);
