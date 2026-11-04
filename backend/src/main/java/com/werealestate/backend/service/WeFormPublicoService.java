package com.werealestate.backend.service;

import tools.jackson.databind.JsonNode;
import com.werealestate.backend.dto.ClienteRequest;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.VentaAportacionItemRequest;
import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.dto.WeFormDtos;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.DestinoPregunta;
import com.werealestate.backend.model.EstadoWeForm;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.EstadosMexico;
import com.werealestate.backend.model.Paises;
import com.werealestate.backend.model.TipoPreguntaForm;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.model.WeForm;
import com.werealestate.backend.model.WeFormPregunta;
import com.werealestate.backend.model.WeFormRespuesta;
import com.werealestate.backend.model.WeFormRespuestaItem;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.WeFormPreguntaRepository;
import com.werealestate.backend.repository.WeFormRepository;
import com.werealestate.backend.repository.WeFormRespuestaItemRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * We Forms, lado del link público: el asesor (ya verificado con su PIN) ve el formulario y lo envía. Al
 * enviarlo, TODO en una sola transacción: se valida lo contestado, se crea (o se vincula por CURP) el
 * cliente, se crea la venta con sus lotes (que pasan a "En proceso de firma") y se guarda la respuesta. Si
 * algo falla —por ejemplo otro asesor acaba de tomar un lote— no se guarda nada y el asesor ve el motivo.
 */
@Service
@Transactional
public class WeFormPublicoService {

    private static final String EMAIL_USUARIO_PUBLICO = "cotizador-publico@weinversiones.com";
    private static final long MAX_ARCHIVO_BYTES = 2L * 1024 * 1024;
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    /** Formas de pago combinables, igual que el Cotizador y Registrar venta (frontend core/utils/formas-pago):
     * una base y, opcional, anualidades o aportaciones; contado va solo. */
    private static final Set<String> BASES_PAGO = Set.of("msi", "downpayment", "initial", "cash");
    private static final Set<String> EXTRAS_PAGO = Set.of("annualities", "aportaciones");
    private static final Map<String, String> ETIQUETA_ENGANCHE = Map.of("downpayment", "Enganche", "initial", "Pago inicial");

    /** Lo que se contestó en "Tipo de pago". Con anualidades trae su monto, mes y cuántas. */
    private record FormaPago(String base, String extra, BigDecimal montoAnualidad, Integer mesAnualidad, Integer cantidadAnualidades) {
        boolean contado() {
            return "cash".equals(base);
        }

        boolean conEntrada() {
            return base != null && ETIQUETA_ENGANCHE.containsKey(base);
        }
    }

    private final WeFormService formService;
    private final WeFormRepository formRepository;
    private final WeFormPreguntaRepository preguntaRepository;
    private final WeFormRespuestaRepository respuestaRepository;
    private final WeFormRespuestaItemRepository itemRepository;
    private final LoteRepository loteRepository;
    private final LoteService loteService;
    private final ClienteService clienteService;
    private final VentaService ventaService;
    private final UsuarioRepository usuarioRepository;

    public WeFormPublicoService(
            WeFormService formService,
            WeFormRepository formRepository,
            WeFormPreguntaRepository preguntaRepository,
            WeFormRespuestaRepository respuestaRepository,
            WeFormRespuestaItemRepository itemRepository,
            LoteRepository loteRepository,
            LoteService loteService,
            ClienteService clienteService,
            VentaService ventaService,
            UsuarioRepository usuarioRepository) {
        this.formService = formService;
        this.formRepository = formRepository;
        this.preguntaRepository = preguntaRepository;
        this.respuestaRepository = respuestaRepository;
        this.itemRepository = itemRepository;
        this.loteRepository = loteRepository;
        this.loteService = loteService;
        this.clienteService = clienteService;
        this.ventaService = ventaService;
        this.usuarioRepository = usuarioRepository;
    }

    // ---------------------------------------------------------------- consulta

    /** El formulario por su link. */
    private WeForm formularioPorSlug(String slug) {
        return formRepository.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Formulario no encontrado"));
    }

    /** A qué plano pertenece el formulario ("samai" / "nanuu"): para pedir el PIN con el acceso correcto. */
    @Transactional(readOnly = true)
    public String proyectoDe(String slug) {
        return WeFormService.proyectoDe(formularioPorSlug(slug));
    }

    /** Formularios activos de un desarrollo, para elegir desde el botón "Registrar venta" del plano. */
    @Transactional(readOnly = true)
    public List<WeFormDtos.PublicoLista> listarActivos(String proyecto) {
        String nombreDesarrollo = WeFormService.PROYECTO_POR_DESARROLLO.entrySet().stream()
                .filter(e -> e.getValue().equals(proyecto))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Proyecto no válido"));
        return formRepository.findAllByOrderByFechaCreacionDesc().stream()
                .filter(f -> f.getEstado() == EstadoWeForm.ACTIVO)
                .filter(f -> f.getDesarrollo().getNombre().equals(nombreDesarrollo))
                .map(f -> new WeFormDtos.PublicoLista(f.getSlug(), f.getNombre(), f.getDescripcion()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WeFormDtos.Publico obtener(String slug, AsesorPublicoService.Identificado asesor) {
        WeForm form = formularioPorSlug(slug);
        if (form.getEstado() != EstadoWeForm.ACTIVO) {
            throw new ConflictException("Este formulario está cerrado y ya no recibe respuestas");
        }
        List<WeFormDtos.PreguntaPublica> preguntas = preguntaRepository
                .findByFormIdAndArchivadaFalseOrderByOrdenAsc(form.getId()).stream()
                .filter(WeFormPregunta::isVisible)
                .map(p -> new WeFormDtos.PreguntaPublica(
                        p.getId(), p.getTipo(), p.getTitulo(), p.getDescripcion(),
                        p.isObligatoria() || (p.getDestino() != null && p.getDestino().siempreObligatoria()),
                        p.opcionesLista(), p.getDestino()))
                .toList();
        Long usuarioId = asesor.usuario() != null ? asesor.usuario().getId() : null;
        List<WeFormDtos.LotePublico> lotes = loteService
                .listarParaRegistroDeVenta(form.getDesarrollo().getId(), usuarioId, asesor.nombre()).stream()
                .map(l -> new WeFormDtos.LotePublico(
                        l.id(), l.manzana(), l.numeroLote(), l.superficie(), l.precio(), l.estado().name()))
                .toList();
        return new WeFormDtos.Publico(
                form.getSlug(), form.getNombre(), form.getDescripcion(), form.getDesarrollo().getNombre(),
                asesor.nombre(), preguntas, lotes);
    }

    // ---------------------------------------------------------------- envío

    public WeFormDtos.EnvioResultado enviar(
            String slug,
            AsesorPublicoService.Identificado asesor,
            WeFormDtos.EnvioRequest envio,
            Map<Long, MultipartFile> archivos) {
        WeForm form = formularioPorSlug(slug);
        if (form.getEstado() != EstadoWeForm.ACTIVO) {
            throw new ConflictException("Este formulario está cerrado y ya no recibe respuestas");
        }
        respuestaRepository.bloquearEnvios();

        List<WeFormPregunta> preguntas = preguntaRepository.findByFormIdAndArchivadaFalseOrderByOrdenAsc(form.getId()).stream()
                .filter(WeFormPregunta::isVisible)
                .toList();
        Map<Long, JsonNode> respuestas = new HashMap<>();
        for (WeFormDtos.RespuestaEnvio r : envio.respuestas()) respuestas.put(r.preguntaId(), r.valor());

        // 1) Valida y normaliza cada respuesta según el tipo de su pregunta.
        Map<Long, String> textoPorPregunta = new LinkedHashMap<>();
        Map<DestinoPregunta, JsonNode> porDestino = new HashMap<>();
        FormaPago formaDePago = null;
        for (WeFormPregunta p : preguntas) {
            if (p.getDestino() == DestinoPregunta.TIPO_PAGO) {
                formaDePago = leerFormaPago(respuestas.get(p.getId()));
            }
        }
        for (WeFormPregunta p : preguntas) {
            if (p.getTipo() == TipoPreguntaForm.TITULO) continue;
            JsonNode valor = respuestas.get(p.getId());
            boolean obligatoria = esObligatoria(p, formaDePago);
            if (p.getTipo() == TipoPreguntaForm.ASESOR) {
                textoPorPregunta.put(p.getId(), asesor.nombre());
                continue;
            }
            if (p.getTipo() == TipoPreguntaForm.ARCHIVO) {
                MultipartFile archivo = archivos.get(p.getId());
                boolean hay = archivo != null && !archivo.isEmpty();
                if (!hay && obligatoria) throw new ValidationException("Falta: " + p.getTitulo());
                if (hay) textoPorPregunta.put(p.getId(), nombreArchivo(archivo));
                continue;
            }
            boolean vacio = valor == null || valor.isNull()
                    || (valor.isTextual() && valor.asText().isBlank())
                    || (valor.isArray() && valor.isEmpty());
            if (vacio) {
                if (obligatoria) throw new ValidationException("Falta: " + p.getTitulo());
                continue;
            }
            if (p.getDestino() != null) porDestino.put(p.getDestino(), valor);
            textoPorPregunta.put(p.getId(), validarYTexto(p, valor));
        }

        // 2) Lotes elegidos: del desarrollo del formulario, que el asesor pueda tomar y que sigan libres.
        JsonNode lotesNode = porDestino.get(DestinoPregunta.LOTES);
        if (lotesNode == null || !lotesNode.isArray() || lotesNode.isEmpty()) {
            throw new ValidationException("Elige al menos un lote");
        }
        Set<Long> loteIds = new java.util.LinkedHashSet<>();
        for (JsonNode n : lotesNode) {
            if (!n.canConvertToLong() || !loteIds.add(n.asLong())) throw new ValidationException("Hay un lote repetido o no válido");
        }
        if (loteIds.size() > 20) throw new ValidationException("Máximo 20 lotes por venta");
        Long usuarioAsesorId = asesor.usuario() != null ? asesor.usuario().getId() : null;
        List<Lote> lotes = new ArrayList<>();
        for (Long id : loteIds) {
            Lote lote = loteRepository.findById(id)
                    .filter(l -> l.getDesarrollo().getId().equals(form.getDesarrollo().getId()))
                    .orElseThrow(() -> new ValidationException("Uno de los lotes no pertenece a este desarrollo"));
            if (!loteService.puedeTomarParaRegistroDeVenta(lote, usuarioAsesorId, asesor.nombre())) {
                throw new ConflictException("El lote Mz " + lote.getManzana() + " / " + lote.getNumeroLote()
                        + " ya no está disponible. Elige otro y vuelve a enviar.");
            }
            lotes.add(lote);
        }

        // 3) Datos de la venta.
        BigDecimal precioTotal = BigDecimal.ZERO;
        List<VentaLoteItemRequest> lotesVenta = new ArrayList<>();
        for (Lote l : lotes) {
            BigDecimal precio = LoteDto.from(l).precio();
            precioTotal = precioTotal.add(precio);
            lotesVenta.add(new VentaLoteItemRequest(l.getId(), precio));
        }
        Integer plazo = entero(porDestino.get(DestinoPregunta.PLAZO), "El plazo");
        Integer diaPago = entero(porDestino.get(DestinoPregunta.DIA_PAGO), "El día de pago");
        BigDecimal enganche = decimal(porDestino.get(DestinoPregunta.ENGANCHE), "El enganche");
        List<VentaAportacionItemRequest> aportaciones = aportaciones(porDestino.get(DestinoPregunta.APORTACIONES));
        if (formaDePago == null) throw new ValidationException("Elige el tipo de pago");

        BigDecimal mensualidad = null;
        String engancheLabel = null;
        if (formaDePago.contado()) {
            plazo = null;
            diaPago = null;
            enganche = null;
            aportaciones = List.of();
        } else {
            if (plazo == null || plazo < 1) throw new ValidationException("Indica el plazo en meses");
            if (diaPago == null || diaPago < 1 || diaPago > 31) throw new ValidationException("Indica el día de pago (1 al 31)");

            // Entrada (enganche o pago inicial): se resta primero.
            engancheLabel = formaDePago.conEntrada() ? ETIQUETA_ENGANCHE.get(formaDePago.base()) : null;
            if (engancheLabel == null) {
                enganche = null;
            } else if (enganche == null || enganche.signum() <= 0 || enganche.compareTo(precioTotal) >= 0) {
                throw new ValidationException("Indica un monto de " + engancheLabel.toLowerCase() + " menor al precio");
            }
            BigDecimal saldo = precioTotal.subtract(enganche == null ? BigDecimal.ZERO : enganche);

            // Anualidades o aportaciones: en su mes se paga solo eso; las anualidades se guardan como aportaciones
            // programadas, una por año, para que quepan junto al enganche.
            if ("aportaciones".equals(formaDePago.extra())) {
                if (aportaciones.isEmpty()) throw new ValidationException("Agrega al menos una aportación");
            } else if ("annualities".equals(formaDePago.extra())) {
                aportaciones = anualidadesComoAportaciones(YearMonth.now(), plazo, formaDePago.mesAnualidad(),
                        formaDePago.montoAnualidad(), formaDePago.cantidadAnualidades());
                if (aportaciones.isEmpty()) {
                    throw new ValidationException("Con ese plazo no caben anualidades: debe ser de al menos 24 meses");
                }
            } else {
                aportaciones = List.of();
            }

            if (aportaciones.isEmpty()) {
                mensualidad = saldo.divide(BigDecimal.valueOf(plazo), 2, RoundingMode.HALF_UP);
            } else {
                BigDecimal aportado = aportaciones.stream().map(VentaAportacionItemRequest::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
                int regulares = plazo - aportaciones.size();
                BigDecimal restante = saldo.subtract(aportado);
                if (regulares <= 0 || restante.signum() <= 0) {
                    throw new ValidationException("annualities".equals(formaDePago.extra())
                            ? "Las anualidades no dejan saldo para las mensualidades regulares"
                            : "Las aportaciones no dejan saldo para las mensualidades regulares");
                }
                mensualidad = restante.divide(BigDecimal.valueOf(regulares), 2, RoundingMode.HALF_UP);
            }
        }

        // 4) Cliente: por CURP (vincula sin duplicar) o nuevo.
        // El país de residencia del cliente sale de la primera pregunta tipo País (por orden) que se contestó.
        String paisResidencia = preguntas.stream()
                .filter(p -> p.getTipo() == TipoPreguntaForm.PAIS)
                .map(p -> textoPorPregunta.get(p.getId()))
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
        // Con México como país, el estado debe ser uno de la lista (la misma regla que en Clientes); con otro
        // país, o sin país, es texto libre. La respuesta guarda el nombre de la lista.
        String estadoCliente = null;
        JsonNode estadoNodo = porDestino.get(DestinoPregunta.ESTADO);
        if (Paises.MEXICO.equals(paisResidencia) && estadoNodo != null && !estadoNodo.asText().isBlank()) {
            estadoCliente = EstadosMexico.canonico(estadoNodo.asText());
            if (estadoCliente == null) {
                throw new ValidationException("Estado: elige un estado de la lista");
            }
            for (WeFormPregunta p : preguntas) {
                if (p.getDestino() == DestinoPregunta.ESTADO) textoPorPregunta.put(p.getId(), estadoCliente);
            }
        }
        ClienteRequest datosCliente = datosCliente(porDestino, paisResidencia, estadoCliente);
        ClienteService.ClienteResuelto resuelto =
                clienteService.resolverDesdeRegistroDeVenta(datosCliente, asesor.usuario(), asesor.externo());

        // 5) Venta (con sus lotes en "En proceso de firma").
        Usuario sistema = usuarioRepository.findByEmail(EMAIL_USUARIO_PUBLICO)
                .orElseThrow(() -> new IllegalStateException("Falta el usuario de sistema del cotizador público"));
        String formaPago = etiquetaFormaPago(formaDePago, plazo);
        VentaCreateRequest ventaRequest = new VentaCreateRequest(
                lotesVenta, resuelto.cliente().getId(), null, List.of(),
                asesor.usuario() != null ? asesor.usuario().getId() : null,
                asesor.externo() != null ? asesor.externo().getId() : null,
                formaPago, LocalDate.now(), mensualidad, plazo, engancheLabel, enganche, diaPago, false,
                "Registrada desde el formulario «" + form.getNombre() + "» (We Forms).", aportaciones,
                false, null, null, null, null, null, null, null, null);
        Venta venta = ventaService.crearDesdeRegistroDeVenta(ventaRequest, sistema);

        // 6) La respuesta, con cada pregunta tal como se contestó.
        WeFormRespuesta respuesta = respuestaRepository.save(new WeFormRespuesta(
                form, asesor.usuario(), asesor.externo(), asesor.nombre(), resuelto.cliente(), resuelto.nuevo(), venta));
        List<Path> escritos = new ArrayList<>();
        try {
            int orden = 0;
            for (WeFormPregunta p : preguntas) {
                String texto = textoPorPregunta.get(p.getId());
                if (texto == null) continue;
                MultipartFile archivo = p.getTipo() == TipoPreguntaForm.ARCHIVO ? archivos.get(p.getId()) : null;
                String extension = archivo == null ? null : extensionDe(archivo);
                WeFormRespuestaItem item = itemRepository.save(new WeFormRespuestaItem(respuesta, p, texto, extension, orden++));
                if (archivo != null) {
                    Path destino = formService.rutaArchivo(respuesta.getId(), item.getId(), extension);
                    Files.createDirectories(destino.getParent());
                    Files.write(destino, archivo.getBytes());
                    escritos.add(destino);
                }
            }
        } catch (IOException e) {
            borrar(escritos);
            throw new UncheckedIOException("No se pudo guardar un archivo del registro de venta", e);
        } catch (RuntimeException e) {
            borrar(escritos);
            throw e;
        }
        return new WeFormDtos.EnvioResultado(
                respuesta.getId(), venta.getNumero(), resuelto.cliente().nombreCompleto(), resuelto.nuevo());
    }

    // ---------------------------------------------------------------- validación de cada pregunta

    private static boolean esObligatoria(WeFormPregunta p, FormaPago forma) {
        DestinoPregunta d = p.getDestino();
        if (d == null) return p.isObligatoria();
        if (d.siempreObligatoria()) return true;
        if (forma == null || forma.contado()) return false;
        return switch (d) {
            case PLAZO, DIA_PAGO -> true;
            case ENGANCHE -> forma.conEntrada();
            case APORTACIONES -> "aportaciones".equals(forma.extra());
            default -> p.isObligatoria();
        };
    }

    /** La respuesta de "Tipo de pago": un objeto {base, extra, anualidad: {monto, mes, cantidad}}. Un texto suelto
     * ("msi", "aportaciones"…) es el formato de antes de poder combinarlas y se sigue aceptando. null si no se
     * eligió nada. */
    private static FormaPago leerFormaPago(JsonNode v) {
        if (v == null || v.isNull()) return null;
        String base;
        String extra;
        JsonNode anualidad = null;
        if (v.isTextual()) {
            String t = v.asText().trim();
            if (t.isEmpty()) return null;
            // Antes "Con anualidades" mandaba un solo monto en la pregunta del enganche: ya no alcanza para
            // programarlas, así que se pide volver a elegir con la página nueva.
            if ("annualities".equals(t)) {
                throw new ValidationException("El formulario se actualizó: recarga la página y vuelve a elegir el tipo de pago");
            }
            base = BASES_PAGO.contains(t) ? t : null;
            extra = EXTRAS_PAGO.contains(t) ? t : null;
            if (base == null && extra == null) throw new ValidationException("Elige un tipo de pago válido");
        } else if (v.isObject()) {
            base = campoTexto(v, "base");
            extra = campoTexto(v, "extra");
            anualidad = v.get("anualidad");
            if (base == null && extra == null) return null;
            if ((base != null && !BASES_PAGO.contains(base)) || (extra != null && !EXTRAS_PAGO.contains(extra))) {
                throw new ValidationException("Elige un tipo de pago válido");
            }
            if ("cash".equals(base) && extra != null) {
                throw new ValidationException("Contado no se combina con anualidades ni aportaciones");
            }
        } else {
            throw new ValidationException("Elige un tipo de pago válido");
        }
        if (!"annualities".equals(extra)) return new FormaPago(base, extra, null, null, null);

        if (anualidad == null || !anualidad.isObject()) {
            throw new ValidationException("Indica el monto, el mes y cuántas anualidades lleva la venta");
        }
        BigDecimal monto = decimal(anualidad.get("monto"), "El monto de la anualidad");
        if (monto == null || monto.signum() <= 0) throw new ValidationException("Ingresa el monto de cada anualidad");
        Integer mes = entero(anualidad.get("mes"), "El mes de la anualidad");
        if (mes == null || mes < 1 || mes > 12) throw new ValidationException("Elige el mes de las anualidades");
        Integer cantidad = entero(anualidad.get("cantidad"), "El número de anualidades");
        if (cantidad == null || cantidad < 1) throw new ValidationException("Indica cuántas anualidades lleva la venta");
        return new FormaPago(base, extra, monto, mes, cantidad);
    }

    private static String campoTexto(JsonNode v, String campo) {
        JsonNode n = v.get(campo);
        return n == null || n.isNull() || n.asText().isBlank() ? null : n.asText().trim();
    }

    /** "Con enganche · 36 MSI", "Con enganche + anualidades · 36 meses", "Con aportaciones · 24 meses", "Contado"
     * (sin plazo, solo el nombre). Mismo texto que arma el frontend en etiquetaFormaPago. */
    private static String etiquetaFormaPago(FormaPago f, Integer plazo) {
        if (f.contado()) return "Contado";
        String extra = "annualities".equals(f.extra()) ? "anualidades" : "aportaciones".equals(f.extra()) ? "aportaciones" : null;
        String nombre;
        if (f.conEntrada()) {
            nombre = ("downpayment".equals(f.base()) ? "Con enganche" : "Pago inicial") + (extra != null ? " + " + extra : "");
        } else {
            nombre = extra != null ? "Con " + extra : "Sin enganche";
        }
        if (plazo == null) return nombre;
        String unidad = extra == null && ("msi".equals(f.base()) || "downpayment".equals(f.base())) ? "MSI" : "meses";
        return nombre + " · " + plazo + " " + unidad;
    }

    /** Las anualidades como aportaciones programadas: una por año en `mes`, desde `inicio`, hasta `cantidad` y nunca
     * en el último año calendario del plazo (la misma regla de las aportaciones; ver VentaService.validarAportaciones).
     * Tampoco más de las que permite el plazo (un año de mensualidades regulares al final). */
    static List<VentaAportacionItemRequest> anualidadesComoAportaciones(
            YearMonth inicio, int plazo, int mes, BigDecimal monto, int cantidad) {
        int tope = Math.min(cantidad, Math.max(plazo / 12 - 1, 0));
        YearMonth fin = inicio.plusMonths(plazo - 1L);
        List<VentaAportacionItemRequest> lista = new ArrayList<>();
        for (int i = 0; i < plazo && lista.size() < tope; i++) {
            YearMonth m = inicio.plusMonths(i);
            if (m.getMonthValue() == mes && m.getYear() != fin.getYear()) {
                lista.add(new VentaAportacionItemRequest(m.getYear(), m.getMonthValue(), monto));
            }
        }
        return lista;
    }

    private String validarYTexto(WeFormPregunta p, JsonNode v) {
        String titulo = p.getTitulo();
        return switch (p.getTipo()) {
            case CORTA, PARRAFO -> {
                String t = v.asText().trim();
                if (t.length() > 2000) throw new ValidationException(titulo + ": es demasiado largo");
                yield t;
            }
            case NUMERO -> {
                BigDecimal n = decimal(v, titulo);
                yield n.stripTrailingZeros().toPlainString();
            }
            case FECHA -> {
                try {
                    yield LocalDate.parse(v.asText().trim()).toString();
                } catch (DateTimeParseException e) {
                    throw new ValidationException(titulo + ": la fecha no es válida");
                }
            }
            case TELEFONO -> {
                String t = v.asText().trim();
                if (t.replaceAll("\\D", "").length() < 8) throw new ValidationException(titulo + ": el teléfono no es válido");
                yield t;
            }
            case CORREO -> {
                String t = v.asText().trim().toLowerCase(Locale.ROOT);
                if (!CORREO.matcher(t).matches()) throw new ValidationException(titulo + ": el correo no es válido");
                yield t;
            }
            case PAIS -> {
                String pais = Paises.canonico(v.asText());
                if (pais == null) throw new ValidationException(titulo + ": elige un país de la lista");
                yield pais;
            }
            case OPCION_MULTIPLE, DESPLEGABLE -> {
                String t = v.asText().trim();
                if (!p.opcionesLista().contains(t)) throw new ValidationException(titulo + ": elige una de las opciones");
                yield t;
            }
            case CASILLAS -> {
                if (!v.isArray()) throw new ValidationException(titulo + ": elige una o más opciones");
                List<String> opciones = p.opcionesLista();
                List<String> elegidas = new ArrayList<>();
                for (JsonNode n : v) {
                    if (!opciones.contains(n.asText())) throw new ValidationException(titulo + ": elige solo de las opciones");
                    elegidas.add(n.asText());
                }
                yield String.join(", ", elegidas);
            }
            case LOTES -> {
                List<String> partes = new ArrayList<>();
                for (JsonNode n : v) {
                    Lote l = loteRepository.findById(n.asLong())
                            .orElseThrow(() -> new ValidationException("Un lote elegido no existe"));
                    partes.add("Mz " + l.getManzana() + " · Lote " + l.getNumeroLote()
                            + " ($" + String.format("%,.2f", LoteDto.from(l).precio()) + ")");
                }
                yield String.join("; ", partes);
            }
            case TIPO_PAGO -> {
                // Solo el nombre ("Con enganche + anualidades"): así se agrupa en el resumen de respuestas; los montos
                // quedan en la venta.
                FormaPago f = leerFormaPago(v);
                if (f == null) throw new ValidationException("Elige el tipo de pago");
                yield etiquetaFormaPago(f, null);
            }
            case APORTACIONES -> {
                List<String> partes = new ArrayList<>();
                for (VentaAportacionItemRequest a : aportaciones(v)) {
                    partes.add(String.format("%02d/%d: $%,.2f", a.mes(), a.anio(), a.monto()));
                }
                yield String.join("; ", partes);
            }
            default -> v.asText();
        };
    }

    private static Integer entero(JsonNode v, String campo) {
        if (v == null || v.isNull()) return null;
        BigDecimal n = decimal(v, campo);
        if (n.stripTrailingZeros().scale() > 0) throw new ValidationException(campo + " debe ser un número entero");
        return n.intValueExact();
    }

    private static BigDecimal decimal(JsonNode v, String campo) {
        if (v == null || v.isNull()) return null;
        try {
            BigDecimal n = new BigDecimal(v.asText().trim().replace(",", ""));
            if (n.signum() < 0) throw new ValidationException(campo + " no puede ser negativo");
            return n;
        } catch (NumberFormatException e) {
            throw new ValidationException(campo + " debe ser un número");
        }
    }

    private static List<VentaAportacionItemRequest> aportaciones(JsonNode v) {
        List<VentaAportacionItemRequest> lista = new ArrayList<>();
        if (v == null || v.isNull() || !v.isArray()) return lista;
        Set<YearMonth> vistos = new HashSet<>();
        for (JsonNode n : v) {
            if (!n.hasNonNull("anio") || !n.hasNonNull("mes") || !n.hasNonNull("monto")) {
                throw new ValidationException("Cada aportación necesita mes, año y monto");
            }
            BigDecimal monto = decimal(n.get("monto"), "El monto de la aportación");
            int anio = n.get("anio").asInt();
            int mes = n.get("mes").asInt();
            if (mes < 1 || mes > 12 || anio < 2000 || anio > 2200 || monto.signum() <= 0) {
                throw new ValidationException("Hay una aportación con datos no válidos");
            }
            if (!vistos.add(YearMonth.of(anio, mes))) throw new ValidationException("Hay dos aportaciones en el mismo mes");
            lista.add(new VentaAportacionItemRequest(anio, mes, monto));
        }
        return lista;
    }

    private static ClienteRequest datosCliente(
            Map<DestinoPregunta, JsonNode> d, String paisResidencia, String estadoDeLista) {
        LocalDate nacimiento;
        try {
            nacimiento = LocalDate.parse(texto(d, DestinoPregunta.FECHA_NACIMIENTO));
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ValidationException("La fecha de nacimiento no es válida");
        }
        return new ClienteRequest(
                texto(d, DestinoPregunta.NOMBRE), texto(d, DestinoPregunta.APELLIDO_PATERNO),
                texto(d, DestinoPregunta.APELLIDO_MATERNO), nacimiento, texto(d, DestinoPregunta.TELEFONO), null,
                minusculas(texto(d, DestinoPregunta.CORREO)), texto(d, DestinoPregunta.CURP), texto(d, DestinoPregunta.RFC),
                null, null, null, null, texto(d, DestinoPregunta.CALLE), texto(d, DestinoPregunta.COLONIA),
                texto(d, DestinoPregunta.MUNICIPIO), estadoDeLista != null ? estadoDeLista : texto(d, DestinoPregunta.ESTADO),
                texto(d, DestinoPregunta.CODIGO_POSTAL), null, null, null, null, null, null, null, null, null, null,
                paisResidencia);
    }

    private static String minusculas(String t) {
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String texto(Map<DestinoPregunta, JsonNode> d, DestinoPregunta destino) {
        JsonNode n = d.get(destino);
        return n == null || n.isNull() ? null : n.asText().trim();
    }

    // ---------------------------------------------------------------- archivos

    private static String nombreArchivo(MultipartFile archivo) {
        validarArchivo(archivo);
        String nombre = archivo.getOriginalFilename();
        return nombre == null || nombre.isBlank() ? "archivo" : nombre;
    }

    private static String extensionDe(MultipartFile archivo) {
        return validarArchivo(archivo);
    }

    /** Imagen JPG/PNG/WEBP o PDF, de hasta 2 MB; se confía en la firma del archivo, no en su nombre. */
    private static String validarArchivo(MultipartFile archivo) {
        if (archivo.getSize() > MAX_ARCHIVO_BYTES) throw new ValidationException("Cada archivo puede pesar hasta 2 MB");
        byte[] b;
        try {
            b = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el archivo", e);
        }
        if (b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if (b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) return "jpg";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E'
                && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "pdf";
        throw new ValidationException("Los archivos deben ser imagen (JPG, PNG, WEBP) o PDF");
    }

    private static void borrar(List<Path> archivos) {
        for (Path p : archivos) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignorado) {
                // sin acción: lo importante es no dejar el registro de venta a medias
            }
        }
    }
}
