package com.werealestate.backend.dto;

import tools.jackson.databind.JsonNode;
import com.werealestate.backend.model.DestinoPregunta;
import com.werealestate.backend.model.EstadoWeForm;
import com.werealestate.backend.model.TipoPreguntaForm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** DTOs de We Forms: el administrador (constructor y respuestas) y el link público de los asesores. */
public final class WeFormDtos {

    private WeFormDtos() {}

    // ------------------------------------------------------------------ panel

    public record Lista(
            Long id, String nombre, String slug, Long desarrolloId, String desarrolloNombre, EstadoWeForm estado,
            long totalRespuestas, LocalDateTime fechaCreacion) {
    }

    public record Pregunta(
            Long id, int orden, TipoPreguntaForm tipo, String titulo, String descripcion, boolean obligatoria,
            List<String> opciones, DestinoPregunta destino, boolean visible,
            // Predefinida: alimenta al cliente o a la venta; no se borra.
            boolean fija,
            // Siempre visible y obligatoria (no se puede ocultar ni volver opcional).
            boolean siempreObligatoria,
            // El admin puede ocultarla o cambiar si es obligatoria.
            boolean opcionalEditable) {
    }

    public record Detalle(
            Long id, String nombre, String descripcion, String slug, Long desarrolloId, String desarrolloNombre,
            String proyecto, EstadoWeForm estado, LocalDateTime fechaCreacion, List<Pregunta> preguntas) {
    }

    public record CrearRequest(
            @NotBlank @Size(max = 150) String nombre,
            @Size(max = 1000) String descripcion,
            @NotNull Long desarrolloId) {
    }

    /** Copia de un formulario para otro desarrollo (o el mismo): nombre, descripción y desarrollo de la copia. */
    public record DuplicarRequest(
            @NotBlank @Size(max = 150) String nombre,
            @Size(max = 1000) String descripcion,
            @NotNull Long desarrolloId) {
    }

    public record ActualizarRequest(
            @NotBlank @Size(max = 150) String nombre,
            @Size(max = 1000) String descripcion,
            @NotNull EstadoWeForm estado) {
    }

    /** Pregunta propia (sin destino): tipo al crearla; título, descripción, opciones, obligatoria y visible al editarla. */
    public record PreguntaRequest(
            TipoPreguntaForm tipo,
            @NotBlank @Size(max = 300) String titulo,
            @Size(max = 500) String descripcion,
            boolean obligatoria,
            List<@Size(max = 200) String> opciones,
            Boolean visible) {
    }

    public record OrdenRequest(@NotNull List<Long> ids) {
    }

    public record ItemRespuesta(Long id, String pregunta, TipoPreguntaForm tipo, String valor, boolean tieneArchivo) {
    }

    public record Respuesta(
            Long id, LocalDateTime fechaEnvio, String asesorNombre, Long clienteId, String clienteNombre,
            boolean clienteNuevo, Long ventaId, Long ventaNumero, List<ItemRespuesta> items) {
    }

    // ------------------------------------------------------------------ estadísticas

    public record Conteo(String etiqueta, long cantidad) {
    }

    /** Resumen de una pregunta: conteo por opción (preguntas de elegir), valores sueltos (texto) o
     * mínimo/máximo/promedio (números). */
    public record ResumenPregunta(
            Long preguntaId, String titulo, TipoPreguntaForm tipo, long respondidas, List<Conteo> opciones,
            List<String> textos, java.math.BigDecimal minimo, java.math.BigDecimal maximo,
            java.math.BigDecimal promedio) {
    }

    public record Estadisticas(
            long total, long clientesNuevos, long clientesExistentes, List<Conteo> porDia, List<Conteo> porAsesor,
            List<ResumenPregunta> preguntas) {
    }

    /** Respuestas por desarrollo y por asesor, de todos los formularios (resumen de la lista). */
    public record ResumenGeneral(long total, List<Conteo> porDesarrollo, List<Conteo> porAsesor) {
    }

    // ------------------------------------------------------------------ link público (con PIN)

    public record PublicoLista(String slug, String nombre, String descripcion) {
    }

    public record LotePublico(
            Long id, String manzana, String numeroLote, java.math.BigDecimal superficie, java.math.BigDecimal precio,
            String estado) {
    }

    public record PreguntaPublica(
            Long id, TipoPreguntaForm tipo, String titulo, String descripcion, boolean obligatoria,
            List<String> opciones, DestinoPregunta destino) {
    }

    public record Publico(
            String slug, String nombre, String descripcion, String desarrolloNombre, String asesorNombre,
            List<PreguntaPublica> preguntas, List<LotePublico> lotes) {
    }

    /** Lo que contestó el asesor, una entrada por pregunta (el valor depende del tipo: texto, número,
     * lista de opciones, lista de ids de lote o lista de aportaciones). Los archivos van aparte. */
    public record EnvioRequest(@NotNull List<RespuestaEnvio> respuestas) {
    }

    public record RespuestaEnvio(@NotNull Long preguntaId, JsonNode valor) {
    }

    public record EnvioResultado(Long respuestaId, Long ventaNumero, String clienteNombre, boolean clienteNuevo) {
    }
}
