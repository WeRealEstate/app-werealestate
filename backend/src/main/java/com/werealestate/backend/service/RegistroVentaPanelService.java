package com.werealestate.backend.service;

import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Registro de venta del asesor interno desde su panel: reutiliza WeFormPublicoService, identificándolo por su sesión en
 * vez de por PIN (igual que si hubiera escrito su PIN: la venta queda a su nombre). Solo rol Asesor; el módulo
 * "Registrar venta" lo revisa ModuloAccesoInterceptor.
 */
@Service
public class RegistroVentaPanelService {

    private final WeFormPublicoService formularios;
    private final CurrentUserProvider currentUserProvider;

    public RegistroVentaPanelService(WeFormPublicoService formularios, CurrentUserProvider currentUserProvider) {
        this.formularios = formularios;
        this.currentUserProvider = currentUserProvider;
    }

    public List<WeFormDtos.PanelLista> listar() {
        exigirAsesor();
        return Stream.of("samai", "nanuu")
                .flatMap(proyecto -> formularios.listarActivos(proyecto).stream()
                        .map(f -> new WeFormDtos.PanelLista(f.slug(), f.nombre(), f.descripcion(), proyecto)))
                .toList();
    }

    public WeFormDtos.Publico obtener(String slug) {
        return formularios.obtener(slug, identificado());
    }

    public WeFormDtos.EnvioResultado enviar(String slug, WeFormDtos.EnvioRequest envio, Map<Long, MultipartFile> archivos) {
        return formularios.enviar(slug, identificado(), envio, archivos);
    }

    private AsesorPublicoService.Identificado identificado() {
        return new AsesorPublicoService.Identificado(AsesorPublicoService.Motivo.OK, exigirAsesor(), null);
    }

    private Usuario exigirAsesor() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ASESOR) {
            throw new ForbiddenOperationException("Solo los asesores registran ventas desde aquí");
        }
        return actual;
    }
}
