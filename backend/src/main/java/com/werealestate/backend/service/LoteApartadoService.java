package com.werealestate.backend.service;

import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Los lotes que un asesor puede cotizar además de los disponibles: los que él mismo apartó (apartó primero para que
 * no se lo ganen y cotiza después). Cotizar no cambia el estado del lote. Admin y administración ven cualquier apartado.
 * Un apartado es "de" un asesor por la clave que se guardó al apartarlo ({@code Lote.apartadoPorClave}); en los
 * anteriores a V90 o hechos sin PIN (clave vacía) se compara el nombre del último movimiento que puso el lote en su
 * estado actual, sin importar mayúsculas, acentos ni espacios. Los reservados por We no son apartados y nunca salen. */
@Service
@Transactional(readOnly = true)
public class LoteApartadoService {

    private final LoteRepository loteRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final MovimientoLoteRepository movimientoLoteRepository;
    private final AutorizacionLotes autorizacion;

    public LoteApartadoService(
            LoteRepository loteRepository,
            DesarrolloRepository desarrolloRepository,
            MovimientoLoteRepository movimientoLoteRepository,
            AutorizacionLotes autorizacion) {
        this.loteRepository = loteRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.autorizacion = autorizacion;
    }

    /** Para el cotizador del panel: los disponibles del desarrollo más sus apartados, en orden natural. Un asesor ve solo
     * los apartados que son suyos; admin y administración, todos. */
    public List<LoteDto> listarCotizables(Long desarrolloId) {
        Usuario actual = autorizacion.usuarioActual();
        List<Lote> cotizables = new ArrayList<>(
                loteRepository.findByDesarrolloIdAndEstadoOrderByManzanaAscNumeroLoteAsc(desarrolloId, EstadoLote.DISPONIBLE));
        List<Lote> apartados = loteRepository.findByDesarrolloIdAndEstadoIn(desarrolloId, EstadosDeLote.APARTADO);
        if (AutorizacionLotes.esAdminOLider(actual)) {
            cotizables.addAll(apartados);
        } else {
            Set<Long> propios = propios(apartados, ClaveAsesor.de(actual, null), actual.getNombre(), actual.getId());
            apartados.stream().filter(l -> propios.contains(l.getId())).forEach(cotizables::add);
        }
        return cotizables.stream().sorted(LoteConsultaService.ORDEN_NATURAL_LOTES).map(LoteDto::sinMapa).toList();
    }

    /** Para el cotizador público con el PIN guardado: solo los apartados de ese asesor en el plano pedido (samai /
     * nanuu), con el estado como lo ve el público. Los disponibles ya los trae la lista pública de siempre. */
    public List<LoteDto> listarApartadosPublicos(String proyecto, String clave, String nombre) {
        Desarrollo desarrollo = desarrolloRepository
                .findByNombre(LoteConsultaService.nombreDesarrolloDeProyecto(proyecto))
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        List<Lote> apartados = loteRepository.findByDesarrolloIdAndEstadoIn(desarrollo.getId(), EstadosDeLote.APARTADO);
        Set<Long> propios = propios(apartados, clave, nombre, null);
        return apartados.stream()
                .filter(l -> propios.contains(l.getId()))
                .sorted(LoteConsultaService.ORDEN_NATURAL_LOTES)
                .map(LoteDto::paraPublico)
                .toList();
    }

    /** Ids de los apartados que son de ese asesor: por la clave guardada o, si el lote no la tiene, por el nombre del
     * último movimiento a su estado actual (una sola consulta del historial de todos los que no la tienen). */
    private Set<Long> propios(List<Lote> apartados, String clave, String nombre, Long usuarioId) {
        Set<Long> propios = new HashSet<>();
        Map<Long, Lote> sinClave = new HashMap<>();
        for (Lote lote : apartados) {
            if (lote.getApartadoPorClave() == null) {
                sinClave.put(lote.getId(), lote);
            } else if (lote.getApartadoPorClave().equals(clave)) {
                propios.add(lote.getId());
            }
        }
        if (sinClave.isEmpty()) return propios;

        String nombreNormalizado = Textos.normalizar(nombre);
        Set<Long> yaRevisados = new HashSet<>();
        for (MovimientoLote m : movimientoLoteRepository.findByLoteIdInOrderByFechaDesc(sinClave.keySet())) {
            Long loteId = m.getLote().getId();
            // El primero (más reciente) que dejó el lote en su estado actual es el que lo apartó.
            if (m.getEstadoNuevo() != sinClave.get(loteId).getEstado() || !yaRevisados.add(loteId)) continue;
            boolean mismoNombre = !nombreNormalizado.isEmpty()
                    && nombreNormalizado.equals(Textos.normalizar(m.getNombreAsesor()));
            // Un apartado del panel hecho por el propio asesor no lleva nombre de asesor: lo identifica quien lo hizo.
            boolean mismoUsuario = usuarioId != null
                    && m.getNombreAsesor() == null
                    && m.getUsuario() != null
                    && usuarioId.equals(m.getUsuario().getId());
            if (mismoNombre || mismoUsuario) propios.add(loteId);
        }
        return propios;
    }
}
