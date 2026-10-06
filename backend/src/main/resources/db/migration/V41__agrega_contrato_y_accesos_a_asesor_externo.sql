-- Contrato y acceso a planos de cada asesor externo. Los ya registrados quedan con contrato
-- VIGENTE y acceso a ambos desarrollos, para que nada cambie hasta que un admin los edite.
ALTER TABLE asesor_externo
    ADD COLUMN contrato_estado VARCHAR(30) NOT NULL DEFAULT 'VIGENTE',
    ADD COLUMN contrato_fecha_firma DATE,
    ADD COLUMN contrato_fecha_vencimiento DATE,
    ADD COLUMN acceso_samai BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN acceso_nanuu BOOLEAN NOT NULL DEFAULT TRUE;
