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
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class DesarrolloService {

    private static final Logger log = LoggerFactory.getLogger(DesarrolloService.class);

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
     * píxeles pesaba ~120MB en PNG. Se guarda como JPEG a calidad 0.82 — mantiene intacta la
     * resolución de 150 MP y reduce ~28% adicional el peso respecto a 0.90 sin diferencia visual. */
    private static final float CALIDAD_JPEG_RASTERIZADO = 0.82f;

    /** Vista rápida (preview) para carga progresiva en 2 pasos: conserva exactamente la misma proporción del plano
     * (~450 KB en vez de ~19–26 MB) para que el visor y los polígonos aparezcan en <0.5 s mientras llega el HD. */
    private static final int LADO_PREVIEW = 2400;
    private static final float CALIDAD_JPEG_PREVIEW = 0.78f;
    private static final long UMBRAL_RECOMPRIMIR_HD_BYTES = 22L * 1024 * 1024;
    private static final Pattern PATRON_PLANO_BASE = Pattern.compile("^desarrollo-(\\d+)\\.(jpg|jpeg|png|webp)$");

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
        byte[] bytesPreview = null;
        if (empiezaCon(bytes, FIRMA_PDF)) {
            RasterizadoPdf rasterizado = rasterizarPrimeraPaginaPdf(bytes);
            bytes = rasterizado.hd();
            bytesPreview = rasterizado.preview();
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

            if (bytesPreview == null) {
                bytesPreview = generarPreviewDesdeArchivo(destino);
            }
            if (bytesPreview != null) {
                Files.write(carpeta.resolve("desarrollo-" + id + "-preview.jpg"), bytesPreview);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen del plano", e);
        }

        desarrollo.setPlanoUrl("/uploads/planos/desarrollo-" + id + "." + extension + "?v=" + System.currentTimeMillis());
        return DesarrolloDto.from(desarrollo);
    }

    /** Al arrancar el servidor, asegura en segundo plano que todos los planos ya subidos tengan su vista rápida
     * (`desarrollo-{id}-preview.jpg`) y recomprime a calidad 0.82 cualquier plano HD que supere 22 MB (conservando su
     * resolución exacta de 150 MP) sin bloquear el arranque ni obligar a volver a subir el PDF. */
    @EventListener(ApplicationReadyEvent.class)
    public void iniciarOptimizacionPlanosEnSegundoPlano() {
        Thread.ofVirtual().name("planos-preview-init").start(this::asegurarPreviewsYOptimizarPlanosExistentes);
    }

    void asegurarPreviewsYOptimizarPlanosExistentes() {
        Path carpeta = Path.of(uploadsDir, "planos");
        if (!Files.isDirectory(carpeta)) return;
        try (Stream<Path> archivos = Files.list(carpeta)) {
            for (Path archivo : archivos.toList()) {
                String nombre = archivo.getFileName().toString();
                Matcher m = PATRON_PLANO_BASE.matcher(nombre);
                if (!m.matches()) continue;
                String id = m.group(1);
                String ext = m.group(2).toLowerCase();
                Path preview = carpeta.resolve("desarrollo-" + id + "-preview.jpg");
                try {
                    boolean faltaPreview = !Files.exists(preview)
                            || Files.getLastModifiedTime(preview).compareTo(Files.getLastModifiedTime(archivo)) < 0;
                    if (faltaPreview) {
                        byte[] bytesPreview = generarPreviewDesdeArchivo(archivo);
                        if (bytesPreview != null) {
                            Path tmp = carpeta.resolve("desarrollo-" + id + "-preview.jpg.tmp");
                            Files.write(tmp, bytesPreview);
                            Files.move(tmp, preview, StandardCopyOption.REPLACE_EXISTING);
                            log.info("Vista rápida generada para {} ({} KB)", nombre, bytesPreview.length / 1024);
                        }
                    }
                    if (("jpg".equals(ext) || "jpeg".equals(ext)) && Files.size(archivo) > UMBRAL_RECOMPRIMIR_HD_BYTES) {
                        long antes = Files.size(archivo);
                        BufferedImage img = ImageIO.read(archivo.toFile());
                        if (img != null) {
                            byte[] recomprimido = codificarJpeg(img, CALIDAD_JPEG_RASTERIZADO);
                            if (recomprimido.length < antes) {
                                Path tmp = carpeta.resolve(nombre + ".tmp");
                                Files.write(tmp, recomprimido);
                                Files.move(tmp, archivo, StandardCopyOption.REPLACE_EXISTING);
                                // Actualiza la fecha de la vista rápida para que siga siendo igual o más reciente que el HD.
                                if (Files.exists(preview)) {
                                    Files.setLastModifiedTime(preview, Files.getLastModifiedTime(archivo));
                                }
                                log.info(
                                        "Plano HD optimizado sin perder resolución {}: {} MB -> {} MB",
                                        nombre,
                                        String.format("%.2f", antes / 1048576.0),
                                        String.format("%.2f", recomprimido.length / 1048576.0));
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("No se pudo optimizar el plano existente {}: {}", nombre, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo inspeccionar la carpeta de planos: {}", e.getMessage());
        }
    }

    private record RasterizadoPdf(byte[] hd, byte[] preview) {}

    /** Rasteriza directamente en RGB opaco (ImageType.RGB pinta sobre blanco dentro de PDFBox) para no duplicar un
     * segundo BufferedImage de ~600 MB en memoria al quitar el canal alfa antes de comprimir a JPEG, y aprovecha esa
     * misma imagen en memoria para sacar la vista rápida (`-preview.jpg`) sin tener que volver a decodificar el JPEG. */
    private static RasterizadoPdf rasterizarPrimeraPaginaPdf(byte[] pdfBytes) {
        try (PDDocument documento = Loader.loadPDF(pdfBytes)) {
            if (documento.getNumberOfPages() == 0) {
                throw new ValidationException("El PDF del plano no tiene páginas");
            }
            PDFRenderer renderer = new PDFRenderer(documento);
            BufferedImage imagen = renderer.renderImageWithDPI(
                    0, dpiSeguroPara(documento.getPage(0).getMediaBox()), ImageType.RGB);

            byte[] hd = codificarJpeg(imagen, CALIDAD_JPEG_RASTERIZADO);
            byte[] preview = codificarJpeg(escalarAPreview(imagen), CALIDAD_JPEG_PREVIEW);
            return new RasterizadoPdf(hd, preview);
        } catch (IOException e) {
            throw new ValidationException("No se pudo leer el PDF del plano");
        }
    }

    /** Genera la vista rápida desde un archivo de imagen en disco usando subsampling del lector para no cargar los
     * 150 MP completos en memoria solo para sacar una vista de 2400 px. */
    private static byte[] generarPreviewDesdeArchivo(Path archivo) {
        try (ImageInputStream in = ImageIO.createImageInputStream(archivo.toFile())) {
            if (in == null) return null;
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(in);
            if (!lectores.hasNext()) return null;
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || (long) ancho * alto > 250_000_000L) return null;
                int salto = Math.max(1, Math.max(ancho, alto) / (LADO_PREVIEW * 2));
                ImageReadParam param = lector.getDefaultReadParam();
                param.setSourceSubsampling(salto, salto, 0, 0);
                BufferedImage leida = lector.read(0, param);
                if (leida == null) return null;
                return codificarJpeg(escalarAPreview(leida, ancho, alto), CALIDAD_JPEG_PREVIEW);
            } finally {
                lector.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage escalarAPreview(BufferedImage origen) {
        return escalarAPreview(origen, origen.getWidth(), origen.getHeight());
    }

    private static BufferedImage escalarAPreview(BufferedImage origen, int anchoOriginal, int altoOriginal) {
        double escala = Math.min(1.0, (double) LADO_PREVIEW / Math.max(anchoOriginal, altoOriginal));
        int w = Math.max(1, (int) Math.round(anchoOriginal * escala));
        int h = Math.max(1, (int) Math.round(altoOriginal * escala));
        BufferedImage salida = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = salida.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(origen, 0, 0, w, h, null);
        g.dispose();
        return salida;
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
