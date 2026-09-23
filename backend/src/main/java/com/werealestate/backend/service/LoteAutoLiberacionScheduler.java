package com.werealestate.backend.service;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.MovimientoLote;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regresa a DISPONIBLE cualquier lote vencido, en dos modalidades:
 * <ul>
 *   <li>APARTADO (el estado simple): a los {@code app.lote.dias-apartado-expira} días fijos desde
 *   que se apartó, igual para todos los lotes.</li>
 *   <li>APARTADO_A_PLAZO: en la fecha propia que el admin/líder de área eligió al apartarlo (ver
 *   Lote#fechaExpiraApartado), no un plazo fijo global.</li>
 * </ul>
 * Corre cada 5 minutos: es el primer proceso en segundo plano de este backend (todo lo demás se
 * calcula al momento en que alguien abre la pantalla), así que la reversión aparece dentro de esos
 * 5 minutos de cumplirse el plazo, no exactamente al segundo.
 */
@Component
public class LoteAutoLiberacionScheduler {

    private final LoteRepository loteRepository;
    private final MovimientoLoteRepository movimientoLoteRepository;
    private final int diasApartadoExpira;

    public LoteAutoLiberacionScheduler(
            LoteRepository loteRepository,
            MovimientoLoteRepository movimientoLoteRepository,
            @Value("${app.lote.dias-apartado-expira}") int diasApartadoExpira) {
        this.loteRepository = loteRepository;
        this.movimientoLoteRepository = movimientoLoteRepository;
        this.diasApartadoExpira = diasApartadoExpira;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void liberarLotesVencidos() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limiteApartadoSimple = ahora.minusDays(diasApartadoExpira);

        liberar(loteRepository.findByEstadoAndFechaCambioEstadoBefore(EstadoLote.APARTADO, limiteApartadoSimple));
        liberar(loteRepository.findByEstadoAndFechaExpiraApartadoBefore(EstadoLote.APARTADO_A_PLAZO, ahora));
    }

    private void liberar(List<Lote> vencidos) {
        for (Lote lote : vencidos) {
            EstadoLote anterior = lote.getEstado();
            lote.cambiarEstado(EstadoLote.DISPONIBLE, null);
            lote.setFechaExpiraApartado(null);
            movimientoLoteRepository.save(
                    new MovimientoLote(lote, anterior, EstadoLote.DISPONIBLE, null, null, null, null, null));
        }
    }
}
