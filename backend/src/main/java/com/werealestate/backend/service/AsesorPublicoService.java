package com.werealestate.backend.service;

import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.EstadoContratoAsesor;
import com.werealestate.backend.repository.AsesorExternoRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candado ligero de los links públicos (/samai, /aldea-nanuu): confirma que el PIN que escribe
 * el visitante corresponde a un asesor externo activo
 * antes de que el frontend le muestre los botones de cotizar y apartar. No es autenticación: no
 * emite ningún token y el apartado público sigue aceptando peticiones directas a la API.
 */
@Service
@Transactional(readOnly = true)
public class AsesorPublicoService {

    private final AsesorExternoRepository asesorExternoRepository;

    public AsesorPublicoService(AsesorExternoRepository asesorExternoRepository) {
        this.asesorExternoRepository = asesorExternoRepository;
    }

    public enum Motivo {
        OK,
        NO_ENCONTRADO,
        CONTRATO_NO_VIGENTE,
        SIN_ACCESO
    }

    public record Resultado(Motivo motivo, String nombre) {
    }

    /** Valida el PIN (4 letras/números, sin importar mayúsculas) de un asesor externo activo, que su
     * contrato esté vigente y que tenga acceso al plano pedido ("samai" / "nanuu"; null = solo se
     * revisa el contrato). Los asesores sin PIN asignado no pueden entrar. */
    public Resultado verificar(String pin, String proyecto) {
        String buscado = pin == null ? "" : pin.trim().toUpperCase(Locale.ROOT);
        if (!buscado.matches("[A-Z0-9]{4}")) return new Resultado(Motivo.NO_ENCONTRADO, null);

        Optional<AsesorExterno> asesor = asesorExternoRepository.findByPin(buscado).filter(AsesorExterno::isActivo);
        if (asesor.isEmpty()) return new Resultado(Motivo.NO_ENCONTRADO, null);
        Motivo motivo = motivoExterno(asesor.get(), proyecto);
        return motivo == Motivo.OK
                ? new Resultado(Motivo.OK, asesor.get().getNombre())
                : new Resultado(motivo, null);
    }

    private static Motivo motivoExterno(AsesorExterno a, String proyecto) {
        if (a.getContratoEstadoEfectivo() != EstadoContratoAsesor.VIGENTE) return Motivo.CONTRATO_NO_VIGENTE;
        if ("samai".equals(proyecto) && !a.isAccesoSamai()) return Motivo.SIN_ACCESO;
        if ("nanuu".equals(proyecto) && !a.isAccesoNanuu()) return Motivo.SIN_ACCESO;
        return Motivo.OK;
    }
}
