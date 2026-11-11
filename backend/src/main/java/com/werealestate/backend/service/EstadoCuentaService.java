package com.werealestate.backend.service;

import com.werealestate.backend.dto.AmortizacionDto;
import com.werealestate.backend.dto.NumeroClienteDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.PagoVenta;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaCopropietario;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.ClienteRepository;
import com.werealestate.backend.repository.PagoVentaRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaCopropietarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tabla de amortización y estado de cuenta de una venta (o de todas las compras de un cliente, como titular o
 * copropietario), para Clientes y Ventas. El cálculo es el de {@link CalendarioPagos}, el mismo de Finanzas. Solo
 * admin y administración.
 */
@Service
@Transactional(readOnly = true)
public class EstadoCuentaService {

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaAportacionRepository ventaAportacionRepository;
    private final PagoVentaRepository pagoVentaRepository;
    private final VentaCopropietarioRepository copropietarioRepository;
    private final ClienteRepository clienteRepository;
    private final ClienteNumeracionService numeracion;
    private final CurrentUserProvider currentUserProvider;

    public EstadoCuentaService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            PagoVentaRepository pagoVentaRepository,
            VentaCopropietarioRepository copropietarioRepository,
            ClienteRepository clienteRepository,
            ClienteNumeracionService numeracion,
            CurrentUserProvider currentUserProvider) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.pagoVentaRepository = pagoVentaRepository;
        this.copropietarioRepository = copropietarioRepository;
        this.clienteRepository = clienteRepository;
        this.numeracion = numeracion;
        this.currentUserProvider = currentUserProvider;
    }

    /** La tabla de amortización de una venta. */
    public AmortizacionDto deVenta(Long ventaId) {
        exigirAdminOLider();
        Venta venta = ventaRepository.findById(ventaId).orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));
        return amortizacion(venta, false, LocalDate.now());
    }

    /** Una tabla por cada compra del cliente (como titular o copropietario), de la más reciente a la más antigua:
     * el mismo orden que la lista de compras de su ficha. */
    public List<AmortizacionDto> deCliente(Long clienteId) {
        exigirAdminOLider();
        if (!clienteRepository.existsById(clienteId)) throw new ResourceNotFoundException("Cliente no encontrado");
        Map<Long, Venta> ventas = new LinkedHashMap<>();
        Set<Long> comoCopropietario = new java.util.HashSet<>();
        for (Venta v : ventaRepository.findByClienteRefId(clienteId)) ventas.put(v.getId(), v);
        for (VentaCopropietario cp : copropietarioRepository.findByClienteId(clienteId)) {
            ventas.putIfAbsent(cp.getVenta().getId(), cp.getVenta());
            comoCopropietario.add(cp.getVenta().getId());
        }
        LocalDate hoy = LocalDate.now();
        return ventas.values().stream()
                .sorted(Comparator.comparing(Venta::getFechaVenta).reversed().thenComparing(Venta::getId, Comparator.reverseOrder()))
                .map(v -> amortizacion(v, comoCopropietario.contains(v.getId()), hoy))
                .toList();
    }

    private AmortizacionDto amortizacion(Venta venta, boolean copropietario, LocalDate hoy) {
        List<VentaLote> lotes = ventaLoteRepository.findByVentaId(venta.getId());
        BigDecimal precio = lotes.stream().map(VentaLote::getPrecio).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<PagoVenta> pagos = pagoVentaRepository.findVigentesByVentaIdOrderByFechaAscIdAsc(venta.getId());
        BigDecimal abonado = pagos.stream()
                .map(PagoVenta::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CalendarioPagos.Concepto> conceptos = CalendarioPagos.conceptos(
                venta, precio, ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(venta.getId()));
        CalendarioPagos.EstadoDeCuenta cuenta = CalendarioPagos.estadoDeCuenta(conceptos, precio, abonado, hoy);

        Cliente titular = venta.getClienteRef();
        Set<Long> desarrollosIds = new LinkedHashSet<>();
        lotes.forEach(l -> desarrollosIds.add(l.getLote().getDesarrollo().getId()));
        List<String> numeros = titular == null
                ? List.of()
                : numeracion.numerosDe(titular.getId()).stream()
                        .filter(n -> desarrollosIds.contains(n.desarrolloId()))
                        .map(NumeroClienteDto::etiqueta)
                        .toList();
        List<String> copropietarios = copropietarioRepository.findByVentaIdOrderByOrdenAsc(venta.getId()).stream()
                .map(cp -> cp.getCliente().nombreCompleto())
                .toList();
        boolean conPlazo = venta.getMensualidad() != null && venta.getPlazoMeses() != null;
        CalendarioPagos.Plan original = CalendarioPagos.planOriginal(venta);

        return new AmortizacionDto(
                venta.getId(),
                venta.getEtiqueta(),
                venta.getFechaVenta(),
                titular != null ? titular.getId() : null,
                titular != null ? titular.nombreCompleto() : venta.getCliente(),
                numeros,
                titular != null ? titular.getTelefono() : null,
                copropietarios,
                copropietario,
                new ArrayList<>(new LinkedHashSet<>(lotes.stream().map(l -> l.getLote().getDesarrollo().getNombre()).toList())),
                lotes.stream().map(l -> "Mz " + l.getLote().getManzana() + " / Lote " + l.getLote().getNumeroLote()).toList(),
                venta.getFormaPago(),
                venta.getNumeroContrato(),
                precio,
                venta.getEnganche() != null ? venta.getEngancheLabel() : null,
                venta.getEnganche(),
                conPlazo ? venta.getPlazoMeses() : null,
                conPlazo ? venta.getMensualidad() : null,
                conPlazo ? venta.getDiaPago() : 0,
                hoy,
                cuenta.abonado(),
                cuenta.saldo(),
                cuenta.atrasoMonto(),
                cuenta.pagosAtrasados(),
                cuenta.mensualidadesAtrasadas(),
                cuenta.atrasadoDesde(),
                cuenta.proximaFecha(),
                cuenta.proximoMonto(),
                cuenta.filas(),
                TablaMensual.construir(conceptos, cuenta.filas(), precio, pagos, hoy),
                reestructuraciones(venta),
                original.primerMes().toString(),
                precio.subtract(original.conPagoInicialPublico() ? original.enganche() : BigDecimal.ZERO));
    }

    private static List<AmortizacionDto.Reestructuracion> reestructuraciones(Venta venta) {
        List<AmortizacionDto.Reestructuracion> lista = new ArrayList<>();
        List<com.werealestate.backend.model.VentaReestructuracion> rs = venta.getReestructuraciones();
        for (int i = 0; i < rs.size(); i++) {
            var r = rs.get(i);
            lista.add(new AmortizacionDto.Reestructuracion(i + 1, r.getFechaCorte(), r.getMotivo(),
                    r.getUsuario() != null ? r.getUsuario().getNombre() : null,
                    r.getAbonadoAlCorte(), r.getSaldoAlCorte(), r.getResumenAnterior(), r.getResumenNuevo()));
        }
        return lista;
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException(
                    "Solo un administrador o personal de administración puede consultar el estado de cuenta");
        }
        return actual;
    }
}
