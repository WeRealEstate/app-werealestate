package com.werealestate.backend.dto;

import java.time.LocalDate;
import java.util.List;

/** Score de ventas: cuántas ventas (y cuántos lotes) lleva cada asesor en un periodo (ver ScoreService). */
public final class ScoreDtos {

    private ScoreDtos() {}

    /** INTERNO: un usuario del sistema; EXTERNO: un asesor externo. */
    public enum TipoAsesor {
        INTERNO,
        EXTERNO
    }

    /** clave: "U-12" (usuario interno) o "E-5" (asesor externo), única dentro del ranking. */
    public record Asesor(String clave, String nombre, TipoAsesor tipo, long ventas, long lotes) {}

    /** desde/hasta: el periodo consultado (null = sin límite, "Todo"). totalVentas: ventas distintas del periodo; una
     * venta compartida cuenta una vez aquí, aunque en el ranking sume a sus dos asesores. asesores: de más a menos
     * ventas y, con las mismas ventas, por nombre (comparten lugar; la posición la numera el panel). */
    public record Ranking(LocalDate desde, LocalDate hasta, long totalVentas, List<Asesor> asesores) {}

    /** Página pública: el ranking, la clave de quien la ve si su dispositivo mandó un PIN válido (para marcar "Tú") y si
     * ese PIN se revisó y no sirvió (la página lo olvida para no volver a mandarlo). */
    public record Publico(Ranking ranking, String miClave, boolean pinRechazado) {}
}
