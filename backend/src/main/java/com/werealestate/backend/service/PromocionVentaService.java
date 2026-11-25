package com.werealestate.backend.service;

import com.werealestate.backend.dto.PromocionDto;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.TipoPrecioPromocion;
import com.werealestate.backend.repository.PromocionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promociones que se pueden usar al registrar una venta. Solo las de bono por lotes (BONO_LOTES): el bono ya viene
 * descontado en el precio de cada lote que se captura; aquí solo se comprueba que la promoción corresponda a la venta
 * y se guarda cuál fue y de cuánto era.
 */
@Service
public class PromocionVentaService {

    /** Arriba de esta superficie un lote de SAMAI usa la tarifa de hectárea (igual que el cotizador). */
    private static final BigDecimal SUPERFICIE_MACROLOTE = new BigDecimal("8000");

    /** La promoción de una venta, con el bono que le toca según su número de lotes. */
    public record Aplicada(String nombre, BigDecimal bono) {
    }

    private final PromocionRepository promocionRepository;

    public PromocionVentaService(PromocionRepository promocionRepository) {
        this.promocionRepository = promocionRepository;
    }

    /** Las que se pueden elegir para una venta de esa fecha (ver {@link #vigenteEn}). */
    @Transactional(readOnly = true)
    public List<PromocionDto> paraVenta(LocalDate fechaVenta) {
        return promocionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .filter(p -> p.getModalidad() == ModalidadPromocion.BONO_LOTES)
                .filter(p -> vigenteEn(p, fechaVenta))
                .map(PromocionDto::fromEntity)
                .toList();
    }

    /** Valida que la promoción aplique a esta venta y devuelve su nombre y bono. */
    @Transactional(readOnly = true)
    public Aplicada aplicar(Long promocionId, LocalDate fechaVenta, List<Lote> lotes) {
        Promocion p = promocionRepository.findById(promocionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
        if (p.getModalidad() != ModalidadPromocion.BONO_LOTES) {
            throw new ValidationException("Solo se pueden registrar ventas con promociones de bono por lotes");
        }
        if (!vigenteEn(p, fechaVenta)) {
            throw new ValidationException("La promoción " + p.getNombre() + " no estaba vigente en la fecha de la venta");
        }
        String proyecto = "nanuu".equals(p.getProyecto()) ? "Aldea Nanuu" : "SAMAI Campestre";
        boolean hectarea = p.getTipoPrecio() == TipoPrecioPromocion.HECTAREA;
        for (Lote lote : lotes) {
            if (!proyecto.equalsIgnoreCase(lote.getDesarrollo().getNombre())) {
                throw new ValidationException("La promoción " + p.getNombre() + " solo aplica a lotes de " + proyecto);
            }
            if ((lote.getSuperficie().compareTo(SUPERFICIE_MACROLOTE) > 0) != hectarea) {
                throw new ValidationException("La promoción " + p.getNombre() + " solo aplica a "
                        + (hectarea ? "hectáreas" : "lotes (no hectáreas)"));
            }
        }
        BigDecimal bono = switch (lotes.size()) {
            case 1 -> p.getBonoUnLote();
            case 2 -> p.getBonoDosLotes();
            default -> p.getBonoTresLotes();
        };
        if (bono == null || bono.signum() <= 0) {
            throw new ValidationException("La promoción " + p.getNombre() + " no aplica a " + lotes.size() + " lote(s)");
        }
        return new Aplicada(p.getNombre(), bono);
    }

    /**
     * Vigente para una venta de esa fecha: hasta su fecha de fin (sin fecha de inicio, decisión del usuario). Una
     * promoción vencida sigue sirviendo para ventas de antes de su fin; una apagada a mano antes de vencer, no.
     */
    private static boolean vigenteEn(Promocion p, LocalDate fechaVenta) {
        LocalDateTime fin = p.getFechaFin();
        if (fin != null && fechaVenta.isAfter(fin.toLocalDate())) return false;
        return p.isActiva() || (fin != null && !fin.isAfter(LocalDateTime.now()));
    }
}
