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

    /** La promoción de una venta: con el bono que le toca según su número de lotes (bono por lotes) o con su
     * mensualidad fija (mensualidad fija; ahí bono es null y el precio no cambia). */
    public record Aplicada(String nombre, BigDecimal bono, BigDecimal mensualidadFija) {
    }

    private final PromocionRepository promocionRepository;

    public PromocionVentaService(PromocionRepository promocionRepository) {
        this.promocionRepository = promocionRepository;
    }

    /** Las que se pueden elegir para una venta de esa fecha (ver {@link #vigenteEn}). Nueva venta solo ofrece las de
     * bono; «Modificar venta» también las de mensualidad fija (incluirMensualidadFija, decisión del usuario 12.17.0). */
    @Transactional(readOnly = true)
    public List<PromocionDto> paraVenta(LocalDate fechaVenta, boolean incluirMensualidadFija) {
        return promocionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .filter(p -> incluirMensualidadFija || p.getModalidad() == ModalidadPromocion.BONO_LOTES)
                .filter(p -> vigenteEn(p, fechaVenta))
                .map(PromocionDto::fromEntity)
                .toList();
    }

    /** Valida que la promoción aplique a esta venta y devuelve su nombre y bono. */
    @Transactional(readOnly = true)
    public Aplicada aplicar(Long promocionId, LocalDate fechaVenta, List<Lote> lotes) {
        return aplicar(promocionId, fechaVenta, lotes, null, false);
    }

    /** Igual; {@code promocionActual} es la que ya tiene la venta (por nombre): conservarla al cambiar los lotes no
     * exige que siga vigente, y si es de mensualidad fija se conserva (no se puede elegir una nueva ahí). */
    @Transactional(readOnly = true)
    public Aplicada aplicar(Long promocionId, LocalDate fechaVenta, List<Lote> lotes, String promocionActual) {
        return aplicar(promocionId, fechaVenta, lotes, promocionActual, false);
    }

    /** permitirMensualidadFija: solo «Modificar venta» puede poner una de mensualidad fija. */
    @Transactional(readOnly = true)
    public Aplicada aplicar(Long promocionId, LocalDate fechaVenta, List<Lote> lotes, String promocionActual,
            boolean permitirMensualidadFija) {
        Promocion p = promocionRepository.findById(promocionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
        boolean mensualidadFija = p.getModalidad() != ModalidadPromocion.BONO_LOTES;
        if (mensualidadFija && !permitirMensualidadFija && !p.getNombre().equals(promocionActual)) {
            throw new ValidationException("Las promociones de mensualidad fija solo se ponen desde «Modificar venta»");
        }
        if (!p.getNombre().equals(promocionActual) && !vigenteEn(p, fechaVenta)) {
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
        if (mensualidadFija) {
            if (p.getMensualidadFija() == null || p.getMensualidadFija().signum() <= 0) {
                throw new ValidationException("La promoción " + p.getNombre() + " no tiene mensualidad fija");
            }
            return new Aplicada(p.getNombre(), null, p.getMensualidadFija());
        }
        BigDecimal bono = switch (lotes.size()) {
            case 1 -> p.getBonoUnLote();
            case 2 -> p.getBonoDosLotes();
            default -> p.getBonoTresLotes();
        };
        if (bono == null || bono.signum() <= 0) {
            throw new ValidationException("La promoción " + p.getNombre() + " no aplica a " + lotes.size() + " lote(s)");
        }
        return new Aplicada(p.getNombre(), bono, null);
    }

    /**
     * Vigente para una venta de esa fecha: hasta su fecha de fin (sin fecha de inicio, decisión del usuario). Una
     * promoción vencida sigue sirviendo para ventas de antes de su fin; una apagada a mano antes de vencer, no.
     */
    private static boolean vigenteEn(Promocion p, LocalDate fechaVenta) {
        if (p.getFechaInicio() != null && fechaVenta.isBefore(p.getFechaInicio().toLocalDate())) return false;
        LocalDateTime fin = p.getFechaFin();
        if (fin != null && fechaVenta.isAfter(fin.toLocalDate())) return false;
        return p.isActiva() || (fin != null && !fin.isAfter(LocalDateTime.now()));
    }
}
