package com.werealestate.backend.dto;

import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.Role;
import java.util.Arrays;
import java.util.List;

/** Un módulo con qué roles pueden tenerlo (permitidoPara) y con cuáles arrancan por defecto
 * (porDefectoPara) — para que el formulario de usuarios no duplique esas reglas. */
public record ModuloCatalogoDto(String codigo, String etiqueta, List<Role> permitidoPara, List<Role> porDefectoPara) {

    public static List<ModuloCatalogoDto> todos() {
        return Arrays.stream(Modulo.values())
                .map(m -> new ModuloCatalogoDto(
                        m.name(),
                        m.etiqueta(),
                        Arrays.stream(Role.values()).filter(r -> ModulosAcceso.maximos(r).contains(m)).toList(),
                        Arrays.stream(Role.values()).filter(r -> ModulosAcceso.porDefecto(r).contains(m)).toList()))
                .toList();
    }
}
