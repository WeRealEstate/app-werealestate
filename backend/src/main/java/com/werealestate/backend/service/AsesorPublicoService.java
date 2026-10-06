package com.werealestate.backend.service;

import com.werealestate.backend.model.AsesorExterno;
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

    /** Devuelve el nombre tal como está registrado si coincide (sin importar mayúsculas, acentos
     * ni espacios de más); vacío si no existe ningún asesor activo con ese nombre. */
    public Optional<String> verificar(String nombre) {
        String buscado = normalizar(nombre);
        if (buscado.isEmpty()) return Optional.empty();

        Optional<String> interno = usuarioRepository.findAll().stream()
                .filter(u -> u.isActivo()
                        && u.getRol() != Role.EQUIPO_INTERNO
                        && !EMAIL_USUARIO_PUBLICO.equalsIgnoreCase(u.getEmail()))
                .map(Usuario::getNombre)
                .filter(n -> normalizar(n).equals(buscado))
                .findFirst();
        if (interno.isPresent()) return interno;

        return asesorExternoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(AsesorExterno::getNombre)
                .filter(n -> normalizar(n).equals(buscado))
                .findFirst();
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
