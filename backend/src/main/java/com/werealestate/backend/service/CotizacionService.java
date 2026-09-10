package com.werealestate.backend.service;

import com.werealestate.backend.dto.CotizacionCreateRequest;
import com.werealestate.backend.dto.CotizacionDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Cotizacion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.CotizacionRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bitácora de cotizaciones generadas desde el Cotizador. Cualquier asesor o admin que genera o
 * comparte un PDF registra aquí una fila; solo el admin puede consultar el historial completo.
 */
@Service
@Transactional
public class CotizacionService {

    private final CotizacionRepository cotizacionRepository;
    private final CurrentUserProvider currentUserProvider;

    public CotizacionService(CotizacionRepository cotizacionRepository, CurrentUserProvider currentUserProvider) {
        this.cotizacionRepository = cotizacionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public CotizacionDto registrar(CotizacionCreateRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();

        Cotizacion cotizacion = new Cotizacion(
                actual,
                request.proyecto(),
                request.nombreCliente(),
                request.manzana(),
                request.lote(),
                request.superficie(),
                request.precioM2(),
                request.precioTotal(),
                request.formaPago(),
                request.engancheLabel(),
                request.enganche(),
                request.montoFinanciado(),
                request.meses(),
                request.mensualidad(),
                request.interesPorcentaje(),
                request.interesMonto(),
                request.totalInversion());

        return CotizacionDto.from(cotizacionRepository.save(cotizacion));
    }

    public List<CotizacionDto> listar() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede ver el historial de cotizaciones");
        }

        return cotizacionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(CotizacionDto::from)
                .toList();
    }
}
