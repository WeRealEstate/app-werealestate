-- Bono por número exacto de lotes (12.20.0, decisión del usuario): cada promoción de bono define los niveles que quiera
-- (p. ej. 3 lotes → $30,000, 4 → $45,000, 6 → $70,000, de 1 a 20 lotes) y una venta solo recibe bono si su número de
-- lotes tiene nivel. Las promociones de antes pasan con sus mismos montos a los niveles 1, 2 y 3 (exactos: «3 o más»
-- deja de valer para 4 o más; las ventas ya registradas no cambian). Las columnas bono_un_lote, bono_dos_lotes y
-- bono_tres_lotes se conservan sin usarse.
CREATE TABLE promocion_bono (
    promocion_id BIGINT NOT NULL REFERENCES promocion (id) ON DELETE CASCADE,
    lotes INTEGER NOT NULL CHECK (lotes BETWEEN 1 AND 20),
    bono NUMERIC(14, 2) NOT NULL CHECK (bono > 0),
    PRIMARY KEY (promocion_id, lotes)
);

INSERT INTO promocion_bono (promocion_id, lotes, bono)
SELECT id, 1, bono_un_lote FROM promocion WHERE modalidad = 'BONO_LOTES' AND bono_un_lote > 0;
INSERT INTO promocion_bono (promocion_id, lotes, bono)
SELECT id, 2, bono_dos_lotes FROM promocion WHERE modalidad = 'BONO_LOTES' AND bono_dos_lotes > 0;
INSERT INTO promocion_bono (promocion_id, lotes, bono)
SELECT id, 3, bono_tres_lotes FROM promocion WHERE modalidad = 'BONO_LOTES' AND bono_tres_lotes > 0;
