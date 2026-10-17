package com.werealestate.backend.service;

import com.werealestate.backend.dto.MaterialDtos;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.CategoriaMaterial;
import com.werealestate.backend.model.Material;
import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.ProyectoMaterial;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.MaterialRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Material de venta (copys, flyers, brochures, videos). Administración lo sube en el panel (módulo Material);
 * los asesores lo consultan desde el plano público con su PIN (el controlador público lo exige en cada
 * petición). Los archivos se guardan en la carpeta privada, nunca en la pública: solo se sirven por aquí.
 */
@Service
public class MaterialService {

    static final long MAX_ARCHIVO_BYTES = 25L * 1024 * 1024;
    /** Lado mayor de la miniatura (JPG) que se genera al subir una imagen JPG o PNG. */
    private static final int LADO_MINIATURA = 480;
    private static final int MAX_TEXTO = 5000;

    private final MaterialRepository repository;
    private final CurrentUserProvider currentUserProvider;
    private final String uploadsPrivadosDir;

    public MaterialService(
            MaterialRepository repository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.uploads-privados.dir:uploads-privados}") String uploadsPrivadosDir) {
        this.repository = repository;
        this.currentUserProvider = currentUserProvider;
        this.uploadsPrivadosDir = uploadsPrivadosDir;
    }

    /** Archivo listo para servir: bytes, tipo y el nombre con que se descarga. */
    public record Descarga(byte[] bytes, MediaType tipo, String nombre) {}

    // ---------------------------------------------------------------- panel

    @Transactional(readOnly = true)
    public List<MaterialDtos.Item> listar() {
        exigirAcceso();
        return repository.findAllByOrderByFechaActualizacionDesc().stream().map(MaterialDtos.Item::from).toList();
    }

    @Transactional
    public MaterialDtos.Item crear(
            String titulo, String descripcion, String categoria, String proyecto, String texto, MultipartFile archivo) {
        Usuario actual = exigirAcceso();
        Material m = new Material(actual);
        return guardar(m, titulo, descripcion, categoria, proyecto, texto, archivo, false);
    }

    @Transactional
    public MaterialDtos.Item actualizar(
            Long id, String titulo, String descripcion, String categoria, String proyecto, String texto,
            MultipartFile archivo, boolean quitarArchivo) {
        exigirAcceso();
        return guardar(obtenerEntidad(id), titulo, descripcion, categoria, proyecto, texto, archivo, quitarArchivo);
    }

    @Transactional
    public void eliminar(Long id) {
        exigirAcceso();
        Material m = obtenerEntidad(id);
        borrarArchivo(m);
        repository.delete(m);
    }

    @Transactional(readOnly = true)
    public Descarga descargarDelPanel(Long id) {
        exigirAcceso();
        return leerArchivo(obtenerEntidad(id));
    }

    @Transactional(readOnly = true)
    public Descarga miniaturaDelPanel(Long id) {
        exigirAcceso();
        return leerMiniatura(obtenerEntidad(id));
    }

    // ---------------------------------------------------------------- plano público (el PIN ya se validó)

    @Transactional(readOnly = true)
    public List<MaterialDtos.Item> listarPublico(String proyecto) {
        exigirProyectoPublico(proyecto);
        Set<ProyectoMaterial> aplicables = EnumSet.of(ProyectoMaterial.AMBOS, ProyectoMaterial.valueOf(proyecto.toUpperCase()));
        return repository.findByProyectoInOrderByFechaActualizacionDesc(aplicables).stream()
                .map(MaterialDtos.Item::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Descarga descargarPublico(Long id, String proyecto) {
        exigirProyectoPublico(proyecto);
        Material m = obtenerEntidad(id);
        // Un material de otro desarrollo se trata como inexistente: no se confirma que exista.
        if (!m.getProyecto().aplicaA(proyecto)) throw new ResourceNotFoundException("Material no encontrado");
        return leerArchivo(m);
    }

    @Transactional(readOnly = true)
    public Descarga miniaturaPublico(Long id, String proyecto) {
        exigirProyectoPublico(proyecto);
        Material m = obtenerEntidad(id);
        if (!m.getProyecto().aplicaA(proyecto)) throw new ResourceNotFoundException("Material no encontrado");
        return leerMiniatura(m);
    }

    // ---------------------------------------------------------------- internos

    private MaterialDtos.Item guardar(
            Material m, String titulo, String descripcion, String categoria, String proyecto, String texto,
            MultipartFile archivo, boolean quitarArchivo) {
        String t = titulo == null ? "" : titulo.trim();
        if (t.isEmpty()) throw new ValidationException("El título es obligatorio");
        if (t.length() > 150) throw new ValidationException("El título puede tener hasta 150 caracteres");
        String d = vacioANulo(descripcion);
        if (d != null && d.length() > 1000) throw new ValidationException("La descripción puede tener hasta 1000 caracteres");
        String tx = vacioANulo(texto);
        if (tx != null && tx.length() > MAX_TEXTO) {
            throw new ValidationException("El texto puede tener hasta " + MAX_TEXTO + " caracteres");
        }
        CategoriaMaterial cat = parsear(CategoriaMaterial.class, categoria, "La categoría no es válida");
        ProyectoMaterial proy = parsear(ProyectoMaterial.class, proyecto, "El desarrollo no es válido");

        boolean hayArchivoNuevo = archivo != null && !archivo.isEmpty();
        boolean quedaArchivo = hayArchivoNuevo || (m.tieneArchivo() && !quitarArchivo);
        if (tx == null && !quedaArchivo) throw new ValidationException("Agrega un texto, un archivo o ambos");

        String extension = hayArchivoNuevo ? validarArchivo(archivo) : null;

        m.actualizarDatos(t, d, cat, proy, tx);
        boolean nuevo = m.getId() == null;
        Material guardado = repository.save(m);

        if (hayArchivoNuevo) {
            borrarArchivo(guardado);
            escribirArchivo(guardado, archivo, extension);
        } else if (quitarArchivo && guardado.tieneArchivo()) {
            borrarArchivo(guardado);
            guardado.quitarArchivo();
        }
        return MaterialDtos.Item.from(nuevo ? repository.save(guardado) : guardado);
    }

    private void escribirArchivo(Material m, MultipartFile archivo, String extension) {
        try {
            Path destino = ruta(m.getId(), extension);
            Files.createDirectories(destino.getParent());
            byte[] bytes = archivo.getBytes();
            Files.write(destino, bytes);
            generarMiniatura(m.getId(), extension, bytes);
            m.asignarArchivo(nombreSeguro(archivo.getOriginalFilename(), extension), extension, archivo.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar el archivo", e);
        }
    }

    private void borrarArchivo(Material m) {
        if (!m.tieneArchivo()) return;
        try {
            Files.deleteIfExists(ruta(m.getId(), m.getArchivoExtension()));
            Files.deleteIfExists(rutaMiniatura(m.getId()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el archivo", e);
        }
    }

    private Descarga leerArchivo(Material m) {
        if (!m.tieneArchivo()) throw new ResourceNotFoundException("Este material no tiene archivo");
        try {
            byte[] bytes = Files.readAllBytes(ruta(m.getId(), m.getArchivoExtension()));
            return new Descarga(bytes, mediaTypeDe(m.getArchivoExtension()), m.getArchivoNombre());
        } catch (IOException e) {
            throw new ResourceNotFoundException("Archivo no encontrado");
        }
    }

    /** Miniatura para las tarjetas: así no se baja la imagen completa (hasta 25 MB) solo para ver una vista previa.
     * Solo imágenes: JPG/PNG se reducen aquí; WEBP (Java no lo lee de fábrica) y cualquier imagen que no se pueda
     * reducir sirven el original; PDF y video no tienen miniatura. */
    private Descarga leerMiniatura(Material m) {
        if (!m.tieneArchivo()) throw new ResourceNotFoundException("Este material no tiene archivo");
        String ext = m.getArchivoExtension();
        if (!ext.equals("jpg") && !ext.equals("png") && !ext.equals("webp")) {
            throw new ResourceNotFoundException("Este material no tiene miniatura");
        }
        Path mini = rutaMiniatura(m.getId());
        if (!Files.exists(mini)) return leerArchivo(m);
        try {
            return new Descarga(Files.readAllBytes(mini), MediaType.IMAGE_JPEG, "miniatura.jpg");
        } catch (IOException e) {
            throw new ResourceNotFoundException("Archivo no encontrado");
        }
    }

    private void generarMiniatura(Long id, String extension, byte[] bytes) {
        if (!extension.equals("jpg") && !extension.equals("png")) return;
        try {
            BufferedImage reducida = reducir(bytes);
            if (reducida == null) return;
            // JPG no admite transparencia: se pinta sobre blanco.
            BufferedImage plana = new BufferedImage(reducida.getWidth(), reducida.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = plana.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, plana.getWidth(), plana.getHeight());
            g.drawImage(reducida, 0, 0, null);
            g.dispose();
            Path destino = rutaMiniatura(id);
            Files.createDirectories(destino.getParent());
            ImageIO.write(plana, "jpg", destino.toFile());
        } catch (IOException | RuntimeException e) {
            // Sin miniatura no pasa nada: la tarjeta sirve el original.
        }
    }

    /** Lee la imagen submuestreada (no carga los megapíxeles completos en memoria) y la ajusta a LADO_MINIATURA. */
    static BufferedImage reducir(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(in);
            if (!lectores.hasNext()) return null;
            ImageReader lector = lectores.next();
            try {
                lector.setInput(in);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || (long) ancho * alto > 100_000_000L) return null;
                int salto = Math.max(1, Math.max(ancho, alto) / (LADO_MINIATURA * 2));
                ImageReadParam param = lector.getDefaultReadParam();
                param.setSourceSubsampling(salto, salto, 0, 0);
                BufferedImage leida = lector.read(0, param);
                double escala = Math.min(1.0, (double) LADO_MINIATURA / Math.max(leida.getWidth(), leida.getHeight()));
                int w = Math.max(1, (int) Math.round(leida.getWidth() * escala));
                int h = Math.max(1, (int) Math.round(leida.getHeight() * escala));
                BufferedImage salida = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = salida.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(leida, 0, 0, w, h, null);
                g.dispose();
                return salida;
            } finally {
                lector.dispose();
            }
        }
    }

    Path rutaMiniatura(Long id) {
        return Path.of(uploadsPrivadosDir, "material", "material-" + id + "-mini.jpg");
    }

    Path ruta(Long id, String extension) {
        return Path.of(uploadsPrivadosDir, "material", "material-" + id + "." + extension);
    }

    private Material obtenerEntidad(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Material no encontrado"));
    }

    private Usuario exigirAcceso() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && !ModulosAcceso.efectivos(actual).contains(Modulo.MATERIAL)) {
            throw new ForbiddenOperationException("No tienes acceso a Material");
        }
        return actual;
    }

    private static void exigirProyectoPublico(String proyecto) {
        if (!"samai".equals(proyecto) && !"nanuu".equals(proyecto)) throw new ValidationException("Desarrollo no válido");
    }

    private static <E extends Enum<E>> E parsear(Class<E> tipo, String valor, String error) {
        try {
            return Enum.valueOf(tipo, valor == null ? "" : valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException(error);
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Nombre con el que se descarga: sin rutas ni caracteres raros, y siempre con la extensión real. */
    static String nombreSeguro(String original, String extension) {
        String base = original == null ? "" : original;
        base = base.substring(Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\')) + 1);
        int punto = base.lastIndexOf('.');
        if (punto > 0) base = base.substring(0, punto);
        base = base.replaceAll("[^\\p{L}\\p{N} ._-]", "").strip();
        if (base.isEmpty()) base = "material";
        if (base.length() > 150) base = base.substring(0, 150);
        return base + "." + extension;
    }

    /** JPG, PNG, WEBP, PDF o MP4 de hasta 25 MB; se confía en la firma del archivo, no en su nombre. */
    static String validarArchivo(MultipartFile archivo) {
        if (archivo.getSize() > MAX_ARCHIVO_BYTES) throw new ValidationException("El archivo puede pesar hasta 25 MB");
        byte[] b;
        try {
            b = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo", e);
        }
        return extensionPorFirma(b);
    }

    static String extensionPorFirma(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) return "jpg";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
                && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "pdf";
        if (b.length >= 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return "mp4";
        throw new ValidationException("El archivo debe ser imagen (JPG, PNG, WEBP), PDF o video MP4");
    }

    private static MediaType mediaTypeDe(String extension) {
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg" -> MediaType.IMAGE_JPEG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "mp4" -> MediaType.parseMediaType("video/mp4");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }
}
