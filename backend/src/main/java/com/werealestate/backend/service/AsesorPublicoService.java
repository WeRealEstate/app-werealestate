package com.werealestate.backend.service;

import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.EstadoContratoAsesor;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candado ligero de los links públicos (/samai, /aldea-nanuu): confirma que el nombre que escribe
 * el visitante corresponde a un asesor registrado y activo (usuario del sistema o asesor externo)
 * antes de que el frontend le muestre los botones de cotizar y apartar. No es autenticación: no
 * emite ningún token y el apartado público sigue aceptando peticiones directas a la API.
 */
@Service
@Transactional(readOnly = true)
public class AsesorPublicoService {

    private static final String EMAIL_USUARIO_PUBLICO = "cotizador-publico@weinversiones.com";

    private final UsuarioRepository usuarioRepository;
    private final AsesorExternoRepository asesorExternoRepository;

    public AsesorPublicoService(UsuarioRepository usuarioRepository, AsesorExternoRepository asesorExternoRepository) {
        this.usuarioRepository = usuarioRepository;
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

    /** Valida el nombre (sin importar mayúsculas, acentos ni espacios de más) y, para un asesor
     * externo, que su contrato esté vigente y tenga acceso al plano pedido ("samai" / "nanuu";
     * null = solo se revisa el contrato). Los usuarios internos entran a cualquier plano. */
    public Resultado verificar(String nombre, String proyecto) {
        String buscado = normalizar(nombre);
        if (buscado.isEmpty()) return new Resultado(Motivo.NO_ENCONTRADO, null);

        Optional<String> interno = usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo()
                        && u.getRol() != Role.EQUIPO_INTERNO
                        && !EMAIL_USUARIO_PUBLICO.equalsIgnoreCase(u.getEmail()))
                .map(Usuario::getNombre)
                .filter(n -> normalizar(n).equals(buscado))
                .findFirst();
        if (interno.isPresent()) return new Resultado(Motivo.OK, interno.get());

        Resultado primerRechazo = new Resultado(Motivo.NO_ENCONTRADO, null);
        boolean hayRechazo = false;
        for (AsesorExterno a : asesorExternoRepository.findByActivoTrueOrderByNombreAsc()) {
            if (!normalizar(a.getNombre()).equals(buscado)) continue;
            Motivo motivo = motivoExterno(a, proyecto);
            if (motivo == Motivo.OK) return new Resultado(Motivo.OK, a.getNombre());
            if (!hayRechazo) {
                primerRechazo = new Resultado(motivo, null);
                hayRechazo = true;
            }
        }
        return primerRechazo;
    }

    private static Motivo motivoExterno(AsesorExterno a, String proyecto) {
        if (a.getContratoEstadoEfectivo() != EstadoContratoAsesor.VIGENTE) return Motivo.CONTRATO_NO_VIGENTE;
        if ("samai".equals(proyecto) && !a.isAccesoSamai()) return Motivo.SIN_ACCESO;
        if ("nanuu".equals(proyecto) && !a.isAccesoNanuu()) return Motivo.SIN_ACCESO;
        return Motivo.OK;
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
