package com.werealestate.backend.dto;

import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Comercializadora;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de Comercializadoras: el panel (admin y administración) y el alta pública del equipo. */
public final class ComercializadoraDtos {

    private ComercializadoraDtos() {}

    // ------------------------------------------------------------------ panel

    public record Item(
            Long id, String nombre, Long liderId, String liderNombre, String notas, boolean activa, long integrantes,
            String token, LocalDateTime fechaCreacion) {

        public static Item from(Comercializadora c, long integrantes) {
            return new Item(
                    c.getId(), c.getNombre(), c.getLider().getId(), c.getLider().getNombre(), c.getNotas(), c.isActiva(),
                    integrantes, c.getToken(), c.getFechaCreacion());
        }
    }

    public record Integrante(Long id, String nombre, String celular, String correo, boolean activo, String pin,
            LocalDateTime fechaAlta) {

        public static Integrante from(AsesorExterno a) {
            return new Integrante(
                    a.getId(), a.getNombre(), a.getCelular(), a.getCorreo(), a.isActivo(), a.getPin(), a.getFechaCreacion());
        }
    }

    public record CrearRequest(
            @NotBlank @Size(max = 200) String nombre, @NotNull Long liderId, @Size(max = 1000) String notas) {}

    public record ActualizarRequest(
            @NotBlank @Size(max = 200) String nombre, @NotNull Long liderId, @Size(max = 1000) String notas,
            boolean activa) {}

    /** pin: null = no cambia; vacío = generar uno nuevo al azar; 4 letras o números = ese PIN (único). */
    public record ActualizarIntegranteRequest(
            @NotBlank @Size(max = 200) String nombre, @NotBlank @Size(max = 20) String celular,
            @Size(max = 150) String correo, @Size(max = 4) String pin) {}

    /** ELIMINADO: salió del sistema. DESACTIVADO: tiene historial (ventas, comisiones…) y solo se desactivó. */
    public record QuitarIntegranteResultado(String resultado, String nombre) {}

    // ------------------------------------------------------------------ público

    /** Lo que ve quien abre el enlace: a qué comercializadora se va a unir. */
    public record PublicoInfo(String comercializadora, String lider) {}

    /** pin: el que elige el integrante (4 letras o números); vacío o null = se le genera uno al azar. */
    public record UnirseRequest(
            @NotBlank @Size(max = 200) String nombre, @NotBlank @Size(max = 20) String celular,
            @Size(max = 150) String correo, @Size(max = 4) String pin) {}

    /** El PIN se muestra una sola vez, en la pantalla de bienvenida. */
    public record UnirseResultado(String nombre, String pin, String comercializadora, String lider) {}
}
