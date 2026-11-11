package com.werealestate.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Cambia el plan de pagos de una venta (ver VentaService.reestructurar). El plan nuevo reparte el saldo de hoy en
 * {@code plazoMeses} meses a partir del mes en curso ({@code primeraEnMesActual}) o del siguiente; los meses con una
 * aportación (anualidad) pagan esa aportación y el resto se reparte en mensualidades iguales que calcula el servidor.
 * Exige la contraseña de quien lo hace y un motivo.
 */
public record VentaReestructurarRequest(
        @NotBlank String password,
        @NotBlank @Size(max = 500) String motivo,
        @NotNull @Min(1) @Max(600) Integer plazoMeses,
        @NotNull @Min(1) @Max(31) Integer diaPago,
        boolean primeraEnMesActual,
        @Valid List<Aportacion> aportaciones) {

    public record Aportacion(
            @NotNull @Min(2000) @Max(2200) Integer anio,
            @NotNull @Min(1) @Max(12) Integer mes,
            @NotNull @DecimalMin(value = "0.01") BigDecimal monto) {
    }
}
