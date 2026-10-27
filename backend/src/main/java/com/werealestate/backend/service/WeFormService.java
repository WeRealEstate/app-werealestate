package com.werealestate.backend.service;

import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.dto.PaginaDto;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.DestinoPregunta;
import com.werealestate.backend.model.EstadoWeForm;
import com.werealestate.backend.model.Modulo;
import com.werealestate.backend.model.ModulosAcceso;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPreguntaForm;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.WeForm;
import com.werealestate.backend.model.WeFormPregunta;
import com.werealestate.backend.model.WeFormRespuesta;
import com.werealestate.backend.model.WeFormRespuestaItem;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.WeFormPreguntaRepository;
import com.werealestate.backend.repository.WeFormRepository;
import com.werealestate.backend.repository.WeFormRespuestaItemRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * We Forms, lado del panel: crear formularios (hoy, el registro de venta de un desarrollo), armar sus preguntas
 * y ver las respuestas. Solo un admin o personal de administración con el módulo We Forms activo.
 * Nada se borra: un formulario se cierra y una pregunta propia se archiva (sus respuestas viejas se
 * conservan tal como se contestaron).
 */
@Service
@Transactional
public class WeFormService {

    /** Desarrollos que tienen link público con PIN (los mismos planos /samai y /aldea-nanuu). */
    static final Map<String, String> PROYECTO_POR_DESARROLLO = Map.of("SAMAI Campestre", "samai", "Aldea Nanuu", "nanuu");

    private static final String ALFABETO = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom AZAR = new SecureRandom();

    private final WeFormRepository formRepository;
    private final WeFormPreguntaRepository preguntaRepository;
    private final WeFormRespuestaRepository respuestaRepository;
    private final WeFormRespuestaItemRepository itemRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DriveService driveService;
    private final String uploadsPrivadosDir;

    public WeFormService(
            WeFormRepository formRepository,
            WeFormPreguntaRepository preguntaRepository,
            WeFormRespuestaRepository respuestaRepository,
            WeFormRespuestaItemRepository itemRepository,
            DesarrolloRepository desarrolloRepository,
            CurrentUserProvider currentUserProvider,
            DriveService driveService,
            @Value("${app.uploads-privados.dir:uploads-privados}") String uploadsPrivadosDir) {
        this.formRepository = formRepository;
        this.preguntaRepository = preguntaRepository;
        this.respuestaRepository = respuestaRepository;
        this.itemRepository = itemRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.currentUserProvider = currentUserProvider;
        this.driveService = driveService;
        this.uploadsPrivadosDir = uploadsPrivadosDir;
    }

    // ---------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<WeFormDtos.Lista> listar() {
        exigirAcceso();
        return formRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(f -> new WeFormDtos.Lista(
                        f.getId(), f.getNombre(), f.getSlug(), f.getDesarrollo().getId(),
                        f.getDesarrollo().getNombre(), f.getEstado(), respuestaRepository.countByFormId(f.getId()),
                        f.getFechaCreacion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WeFormDtos.Detalle obtener(Long id) {
        exigirAcceso();
        return detalle(obtenerEntidad(id));
    }

    /** Los desarrollos en los que se puede crear un formulario (los que tienen link público con PIN). */
    @Transactional(readOnly = true)
    public List<Desarrollo> desarrollosDisponibles() {
        exigirAcceso();
        return desarrolloRepository.findAll().stream()
                .filter(d -> PROYECTO_POR_DESARROLLO.containsKey(d.getNombre()))
                .toList();
    }

    /** Respuestas del formulario filtradas por fecha de envío (ambas fechas inclusivas) y por asesor
     * (parte del nombre), de la más reciente a la más antigua. */
    private List<WeFormRespuesta> respuestasFiltradas(Long formId, LocalDate desde, LocalDate hasta, String asesor) {
        String buscado = asesor == null ? "" : asesor.trim().toLowerCase(Locale.ROOT);
        return respuestaRepository.findByFormIdOrderByFechaEnvioDesc(formId).stream()
                .filter(r -> desde == null || !r.getFechaEnvio().toLocalDate().isBefore(desde))
                .filter(r -> hasta == null || !r.getFechaEnvio().toLocalDate().isAfter(hasta))
                .filter(r -> buscado.isEmpty() || r.getAsesorNombre().toLowerCase(Locale.ROOT).contains(buscado))
                .toList();
    }

    private List<WeFormDtos.Respuesta> conItems(List<WeFormRespuesta> respuestas) {
        if (respuestas.isEmpty()) return List.of();
        Map<Long, List<WeFormRespuestaItem>> itemsPorRespuesta = itemRepository
                .findByRespuestaIdInOrderByOrdenAsc(respuestas.stream().map(WeFormRespuesta::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(i -> i.getRespuesta().getId()));
        return respuestas.stream()
                .map(r -> respuestaDto(r, itemsPorRespuesta.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public PaginaDto<WeFormDtos.Respuesta> listarRespuestas(
            Long formId, int pagina, int tamano, LocalDate desde, LocalDate hasta, String asesor) {
        exigirAcceso();
        obtenerEntidad(formId);
        int tam = Math.min(Math.max(1, tamano), 50);
        int inicio = Math.max(0, pagina) * tam;
        List<WeFormRespuesta> todas = respuestasFiltradas(formId, desde, hasta, asesor);
        List<WeFormRespuesta> pag = todas.stream().skip(inicio).limit(tam).toList();
        return new PaginaDto<>(conItems(pag), inicio + tam < todas.size());
    }

    private static final int MAX_EXPORTAR = 5000;

    /** Todas las respuestas del filtro (hasta 5000), para exportar a Excel o PDF. */
    @Transactional(readOnly = true)
    public List<WeFormDtos.Respuesta> exportarRespuestas(Long formId, LocalDate desde, LocalDate hasta, String asesor) {
        exigirAcceso();
        obtenerEntidad(formId);
        return conItems(respuestasFiltradas(formId, desde, hasta, asesor).stream().limit(MAX_EXPORTAR).toList());
    }

    private static final int MAX_TEXTOS = 100;
    private static final java.time.format.DateTimeFormatter DIA = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional(readOnly = true)
    public WeFormDtos.Estadisticas estadisticas(Long formId, LocalDate desde, LocalDate hasta, String asesor) {
        exigirAcceso();
        obtenerEntidad(formId);
        List<WeFormRespuesta> respuestas = respuestasFiltradas(formId, desde, hasta, asesor);

        long nuevos = respuestas.stream().filter(WeFormRespuesta::isClienteNuevo).count();
        Map<LocalDate, Long> dias = new java.util.TreeMap<>();
        Map<String, Long> asesores = new java.util.LinkedHashMap<>();
        for (WeFormRespuesta r : respuestas) {
            dias.merge(r.getFechaEnvio().toLocalDate(), 1L, Long::sum);
            asesores.merge(r.getAsesorNombre(), 1L, Long::sum);
        }
        List<WeFormDtos.Conteo> porDia = dias.entrySet().stream()
                .map(e -> new WeFormDtos.Conteo(e.getKey().format(DIA), e.getValue()))
                .toList();
        List<WeFormDtos.Conteo> porAsesor = ordenado(asesores);

        // Resumen por pregunta: se agrupa por pregunta (aunque ya esté archivada) con el título que tenía al responder.
        Map<Long, List<WeFormRespuestaItem>> itemsPorPregunta = new java.util.LinkedHashMap<>();
        if (!respuestas.isEmpty()) {
            itemRepository.findByRespuestaIdInOrderByOrdenAsc(respuestas.stream().map(WeFormRespuesta::getId).toList())
                    .stream()
                    .filter(i -> i.getPreguntaTipo() != TipoPreguntaForm.TITULO)
                    .forEach(i -> itemsPorPregunta.computeIfAbsent(i.getPregunta().getId(), k -> new ArrayList<>()).add(i));
        }
        List<WeFormDtos.ResumenPregunta> resumenes = new ArrayList<>();
        itemsPorPregunta.forEach((preguntaId, items) -> resumenes.add(resumir(preguntaId, items)));
        resumenes.sort(java.util.Comparator.comparingInt(r -> ordenDePregunta(itemsPorPregunta.get(r.preguntaId()))));
        return new WeFormDtos.Estadisticas(
                respuestas.size(), nuevos, respuestas.size() - nuevos, porDia, porAsesor, resumenes);
    }

    private static int ordenDePregunta(List<WeFormRespuestaItem> items) {
        return items.get(0).getOrden();
    }

    private static List<WeFormDtos.Conteo> ordenado(Map<String, Long> conteos) {
        return conteos.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new WeFormDtos.Conteo(e.getKey(), e.getValue()))
                .toList();
    }

    private WeFormDtos.ResumenPregunta resumir(Long preguntaId, List<WeFormRespuestaItem> items) {
        WeFormRespuestaItem ultimo = items.get(items.size() - 1);
        TipoPreguntaForm tipo = ultimo.getPreguntaTipo();
        List<String> valores = items.stream()
                .map(WeFormRespuestaItem::getValor)
                .filter(v -> v != null && !v.isBlank())
                .toList();
        long respondidas = items.stream()
                .filter(i -> i.getArchivoExtension() != null || (i.getValor() != null && !i.getValor().isBlank()))
                .count();

        Map<String, Long> conteos = new java.util.LinkedHashMap<>();
        List<String> textos = List.of();
        java.math.BigDecimal min = null;
        java.math.BigDecimal max = null;
        java.math.BigDecimal promedio = null;
        switch (tipo) {
            case OPCION_MULTIPLE, DESPLEGABLE, TIPO_PAGO, ASESOR, PAIS ->
                    valores.forEach(v -> conteos.merge(v.trim(), 1L, Long::sum));
            case CASILLAS -> valores.forEach(v -> {
                for (String parte : v.split(", ")) conteos.merge(parte.trim(), 1L, Long::sum);
            });
            case LOTES -> valores.forEach(v -> {
                for (String parte : v.split("; ")) {
                    int corte = parte.indexOf(" ($");
                    conteos.merge((corte > 0 ? parte.substring(0, corte) : parte).trim(), 1L, Long::sum);
                }
            });
            case NUMERO -> {
                List<java.math.BigDecimal> nums = new ArrayList<>();
                for (String v : valores) {
                    try {
                        nums.add(new java.math.BigDecimal(v.trim().replace(",", "")));
                    } catch (NumberFormatException e) {
                        // valor que no es número: no entra al cálculo
                    }
                }
                if (!nums.isEmpty()) {
                    min = nums.stream().min(java.util.Comparator.naturalOrder()).orElse(null);
                    max = nums.stream().max(java.util.Comparator.naturalOrder()).orElse(null);
                    promedio = nums.stream().reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)
                            .divide(java.math.BigDecimal.valueOf(nums.size()), 2, java.math.RoundingMode.HALF_UP);
                }
            }
            case ARCHIVO, APORTACIONES -> {
                // solo cuenta cuántas respondieron
            }
            default -> textos = valores.stream().limit(MAX_TEXTOS).toList();
        }
        return new WeFormDtos.ResumenPregunta(
                preguntaId, ultimo.getPreguntaTitulo(), tipo, respondidas, ordenado(conteos), textos, min, max,
                promedio);
    }

    /** Totales de todos los formularios por desarrollo y por asesor (la lista del módulo). */
    @Transactional(readOnly = true)
    public WeFormDtos.ResumenGeneral resumenGeneral() {
        exigirAcceso();
        Map<String, Long> desarrollos = new java.util.LinkedHashMap<>();
        Map<String, Long> asesores = new java.util.LinkedHashMap<>();
        long total = 0;
        for (WeForm f : formRepository.findAll()) {
            for (WeFormRespuesta r : respuestaRepository.findByFormIdOrderByFechaEnvioDesc(f.getId())) {
                total++;
                desarrollos.merge(f.getDesarrollo().getNombre(), 1L, Long::sum);
                asesores.merge(r.getAsesorNombre(), 1L, Long::sum);
            }
        }
        return new WeFormDtos.ResumenGeneral(total, ordenado(desarrollos), ordenado(asesores));
    }

    /** El archivo (INE, comprobante) de una respuesta, solo con sesión y el módulo; nunca como URL estática. */
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> archivoDeRespuesta(Long respuestaId, Long itemId) {
        exigirAcceso();
        WeFormRespuestaItem item = itemRepository.findById(itemId)
                .filter(i -> i.getRespuesta().getId().equals(respuestaId))
                .filter(i -> i.getArchivoExtension() != null)
                .orElseThrow(() -> new ResourceNotFoundException("Archivo no encontrado"));
        // Ya subido a Google Drive, el servidor no guarda copia: se lee de allá (ver DriveService).
        if (item.getDriveArchivoId() != null) {
            byte[] bytes = driveService.descargar(item.getDriveArchivoId());
            return ResponseEntity.ok().contentType(mediaTypeDe(item.getArchivoExtension())).body(bytes);
        }
        try {
            byte[] bytes = Files.readAllBytes(rutaArchivo(respuestaId, itemId, item.getArchivoExtension()));
            return ResponseEntity.ok().contentType(mediaTypeDe(item.getArchivoExtension())).body(bytes);
        } catch (IOException e) {
            throw new ResourceNotFoundException("Archivo no encontrado");
        }
    }

    // ---------------------------------------------------------------- formularios

    public WeFormDtos.Detalle crear(WeFormDtos.CrearRequest request) {
        Usuario actual = exigirAcceso();
        Desarrollo desarrollo = desarrolloRepository.findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        if (!PROYECTO_POR_DESARROLLO.containsKey(desarrollo.getNombre())) {
            throw new ValidationException("Solo se pueden crear formularios para SAMAI Campestre o Aldea Nanuu");
        }
        WeForm form = formRepository.save(new WeForm(
                request.nombre().trim(), limpio(request.descripcion()), nuevoSlug(request.nombre()), desarrollo, actual));
        int orden = 0;
        for (Plantilla p : PLANTILLA) {
            preguntaRepository.save(new WeFormPregunta(
                    form, orden++, p.tipo(), p.titulo(), p.descripcion(), p.obligatoria(), p.opciones(), p.destino(), true));
        }
        return detalle(form);
    }

    /** Copia un formulario: sus preguntas activas tal como están (orden, título, obligatoria, visible, opciones y vínculo
     * con el cliente o la venta) en un formulario nuevo, Activo y con su propio link, para el desarrollo elegido. No copia
     * respuestas ni preguntas archivadas. Los lotes que se ofrecen salen del desarrollo del formulario nuevo. */
    public WeFormDtos.Detalle duplicar(Long id, WeFormDtos.DuplicarRequest request) {
        Usuario actual = exigirAcceso();
        WeForm original = obtenerEntidad(id);
        Desarrollo desarrollo = desarrolloRepository.findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        if (!PROYECTO_POR_DESARROLLO.containsKey(desarrollo.getNombre())) {
            throw new ValidationException("Solo se pueden crear formularios para SAMAI Campestre o Aldea Nanuu");
        }
        WeForm copia = formRepository.save(new WeForm(
                request.nombre().trim(), limpio(request.descripcion()), nuevoSlug(request.nombre()), desarrollo, actual));
        int orden = 0;
        for (WeFormPregunta p : preguntaRepository.findByFormIdAndArchivadaFalseOrderByOrdenAsc(original.getId())) {
            preguntaRepository.save(new WeFormPregunta(
                    copia, orden++, p.getTipo(), p.getTitulo(), p.getDescripcion(), p.isObligatoria(), p.getOpciones(),
                    p.getDestino(), p.isVisible()));
        }
        return detalle(copia);
    }

    public WeFormDtos.Detalle actualizar(Long id, WeFormDtos.ActualizarRequest request) {
        exigirAcceso();
        WeForm form = obtenerEntidad(id);
        form.actualizar(request.nombre().trim(), limpio(request.descripcion()), request.estado());
        return detalle(form);
    }

    // ---------------------------------------------------------------- preguntas

    public WeFormDtos.Pregunta crearPregunta(Long formId, WeFormDtos.PreguntaRequest request) {
        exigirAcceso();
        WeForm form = obtenerEntidad(formId);
        TipoPreguntaForm tipo = request.tipo();
        if (tipo == null || ESPECIALES.contains(tipo)) {
            throw new ValidationException("Elige un tipo de pregunta");
        }
        String opciones = opcionesTexto(tipo, request.opciones());
        int orden = preguntaRepository.findByFormIdOrderByOrdenAsc(formId).stream()
                .mapToInt(WeFormPregunta::getOrden).max().orElse(-1) + 1;
        boolean obligatoria = tipo != TipoPreguntaForm.TITULO && request.obligatoria();
        WeFormPregunta p = preguntaRepository.save(new WeFormPregunta(
                form, orden, tipo, request.titulo().trim(), limpio(request.descripcion()), obligatoria, opciones, null,
                request.visible() == null || request.visible()));
        return preguntaDto(p);
    }

    public WeFormDtos.Pregunta editarPregunta(Long formId, Long preguntaId, WeFormDtos.PreguntaRequest request) {
        exigirAcceso();
        WeFormPregunta p = obtenerPregunta(formId, preguntaId);
        boolean visible = request.visible() == null || request.visible();
        boolean obligatoria = request.obligatoria();
        DestinoPregunta destino = p.getDestino();
        if (destino != null) {
            if (destino.siempreObligatoria()) {
                visible = true;
                obligatoria = true;
            } else if (!destino.opcionalEditable()) {
                visible = true;
                obligatoria = false; // se decide sola según el tipo de pago
            }
        }
        if (p.getTipo() == TipoPreguntaForm.TITULO) obligatoria = false;
        p.editar(
                request.titulo().trim(), limpio(request.descripcion()), obligatoria,
                destino == null ? opcionesTexto(p.getTipo(), request.opciones()) : p.getOpciones(), visible);
        return preguntaDto(p);
    }

    /** Las preguntas propias se archivan (dejan de verse en el formulario, sus respuestas viejas se conservan);
     * las predefinidas no se pueden quitar. */
    public void archivarPregunta(Long formId, Long preguntaId) {
        exigirAcceso();
        WeFormPregunta p = obtenerPregunta(formId, preguntaId);
        if (p.getDestino() != null) {
            throw new ValidationException("Esta pregunta alimenta al cliente o a la venta y no se puede quitar");
        }
        p.archivar();
    }

    public List<WeFormDtos.Pregunta> reordenar(Long formId, WeFormDtos.OrdenRequest request) {
        exigirAcceso();
        obtenerEntidad(formId);
        List<WeFormPregunta> actuales = preguntaRepository.findByFormIdAndArchivadaFalseOrderByOrdenAsc(formId);
        Set<Long> esperados = actuales.stream().map(WeFormPregunta::getId).collect(Collectors.toSet());
        if (!esperados.equals(Set.copyOf(request.ids())) || request.ids().size() != actuales.size()) {
            throw new ValidationException("El orden debe incluir todas las preguntas del formulario");
        }
        Map<Long, WeFormPregunta> porId = actuales.stream().collect(Collectors.toMap(WeFormPregunta::getId, p -> p));
        int orden = 0;
        for (Long id : request.ids()) porId.get(id).setOrden(orden++);
        return actuales.stream().sorted(java.util.Comparator.comparingInt(WeFormPregunta::getOrden)).map(this::preguntaDto).toList();
    }

    // ---------------------------------------------------------------- utilidades (también las usa el link público)

    WeForm obtenerEntidad(Long id) {
        return formRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Formulario no encontrado"));
    }

    private WeFormPregunta obtenerPregunta(Long formId, Long preguntaId) {
        return preguntaRepository.findById(preguntaId)
                .filter(p -> p.getForm().getId().equals(formId) && !p.isArchivada())
                .orElseThrow(() -> new ResourceNotFoundException("Pregunta no encontrada"));
    }

    static String proyectoDe(WeForm form) {
        return PROYECTO_POR_DESARROLLO.get(form.getDesarrollo().getNombre());
    }

    Path rutaArchivo(Long respuestaId, Long itemId, String extension) {
        return rutaArchivo(uploadsPrivadosDir, respuestaId, itemId, extension);
    }

    /** Dónde vive el archivo en el servidor mientras no se haya subido a Google Drive (ver DriveService). */
    static Path rutaArchivo(String uploadsPrivadosDir, Long respuestaId, Long itemId, String extension) {
        return Path.of(uploadsPrivadosDir, "we-forms", "resp-" + respuestaId + "-" + itemId + "." + extension);
    }

    static MediaType mediaTypeDe(String extension) {
        return switch (extension) {
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg" -> MediaType.IMAGE_JPEG;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "pdf" -> MediaType.APPLICATION_PDF;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private Usuario exigirAcceso() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN && !ModulosAcceso.efectivos(actual).contains(Modulo.WE_FORMS)) {
            throw new ForbiddenOperationException("No tienes acceso a We Forms");
        }
        return actual;
    }

    private WeFormDtos.Detalle detalle(WeForm f) {
        List<WeFormDtos.Pregunta> preguntas = preguntaRepository.findByFormIdAndArchivadaFalseOrderByOrdenAsc(f.getId())
                .stream().map(this::preguntaDto).toList();
        return new WeFormDtos.Detalle(
                f.getId(), f.getNombre(), f.getDescripcion(), f.getSlug(), f.getDesarrollo().getId(),
                f.getDesarrollo().getNombre(), proyectoDe(f), f.getEstado(), f.getFechaCreacion(), preguntas);
    }

    private WeFormDtos.Pregunta preguntaDto(WeFormPregunta p) {
        DestinoPregunta d = p.getDestino();
        return new WeFormDtos.Pregunta(
                p.getId(), p.getOrden(), p.getTipo(), p.getTitulo(), p.getDescripcion(), p.isObligatoria(),
                p.opcionesLista(), d, p.isVisible(), d != null, d != null && d.siempreObligatoria(),
                d != null && d.opcionalEditable());
    }

    private WeFormDtos.Respuesta respuestaDto(WeFormRespuesta r, List<WeFormRespuestaItem> items) {
        return new WeFormDtos.Respuesta(
                r.getId(), r.getFechaEnvio(), r.getAsesorNombre(),
                r.getCliente() != null ? r.getCliente().getId() : null,
                r.getCliente() != null ? r.getCliente().nombreCompleto() : null,
                r.isClienteNuevo(),
                r.getVenta() != null ? r.getVenta().getId() : null,
                r.getVenta() != null ? r.getVenta().getNumero() : null,
                items.stream()
                        .map(i -> new WeFormDtos.ItemRespuesta(
                                i.getId(), i.getPreguntaTitulo(), i.getPreguntaTipo(), i.getValor(),
                                i.getArchivoExtension() != null))
                        .toList());
    }

    private static String opcionesTexto(TipoPreguntaForm tipo, List<String> opciones) {
        boolean conOpciones = tipo == TipoPreguntaForm.OPCION_MULTIPLE
                || tipo == TipoPreguntaForm.CASILLAS
                || tipo == TipoPreguntaForm.DESPLEGABLE;
        if (!conOpciones) return null;
        List<String> limpias = opciones == null
                ? List.of()
                : opciones.stream().map(o -> o == null ? "" : o.trim()).filter(o -> !o.isEmpty()).distinct().toList();
        if (limpias.size() < 2) throw new ValidationException("Agrega al menos dos opciones");
        return String.join("\n", limpias);
    }

    private String nuevoSlug(String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.length() > 24) base = base.substring(0, 24).replaceAll("-+$", "");
        if (base.isEmpty()) base = "form";
        for (int intento = 0; intento < 20; intento++) {
            StringBuilder azar = new StringBuilder();
            for (int i = 0; i < 5; i++) azar.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
            String slug = base + "-" + azar;
            if (!formRepository.existsBySlug(slug)) return slug;
        }
        throw new IllegalStateException("No se pudo generar el link del formulario");
    }

    private static String limpio(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static final Set<TipoPreguntaForm> ESPECIALES = Set.of(
            TipoPreguntaForm.ASESOR, TipoPreguntaForm.LOTES, TipoPreguntaForm.TIPO_PAGO, TipoPreguntaForm.APORTACIONES);

    private record Plantilla(
            TipoPreguntaForm tipo, String titulo, String descripcion, boolean obligatoria, String opciones,
            DestinoPregunta destino) {
    }

    /** Preguntas con las que nace cada formulario: las que alimentan al cliente y a la venta, ya ligadas. */
    private static final List<Plantilla> PLANTILLA = armarPlantilla();

    private static List<Plantilla> armarPlantilla() {
        List<Plantilla> l = new ArrayList<>();
        l.add(new Plantilla(TipoPreguntaForm.TITULO, "Datos del cliente", null, false, null, null));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Nombre(s)", null, true, null, DestinoPregunta.NOMBRE));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Apellido paterno", null, true, null, DestinoPregunta.APELLIDO_PATERNO));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Apellido materno", null, false, null, DestinoPregunta.APELLIDO_MATERNO));
        l.add(new Plantilla(TipoPreguntaForm.FECHA, "Fecha de nacimiento", "Debe ser mayor de edad.", true, null, DestinoPregunta.FECHA_NACIMIENTO));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "CURP", "18 caracteres.", true, null, DestinoPregunta.CURP));
        l.add(new Plantilla(TipoPreguntaForm.TELEFONO, "Teléfono", null, true, null, DestinoPregunta.TELEFONO));
        l.add(new Plantilla(TipoPreguntaForm.CORREO, "Correo electrónico", null, false, null, DestinoPregunta.CORREO));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "RFC", null, false, null, DestinoPregunta.RFC));
        l.add(new Plantilla(TipoPreguntaForm.TITULO, "Domicilio", null, false, null, null));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Calle y número", null, false, null, DestinoPregunta.CALLE));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Colonia", null, false, null, DestinoPregunta.COLONIA));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Municipio", null, false, null, DestinoPregunta.MUNICIPIO));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Estado", null, false, null, DestinoPregunta.ESTADO));
        l.add(new Plantilla(TipoPreguntaForm.CORTA, "Código postal", null, false, null, DestinoPregunta.CODIGO_POSTAL));
        l.add(new Plantilla(TipoPreguntaForm.TITULO, "Lote(s) y forma de pago", null, false, null, null));
        l.add(new Plantilla(TipoPreguntaForm.LOTES, "Lote(s)", "Elige uno o varios. Solo aparecen los disponibles y los que tú apartaste.", true, null, DestinoPregunta.LOTES));
        l.add(new Plantilla(TipoPreguntaForm.TIPO_PAGO, "Tipo de pago", null, true, null, DestinoPregunta.TIPO_PAGO));
        l.add(new Plantilla(TipoPreguntaForm.NUMERO, "Monto de enganche, pago inicial o aportación anual", "Según el tipo de pago.", false, null, DestinoPregunta.ENGANCHE));
        l.add(new Plantilla(TipoPreguntaForm.NUMERO, "Plazo (meses)", "Según el tipo de pago.", false, null, DestinoPregunta.PLAZO));
        l.add(new Plantilla(TipoPreguntaForm.NUMERO, "Día de pago (1 al 31)", "El día del mes en que se pagan las mensualidades.", false, null, DestinoPregunta.DIA_PAGO));
        l.add(new Plantilla(TipoPreguntaForm.APORTACIONES, "Aportaciones", "Mes y monto de cada aportación.", false, null, DestinoPregunta.APORTACIONES));
        l.add(new Plantilla(TipoPreguntaForm.ASESOR, "Asesor", "Es quien entró con su PIN.", false, null, DestinoPregunta.ASESOR));
        return List.copyOf(l);
    }
}
