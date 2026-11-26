package com.werealestate.backend.service;

import com.werealestate.backend.dto.VentaCambiarLotesRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaAportacion;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.VentaAportacionRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambiar los lotes de una venta ya registrada: sustituir, agregar, quitar y corregir precios (decisiones del
 * usuario). Los pagos se conservan; el precio nuevo puede quedar por debajo de lo abonado (el saldo queda en $0). Solo
 * lotes del mismo desarrollo (el número de la venta es de ese desarrollo). Los lotes que salen vuelven a Disponible,
 * los que entran toman el estado según el contrato (que se conserva), y la comisión se recalcula con el mismo %.
 */
@Service
@Transactional
public class CambioLotesVentaService {

    private final AutorizacionVentas autorizacion;
    private final ConsultasVenta consultas;
    private final VentaLoteRepository ventaLoteRepository;
    private final VentaAportacionRepository ventaAportacionRepository;
    private final LoteRepository loteRepository;
    private final LoteVentaService loteVentaService;
    private final ComisionService comisionService;
    private final PromocionVentaService promocionVenta;
    private final VentaDtoArmador dtoArmador;

    public CambioLotesVentaService(
            AutorizacionVentas autorizacion,
            ConsultasVenta consultas,
            VentaLoteRepository ventaLoteRepository,
            VentaAportacionRepository ventaAportacionRepository,
            LoteRepository loteRepository,
            LoteVentaService loteVentaService,
            ComisionService comisionService,
            PromocionVentaService promocionVenta,
            VentaDtoArmador dtoArmador) {
        this.autorizacion = autorizacion;
        this.consultas = consultas;
        this.ventaLoteRepository = ventaLoteRepository;
        this.ventaAportacionRepository = ventaAportacionRepository;
        this.loteRepository = loteRepository;
        this.loteVentaService = loteVentaService;
        this.comisionService = comisionService;
        this.promocionVenta = promocionVenta;
        this.dtoArmador = dtoArmador;
    }

    public VentaDto cambiar(Long ventaId, VentaCambiarLotesRequest request) {
        autorizacion.exigirAdminOLiderConContrasena(
                "Solo un administrador o personal de administración puede cambiar los lotes de una venta", request.password());
        Venta venta = consultas.obtenerEntidad(ventaId);

        List<VentaLote> actuales = ventaLoteRepository.findByVentaId(ventaId);
        Map<Long, VentaLote> porLote = new LinkedHashMap<>();
        actuales.forEach(vl -> porLote.put(vl.getLote().getId(), vl));
        Desarrollo desarrollo = venta.getDesarrollo() != null ? venta.getDesarrollo()
                : actuales.stream().findFirst().map(vl -> vl.getLote().getDesarrollo()).orElse(null);

        Map<Long, VentaLoteItemRequest> pedidos = new LinkedHashMap<>();
        List<Lote> lotesNuevos = new ArrayList<>();
        for (VentaLoteItemRequest item : request.lotes()) {
            if (pedidos.put(item.loteId(), item) != null) {
                throw new ValidationException("El mismo lote no puede repetirse dentro de una venta");
            }
            Lote lote = loteRepository.findById(item.loteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Lote no encontrado"));
            if (desarrollo != null && !lote.getDesarrollo().getId().equals(desarrollo.getId())) {
                throw new ValidationException("Solo se pueden poner lotes de " + desarrollo.getNombre()
                        + ": para pasar a otro desarrollo, elimina la venta y regístrala de nuevo");
            }
            if (!porLote.containsKey(item.loteId())) {
                ventaLoteRepository.findFirstByLoteIdOrderByIdDesc(item.loteId()).ifPresent(otra -> {
                    throw new ConflictException("El lote Mz " + lote.getManzana() + " / Lote " + lote.getNumeroLote()
                            + " ya está en la venta " + otra.getVenta().getEtiqueta());
                });
                lotesNuevos.add(lote);
            }
        }

        BigDecimal precioNuevo = request.lotes().stream().map(VentaLoteItemRequest::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        validarPlan(venta, precioNuevo);

        if (request.promocionId() != null) {
            List<Lote> todos = pedidos.keySet().stream().map(id -> loteRepository.findById(id).orElseThrow()).toList();
            var promocion = promocionVenta.aplicar(request.promocionId(), venta.getFechaVenta(), todos, venta.getPromocionNombre());
            venta.registrarPromocion(promocion.nombre(), promocion.bono(), promocion.mensualidadFija());
        } else {
            venta.registrarPromocion(null, null);
        }

        // Primero salen (así quedan libres), luego se corrigen precios y entran los nuevos.
        List<Long> quitados = porLote.keySet().stream().filter(id -> !pedidos.containsKey(id)).toList();
        ventaLoteRepository.deleteAll(quitados.stream().map(porLote::get).toList());
        porLote.forEach((loteId, vl) -> {
            VentaLoteItemRequest item = pedidos.get(loteId);
            if (item != null) vl.setPrecio(item.precio());
        });
        ventaLoteRepository.flush();

        for (Long loteId : quitados) {
            if (!ventaLoteRepository.existsByLoteId(loteId)) {
                loteVentaService.liberarPorSalirDeVenta(loteId, venta.getEtiquetaReal());
            }
        }
        BigDecimal apartado = BigDecimal.ZERO;
        for (Lote lote : lotesNuevos) {
            // El dinero con el que estuviera apartado el lote no se pierde: queda como depósito pendiente de aplicar.
            if (lote.getMontoApartado() != null) apartado = apartado.add(lote.getMontoApartado());
            ventaLoteRepository.save(new VentaLote(venta, lote, pedidos.get(lote.getId()).precio()));
            loteVentaService.marcarPorCambioDeVenta(lote.getId(), venta.isContratoFirmado(), venta.getCliente(),
                    venta.getAsesorNombre(), venta.getEtiquetaReal());
        }
        if (apartado.signum() > 0) {
            BigDecimal previo = venta.getDepositoPendiente() != null ? venta.getDepositoPendiente() : BigDecimal.ZERO;
            venta.setDepositoPendiente(previo.add(apartado));
        }

        if (venta.getPlazoMeses() != null && request.mensualidad() != null) {
            venta.setMensualidad(request.mensualidad());
        }
        ventaLoteRepository.flush();
        comisionService.recalcularPorCambioDePrecio(venta);
        return dtoArmador.armar(venta);
    }

    /** Con saldo por pagar, el enganche y las aportaciones del plan no pueden cubrir todo el precio nuevo. En una venta
     * reestructurada el plan vigente solo reparte lo que haya (la tabla nunca pasa del precio). */
    private void validarPlan(Venta venta, BigDecimal precio) {
        if (venta.getPlazoMeses() == null || !venta.getReestructuraciones().isEmpty()) return;
        if (precio.compareTo(consultas.totalAbonado(venta.getId())) <= 0) return;
        BigDecimal fijo = venta.getEnganche() != null ? venta.getEnganche() : BigDecimal.ZERO;
        for (VentaAportacion a : ventaAportacionRepository.findByVentaIdOrderByAnioAscMesAsc(venta.getId())) {
            fijo = fijo.add(a.getMonto());
        }
        if (fijo.compareTo(precio) >= 0) {
            throw new ValidationException("El enganche y las aportaciones cubren todo el precio nuevo: "
                    + "corrige primero el enganche en «Modificar venta»");
        }
    }
}
