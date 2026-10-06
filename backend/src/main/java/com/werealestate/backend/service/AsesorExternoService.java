package com.werealestate.backend.service;

import com.werealestate.backend.dto.AsesorExternoCreateRequest;
import com.werealestate.backend.dto.AsesorExternoDto;
import com.werealestate.backend.dto.AsesorExternoUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.EstadoContratoAsesor;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoAsesorExterno;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roster de asesores externos: gente que vende pero no tiene cuenta en el sistema (ver
 * AsesorExterno). Gestionarlo (crear/editar/desactivar/eliminar) es exclusivo de admin, igual que
 * Usuarios; listar los activos también lo puede hacer un líder de área, porque también registra
 * ventas y necesita poder elegir un asesor externo ahí (ver VentaService).
 */
@Service
@Transactional
public class AsesorExternoService {

    private final AsesorExternoRepository asesorExternoRepository;
    private final VentaRepository ventaRepository;
    private final CurrentUserProvider currentUserProvider;

    public AsesorExternoService(
            AsesorExternoRepository asesorExternoRepository,
            VentaRepository ventaRepository,
            CurrentUserProvider currentUserProvider) {
        this.asesorExternoRepository = asesorExternoRepository;
        this.ventaRepository = ventaRepository;
        this.currentUserProvider = currentUserProvider;
    }

    public List<AsesorExternoDto> listar() {
        exigirAdmin();
        return asesorExternoRepository.findAllByOrderByNombreAsc().stream().map(AsesorExternoDto::from).toList();
    }

    public List<AsesorExternoDto> listarActivos() {
        exigirAdminOLider();
        return asesorExternoRepository.findByActivoTrueOrderByNombreAsc().stream().map(AsesorExternoDto::from).toList();
    }

    public AsesorExternoDto crear(AsesorExternoCreateRequest request) {
        exigirAdmin();
        AsesorExterno asesor = new AsesorExterno(
                request.nombre().trim(), request.celular().trim(), normalizarOpcional(request.correo()));
        aplicarContrato(
                asesor,
                request.contratoEstado() != null ? request.contratoEstado() : EstadoContratoAsesor.VIGENTE,
                request.contratoFechaFirma(),
                request.contratoFechaVencimiento());
        if (request.accesoSamai() != null) asesor.setAccesoSamai(request.accesoSamai());
        if (request.accesoNanuu() != null) asesor.setAccesoNanuu(request.accesoNanuu());
        return AsesorExternoDto.from(asesorExternoRepository.save(asesor));
    }

    public AsesorExternoDto actualizar(Long id, AsesorExternoUpdateRequest request) {
        exigirAdmin();
        AsesorExterno asesor = asesorExternoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asesor externo no encontrado"));
        asesor.setNombre(request.nombre().trim());
        asesor.setCelular(normalizarOpcional(request.celular()));
        asesor.setCorreo(normalizarOpcional(request.correo()));
        asesor.setActivo(request.activo());
        aplicarJerarquia(asesor, request.tipo(), request.liderDirectoId());
        if (request.contratoEstado() != null) {
            aplicarContrato(
                    asesor, request.contratoEstado(), request.contratoFechaFirma(), request.contratoFechaVencimiento());
        }
        if (request.accesoSamai() != null) asesor.setAccesoSamai(request.accesoSamai());
        if (request.accesoNanuu() != null) asesor.setAccesoNanuu(request.accesoNanuu());
        return AsesorExternoDto.from(asesorExternoRepository.save(asesor));
    }

    /** Se rechaza si ya se le acreditó alguna venta, para no dejar ventas con una referencia
     * colgando: en ese caso hay que desactivarlo en vez de eliminarlo (igual que un Usuario).
     * También se rechaza si tiene gente reportándole en Teams (ver aplicarJerarquia). */
    public void eliminar(Long id) {
        exigirAdmin();
        AsesorExterno asesor = asesorExternoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asesor externo no encontrado"));

        if (ventaRepository.existsByAsesorExternoId(id)) {
            throw new ConflictException(
                    "No se puede eliminar a " + asesor.getNombre()
                            + ": tiene ventas registradas. Desactívalo para quitarlo de la lista sin perder ese historial.");
        }
        if (asesorExternoRepository.existsByLiderDirectoId(id)) {
            throw new ConflictException(
                    "No se puede eliminar a " + asesor.getNombre()
                            + ": tiene gente reportándole en Teams. Reasígnalos o quítalos del equipo primero.");
        }

        asesorExternoRepository.delete(asesor);
    }

    private void aplicarContrato(
            AsesorExterno asesor, EstadoContratoAsesor estado, LocalDate firma, LocalDate vencimiento) {
        if (firma != null && vencimiento != null && vencimiento.isBefore(firma)) {
            throw new ValidationException("La fecha de vencimiento no puede ser anterior a la de firma");
        }
        asesor.setContratoEstado(estado);
        asesor.setContratoFechaFirma(firma);
        asesor.setContratoFechaVencimiento(vencimiento);
    }

    /**
     * Aplica el tipo (INDEPENDIENTE/LIDER/LINEA) y, si es LINEA, a quién reporta — validando en el
     * servidor las reglas de Teams, no solo confiando en lo que mande el frontend:
     * <ul>
     *   <li>INDEPENDIENTE/LIDER nunca reportan a nadie.
     *   <li>LINEA siempre reporta a alguien: a un LIDER (queda en línea 1) o a un LINEA que ya esté
     *       en línea 1 (queda en línea 2) — nunca a otro LINEA de línea 2, ahí se corta el árbol.
     *   <li>Si el tipo cambia y ya tiene gente reportándole, se rechaza: hay que reasignar o quitar
     *       a esa gente del equipo antes de poder cambiarlo (evita dejar el árbol roto).
     * </ul>
     * nivelLinea nunca viene del request: siempre se calcula aquí a partir de liderDirecto.
     */
    private void aplicarJerarquia(AsesorExterno asesor, TipoAsesorExterno tipo, Long liderDirectoId) {
        boolean tieneDependientes =
                asesor.getId() != null && asesorExternoRepository.existsByLiderDirectoId(asesor.getId());
        if (tieneDependientes && tipo != asesor.getTipo()) {
            throw new ConflictException(
                    "No se puede cambiar el tipo de " + asesor.getNombre()
                            + ": tiene gente reportándole en Teams. Reasígnalos o quítalos del equipo primero.");
        }

        if (tipo != TipoAsesorExterno.LINEA) {
            if (liderDirectoId != null) {
                throw new ValidationException(tipo + " no puede reportar a nadie");
            }
            asesor.setTipo(tipo);
            asesor.setLiderDirecto(null);
            asesor.setNivelLinea(null);
            return;
        }

        if (liderDirectoId == null) {
            throw new ValidationException("Selecciona a quién reporta este asesor de línea");
        }
        if (liderDirectoId.equals(asesor.getId())) {
            throw new ValidationException("Un asesor no puede reportarse a sí mismo");
        }
        AsesorExterno liderDirecto = asesorExternoRepository.findById(liderDirectoId)
                .orElseThrow(() -> new ResourceNotFoundException("El líder directo no existe"));

        int nivel;
        if (liderDirecto.getTipo() == TipoAsesorExterno.LIDER) {
            nivel = 1;
        } else if (liderDirecto.getTipo() == TipoAsesorExterno.LINEA && liderDirecto.getNivelLinea() == 1) {
            nivel = 2;
        } else {
            throw new ValidationException(
                    "Solo se puede reportar a un líder (línea 1) o a alguien ya en línea 1 (línea 2) — no hay línea 3");
        }

        asesor.setTipo(TipoAsesorExterno.LINEA);
        asesor.setLiderDirecto(liderDirecto);
        asesor.setNivelLinea(nivel);
    }

    private Usuario exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede gestionar asesores externos");
        }
        return actual;
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede consultar esta lista");
        }
        return actual;
    }

    private String normalizarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
