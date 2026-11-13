package com.werealestate.backend.service;

import com.werealestate.backend.dto.PromocionCreateRequest;
import com.werealestate.backend.dto.PromocionDto;
import com.werealestate.backend.dto.PromocionUpdateRequest;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import com.werealestate.backend.repository.PromocionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromocionService {

    private final PromocionRepository promocionRepository;

    public PromocionService(PromocionRepository promocionRepository) {
        this.promocionRepository = promocionRepository;
    }

    @Transactional
    public List<PromocionDto> listarActivas() {
        desactivarVencidas();
        return promocionRepository.findByActivaTrue().stream()
                .map(PromocionDto::fromEntity)
                .toList();
    }

    @Transactional
    public List<PromocionDto> listar() {
        desactivarVencidas();
        return promocionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(PromocionDto::fromEntity)
                .toList();
    }

    @Transactional
    public PromocionDto crear(PromocionCreateRequest request) {
        TipoPrecioPromocion tipoPrecio = request.tipoPrecio() != null ? request.tipoPrecio() : TipoPrecioPromocion.LOTE;
        ModalidadPromocion modalidad = request.modalidad() != null ? request.modalidad() : ModalidadPromocion.MENSUALIDAD_FIJA;

        Promocion p = new Promocion();
        p.setNombre(request.nombre().trim());
        p.setProyecto(request.proyecto().trim());
        p.setTipoPrecio(tipoPrecio);
        p.setModalidad(modalidad);
        aplicarMontosSegunModalidad(
                p,
                modalidad,
                request.mensualidadFija(),
                request.bonoUnLote(),
                request.bonoDosLotes(),
                request.bonoTresLotes(),
                request.plazoFijo()
        );
        p.setDescripcion(limpiarDescripcion(request.descripcion()));
        p.setFechaFin(request.fechaFin());

        boolean activa = request.activa() == null || request.activa();
        if (activa && p.getFechaFin() != null && !p.getFechaFin().isAfter(LocalDateTime.now())) {
            activa = false;
        }
        p.setActiva(activa);

        if (activa) {
            desactivarOtrasDelProyecto(p.getProyecto(), p.getTipoPrecio(), p.getModalidad(), null);
        }

        return PromocionDto.fromEntity(promocionRepository.save(p));
    }

    @Transactional
    public PromocionDto actualizar(Long id, PromocionUpdateRequest request) {
        Promocion p = promocionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));

        if (request.nombre() != null && !request.nombre().isBlank()) {
            p.setNombre(request.nombre().trim());
        }
        if (request.proyecto() != null && !request.proyecto().isBlank()) {
            p.setProyecto(request.proyecto().trim());
        }
        if (request.tipoPrecio() != null) {
            p.setTipoPrecio(request.tipoPrecio());
        }
        ModalidadPromocion modalidad = request.modalidad() != null
                ? request.modalidad()
                : (p.getModalidad() != null ? p.getModalidad() : ModalidadPromocion.MENSUALIDAD_FIJA);
        p.setModalidad(modalidad);

        BigDecimal mensualidadFija = request.mensualidadFija() != null ? request.mensualidadFija() : p.getMensualidadFija();
        BigDecimal bonoUnLote = request.bonoUnLote() != null ? request.bonoUnLote() : p.getBonoUnLote();
        BigDecimal bonoDosLotes = request.bonoDosLotes() != null ? request.bonoDosLotes() : p.getBonoDosLotes();
        BigDecimal bonoTresLotes = request.bonoTresLotes() != null ? request.bonoTresLotes() : p.getBonoTresLotes();
        Integer plazoFijo = request.plazoFijo() != null ? request.plazoFijo() : p.getPlazoFijo();

        aplicarMontosSegunModalidad(p, modalidad, mensualidadFija, bonoUnLote, bonoDosLotes, bonoTresLotes, plazoFijo);

        if (request.descripcion() != null) {
            p.setDescripcion(limpiarDescripcion(request.descripcion()));
        }
        if (Boolean.TRUE.equals(request.limpiarFechaFin())) {
            p.setFechaFin(null);
        } else if (request.fechaFin() != null) {
            p.setFechaFin(request.fechaFin());
        }
        if (request.activa() != null) {
            p.setActiva(request.activa());
        }
        if (p.isActiva() && p.getFechaFin() != null && !p.getFechaFin().isAfter(LocalDateTime.now())) {
            p.setActiva(false);
        }

        if (p.isActiva()) {
            desactivarOtrasDelProyecto(p.getProyecto(), p.getTipoPrecio(), p.getModalidad(), p.getId());
        }

        return PromocionDto.fromEntity(promocionRepository.save(p));
    }

    @Transactional
    public PromocionDto cambiarEstado(Long id, boolean activa) {
        Promocion p = promocionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));

        if (activa && p.getFechaFin() != null && !p.getFechaFin().isAfter(LocalDateTime.now())) {
            throw new ValidationException("La fecha de fin ya venció; amplíala o quítala antes de activar la promoción.");
        }

        p.setActiva(activa);
        if (activa) {
            ModalidadPromocion modalidad = p.getModalidad() != null ? p.getModalidad() : ModalidadPromocion.MENSUALIDAD_FIJA;
            desactivarOtrasDelProyecto(p.getProyecto(), p.getTipoPrecio(), modalidad, p.getId());
        }

        return PromocionDto.fromEntity(promocionRepository.save(p));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!promocionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Promoción no encontrada");
        }
        promocionRepository.deleteById(id);
    }

    private void aplicarMontosSegunModalidad(
            Promocion p,
            ModalidadPromocion modalidad,
            BigDecimal mensualidadFija,
            BigDecimal bonoUnLote,
            BigDecimal bonoDosLotes,
            BigDecimal bonoTresLotes,
            Integer plazoFijo
    ) {
        if (modalidad == ModalidadPromocion.BONO_LOTES) {
            if (bonoDosLotes == null || bonoDosLotes.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("El bono para 2 lotes debe ser mayor a 0");
            }
            if (bonoTresLotes == null || bonoTresLotes.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("El bono para 3 o más lotes debe ser mayor a 0");
            }
            int plazo = plazoFijo != null ? plazoFijo : 60;
            if (plazo <= 0) {
                throw new ValidationException("El plazo fijo debe ser mayor a 0");
            }
            if (bonoUnLote != null && bonoUnLote.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidationException("El bono para 1 lote no puede ser negativo");
            }
            p.setMensualidadFija(null);
            // 0 o vacío = la promoción no aplica a 1 lote (así también se quita al editar).
            p.setBonoUnLote(bonoUnLote != null && bonoUnLote.signum() > 0 ? bonoUnLote : null);
            p.setBonoDosLotes(bonoDosLotes);
            p.setBonoTresLotes(bonoTresLotes);
            p.setPlazoFijo(plazo);
        } else {
            if (mensualidadFija == null || mensualidadFija.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("La mensualidad fija debe ser mayor a 0");
            }
            p.setMensualidadFija(mensualidadFija);
            p.setBonoUnLote(null);
            p.setBonoDosLotes(null);
            p.setBonoTresLotes(null);
            p.setPlazoFijo(null);
        }
    }

    private void desactivarVencidas() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Promocion> activas = promocionRepository.findByActivaTrue();
        for (Promocion p : activas) {
            if (p.getFechaFin() != null && !p.getFechaFin().isAfter(ahora)) {
                p.setActiva(false);
                promocionRepository.save(p);
            }
        }
    }

    private void desactivarOtrasDelProyecto(
            String proyecto,
            TipoPrecioPromocion tipoPrecio,
            ModalidadPromocion modalidad,
            Long excludeId
    ) {
        List<Promocion> activas = promocionRepository.findByProyectoAndTipoPrecioAndModalidadAndActivaTrue(
                proyecto,
                tipoPrecio,
                modalidad
        );
        for (Promocion otra : activas) {
            if (excludeId == null || !otra.getId().equals(excludeId)) {
                otra.setActiva(false);
                promocionRepository.save(otra);
            }
        }
    }

    private String limpiarDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String limpia = descripcion.trim();
        return limpia.isEmpty() ? null : limpia;
    }
}
