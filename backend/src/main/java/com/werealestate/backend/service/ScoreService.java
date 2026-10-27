package com.werealestate.backend.service;

import com.werealestate.backend.dto.ScoreDtos;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaComision;
import com.werealestate.backend.repository.VentaComisionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.text.Collator;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Score de ventas: cuántas ventas lleva cada asesor (interno o externo) en un periodo. Cuentan todas las del módulo
 * Ventas por su fecha de venta, las capturadas a mano y las que entran por We Forms (una venta eliminada ya no existe,
 * así que deja de contar). Una venta compartida suma una venta completa, con todos sus lotes, a cada uno de sus dos
 * asesores: el principal y el copropietario, este último solo mientras su comisión secundaria no esté cancelada
 * (cancelarla es la forma de quitar a un copropietario puesto por error). Los asesores inactivos no aparecen.
 * Solo admin y Administración; el módulo SCORE lo revisa ModuloAccesoInterceptor.
 */
@Service
@Transactional(readOnly = true)
public class ScoreService {

    /** Sin fechas ("Todo"): cubre cualquier fecha de venta que pueda existir. */
    private static final LocalDate SIN_INICIO = LocalDate.of(2000, 1, 1);
    private static final LocalDate SIN_FIN = LocalDate.of(2999, 12, 31);

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaComisionRepository comisionRepository;
    private final CurrentUserProvider currentUserProvider;

    public ScoreService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaComisionRepository comisionRepository,
            CurrentUserProvider currentUserProvider) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.comisionRepository = comisionRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /** Ventas y lotes por asesor con fecha de venta entre desde y hasta (ambas inclusivas; null = sin límite). */
    public ScoreDtos.Ranking ranking(LocalDate desde, LocalDate hasta) {
        exigirAdminOLider();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ValidationException("La fecha inicial no puede ser posterior a la final");
        }
        List<Venta> ventas = ventaRepository.findByFechaVentaBetween(
                desde != null ? desde : SIN_INICIO, hasta != null ? hasta : SIN_FIN);
        if (ventas.isEmpty()) return new ScoreDtos.Ranking(desde, hasta, 0, List.of());

        List<Long> ids = ventas.stream().map(Venta::getId).toList();
        Map<Long, Long> lotesPorVenta = new HashMap<>();
        for (Object[] fila : ventaLoteRepository.contarLotesPorVenta(ids)) {
            lotesPorVenta.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        Map<Long, List<VentaComision>> copropietarios = comisionRepository
                .findByVentaIdInAndSecundariaTrueAndCanceladaFalse(ids).stream()
                .collect(Collectors.groupingBy(c -> c.getVenta().getId()));

        Map<String, Acumulado> porAsesor = new HashMap<>();
        for (Venta venta : ventas) {
            // Por clave: el mismo asesor no suma dos veces una venta aunque figure como principal y como copropietario.
            Map<String, Participante> participantes = new LinkedHashMap<>();
            agregar(participantes, participante(venta.getUsuarioAsesor(), venta.getAsesorExterno()));
            for (VentaComision c : copropietarios.getOrDefault(venta.getId(), List.of())) {
                agregar(participantes, participante(c.getUsuarioAsesor(), c.getAsesorExterno()));
            }
            long lotes = lotesPorVenta.getOrDefault(venta.getId(), 0L);
            for (Participante p : participantes.values()) {
                if (p.activo()) porAsesor.computeIfAbsent(p.clave(), k -> new Acumulado(p)).sumar(lotes);
            }
        }

        Collator porNombre = Collator.getInstance(Locale.forLanguageTag("es-MX"));
        porNombre.setStrength(Collator.PRIMARY);
        List<ScoreDtos.Asesor> asesores = porAsesor.values().stream()
                .map(Acumulado::dto)
                .sorted(Comparator.comparingLong(ScoreDtos.Asesor::ventas)
                        .reversed()
                        .thenComparing(ScoreDtos.Asesor::nombre, porNombre))
                .toList();
        return new ScoreDtos.Ranking(desde, hasta, ventas.size(), asesores);
    }

    // ---------------------------------------------------------------- internos

    private record Participante(String clave, String nombre, ScoreDtos.TipoAsesor tipo, boolean activo) {}

    private static void agregar(Map<String, Participante> participantes, Participante p) {
        if (p != null) participantes.putIfAbsent(p.clave(), p);
    }

    /** Quién es el asesor de una venta (o de una comisión): un usuario interno o un asesor externo. La ficha de comunidad
     * de un usuario interno (asesor externo con usuarioId) es la misma persona que su usuario: cuenta en la misma fila. */
    private static Participante participante(Usuario usuario, AsesorExterno externo) {
        if (usuario != null) {
            return new Participante(
                    "U-" + usuario.getId(), usuario.getNombre(), ScoreDtos.TipoAsesor.INTERNO, usuario.isActivo());
        }
        if (externo == null) return null;
        if (externo.getUsuarioId() != null) {
            return new Participante(
                    "U-" + externo.getUsuarioId(), externo.getNombre(), ScoreDtos.TipoAsesor.INTERNO, externo.isActivo());
        }
        return new Participante(
                "E-" + externo.getId(), externo.getNombre(), ScoreDtos.TipoAsesor.EXTERNO, externo.isActivo());
    }

    private static final class Acumulado {
        private final Participante asesor;
        private long ventas;
        private long lotes;

        Acumulado(Participante asesor) {
            this.asesor = asesor;
        }

        void sumar(long lotesDeLaVenta) {
            ventas++;
            lotes += lotesDeLaVenta;
        }

        ScoreDtos.Asesor dto() {
            return new ScoreDtos.Asesor(asesor.clave(), asesor.nombre(), asesor.tipo(), ventas, lotes);
        }
    }

    private void exigirAdminOLider() {
        Role rol = currentUserProvider.getUsuarioActual().getRol();
        if (rol != Role.ADMIN && rol != Role.LIDER_AREA) {
            throw new ForbiddenOperationException(
                    "Solo un administrador o personal de administración puede ver el score de ventas");
        }
    }
}
