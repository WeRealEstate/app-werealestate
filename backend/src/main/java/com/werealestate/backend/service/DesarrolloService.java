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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
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
    private static final byte[] FIRMA_PDF = {0x25, 0x50, 0x44, 0x46}; // "%PDF"

    /** Suficiente para que un plano vectorial (AutoCAD, Illustrator, etc.) se vea nítido incluso
     * haciendo zoom en el canvas interactivo — más alto que la resolución típica de un plano
     * exportado apurado como JPG/PNG, que es la causa real del pixeleo. */
    private static final float DPI_RASTERIZADO_PDF = 900f;

    /** Tope de píxeles totales del raster de salida: a 900 DPI, una hoja tamaño carta ya da ~76
     * millones de píxeles; un plano en una hoja más grande (formato arquitectónico, p. ej. A1/A0)
     * sin este tope podría pedir un BufferedImage de varios GB y tardar minutos o agotar la memoria
     * del backend a mitad de la subida. Si se excede, se baja el DPI efectivo (conservando la
     * proporción) para quedar dentro del tope — un plano tamaño carta o tabloide sigue rasterizando
     * a los 900 DPI completos. */
    private static final double MAX_PIXELES_RASTERIZADO = 150_000_000d;

    /** El plano rasterizado (fondo tipo foto/satélite con mucha textura, no colores planos) casi
     * no se beneficia de la compresión sin pérdida de PNG: un plano real de ~150 millones de
     * píxeles pesaba ~120MB en PNG. Se guarda como JPEG en su lugar — probado a esta calidad contra
     * el plano real sin ninguna diferencia visible, ni siquiera acercando el texto más chico, y baja
     * el archivo a ~25% de su peso en PNG. */
    private static final float CALIDAD_JPEG_RASTERIZADO = 0.9f;

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

        // Un PDF no se puede pintar directo en el canvas interactivo del frontend: se rasteriza
        // su primera página (se asume un plano de una sola página) a JPEG antes de seguir — de ahí
        // en adelante se guarda y se sirve exactamente igual que cualquier otro plano subido en JPG.
        if (empiezaCon(bytes, FIRMA_PDF)) {
            bytes = rasterizarPrimeraPaginaPdf(bytes);
        }

        // Se valida por los primeros bytes del archivo, no por el Content-Type que manda el
        // navegador (que puede venir vacío o genérico según cómo se haya creado/exportado la
        // imagen, aunque sí sea un PNG/JPG/WEBP válido).
        String extension = detectarExtension(bytes);
        if (extension == null) {
            throw new ValidationException("El plano debe ser una imagen PNG, JPG, WEBP o un PDF de una sola página");
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

    private static byte[] rasterizarPrimeraPaginaPdf(byte[] pdfBytes) {
        try (PDDocument documento = Loader.loadPDF(pdfBytes)) {
            if (documento.getNumberOfPages() == 0) {
                throw new ValidationException("El PDF del plano no tiene páginas");
            }
            PDFRenderer renderer = new PDFRenderer(documento);
            BufferedImage imagen = renderer.renderImageWithDPI(0, dpiSeguroPara(documento.getPage(0).getMediaBox()));

            return codificarJpeg(sinCanalAlfa(imagen), CALIDAD_JPEG_RASTERIZADO);
        } catch (IOException e) {
            throw new ValidationException("No se pudo leer el PDF del plano");
        }
    }

    /** JPEG no soporta transparencia; PDFRenderer entrega la página ya rasterizada con canal alfa
     * (siempre opaco en la práctica, una página de PDF no tiene fondo transparente), así que se
     * aplana sobre blanco para evitar que el escritor de JPEG falle o distorsione los colores. */
    private static BufferedImage sinCanalAlfa(BufferedImage imagen) {
        BufferedImage opaca = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = opaca.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
        g.drawImage(imagen, 0, 0, null);
        g.dispose();
        return opaca;
    }

    private static byte[] codificarJpeg(BufferedImage imagen, float calidad) throws IOException {
        Iterator<ImageWriter> escritores = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter escritor = escritores.next();
        ImageWriteParam parametros = escritor.getDefaultWriteParam();
        parametros.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parametros.setCompressionQuality(calidad);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ImageOutputStream flujo = ImageIO.createImageOutputStream(salida)) {
            escritor.setOutput(flujo);
            escritor.write(null, new IIOImage(imagen, null, null), parametros);
        } finally {
            escritor.dispose();
        }
        return salida.toByteArray();
    }

    /** DPI_RASTERIZADO_PDF si el tamaño de página del PDF (en puntos, 72 por pulgada) cabe dentro
     * de MAX_PIXELES_RASTERIZADO a ese DPI; si no, el DPI más alto que sí quepa, conservando la
     * proporción de la hoja. */
    private static float dpiSeguroPara(PDRectangle pagina) {
        double anchoPulgadas = pagina.getWidth() / 72d;
        double altoPulgadas = pagina.getHeight() / 72d;
        double pixelesAlDpiPedido = (anchoPulgadas * DPI_RASTERIZADO_PDF) * (altoPulgadas * DPI_RASTERIZADO_PDF);
        if (pixelesAlDpiPedido <= MAX_PIXELES_RASTERIZADO) {
            return DPI_RASTERIZADO_PDF;
        }
        return (float) (DPI_RASTERIZADO_PDF * Math.sqrt(MAX_PIXELES_RASTERIZADO / pixelesAlDpiPedido));
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
