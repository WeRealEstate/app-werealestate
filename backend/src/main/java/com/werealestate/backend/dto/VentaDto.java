package com.werealestate.backend.dto;

import com.werealestate.backend.model.Venta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record VentaDto(
        Long id,
        // Número de la venta dentro de su desarrollo (1, 2, 3…, sin saltos) y cómo se ve: "S-12" (SAMAI) o "N-5" (Aldea
        // Nanuu). desarrolloId = el desarrollo con el que cuenta (el de su primer lote). Ver EtiquetaVenta.
        Long numero,
        String etiqueta,
        Long desarrolloId,
        List<VentaLoteDto> lotes,
        // Aportaciones programadas de la venta (ver VentaAportacion); vacía si no las tiene.
        List<VentaAportacionDto> aportaciones,
        String cliente,
        // El cliente ligado (null solo en una venta anterior que aún no se migra).
        Long clienteId,
        // Copropietarios además del cliente principal (máximo 4).
        List<ClienteResumenDto> copropietarios,
        VentaAsesorDto asesor,
        String formaPago,
        LocalDate fechaVenta,
        BigDecimal mensualidad,
        Integer plazoMeses,
        String engancheLabel,
        BigDecimal enganche,
        int diaPago,
        boolean primeraMensualidadMesVenta,
        String notas,
        // Contrato firmado (y desde cuándo): sin él no se puede entregar la comisión (regla nueva).
        boolean contratoFirmado,
        LocalDate contratoFechaFirma,
        // FISICO o DOCUSIGN (solo con el contrato firmado) y enlace opcional a la carpeta de Drive.
        com.werealestate.backend.model.MedioFirma contratoMedio,
        String contratoDriveUrl,
        // Número de contrato (opcional, único entre ventas).
        String numeroContrato,
        // Efectivo o transferencia (null = sin definir).
        com.werealestate.backend.model.ModalidadPago modalidadPago,
        LocalDateTime fechaCreacion,
        // Se calculan a partir de sus lotes y sus pagos (ver VentaService), nunca se guardan directo.
        BigDecimal precioVenta,
        BigDecimal totalAbonado,
        BigDecimal saldoPendiente,
        // Venta que nació de un registro de venta: su fecha es la del envío y administración la confirma.
        boolean fechaProvisional,
        // Dinero apartado del lote que aún no se aplica a la venta (null = nada pendiente).
        BigDecimal depositoPendiente) {

    public static VentaDto from(
            Venta venta,
            List<VentaLoteDto> lotes,
            List<VentaAportacionDto> aportaciones,
            List<ClienteResumenDto> copropietarios,
            BigDecimal totalAbonado) {
        BigDecimal precioVenta = lotes.stream().map(VentaLoteDto::precio).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VentaDto(
                venta.getId(),
                venta.getNumeroDesarrollo() != null ? venta.getNumeroDesarrollo() : venta.getNumeroGlobal(),
                venta.getEtiqueta(),
                venta.getDesarrollo() != null ? venta.getDesarrollo().getId() : null,
                lotes,
                aportaciones,
                venta.getCliente(),
                venta.getClienteRef() != null ? venta.getClienteRef().getId() : null,
                copropietarios,
                VentaAsesorDto.from(venta),
                venta.getFormaPago(),
                venta.getFechaVenta(),
                venta.getMensualidad(),
                venta.getPlazoMeses(),
                venta.getEngancheLabel(),
                venta.getEnganche(),
                venta.getDiaPago(),
                venta.isPrimeraMensualidadMesVenta(),
                venta.getNotas(),
                venta.isContratoFirmado(),
                venta.getContratoFechaFirma(),
                venta.getContratoMedio(),
                venta.getContratoDriveUrl(),
                venta.getNumeroContrato(),
                venta.getModalidadPago(),
                venta.getFechaCreacion(),
                precioVenta,
                totalAbonado,
                precioVenta.subtract(totalAbonado),
                venta.isFechaProvisional(),
                venta.getDepositoPendiente());
    }
}
