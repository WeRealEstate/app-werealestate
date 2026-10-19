package com.werealestate.backend.service;

import com.werealestate.backend.dto.MaterialDtos;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.CategoriaMaterial;
import com.werealestate.backend.model.Material;
import com.werealestate.backend.model.MaterialImagen;
import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.ProyectoMaterial;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.MaterialImagenRepository;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
 * Material de venta (copys, flyers, brochures, videos y galerías de imágenes). Administración lo sube en el panel
 * (módulo Material); los asesores lo consultan desde el plano público con su PIN (el controlador público lo exige
 * en cada petición). Los archivos se guardan en la carpeta privada, nunca en la pública: solo se sirven por aquí.
 * Una galería (categoría GALERIA) no tiene archivo propio: agrupa hasta 25 imágenes de hasta 10 MB cada una.
 */
@Service
public class MaterialService {

    static final long MAX_ARCHIVO_BYTES = 25L * 1024 * 1024;
    /** Cada imagen de una galería: más chica que un archivo suelto para que la galería cargue rápido en el celular. */
    static final long MAX_IMAGEN_GALERIA_BYTES = 10L * 1024 * 1024;
    static final int MAX_IMAGENES_GALERIA = 25;
    private static final Set<String> EXTENSIONES_IMAGEN = Set.of("jpg", "png", "webp");
    /** Lado mayor de la miniatura (JPG) que se genera al subir una imagen JPG o PNG. */
    private static final int LADO_MINIATURA = 480;
    private static final int MAX_TEXTO = 5000;

    private final MaterialRepository repository;
    private final MaterialImagenRepository imagenRepository;
    private final CurrentUserProvider currentUserProvider;
    private final String uploadsPrivadosDir;

    public MaterialService(
            MaterialRepository repository,
            MaterialImagenRepository imagenRepository,
            CurrentUserProvider currentUserProvider,
            @Value("${app.uploads-privados.dir:uploads-privados}") String uploadsPrivadosDir) {
        this.repository = repository;
        this.imagenRepository = imagenRepository;
        this.currentUserProvider = currentUserProvider;
        this.uploadsPrivadosDir = uploadsPrivadosDir;
    }

    /** Archivo listo para servir: bytes, tipo y el nombre con que se descarga. */
    public record Descarga(byte[] bytes, MediaType tipo, String nombre) {}

    /** Una galería lista para empaquetar en ZIP: el controlador escribe los archivos uno por uno directo en la
     * respuesta (hasta 25 × 10 MB: no se arma en memoria). */
    public record Zip(String nombre, List<EntradaZip> archivos) {}

    public record EntradaZip(Path ruta, String nombre) {}

    // ---------------------------------------------------------------- panel

    @Transactional(readOnly = true)
    public List<MaterialDtos.Item> listar() {
        exigirAcceso();
        return conImagenes(repository.findAllByOrderByFechaActualizacionDesc());
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
        List<MaterialImagen> imagenes = imagenRepository.findByMaterialIdOrderByOrdenAscIdAsc(id);
        imagenes.forEach(this::borrarArchivoImagen);
        imagenRepository.deleteAll(imagenes);
        borrarCarpetaGaleria(id);
        repository.delete(m);
    }

    // ---------------------------------------------------------------- galería (panel)

    /** Agrega una imagen al final de una galería. Las imágenes se suben de una en una: así cada petición queda
     * lejos del tope de 26 MB del servidor y, si una falla, las demás sí quedan. */
    @Transactional
    public MaterialDtos.Item agregarImagen(Long id, MultipartFile archivo) {
        exigirAcceso();
        Material m = obtenerGaleria(id);
        if (archivo == null || archivo.isEmpty()) throw new ValidationException("Elige una imagen");
        List<MaterialImagen> actuales = imagenRepository.findByMaterialIdOrderByOrdenAscIdAsc(id);
        if (actuales.size() >= MAX_IMAGENES_GALERIA) {
            throw new ValidationException("Una galería puede tener hasta " + MAX_IMAGENES_GALERIA + " imágenes");
        }
        if (archivo.getSize() > MAX_IMAGEN_GALERIA_BYTES) throw new ValidationException("Cada imagen puede pesar hasta 10 MB");
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la imagen", e);
        }
        String extension = extensionDeImagen(bytes);
        int orden = actuales.stream().mapToInt(MaterialImagen::getOrden).max().orElse(-1) + 1;
        MaterialImagen imagen = imagenRepository.save(
                new MaterialImagen(m, orden, nombreSeguro(archivo.getOriginalFilename(), extension), extension, archivo.getSize()));
        try {
            Path destino = rutaImagen(id, imagen.getId(), extension);
            Files.createDirectories(destino.getParent());
            Files.write(destino, bytes);
            generarMiniatura(rutaMiniaturaImagen(id, imagen.getId()), extension, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo guardar la imagen", e);
        }
        m.tocar();
        return item(m);
    }

    @Transactional
    public MaterialDtos.Item quitarImagen(Long id, Long imagenId) {
        exigirAcceso();
        Material m = obtenerGaleria(id);
        MaterialImagen imagen = obtenerImagen(id, imagenId);
        borrarArchivoImagen(imagen);
        imagenRepository.delete(imagen);
        m.tocar();
        return item(m);
    }

    /** Reordena la galería: ids trae todas sus imágenes, de la portada a la última. */
    @Transactional
    public MaterialDtos.Item ordenarImagenes(Long id, List<Long> ids) {
        exigirAcceso();
        Material m = obtenerGaleria(id);
        List<MaterialImagen> imagenes = imagenRepository.findByMaterialIdOrderByOrdenAscIdAsc(id);
        Set<Long> actuales = imagenes.stream().map(MaterialImagen::getId).collect(Collectors.toSet());
        if (ids == null || ids.size() != imagenes.size() || !actuales.equals(new HashSet<>(ids))) {
            throw new ValidationException("La lista de imágenes no coincide con la galería; recarga la página");
        }
        Map<Long, MaterialImagen> porId = imagenes.stream().collect(Collectors.toMap(MaterialImagen::getId, i -> i));
        for (int i = 0; i < ids.size(); i++) {
            porId.get(ids.get(i)).cambiarOrden(i);
        }
        m.tocar();
        return item(m);
    }

    @Transactional(readOnly = true)
    public Descarga imagenDelPanel(Long id, Long imagenId) {
        exigirAcceso();
        return leerImagen(obtenerImagen(id, imagenId));
    }

    @Transactional(readOnly = true)
    public Descarga miniaturaImagenDelPanel(Long id, Long imagenId) {
        exigirAcceso();
        return leerMiniaturaImagen(obtenerImagen(id, imagenId));
    }

    @Transactional(readOnly = true)
    public Zip zipDelPanel(Long id) {
        exigirAcceso();
        return armarZip(obtenerGaleria(id));
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
        return conImagenes(repository.findByProyectoInOrderByFechaActualizacionDesc(aplicables));
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

    @Transactional(readOnly = true)
    public Descarga imagenPublico(Long id, Long imagenId, String proyecto) {
        return leerImagen(obtenerImagenPublica(id, imagenId, proyecto));
    }

    @Transactional(readOnly = true)
    public Descarga miniaturaImagenPublico(Long id, Long imagenId, String proyecto) {
        return leerMiniaturaImagen(obtenerImagenPublica(id, imagenId, proyecto));
    }

    @Transactional(readOnly = true)
    public Zip zipPublico(Long id, String proyecto) {
        exigirProyectoPublico(proyecto);
        Material m = obtenerGaleria(id);
        if (!m.getProyecto().aplicaA(proyecto)) throw new ResourceNotFoundException("Material no encontrado");
        return armarZip(m);
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
        if (cat == CategoriaMaterial.GALERIA) {
            // Una galería no lleva archivo propio: sus imágenes se suben aparte (agregarImagen), después de crearla.
            if (hayArchivoNuevo) throw new ValidationException("Una galería no lleva archivo: sus imágenes se agregan aparte");
            if (m.tieneArchivo() && !quitarArchivo) {
                throw new ValidationException("Quita el archivo actual para convertir este material en galería");
            }
        } else {
            if (m.getId() != null && imagenRepository.countByMaterialId(m.getId()) > 0) {
                throw new ValidationException("Quita las imágenes de la galería antes de cambiarle el tipo");
            }
            if (tx == null && !quedaArchivo) throw new ValidationException("Agrega un texto, un archivo o ambos");
        }

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
        return item(nuevo ? repository.save(guardado) : guardado);
    }

    /** El material con las imágenes de su galería (vacía si no es galería). */
    private MaterialDtos.Item item(Material m) {
        return MaterialDtos.Item.from(m, imagenRepository.findByMaterialIdOrderByOrdenAscIdAsc(m.getId()));
    }

    /** Lista de materiales con sus imágenes, en una sola consulta para todas las galerías. */
    private List<MaterialDtos.Item> conImagenes(List<Material> materiales) {
        List<Long> galerias = materiales.stream()
                .filter(m -> m.getCategoria() == CategoriaMaterial.GALERIA)
                .map(Material::getId)
                .toList();
        Map<Long, List<MaterialImagen>> porMaterial = galerias.isEmpty()
                ? Map.of()
                : imagenRepository.findByMaterialIdInOrderByOrdenAscIdAsc(galerias).stream()
                        .collect(Collectors.groupingBy(i -> i.getMaterial().getId()));
        return materiales.stream()
                .map(m -> MaterialDtos.Item.from(m, porMaterial.getOrDefault(m.getId(), List.of())))
                .toList();
    }

    private void escribirArchivo(Material m, MultipartFile archivo, String extension) {
        try {
            Path destino = ruta(m.getId(), extension);
            Files.createDirectories(destino.getParent());
            byte[] bytes = archivo.getBytes();
            Files.write(destino, bytes);
            generarMiniatura(rutaMiniatura(m.getId()), extension, bytes);
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

    private void generarMiniatura(Path destino, String extension, byte[] bytes) {
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

    Path rutaImagen(Long materialId, Long imagenId, String extension) {
        return Path.of(uploadsPrivadosDir, "material", "galeria-" + materialId, "imagen-" + imagenId + "." + extension);
    }

    Path rutaMiniaturaImagen(Long materialId, Long imagenId) {
        return Path.of(uploadsPrivadosDir, "material", "galeria-" + materialId, "imagen-" + imagenId + "-mini.jpg");
    }

    private Descarga leerImagen(MaterialImagen imagen) {
        Long materialId = imagen.getMaterial().getId();
        try {
            byte[] bytes = Files.readAllBytes(rutaImagen(materialId, imagen.getId(), imagen.getExtension()));
            return new Descarga(bytes, mediaTypeDe(imagen.getExtension()), imagen.getNombre());
        } catch (IOException e) {
            throw new ResourceNotFoundException("Imagen no encontrada");
        }
    }

    /** Igual que la miniatura de un archivo suelto: la reducida si existe (JPG/PNG) y si no, la imagen completa. */
    private Descarga leerMiniaturaImagen(MaterialImagen imagen) {
        Path mini = rutaMiniaturaImagen(imagen.getMaterial().getId(), imagen.getId());
        if (!Files.exists(mini)) return leerImagen(imagen);
        try {
            return new Descarga(Files.readAllBytes(mini), MediaType.IMAGE_JPEG, "miniatura.jpg");
        } catch (IOException e) {
            throw new ResourceNotFoundException("Imagen no encontrada");
        }
    }

    private void borrarArchivoImagen(MaterialImagen imagen) {
        Long materialId = imagen.getMaterial().getId();
        try {
            Files.deleteIfExists(rutaImagen(materialId, imagen.getId(), imagen.getExtension()));
            Files.deleteIfExists(rutaMiniaturaImagen(materialId, imagen.getId()));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar la imagen", e);
        }
    }

    private void borrarCarpetaGaleria(Long materialId) {
        try {
            Files.deleteIfExists(Path.of(uploadsPrivadosDir, "material", "galeria-" + materialId));
        } catch (IOException e) {
            // Si quedó algo dentro no se borra: no estorba y no vale la pena fallar la eliminación por eso.
        }
    }

    /** Los archivos de la galería en orden, con nombres numerados (01-…, 02-…) para que no choquen dentro del ZIP. */
    private Zip armarZip(Material m) {
        List<MaterialImagen> imagenes = imagenRepository.findByMaterialIdOrderByOrdenAscIdAsc(m.getId());
        if (imagenes.isEmpty()) throw new ResourceNotFoundException("Esta galería todavía no tiene imágenes");
        List<EntradaZip> archivos = new ArrayList<>();
        for (int i = 0; i < imagenes.size(); i++) {
            MaterialImagen img = imagenes.get(i);
            archivos.add(new EntradaZip(
                    rutaImagen(m.getId(), img.getId(), img.getExtension()), String.format("%02d-%s", i + 1, img.getNombre())));
        }
        return new Zip(nombreSeguro(m.getTitulo(), "zip"), archivos);
    }

    private Material obtenerEntidad(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Material no encontrado"));
    }

    private Material obtenerGaleria(Long id) {
        Material m = obtenerEntidad(id);
        if (m.getCategoria() != CategoriaMaterial.GALERIA) throw new ValidationException("Este material no es una galería");
        return m;
    }

    /** La imagen, solo si es de esa galería (si no, se trata como inexistente). */
    private MaterialImagen obtenerImagen(Long materialId, Long imagenId) {
        return imagenRepository.findById(imagenId)
                .filter(i -> i.getMaterial().getId().equals(materialId))
                .orElseThrow(() -> new ResourceNotFoundException("Imagen no encontrada"));
    }

    private MaterialImagen obtenerImagenPublica(Long materialId, Long imagenId, String proyecto) {
        exigirProyectoPublico(proyecto);
        MaterialImagen imagen = obtenerImagen(materialId, imagenId);
        if (!imagen.getMaterial().getProyecto().aplicaA(proyecto)) throw new ResourceNotFoundException("Imagen no encontrada");
        return imagen;
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

    /** Solo JPG, PNG o WEBP (por firma): lo único que acepta una galería. */
    static String extensionDeImagen(byte[] bytes) {
        String extension;
        try {
            extension = extensionPorFirma(bytes);
        } catch (ValidationException e) {
            extension = null;
        }
        if (extension == null || !EXTENSIONES_IMAGEN.contains(extension)) {
            throw new ValidationException("Solo se aceptan imágenes JPG, PNG o WEBP");
        }
        return extension;
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
