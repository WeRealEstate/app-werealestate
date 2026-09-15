package com.werealestate.backend.service;

import com.werealestate.backend.dto.DesarrolloDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DesarrolloService {

    private static final byte[] FIRMA_PNG = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] FIRMA_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] FIRMA_RIFF = {0x52, 0x49, 0x46, 0x46}; // "RIFF", contenedor de WEBP

    private final DesarrolloRepository desarrolloRepository;
    private final CurrentUserProvider currentUserProvider;
    private final String uploadsDir;

    public DesarrolloService(
            DesarrolloRepository desarrolloRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.desarrolloRepository = desarrolloRepository;
        this.currentUserProvider = currentUserProvider;
        this.uploadsDir = uploadsDir;
    }

    /** Sube (o reemplaza) la imagen del plano de un desarrollo; exclusivo de admin, igual que el
     * resto de la gestión de lotes. La URL guardada lleva un query de caché-bust con la hora de
     * subida para que el navegador no siga mostrando la imagen anterior tras reemplazarla. */
    public DesarrolloDto actualizarPlano(Long id, MultipartFile archivo) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo un administrador puede subir el plano de un desarrollo");
        }
        Desarrollo desarrollo = desarrolloRepository
                .findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));

        if (archivo == null || archivo.isEmpty()) {
            throw new ValidationException("El archivo del plano es obligatorio");
        }

        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen del plano", e);
        }

        // Se valida por los primeros bytes del archivo, no por el Content-Type que manda el
        // navegador (que puede venir vacío o genérico según cómo se haya creado/exportado la
        // imagen, aunque sí sea un PNG/JPG/WEBP válido).
        String extension = detectarExtension(bytes);
        if (extension == null) {
            throw new ValidationException("El plano debe ser una imagen PNG, JPG o WEBP");
        }

        try {
            Path carpeta = Path.of(uploadsDir, "planos");
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve("desarrollo-" + id + "." + extension);
            Files.write(destino, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen del plano", e);
        }

        desarrollo.setPlanoUrl("/uploads/planos/desarrollo-" + id + "." + extension + "?v=" + System.currentTimeMillis());
        return DesarrolloDto.from(desarrollo);
    }

    /** null si no reconoce ninguna de las tres firmas de archivo (magic bytes) que aceptamos. */
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
}
