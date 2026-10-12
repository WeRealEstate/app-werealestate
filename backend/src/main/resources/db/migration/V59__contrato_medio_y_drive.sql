-- Cómo se firmó el contrato (físico o DocuSign) y, opcional, el enlace a su carpeta de Drive.
-- Migración ADITIVA: solo agrega columnas vacías; no cambia ninguna fila existente.
ALTER TABLE venta ADD COLUMN contrato_medio VARCHAR(20);
ALTER TABLE venta ADD COLUMN contrato_drive_url VARCHAR(500);
