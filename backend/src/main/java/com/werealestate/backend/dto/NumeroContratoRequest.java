package com.werealestate.backend.dto;

import jakarta.validation.constraints.Size;

/** El número de contrato de una venta (texto libre, hasta 50). Vacío o null = quitarlo. password: la contraseña de
 * quien lo pide; se exige solo para cambiar o quitar un número que la venta ya tiene (capturarlo la primera vez no la
 * pide). Ver VentaService.registrarNumeroContrato. */
public record NumeroContratoRequest(@Size(max = 50) String numeroContrato, String password) {
}
