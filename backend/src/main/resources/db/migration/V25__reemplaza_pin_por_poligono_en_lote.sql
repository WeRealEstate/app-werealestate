-- El plano ahora delimita cada lote con un polígono (varios vértices), no con un solo pin.
-- mapa_x/mapa_y se dejan sin usar (no se borran) para no perder datos si algún lote ya tenía pin.
ALTER TABLE lote ADD COLUMN mapa_poligono TEXT;
