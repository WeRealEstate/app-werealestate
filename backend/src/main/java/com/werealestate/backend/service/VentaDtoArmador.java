package com.werealestate.backend.service;

import com.werealestate.backend.dto.ClienteResumenDto;
import com.werealestate.backend.dto.VentaAportacionDto;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaLoteDto;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Arma el {@link VentaDto} de una venta (o de una página de ventas) con sus lotes, aportaciones, copropietarios y
 * lo abonado. */
@Component
public class VentaDtoArmador {

    private final VentaLoteRepository ventaLoteRepository;
    private final VentaAportacionRepository ventaAportacionRepository;
    private final VentaCopropietarioRepository copropietarioRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final ConsultasVenta consultas;

    public VentaDtoArmador(
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            VentaCopropietarioRepository copropietarioRepository,
            PagoVentaRepository pagoVentaRepository,
            ConsultasVenta consultas) {
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.copropietarioRepository = copropietarioRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.consultas = consultas;
    }

    public VentaDto armar(Venta venta) {
        List<VentaLoteDto> lotes =
                ventaLoteRepository.findByVentaId(venta.getId()).stream().map(VentaLoteDto::from).toList();
        List<VentaAportacionDto> aportaciones = ventaAportacionRepository
                .findByVentaIdOrderByAnioAscMesAsc(venta.getId())
                .stream()
                .filter(a -> ConsultasVenta.delPlanVigente(venta, a))
                .map(VentaAportacionDto::from)
                .toList();
        List<ClienteResumenDto> copropietarios = copropietarioRepository.findByVentaIdOrderByOrdenAsc(venta.getId()).stream()
                .map(cp -> ClienteResumenDto.from(cp.getCliente()))
                .toList();
        return VentaDto.from(venta, lotes, aportaciones, copropietarios, consultas.totalAbonado(venta.getId()));
    }

    /**
     * Lo mismo que {@link #armar} para una página de ventas, pero leyendo lotes, aportaciones, copropietarios y lo
     * abonado de todas con una consulta por tipo: armar hace cuatro por venta y la lista de Ventas llegaba a ~60
     * consultas por página. Mismo resultado y mismo orden que llamar a armar con cada una.
     */
    public List<VentaDto> armarPagina(List<Venta> ventas) {
        if (ventas.isEmpty()) return List.of();
        List<Long> ids = ventas.stream().map(Venta::getId).toList();

        Map<Long, List<VentaLoteDto>> lotes = new HashMap<>();
        for (VentaLote vl : ventaLoteRepository.findByVentaIdInOrderByIdAsc(ids)) {
            lotes.computeIfAbsent(vl.getVenta().getId(), id -> new ArrayList<>()).add(VentaLoteDto.from(vl));
        }
        Map<Long, List<VentaAportacionDto>> aportaciones = new HashMap<>();
        for (VentaAportacion a : ventaAportacionRepository.findByVentaIdInOrderByAnioAscMesAsc(ids)) {
            if (!ConsultasVenta.delPlanVigente(a.getVenta(), a)) continue;
            aportaciones.computeIfAbsent(a.getVenta().getId(), id -> new ArrayList<>()).add(VentaAportacionDto.from(a));
        }
        Map<Long, List<ClienteResumenDto>> copropietarios = new HashMap<>();
        for (VentaCopropietario cp : copropietarioRepository.findByVentaIdInOrderByOrdenAsc(ids)) {
            copropietarios.computeIfAbsent(cp.getVenta().getId(), id -> new ArrayList<>())
                    .add(ClienteResumenDto.from(cp.getCliente()));
        }
        Map<Long, BigDecimal> abonado = new HashMap<>();
        for (Object[] fila : pagoVentaRepository.totalVigentePorVenta(ids)) {
            abonado.put((Long) fila[0], (BigDecimal) fila[1]);
        }

        return ventas.stream()
                .map(v -> VentaDto.from(
                        v,
                        lotes.getOrDefault(v.getId(), List.of()),
                        aportaciones.getOrDefault(v.getId(), List.of()),
                        copropietarios.getOrDefault(v.getId(), List.of()),
                        abonado.getOrDefault(v.getId(), BigDecimal.ZERO)))
                .toList();
    }
}
