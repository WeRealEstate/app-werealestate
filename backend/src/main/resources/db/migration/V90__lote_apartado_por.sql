-- Quién apartó cada lote, para que un asesor pueda cotizar los lotes que él mismo apartó. Se guarda con la misma clave
-- con la que el Score y las comisiones identifican a un asesor: 'U-<id de usuario>' (interno) o 'E-<id de asesor externo>'.
-- Solo vale mientras el lote está en un estado de apartado (el servidor la limpia al salir de él); null = no se sabe
-- (apartados de antes de esta migración o hechos sin PIN en el plano público). Solo agrega una columna: no toca datos.
ALTER TABLE lote ADD COLUMN apartado_por_clave VARCHAR(20);
