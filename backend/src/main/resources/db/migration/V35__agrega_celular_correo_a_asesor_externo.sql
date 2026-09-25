-- Al crear un asesor externo ahora se pide también su celular y correo (antes solo el nombre).
-- Nullable porque los asesores externos ya registrados no los tienen: se van completando al
-- editarlos, no se fuerza un backfill.
ALTER TABLE asesor_externo ADD COLUMN celular VARCHAR(20);
ALTER TABLE asesor_externo ADD COLUMN correo VARCHAR(150);
