package com.werealestate.backend.service;

import com.werealestate.backend.dto.TipoGastoCreateRequest;
import com.werealestate.backend.dto.TipoGastoDto;
import com.werealestate.backend.dto.TipoGastoUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoGasto;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.TipoGastoRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de tipos de gasto (ej. "Comisiones", "Renta de oficina"). Gestionarlo
 * (crear/editar/eliminar) es exclusivo de admin, igual que Asesores externos; listar los activos
 * también lo puede hacer un líder de área, porque también registra gastos y necesita elegir un
 * tipo ahí (ver GastoService).
 */
@Service
@Transactional
public class TipoGastoService {

    private final TipoGastoRepository tipoGastoRepository;
    private final GastoRepository gastoRepository;
    private final CurrentUserProvider currentUserProvider;

    public TipoGastoService(
            TipoGastoRepository tipoGastoRepository,
            GastoRepository gastoRepository,
            CurrentUserProvider currentUserProvider) {
        this.tipoGastoRepository = tipoGastoRepository;
        this.gastoRepository = gastoRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<TipoGastoDto> listar() {
        exigirAdmin();
        return tipoGastoRepository.findAllByOrderByNombreAsc().stream().map(TipoGastoDto::from).toList();
    }

    public List<TipoGastoDto> listarActivos() {
        exigirAdminOLider();
        return tipoGastoRepository.findByActivoTrueOrderByNombreAsc().stream().map(TipoGastoDto::from).toList();
    }

    public TipoGastoDto crear(TipoGastoCreateRequest request) {
        exigirAdmin();
        String nombre = request.nombre().trim();
        if (tipoGastoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictException("Ya existe un tipo de gasto llamado \"" + nombre + "\"");
        }
        TipoGasto tipo = new TipoGasto(nombre, request.requiereTicket());
        return TipoGastoDto.from(tipoGastoRepository.save(tipo));
    }

    public TipoGastoDto actualizar(Long id, TipoGastoUpdateRequest request) {
        exigirAdmin();
        TipoGasto tipo = tipoGastoRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de gasto no encontrado"));
        String nombre = request.nombre().trim();
        if (tipoGastoRepository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw new ConflictException("Ya existe un tipo de gasto llamado \"" + nombre + "\"");
        }
        tipo.setNombre(nombre);
        tipo.setRequiereTicket(request.requiereTicket());
        tipo.setActivo(request.activo());
        return TipoGastoDto.from(tipoGastoRepository.save(tipo));
    }

    /** Se rechaza si ya tiene gastos registrados, para no dejarlos con una referencia colgando: en
     * ese caso hay que desactivarlo en vez de eliminarlo (igual que un Asesor externo). */
    public void eliminar(Long id) {
        exigirAdmin();
        TipoGasto tipo = tipoGastoRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de gasto no encontrado"));
        if (gastoRepository.existsByTipoGastoId(id)) {
            throw new ConflictException(
                    "No se puede eliminar \"" + tipo.getNombre()
                            + "\": tiene gastos registrados. Desactívalo para quitarlo de la lista sin perder ese historial.");
        }
        tipoGastoRepository.delete(tipo);
    }

    private Usuario exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede gestionar tipos de gasto");
        }
        return actual;
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o líder de área puede consultar esta lista");
        }
        return actual;
    }
}
