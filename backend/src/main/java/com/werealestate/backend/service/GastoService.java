package com.werealestate.backend.service;

import com.werealestate.backend.dto.GastoDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Gasto;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.OrigenGasto;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.GastoRecurrentePagoRepository;
import com.werealestate.backend.repository.VentaComisionEntregaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Gastos registrados (únicos, pagos de gastos recurrentes y comisiones entregadas)
 * y si cada uno exige subir un comprobante (ticket) al registrarlo.
 *
 * <p>A diferencia del plano de un desarrollo (público, servido como estático bajo /uploads/**,
 * ver DesarrolloService/WebConfig), un ticket es un documento financiero interno: se guarda fuera
 * de esa carpeta pública y solo se entrega por {@link #obtenerTicket}, que exige sesión con el
 * mismo rol que el resto de Gastos.
 */
@Service
@Transactional
public class GastoService {

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF", contenedor de WEBP
    private static final byte[] FIRMA_PDF = {0x25, 0x50, 0x44, 0x46}; // "%PDF"

    private final GastoRepository gastoRepository;
    private final VentaComisionEntregaRepository entregaComisionRepository;
    private final GastoRecurrentePagoRepository pagoRecurrenteRepository;
    private final CurrentUserProvider currentUserProvider;
    private final String uploadsPrivadosDir;

    public GastoService(
            GastoRepository gastoRepository,
            VentaComisionEntregaRepository entregaComisionRepository,
            GastoRecurrentePagoRepository pagoRecurrenteRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.uploads-privados.dir:uploads-privados}") String uploadsPrivadosDir) {
        this.gastoRepository = gastoRepository;
        this.entregaComisionRepository = entregaComisionRepository;
        this.pagoRecurrenteRepository = pagoRecurrenteRepository;
        this.currentUserProvider = currentUserProvider;
        this.uploadsPrivadosDir = uploadsPrivadosDir;
    }

    public List<GastoDto> listar() {
        exigirAdminOLider();
        return gastoRepository.findAllByOrderByFechaDescIdDesc().stream().map(GastoDto::from).toList();
    }

    /** Gasto de una sola vez (papelería, insumos...): concepto, fecha y monto; el comprobante es
     * opcional (se valida el archivo por sus primeros bytes, no por el Content-Type del navegador). */
    public GastoDto crear(String concepto, LocalDate fecha, BigDecimal monto, MultipartFile ticket) {
        Usuario actual = exigirAdminOLider();
        return GastoDto.from(crearGasto(concepto, OrigenGasto.UNICO, fecha, monto, ticket, actual));
    }

    /** Crea y guarda un gasto (y su comprobante, si lo hay); lo usan los gastos únicos y el pago de
     * los gastos recurrentes. */
    public Gasto crearGasto(
            String concepto, OrigenGasto origen, LocalDate fecha, BigDecimal monto, MultipartFile ticket, Usuario actual) {
        if (concepto == null || concepto.isBlank()) {
            throw new ValidationException("Indica qué se compró o pagó");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("El monto debe ser mayor a cero");
        }
        if (fecha == null) {
            throw new ValidationException("Indica la fecha");
        }
        Gasto gasto = gastoRepository.save(new Gasto(concepto.trim(), origen, null, fecha, monto, actual));
        if (ticket != null && !ticket.isEmpty()) {
            gasto.setTicketExtension(guardarTicket(gasto.getId(), ticket));
        }
        return gasto;
    }

    /** Streamea el ticket del gasto ya autenticado (mismo rol que el resto de Gastos); nunca se
     * expone como URL estática (ver la nota de la clase). */
    public ResponseEntity<byte[]> obtenerTicket(Long id) {
        exigirAdminOLider();
        Gasto gasto = obtenerEntidad(id);
        if (gasto.getTicketExtension() == null) {
            throw new ResourceNotFoundException("Este gasto no tiene ticket");
        }

        Path archivo = rutaTicket(id, gasto.getTicketExtension());
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el ticket", e);
        }

        return ResponseEntity.ok().contentType(mediaTypeDe(gasto.getTicketExtension())).body(bytes);
    }

    /** Solo admin, igual que corregir cualquier otro registro financiero ya guardado; borra
     * también el archivo del ticket en disco si tenía uno, para no dejar basura huérfana. */
    public void eliminar(Long id) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede eliminar un gasto");
        }
        Gasto gasto = obtenerEntidad(id);
        if (entregaComisionRepository.existsByGastoId(id)) {
            throw new ConflictException(
                    "Este gasto viene de una entrega de comisión: anula la entrega en Finanzas para eliminarlo");
        }
        if (pagoRecurrenteRepository.existsByGastoId(id)) {
            throw new ConflictException(
                    "Este gasto es el pago de un gasto recurrente: usa \"Deshacer pago\" en Gastos recurrentes");
        }
        eliminarGasto(gasto);
    }

    /** Borra el gasto y el archivo de su ticket, sin revisar a qué está ligado (lo hace quien lo llama). */
    public void eliminarGasto(Gasto gasto) {
        if (gasto.getTicketExtension() != null) {
            try {
                Files.deleteIfExists(rutaTicket(gasto.getId(), gasto.getTicketExtension()));
            } catch (IOException ignored) {
                // No pasa nada si ya no estaba: lo que importa es borrar el registro.
            }
        }
        gastoRepository.delete(gasto);
    }

    private Gasto obtenerEntidad(Long id) {
        return gastoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));
    }

    private Path rutaTicket(Long gastoId, String extension) {
        return Path.of(uploadsPrivadosDir, "gastos", "gasto-" + gastoId + "." + extension);
    }

    private String guardarTicket(Long gastoId, MultipartFile archivo) {
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el ticket", e);
        }

        String extension = detectarExtension(bytes);
        if (extension == null) {
            throw new ValidationException("El ticket debe ser una imagen PNG, JPG, WEBP o un PDF");
        }

        try {
            Path destino = rutaTicket(gastoId, extension);
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el ticket", e);
        }

        return extension;
    }

    private static MediaType mediaTypeDe(String extension) {
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg" -> MediaType.IMAGE_JPEG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    /** null si no reconoce ninguna de las cuatro firmas de archivo (magic bytes) que aceptamos. */
    private static String detectarExtension(byte[] bytes) {
        if (empiezaCon(bytes, FIRMA_PNG)) {
            return "png";
        }
        if (empiezaCon(bytes, FIRMA_JPEG)) {
            return "jpg";
        }
        if (empiezaCon(bytes, FIRMA_RIFF) && bytes.length >= 12 && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return "webp";
        }
        if (empiezaCon(bytes, FIRMA_PDF)) {
            return "pdf";
        }
        return null;
    }

    private static boolean empiezaCon(byte[] bytes, byte[] firma) {
        if (bytes.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (bytes[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }

    private Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && actual.getRol() != Role.LIDER_AREA) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede gestionar gastos");
        }
        return actual;
    }
}
