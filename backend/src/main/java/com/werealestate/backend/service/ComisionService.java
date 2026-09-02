package com.werealestate.backend.service;

import com.werealestate.backend.dto.ComisionConfigDto;
import com.werealestate.backend.dto.ComisionConfigRequest;
import com.werealestate.backend.dto.ComisionDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Comision;
import com.werealestate.backend.model.ConfiguracionComision;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.ComisionRepository;
import com.werealestate.backend.repository.ConfiguracionComisionRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ComisionService {

    private static final Long CONFIG_ID = 1L;
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ComisionRepository comisionRepository;
    private final ConfiguracionComisionRepository configuracionRepository;
    private final CurrentUserProvider currentUserProvider;

    public ComisionService(
            ComisionRepository comisionRepository,
            ConfiguracionComisionRepository configuracionRepository,
            CurrentUserProvider currentUserProvider) {
        this.comisionRepository = comisionRepository;
        this.configuracionRepository = configuracionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<ComisionDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        List<Comision> comisiones = actual.getRol() == Role.ADMIN
                ? comisionRepository.findAllByOrderByFechaCreacionDesc()
                : comisionRepository.findByAsesorIdOrderByFechaCreacionDesc(actual.getId());
        return comisiones.stream().map(ComisionDto::from).toList();
    }

    public ComisionConfigDto configuracion() {
        return new ComisionConfigDto(obtenerConfiguracion().getPorcentaje());
    }

    public ComisionConfigDto actualizarConfiguracion(ComisionConfigRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede cambiar el porcentaje de comisión");
        }
        ConfiguracionComision configuracion = obtenerConfiguracion();
        configuracion.setPorcentaje(request.porcentaje());
        configuracionRepository.save(configuracion);
        return new ComisionConfigDto(configuracion.getPorcentaje());
    }

    public ComisionDto marcarPagada(Long id, boolean pagada) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede marcar una comisión como pagada");
        }
        Comision comision =
                comisionRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Comisión no encontrada"));
        comision.setPagada(pagada);
        return ComisionDto.from(comisionRepository.save(comision));
    }

    /**
     * Genera la comisión de un lead la primera vez que cierra como ganado. No hace nada si ya
     * existe una comisión para ese lead, si no cerró ganado, o si no tiene valor estimado.
     */
    void generarSiCorresponde(Lead lead, boolean eraGanadoAntes, boolean esGanadoAhora) {
        if (eraGanadoAntes || !esGanadoAhora) return;
        if (lead.getValorEstimado() == null || lead.getValorEstimado().signum() <= 0) return;
        if (comisionRepository.existsByLeadId(lead.getId())) return;

        BigDecimal porcentaje = obtenerConfiguracion().getPorcentaje();
        BigDecimal monto = lead.getValorEstimado()
                .multiply(porcentaje)
                .divide(CIEN, 2, RoundingMode.HALF_UP);

        comisionRepository.save(new Comision(lead, lead.getAsesor(), monto, porcentaje));
    }

    private ConfiguracionComision obtenerConfiguracion() {
        return configuracionRepository
                .findById(CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("Falta la configuración de comisión"));
    }
}
