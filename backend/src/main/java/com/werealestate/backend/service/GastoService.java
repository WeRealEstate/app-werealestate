package com.werealestate.backend.service;

import com.werealestate.backend.dto.GastoDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Gasto;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoGasto;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.GastoRepository;
import com.werealestate.backend.repository.TipoGastoRepository;
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
 * Gastos registrados mes con mes (ej. comisiones, renta). Ver TipoGasto para el catálogo de tipos
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
    private final TipoGastoRepository tipoGastoRepository;
    private final CurrentUserProvider currentUserProvider;
    private final String uploadsPrivadosDir;

    public GastoService(
            GastoRepository gastoRepository,
            TipoGastoRepository tipoGastoRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.uploads-privados.dir:uploads-privados}") String uploadsPrivadosDir) {
        this.gastoRepository = gastoRepository;
        this.tipoGastoRepository = tipoGastoRepository;
        this.currentUserProvider = currentUserProvider;
        this.uploadsPrivadosDir = uploadsPrivadosDir;
    }

    public List<GastoDto> listar() {
        exigirAdminOLider();
        return gastoRepository.findAllByOrderByFechaDescIdDesc().stream().map(GastoDto::from).toList();
    }

    /** ticket es obligatorio si el tipo de gasto elegido lo requiere (ver
     * TipoGasto.requiereTicket); se valida el archivo por sus primeros bytes, no por el
     * Content-Type que manda el navegador. */
    public GastoDto crear(Long tipoGastoId, LocalDate fecha, BigDecimal monto, MultipartFile ticket) {
        Usuario actual = exigirAdminOLider();
        TipoGasto tipo = tipoGastoRepository
                .findById(tipoGastoId)
                .orElseThrow(() -> new ResourceNotFoundException("Tipo de gasto no encontrado"));

        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("El monto debe ser mayor a cero");
        }
        boolean hayTicket = ticket != null && !ticket.isEmpty();
        if (tipo.isRequiereTicket() && !hayTicket) {
            throw new ValidationException("\"" + tipo.getNombre() + "\" exige subir un ticket/comprobante");
        }

        Gasto gasto = new Gasto(tipo, fecha, monto, actual);
        gasto = gastoRepository.save(gasto);

        if (hayTicket) {
            gasto.setTicketExtension(guardarTicket(gasto.getId(), ticket));
        }

        return GastoDto.from(gasto);
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

        if (gasto.getTicketExtension() != null) {
            try {
                Files.deleteIfExists(rutaTicket(id, gasto.getTicketExtension()));
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
