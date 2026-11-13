package com.werealestate.backend.service;

import com.werealestate.backend.dto.MovimientoLoteDto;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La bitácora de los lotes (MovimientoLote): registra cada cambio de estado y la consulta/depura. */
@Service
@Transactional
public class HistorialLoteService {

    private final MovimientoLoteRepository movimientoLoteRepository;
    private final LoteConsultaService consulta;
    private final AutorizacionLotes autorizacion;

    public HistorialLoteService(
            MovimientoLoteRepository movimientoLoteRepository,
            LoteConsultaService consulta,
            AutorizacionLotes autorizacion) {
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.consulta = consulta;
        this.autorizacion = autorizacion;
    }

    /** Único punto que cambia el estado de un lote Y dejar registro en la bitácora: así el
     * historial siempre queda consistente con el estado real del lote, sin importar desde dónde se
     * haya originado el cambio (asesor/admin autenticado, visitante público o el revertido
     * automático por vencimiento). */
    public void cambiarEstadoConHistorial(
            Lote lote,
            EstadoLote nuevoEstado,
            Usuario usuario,
            String nombreAsesor,
            String nombreCliente,
            BigDecimal monto,
            String nota) {
        EstadoLote anterior = lote.getEstado();
        lote.cambiarEstado(nuevoEstado, usuario);
        movimientoLoteRepository.save(
                new MovimientoLote(lote, anterior, nuevoEstado, usuario, nombreAsesor, nombreCliente, monto, nota));
    }

    /** Historial de movimientos de todos los lotes (quién cambió qué y cuándo), para /panel/lotes.
     * Filtrable por manzana/lote/desarrollo igual que {@link #buscarPaginado}. */
    public PaginaDto<MovimientoLoteDto> buscarMovimientos(
            String manzana, String numeroLote, Long desarrolloId, int pagina, int tamano) {
        Specification<MovimientoLote> spec = (root, query, cb) -> cb.conjunction();

        if (manzana != null && !manzana.isBlank()) {
            String comodin = Textos.comodinMinusculas(manzana);
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("lote").get("manzana")), comodin));
        }
        if (numeroLote != null && !numeroLote.isBlank()) {
            String comodin = Textos.comodinMinusculas(numeroLote);
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("lote").get("numeroLote")), comodin));
        }
        if (desarrolloId != null) {
            spec = spec.and(
                    (root, query, cb) -> cb.equal(root.get("lote").get("desarrollo").get("id"), desarrolloId));
        }
        spec = spec.and((root, query, cb) -> {
            query.orderBy(cb.desc(root.get("fecha")));
            return cb.conjunction();
        });

        Pageable pageable = PageRequest.of(Math.max(pagina, 0), Math.max(tamano, 1));
        Page<MovimientoLote> resultado = movimientoLoteRepository.findAll(spec, pageable);
        return new PaginaDto<>(resultado.getContent().stream().map(MovimientoLoteDto::from).toList(), resultado.hasNext());
    }

    /** Historial completo de un lote específico (más reciente primero), para el ícono de "ver
     * información" en /panel/lotes: cuándo entró a su estado actual, quién lo cambió y el nombre
     * del cliente/nota que se haya capturado en cada paso. Mismo alcance de acceso que
     * {@link #buscarMovimientos}: cualquier rol autenticado puede consultarlo. */
    public List<MovimientoLoteDto> historialDeLote(Long id) {
        consulta.obtenerEntidad(id); // valida que el lote exista antes de listar su historial
        return movimientoLoteRepository.findByLoteIdOrderByFechaDesc(id).stream()
                .map(MovimientoLoteDto::from)
                .toList();
    }

    /** Borrar un registro del historial de movimientos es exclusivo de admin: es la bitácora de
     * auditoría de todo lo que pasa con los lotes. */
    public void eliminarMovimiento(Long id) {
        autorizacion.exigirAdmin("eliminar movimientos del historial");
        MovimientoLote movimiento = movimientoLoteRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Movimiento no encontrado"));
        movimientoLoteRepository.delete(movimiento);
    }
}
