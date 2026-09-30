package com.werealestate.backend.dto;

import com.werealestate.backend.model.TipoGasto;

public record TipoGastoDto(Long id, String nombre, boolean requiereTicket, boolean activo) {

    public static TipoGastoDto from(TipoGasto tipo) {
        return new TipoGastoDto(tipo.getId(), tipo.getNombre(), tipo.isRequiereTicket(), tipo.isActivo());
    }
}
