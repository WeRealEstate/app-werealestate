package com.werealestate.backend.service;

import com.werealestate.backend.dto.ReporteAsesorEstrellaDto;
import com.werealestate.backend.dto.ReporteConteoDto;
import com.werealestate.backend.dto.ReporteCotizacionesDto;
import com.werealestate.backend.dto.ReporteDesempenoDto;
import com.werealestate.backend.dto.ReporteRiesgoDto;
import com.werealestate.backend.dto.ReporteTendenciaPuntoDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Cotizacion;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.CotizacionRepository;
import com.werealestate.backend.repository.LeadRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboard de desempeño (solo admin). Todo se recalcula en cada consulta a partir de leads,
 * seguimientos y cotizaciones filtrados por el rango de fechas pedido; "riesgo" es la única
 * excepción, siempre es una foto del estado actual (ver ReporteRiesgoDto).
 */
@Service
@Transactional
public class ReporteService {

    private static final List<EstadoLead> CERRADOS = List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO);
    private static final Locale ES_MX = Locale.forLanguageTag("es-MX");
    private static final DateTimeFormatter FORMATO_SEMANA = DateTimeFormatter.ofPattern("d MMM", ES_MX);
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("MMM yyyy", ES_MX);

    private final LeadRepository leadRepository;
    private final SeguimientoRepository seguimientoRepository;
    private final CotizacionRepository cotizacionRepository;
    private final CurrentUserProvider currentUserProvider;
    private final int diasFrio;
    private final int diasSinContactarNuevo;

    public ReporteService(
            LeadRepository leadRepository,
            SeguimientoRepository seguimientoRepository,
            CotizacionRepository cotizacionRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.lead.dias-frio}") int diasFrio,
            @Value("${app.lead.dias-sin-contactar-nuevo}") int diasSinContactarNuevo) {
        this.leadRepository = leadRepository;
        this.seguimientoRepository = seguimientoRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.currentUserProvider = currentUserProvider;
        this.diasFrio = diasFrio;
        this.diasSinContactarNuevo = diasSinContactarNuevo;
    }

    public ReporteDesempenoDto obtenerDesempeno(LocalDate desde, LocalDate hasta) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede ver los reportes");
        }

        LocalDateTime desdeDt = desde != null ? desde.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime hastaDt = hasta != null ? hasta.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        List<Lead> leadsCreados = leadRepository.findByFechaCreacionBetween(desdeDt, hastaDt);
        List<Lead> leadsCerrados = leadRepository.findByEstadoInAndFechaUltimoContactoBetween(CERRADOS, desdeDt, hastaDt);

        long totalLeadsCreados = leadsCreados.size();
        long ventasCerradas = leadsCerrados.stream().filter(l -> l.getEstado() == EstadoLead.CERRADO_GANADO).count();
        long perdidosCerrados = leadsCerrados.size() - ventasCerradas;

        long ganadosDelCohorte = leadsCreados.stream().filter(l -> l.getEstado() == EstadoLead.CERRADO_GANADO).count();
        double tasaConversion = totalLeadsCreados == 0 ? 0 : (ganadosDelCohorte * 100.0) / totalLeadsCreados;

        List<Seguimiento> seguimientos = seguimientoRepository.findByFechaBetween(desdeDt, hastaDt);
        List<Cotizacion> cotizaciones = cotizacionRepository.findByFechaCreacionBetween(desdeDt, hastaDt);

        return new ReporteDesempenoDto(
                totalLeadsCreados,
                tasaConversion,
                ventasCerradas,
                perdidosCerrados,
                calcularAsesorEstrella(leadsCerrados),
                agrupar(leadsCreados, l -> l.getEstado().name()),
                agrupar(leadsCreados, l -> l.getDesarrollo().getNombre()),
                agrupar(leadsCreados, l -> l.getAsesor().getNombre()),
                agrupar(seguimientos, s -> s.getAsesor().getNombre()),
                calcularTendencia(leadsCreados, leadsCerrados, desdeDt, hastaDt),
                calcularRiesgo(),
                calcularCotizaciones(cotizaciones));
    }

    private ReporteAsesorEstrellaDto calcularAsesorEstrella(List<Lead> leadsCerrados) {
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (Lead lead : leadsCerrados) {
            if (lead.getEstado() != EstadoLead.CERRADO_GANADO) continue;
            conteo.merge(lead.getAsesor().getNombre(), 1L, Long::sum);
        }
        return conteo.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> new ReporteAsesorEstrellaDto(e.getKey(), e.getValue()))
                .orElse(null);
    }

    private ReporteRiesgoDto calcularRiesgo() {
        LocalDateTime ahora = LocalDateTime.now();
        long total = 0;
        Map<String, Long> porAsesor = new LinkedHashMap<>();

        for (Lead lead : leadRepository.findByArchivadoFalseOrderByFechaUltimoContactoAsc()) {
            if (CERRADOS.contains(lead.getEstado())) continue;

            boolean frio = ChronoUnit.DAYS.between(lead.getFechaUltimoContacto(), ahora) >= diasFrio;
            boolean sinContactar = !frio
                    && seguimientoRepository.findByLeadIdOrderByFechaDesc(lead.getId()).isEmpty()
                    && ChronoUnit.DAYS.between(lead.getFechaCreacion(), ahora) >= diasSinContactarNuevo;

            if (frio || sinContactar) {
                total++;
                porAsesor.merge(lead.getAsesor().getNombre(), 1L, Long::sum);
            }
        }

        List<ReporteConteoDto> lista = porAsesor.entrySet().stream()
                .map(e -> new ReporteConteoDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(ReporteConteoDto::total).reversed())
                .toList();
        return new ReporteRiesgoDto(total, lista);
    }

    private ReporteCotizacionesDto calcularCotizaciones(List<Cotizacion> cotizaciones) {
        BigDecimal montoTotal = cotizaciones.stream().map(Cotizacion::getTotalInversion).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ReporteCotizacionesDto(cotizaciones.size(), montoTotal, agrupar(cotizaciones, Cotizacion::getProyecto));
    }

    /** Granularidad automática: por semana si el rango real de datos cabe en ~100 días, por mes si
     * es más largo (por ejemplo, al elegir "Todo" con varios años de historial). Genera también
     * los sub-periodos sin actividad (en 0), para que la línea no tenga huecos. */
    private List<ReporteTendenciaPuntoDto> calcularTendencia(
            List<Lead> leadsCreados, List<Lead> leadsCerrados, LocalDateTime desdeDt, LocalDateTime hastaDt) {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime finReal = hastaDt.isAfter(ahora) ? ahora : hastaDt;

        LocalDateTime inicioReal = Stream.concat(
                        leadsCreados.stream().map(Lead::getFechaCreacion),
                        leadsCerrados.stream().map(Lead::getFechaUltimoContacto))
                .min(LocalDateTime::compareTo)
                .orElse(finReal.minusWeeks(1));
        if (inicioReal.isBefore(desdeDt)) {
            inicioReal = desdeDt;
        }
        if (!inicioReal.isBefore(finReal)) {
            inicioReal = finReal.minusWeeks(1);
        }

        boolean porMes = ChronoUnit.DAYS.between(inicioReal, finReal) > 100;
        DateTimeFormatter formato = porMes ? FORMATO_MES : FORMATO_SEMANA;

        Map<String, long[]> buckets = new LinkedHashMap<>();
        LocalDate cursor = porMes
                ? inicioReal.toLocalDate().withDayOfMonth(1)
                : inicioReal.toLocalDate().with(DayOfWeek.MONDAY);
        LocalDate limite = finReal.toLocalDate();
        while (!cursor.isAfter(limite)) {
            buckets.put(claveBucket(cursor, formato), new long[] {0, 0});
            cursor = porMes ? cursor.plusMonths(1) : cursor.plusWeeks(1);
        }

        for (Lead lead : leadsCreados) {
            incrementarBucket(buckets, lead.getFechaCreacion(), porMes, formato, 0);
        }
        for (Lead lead : leadsCerrados) {
            if (lead.getEstado() != EstadoLead.CERRADO_GANADO) continue;
            incrementarBucket(buckets, lead.getFechaUltimoContacto(), porMes, formato, 1);
        }

        return buckets.entrySet().stream()
                .map(e -> new ReporteTendenciaPuntoDto(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    private void incrementarBucket(
            Map<String, long[]> buckets, LocalDateTime fecha, boolean porMes, DateTimeFormatter formato, int indice) {
        LocalDate inicioBucket = porMes ? fecha.toLocalDate().withDayOfMonth(1) : fecha.toLocalDate().with(DayOfWeek.MONDAY);
        long[] valores = buckets.get(claveBucket(inicioBucket, formato));
        if (valores != null) {
            valores[indice]++;
        }
    }

    private String claveBucket(LocalDate inicioBucket, DateTimeFormatter formato) {
        String etiqueta = formato.format(inicioBucket);
        return etiqueta.substring(0, 1).toUpperCase(ES_MX) + etiqueta.substring(1);
    }

    private <T> List<ReporteConteoDto> agrupar(List<T> items, Function<T, String> clave) {
        Map<String, Long> conteo = new LinkedHashMap<>();
        for (T item : items) {
            conteo.merge(clave.apply(item), 1L, Long::sum);
        }
        return conteo.entrySet().stream()
                .map(e -> new ReporteConteoDto(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(ReporteConteoDto::total).reversed())
                .toList();
    }
}
