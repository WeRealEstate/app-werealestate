package com.werealestate.backend.service;

import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Qué lotes puede poner un asesor en un registro de venta de We Forms. */
@Service
@Transactional
public class LoteRegistroVentaService {

    private final LoteRepository loteRepository;
    private final MovimientoLoteRepository movimientoLoteRepository;
    private final VentaLoteRepository ventaLoteRepository;

    public LoteRegistroVentaService(
            LoteRepository loteRepository,
            MovimientoLoteRepository movimientoLoteRepository,
            VentaLoteRepository ventaLoteRepository) {
        this.loteRepository = loteRepository;
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.ventaLoteRepository = ventaLoteRepository;
    }

    /** Lotes que un asesor puede poner en un registro de venta: los disponibles del desarrollo y los que ÉL mismo
     * apartó (quien aparece en el último cambio a su estado actual). Un apartado que alguien más hizo, o
     * uno sin asesor atribuido (por ejemplo, fijado por administración), no se ofrece.
     * Resuelve las ventas del desarrollo y el historial de los lotes apartados en bloque (2 consultas en vez de N+1
     * por lote) y devuelve LoteDto.sinMapa porque el selector de We Forms no dibuja el plano. */
    @Transactional(readOnly = true)
    public List<LoteDto> listarParaRegistroDeVenta(Long desarrolloId, Long asesorUsuarioId, String asesorNombre) {
        List<Lote> todos = loteRepository.findByDesarrolloId(desarrolloId);
        if (todos.isEmpty()) return List.of();
        Set<Long> conVenta = new HashSet<>(ventaLoteRepository.lotesConVentaEnDesarrollo(desarrolloId));
        List<Long> apartadoIds = todos.stream()
                .filter(l -> !conVenta.contains(l.getId()) && EstadosDeLote.APARTADO.contains(l.getEstado()))
                .map(Lote::getId)
                .toList();
        Map<Long, List<MovimientoLote>> movimientosPorLote = new HashMap<>();
        if (!apartadoIds.isEmpty()) {
            for (MovimientoLote m : movimientoLoteRepository.findByLoteIdInOrderByFechaDesc(apartadoIds)) {
                movimientosPorLote.computeIfAbsent(m.getLote().getId(), k -> new ArrayList<>()).add(m);
            }
        }
        return todos.stream()
                .filter(l -> !conVenta.contains(l.getId())
                        && puedeTomarSegunEstado(
                                l, asesorUsuarioId, asesorNombre, movimientosPorLote.getOrDefault(l.getId(), List.of())))
                .sorted(LoteConsultaService.ORDEN_NATURAL_LOTES)
                .map(LoteDto::sinMapa)
                .toList();
    }

    /** Regla de {@link #listarParaRegistroDeVenta}, y que no tenga ya una venta. */
    public boolean puedeTomarParaRegistroDeVenta(Lote lote, Long asesorUsuarioId, String asesorNombre) {
        if (ventaLoteRepository.existsByLoteId(lote.getId())) return false;
        if (lote.getEstado() == EstadoLote.DISPONIBLE) return true;
        if (!EstadosDeLote.APARTADO.contains(lote.getEstado())) return false;
        return puedeTomarSegunEstado(
                lote, asesorUsuarioId, asesorNombre, movimientoLoteRepository.findByLoteIdOrderByFechaDesc(lote.getId()));
    }

    private static boolean puedeTomarSegunEstado(
            Lote lote, Long asesorUsuarioId, String asesorNombre, List<MovimientoLote> movimientos) {
        EstadoLote estado = lote.getEstado();
        if (estado == EstadoLote.DISPONIBLE) return true;
        if (!EstadosDeLote.APARTADO.contains(estado)) return false;
        return movimientos.stream()
                .filter(m -> m.getEstadoNuevo() == estado)
                .findFirst()
                .map(m -> {
                    boolean mismoNombre = m.getNombreAsesor() != null
                            && m.getNombreAsesor().trim().equalsIgnoreCase(asesorNombre == null ? "" : asesorNombre.trim());
                    boolean mismoUsuario = asesorUsuarioId != null
                            && m.getUsuario() != null
                            && asesorUsuarioId.equals(m.getUsuario().getId())
                            && m.getNombreAsesor() == null;
                    return mismoNombre || mismoUsuario;
                })
                .orElse(false);
    }
}
