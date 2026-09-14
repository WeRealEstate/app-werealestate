package com.werealestate.backend.service;

import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.repository.LoteRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regresa a DISPONIBLE cualquier lote en APARTADO (el estado simple, no los que solo puede poner
 * un admin) cuyas {@code app.lote.dias-apartado-expira} días ya se cumplieron. Corre cada 5
 * minutos: es el primer proceso en segundo plano de este backend (todo lo demás se calcula al
 * momento en que alguien abre la pantalla), así que la reversión aparece dentro de esos 5 minutos
 * de cumplirse el plazo, no exactamente al segundo.
 */
@Component
public class LoteAutoLiberacionScheduler {

    private final LoteRepository loteRepository;
    private final int diasApartadoExpira;

    public LoteAutoLiberacionScheduler(
            LoteRepository loteRepository,
            @Value("${app.lote.dias-apartado-expira}") int diasApartadoExpira) {
        this.loteRepository = loteRepository;
        this.diasApartadoExpira = diasApartadoExpira;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void liberarLotesVencidos() {
        LocalDateTime limite = LocalDateTime.now().minusDays(diasApartadoExpira);
        List<Lote> vencidos = loteRepository.findByEstadoAndFechaCambioEstadoBefore(EstadoLote.APARTADO, limite);
        for (Lote lote : vencidos) {
            lote.cambiarEstado(EstadoLote.DISPONIBLE, null);
        }
    }
}
