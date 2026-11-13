package com.werealestate.backend.service;

import com.werealestate.backend.dto.ContratoRequest;
import com.werealestate.backend.dto.NumeroContratoRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.MedioFirma;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.VentaLote;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import java.time.LocalDate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** El contrato de una venta: si está firmado (y con ello el estado de sus lotes) y su número de contrato. */
@Service
@Transactional
public class ContratoVentaService {

    private final VentaRepository ventaRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final LoteVentaService loteVentaService;
    private final ComisionService comisionService;
    private final ConsultasVenta consultas;
    private final AutorizacionVentas autorizacion;
    private final VentaDtoArmador dtoArmador;

    public ContratoVentaService(
            VentaRepository ventaRepository,
            VentaLoteRepository ventaLoteRepository,
            LoteVentaService loteVentaService,
            ComisionService comisionService,
            ConsultasVenta consultas,
            AutorizacionVentas autorizacion,
            VentaDtoArmador dtoArmador) {
        this.ventaRepository = ventaRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.loteVentaService = loteVentaService;
        this.comisionService = comisionService;
        this.consultas = consultas;
        this.autorizacion = autorizacion;
        this.dtoArmador = dtoArmador;
    }

    /** Registra o corrige si el contrato de la venta ya está firmado y desde cuándo. Sin contrato
     * firmado, la comisión de una venta nueva se acumula pero no se puede entregar; al firmar queda
     * disponible el sábado siguiente a la firma. Si cambia la firma, cambian también sus lotes (ver
     * {@link #alinearLotesConContrato}); corregir solo la fecha, el medio o el enlace no los mueve. */
    public VentaDto registrarContrato(Long ventaId, ContratoRequest request) {
        autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(ventaId);
        boolean estabaFirmado = venta.isContratoFirmado();
        String driveUrl = validarContrato(request.firmado(), request.fechaFirma(), request.medio(), request.driveUrl());
        venta.registrarContrato(request.firmado(), request.fechaFirma(), request.medio(), driveUrl);
        ventaRepository.save(venta);
        if (estabaFirmado != request.firmado()) {
            alinearLotesConContrato(venta);
        }
        comisionService.sincronizar(venta);
        return dtoArmador.armar(venta);
    }

    /** El estado de los lotes lo manda su venta: con el contrato firmado pasan a Vendido y, si se le quita la firma,
     * regresan a En proceso de firma, cada cambio con su historial. Si un lote quedó en más de una venta (datos de
     * antes de que se impidiera), basta con que una esté firmada para que siga Vendido. */
    private void alinearLotesConContrato(Venta venta) {
        String nota = venta.isContratoFirmado() ? "Contrato firmado" : "Se quitó la firma del contrato";
        for (VentaLote vl : ventaLoteRepository.findByVentaId(venta.getId())) {
            Long loteId = vl.getLote().getId();
            boolean firmada = ventaLoteRepository.findByLoteId(loteId).stream()
                    .anyMatch(otra -> otra.getVenta().isContratoFirmado());
            loteVentaService.ponerEstadoDeVenta(
                    loteId, firmada ? EstadoLote.VENDIDO : EstadoLote.EN_PROCESO_DE_FIRMA, venta.getCliente(), nota);
        }
    }

    /** Con el contrato firmado: fecha (no futura) y medio (físico o DocuSign) obligatorios. El enlace a
     * la carpeta de Drive es opcional (siempre, firmado o no) y debe ser http(s). Devuelve el enlace
     * limpio (o null). */
    static String validarContrato(
            boolean firmado, LocalDate fechaFirma, MedioFirma medio, String driveUrl) {
        String url = driveUrl == null || driveUrl.isBlank() ? null : driveUrl.trim();
        if (url != null && !url.startsWith("https://") && !url.startsWith("http://")) {
            throw new ValidationException("El enlace de Drive debe empezar con https://");
        }
        if (!firmado) return url;
        if (fechaFirma == null) {
            throw new ValidationException("Indica la fecha de firma del contrato");
        }
        if (fechaFirma.isAfter(LocalDate.now())) {
            throw new ValidationException("La fecha de firma no puede ser futura");
        }
        if (medio == null) {
            throw new ValidationException("Indica si el contrato se firmó en físico o en DocuSign");
        }
        return url;
    }

    /** Pone, cambia o quita (vacío) el número de contrato de la venta. Es texto libre pero no puede repetirse en
     * otra venta (sin importar mayúsculas). Lo pueden usar admin y administración, igual que el resto de Ventas. Poner
     * el primero es libre; cambiar o quitar uno que ya existe exige la contraseña de quien lo hace (como eliminar). */
    public VentaDto registrarNumeroContrato(Long ventaId, NumeroContratoRequest request) {
        Usuario actual = autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(ventaId);
        String numero = Textos.opcional(request.numeroContrato());
        if (venta.getNumeroContrato() != null && !venta.getNumeroContrato().equals(numero)) {
            if (request.password() == null || request.password().isBlank()) {
                throw new ValidationException("Escribe tu contraseña para cambiar o quitar el número de contrato");
            }
            autorizacion.exigirContrasena(actual, request.password());
        }
        if (numero != null) {
            ventaRepository.findFirstByNumeroContratoIgnoreCase(numero)
                    .filter(otra -> !otra.getId().equals(ventaId))
                    .ifPresent(otra -> {
                        throw new ConflictException(
                                "El número de contrato " + numero + " ya lo tiene la venta " + otra.getEtiqueta());
                    });
        }
        venta.setNumeroContrato(numero);
        try {
            ventaRepository.saveAndFlush(venta);
        } catch (DataIntegrityViolationException e) {
            // Dos personas guardando el mismo número a la vez: lo frena el índice único.
            throw new ConflictException("Ese número de contrato ya lo tiene otra venta");
        }
        return dtoArmador.armar(venta);
    }

}
