package com.werealestate.backend.dto;

import java.math.BigDecimal;
import java.util.List;

/** Una fila de la lista de clientes (y del buscador de la venta). */
public record ClienteListaDto(
        Long id,
        // El consecutivo de siempre: ya no se muestra (queda de respaldo); lo que se muestra es "numeros".
        Long numero,
        // Su número en cada desarrollo donde compró (S-40, N-12), SAMAI primero; vacío si aún no compra.
        List<NumeroClienteDto> numeros,
        String nombreCompleto,
        String telefono,
        String correo,
        boolean activo,
        boolean datosIncompletos,
        int compras,
        List<String> desarrollos,
        BigDecimal saldoPendiente,
        // Pagos atrasados de todas sus compras (también donde es copropietario): monto, cuántos pagos y cuántos de
        // ellos mensualidades (ver service/CalendarioPagos); 0 si va al corriente.
        BigDecimal atrasoMonto,
        int pagosAtrasados,
        int mensualidadesAtrasadas) {
}
