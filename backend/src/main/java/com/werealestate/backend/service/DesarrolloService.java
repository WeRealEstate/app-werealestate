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
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DesarrolloService {

    /** Únicas extensiones aceptadas para la imagen del plano; el nombre en disco es siempre
     * "desarrollo-{id}.{ext}" (nunca el nombre original) para no depender de entrada del cliente. */
    private static final Map<String, String> EXTENSION_POR_CONTENT_TYPE =
            Map.of("image/png", "png", "image/jpeg", "jpg", "image/webp", "webp");

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
        String extension = EXTENSION_POR_CONTENT_TYPE.get(archivo.getContentType());
        if (extension == null) {
            throw new ValidationException("El plano debe ser una imagen PNG, JPG o WEBP");
        }

        try {
            Path carpeta = Path.of(uploadsDir, "planos");
            Files.createDirectories(carpeta);
            Path destino = carpeta.resolve("desarrollo-" + id + "." + extension);
            archivo.transferTo(destino);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen del plano", e);
        }

        desarrollo.setPlanoUrl("/uploads/planos/desarrollo-" + id + "." + extension + "?v=" + System.currentTimeMillis());
        return DesarrolloDto.from(desarrollo);
    }
}
