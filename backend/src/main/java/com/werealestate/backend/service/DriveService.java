package com.werealestate.backend.service;

import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.WeFormRespuesta;
import com.werealestate.backend.model.WeFormRespuestaItem;
import com.werealestate.backend.repository.ClienteRepository;
import com.werealestate.backend.repository.WeFormRespuestaItemRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import com.werealestate.backend.service.GoogleDriveClient.GoogleError;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Archivos de Registrar venta (We Forms) en Google Drive. El admin conecta una vez la cuenta (OAuth, permiso
 * {@code drive.file}) y elige con el selector de Google la carpeta existente donde van los expedientes. Desde entonces
 * {@link DriveSyncScheduler} sube cada archivo pendiente —los nuevos y los de registros anteriores— a
 * "carpeta elegida › #0012 · Nombre del cliente". Ya confirmado por Google, el archivo se borra del servidor y el panel
 * lo lee de Drive.
 *
 * <p>Reglas para no entrar en bucles ni dejar cosas a medias:
 * <ul>
 *   <li>Cada vuelta sube como máximo {@link #POR_VUELTA} archivos y solo llama a Google si alguno ya "le toca".</li>
 *   <li>Un archivo que falla espera 2, 4, 8… minutos (hasta 6 h) y a los {@link #MAX_INTENTOS} se rinde hasta que el
 *       admin pulse "Reintentar".</li>
 *   <li>Las fallas de Google o de la red (sin respuesta, 5xx, 429, límites) detienen la vuelta sin contar como fallo
 *       de cada archivo: una caída de horas no hace que todos se rindan.</li>
 *   <li>Carpetas y archivos llevan una etiqueta interna (appProperty) con el entorno y el id; antes de crear o subir se
 *       busca esa etiqueta, así un corte a la mitad nunca deja carpetas o archivos repetidos.</li>
 *   <li>Si la carpeta elegida desaparece, las subidas se pausan (no se crea otra por su cuenta) hasta elegir otra.</li>
 *   <li>No se conecta otra cuenta si ya hay archivos subidos en la anterior (quedarían inaccesibles).</li>
 * </ul>
 *
 * <p>La configuración (una sola fila, {@code drive_config}) se maneja con SQL directo, como {@code push_config}.
 */
@Service
public class DriveService {

    private static final Logger log = LoggerFactory.getLogger(DriveService.class);

    /** Después de tantos fallos un archivo deja de reintentarse solo; el admin puede pedir "Reintentar". */
    static final int MAX_INTENTOS = 8;
    /** Archivos por vuelta del proceso (cada minuto): poco para no saturar ni tardar en una sola vuelta. */
    private static final int POR_VUELTA = 25;
    private static final long VIGENCIA_STATE_SEGUNDOS = 15 * 60;
    private static final String ETIQUETA_ARCHIVO = "weItem";
    private static final String ETIQUETA_CLIENTE = "weCliente";

    private final GoogleDriveClient google;
    private final JdbcTemplate jdbc;
    private final WeFormRespuestaItemRepository itemRepository;
    private final ClienteRepository clienteRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TransactionTemplate tx;
    private final String uploadsPrivadosDir;
    /** "produccion" o "local": va en las etiquetas para que pruebas y producción en la misma cuenta no se crucen. */
    private final String entorno;

    /** "state" de OAuth emitidos al pulsar Conectar (contra falsificaciones): valor → cuándo vence. */
    private final Map<String, Instant> statesPendientes = new ConcurrentHashMap<>();
    private final SecureRandom aleatorio = new SecureRandom();
    private final AtomicBoolean sincronizando = new AtomicBoolean(false);

    private volatile String accessToken;
    private volatile Instant accessTokenVence = Instant.EPOCH;

    public DriveService(
            GoogleDriveClient google,
            JdbcTemplate jdbc,
            WeFormRespuestaItemRepository itemRepository,
            ClienteRepository clienteRepository,
            CurrentUserProvider currentUserProvider,
            TransactionTemplate tx,
            @Value("${app.uploads-privados.dir:uploads-privados}") String uploadsPrivadosDir,
            @Value("${app.google.drive-entorno:produccion}") String entorno) {
        this.google = google;
        this.jdbc = jdbc;
        this.itemRepository = itemRepository;
        this.clienteRepository = clienteRepository;
        this.currentUserProvider = currentUserProvider;
        this.tx = tx;
        this.uploadsPrivadosDir = uploadsPrivadosDir;
        this.entorno = entorno == null || entorno.isBlank() ? "produccion" : entorno.trim().replaceAll("[^A-Za-z0-9_-]", "");
    }

    public record Estado(
            boolean configurado,
            boolean selectorConfigurado,
            boolean conectado,
            String cuenta,
            LocalDateTime conectadoEn,
            String carpetaNombre,
            String carpetaUrl,
            long pendientes,
            long subidos,
            long fallidos,
            String ultimoError,
            LocalDateTime ultimoErrorEn) {}

    /** Lo que necesita el selector de Google en el navegador del admin (el token vive una hora). */
    public record DatosSelector(String accessToken, String apiKey, String appId) {}

    private record Config(
            String refreshToken,
            String cuentaEmail,
            LocalDateTime conectadoEn,
            String carpetaId,
            String carpetaNombre,
            String ultimoError,
            LocalDateTime ultimoErrorEn) {

        boolean conectado() {
            return refreshToken != null && !refreshToken.isBlank();
        }
    }

    // ---------------------------------------------------------------- panel (solo admin)

    public Estado estado() {
        exigirAdmin();
        Config c = config();
        return new Estado(
                google.configurado(),
                google.selectorConfigurado(),
                c.conectado(),
                c.cuentaEmail(),
                c.conectadoEn(),
                c.carpetaNombre(),
                c.carpetaId() == null ? null : GoogleDriveClient.urlDeCarpeta(c.carpetaId()),
                itemRepository.contarPendientesDeDrive(MAX_INTENTOS),
                itemRepository.contarSubidosADrive(),
                itemRepository.contarFallidosDeDrive(MAX_INTENTOS),
                c.ultimoError(),
                c.ultimoErrorEn());
    }

    /** La dirección de Google a la que el navegador del admin va para elegir la cuenta y aceptar. */
    public String urlParaConectar() {
        exigirAdmin();
        if (!google.configurado()) {
            throw new ConflictException(
                    "Faltan las credenciales de Google en el servidor (GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET y GOOGLE_DRIVE_REDIRECT_URI)");
        }
        Instant ahora = Instant.now();
        statesPendientes.values().removeIf(vence -> vence.isBefore(ahora));
        byte[] bytes = new byte[24];
        aleatorio.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        statesPendientes.put(state, ahora.plusSeconds(VIGENCIA_STATE_SEGUNDOS));
        return google.urlDeAutorizacion(state);
    }

    /**
     * Google regresa aquí (sin sesión: es una redirección del navegador). El {@code state} prueba que el admin pulsó
     * Conectar hace poco y solo sirve una vez. Devuelve a dónde mandar al navegador de vuelta en el panel.
     */
    public String completarConexion(String code, String state, String error) {
        String volver = "/panel/we-forms?drive=";
        Instant vence = state == null ? null : statesPendientes.remove(state);
        if (vence == null || vence.isBefore(Instant.now())) return volver + "vencido";
        if (error != null || code == null) return volver + "cancelado";
        try {
            GoogleDriveClient.Tokens tokens = google.canjearCodigo(code);
            if (tokens.refreshToken() == null) return volver + "error";
            String correo = google.correoDeLaCuenta(tokens.accessToken());
            Config anterior = config();
            boolean otraCuenta = anterior.cuentaEmail() != null && correo != null
                    && !anterior.cuentaEmail().equalsIgnoreCase(correo);
            // Los archivos ya subidos viven en la cuenta anterior: con otra, el panel ya no podría abrirlos.
            if (otraCuenta && itemRepository.contarSubidosADrive() > 0) return volver + "otra-cuenta";
            tx.executeWithoutResult(s -> {
                if (otraCuenta) {
                    jdbc.update("UPDATE drive_config SET carpeta_id = NULL, carpeta_nombre = NULL WHERE id = 1");
                    clienteRepository.quitarEnlacesAutomaticosDrive();
                    clienteRepository.olvidarCarpetasDrive();
                }
                jdbc.update("""
                        UPDATE drive_config SET refresh_token = ?, cuenta_email = ?, conectado_en = ?,
                               ultimo_error = NULL, ultimo_error_en = NULL WHERE id = 1""",
                        tokens.refreshToken(), correo, LocalDateTime.now());
            });
            guardarAccessToken(tokens);
            return volver + "conectado";
        } catch (RuntimeException e) {
            log.warn("No se pudo completar la conexión con Google Drive: {}", e.toString());
            return volver + "error";
        }
    }

    /** Para abrir el selector de Google en el navegador del admin. */
    public DatosSelector datosSelector() {
        exigirAdmin();
        if (!google.selectorConfigurado()) {
            throw new ConflictException("Falta la clave del selector de Google en el servidor (GOOGLE_PICKER_API_KEY)");
        }
        Config c = exigirConectado();
        return new DatosSelector(tokenParaPanel(c), google.pickerApiKey(), google.appId());
    }

    /** Guarda la carpeta que el admin eligió con el selector, después de revisar que exista y se pueda escribir. */
    public Estado elegirCarpeta(String carpetaId) {
        exigirAdmin();
        if (carpetaId == null || !carpetaId.matches("[A-Za-z0-9_-]{5,100}")) {
            throw new ValidationException("Esa carpeta no es válida");
        }
        Config c = exigirConectado();
        GoogleDriveClient.Carpeta carpeta;
        try {
            carpeta = google.carpeta(tokenParaPanel(c), carpetaId);
        } catch (GoogleError e) {
            if (e.status() == 404) {
                throw new ValidationException("La app no tiene acceso a esa carpeta: elígela con el botón \"Elegir carpeta\"");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Drive no respondió; intenta de nuevo");
        }
        if (!carpeta.esCarpeta()) throw new ValidationException("Elige una carpeta, no un archivo");
        if (carpeta.enPapelera()) throw new ValidationException("Esa carpeta está en la papelera de Drive");
        if (!carpeta.sePuedenAgregar()) throw new ValidationException("No se pueden agregar archivos a esa carpeta");
        jdbc.update("UPDATE drive_config SET carpeta_id = ?, carpeta_nombre = ?, ultimo_error = NULL, ultimo_error_en = NULL WHERE id = 1",
                carpeta.id(), recortar(carpeta.nombre(), 300));
        sincronizarEnSegundoPlano();
        return estado();
    }

    /**
     * Olvida la conexión en este servidor. A propósito NO revoca el acceso en Google: no está garantizado que al volver
     * a conectar la app recupere el acceso a los archivos que ya subió, y sin él el panel no podría abrirlos. Para
     * quitarle el acceso del todo se hace desde la cuenta de Google (Seguridad › Apps con acceso).
     */
    public void desconectar() {
        exigirAdmin();
        jdbc.update("UPDATE drive_config SET refresh_token = NULL WHERE id = 1");
        olvidarAccessToken();
    }

    /** Los archivos que se rindieron (demasiados fallos) vuelven a intentarse. */
    public Estado reintentarFallidos() {
        exigirAdmin();
        tx.executeWithoutResult(s -> itemRepository.reiniciarIntentosDeDrive());
        sincronizarEnSegundoPlano();
        return estado();
    }

    /** "Subir ahora": corre una vuelta en segundo plano y responde de inmediato (subir 25 archivos puede tardar más
     * de lo que espera el navegador). Si ya hay una vuelta corriendo, no inicia otra. */
    public Estado sincronizarAhora() {
        exigirAdmin();
        sincronizarEnSegundoPlano();
        return estado();
    }

    private void sincronizarEnSegundoPlano() {
        Thread.ofVirtual().name("drive-sync-manual").start(() -> {
            try {
                sincronizar();
            } catch (RuntimeException e) {
                log.warn("Falló la subida a Google Drive: {}", e.toString());
            }
        });
    }

    // ---------------------------------------------------------------- lectura desde el panel

    /** El contenido de un archivo ya subido (para verlo o descargarlo desde el detalle del registro de venta). */
    public byte[] descargar(String archivoId) {
        Config c = config();
        if (!c.conectado()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Este archivo está en Google Drive y la cuenta no está conectada: reconéctala en We Forms");
        }
        try {
            try {
                return google.descargar(token(c), archivoId);
            } catch (GoogleError e) {
                if (e.status() != 401) throw e;
                olvidarAccessToken(); // el token venció antes de tiempo: uno nuevo y otro intento
                return google.descargar(token(c), archivoId);
            }
        } catch (GoogleError e) {
            if (e.status() == 404) throw new ResourceNotFoundException("El archivo ya no está en Google Drive");
            if (e.accesoPerdido()) {
                marcarDesconectado();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Google ya no acepta la conexión: vuelve a conectar la cuenta en We Forms");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Drive no respondió; intenta de nuevo");
        }
    }

    // ---------------------------------------------------------------- subida en segundo plano

    /** Una vuelta: sube hasta {@link #POR_VUELTA} archivos pendientes a los que ya les toca. */
    public void sincronizar() {
        if (!google.configurado() || !sincronizando.compareAndSet(false, true)) return;
        try {
            Config c = config();
            if (!c.conectado() || c.carpetaId() == null) return; // sin cuenta o sin carpeta elegida: esperan en el servidor

            // Sin llamar a Google: ¿hay alguno al que ya le toque? (los que fallaron esperan su turno)
            List<Long> ids = itemRepository.idsPendientesDeDrive(MAX_INTENTOS, PageRequest.of(0, POR_VUELTA * 2));
            if (ids.isEmpty()) return;
            List<Long> tocan = tx.execute(s -> itemRepository.findAllById(ids).stream()
                    .filter(DriveService::leToca)
                    .sorted(Comparator.comparing(WeFormRespuestaItem::getId))
                    .map(WeFormRespuestaItem::getId)
                    .limit(POR_VUELTA)
                    .toList());
            if (tocan == null || tocan.isEmpty()) return;

            String token = token(c);
            if (!carpetaDestinoSirve(token, c)) return;

            int subidos = 0;
            for (Long id : tocan) {
                try {
                    if (subirArchivo(id, token, c.carpetaId())) subidos++;
                } catch (GoogleError e) {
                    if (e.accesoPerdido()) {
                        marcarDesconectado();
                        return;
                    }
                    if (e.status() == 401) {
                        olvidarAccessToken();
                        guardarError("Google rechazó el acceso (401); se reintenta con un token nuevo");
                        return;
                    }
                    if (e.esTemporal()) {
                        guardarError("Google Drive no está respondiendo (" + e.motivo() + "); se reintenta en un minuto");
                        return;
                    }
                    registrarFallo(id, "Google Drive " + e.status() + ": " + e.motivo());
                } catch (IOException | RuntimeException e) {
                    registrarFallo(id, e.toString());
                }
            }
            if (subidos > 0) {
                log.info("Google Drive: {} archivo(s) de registros de venta subido(s)", subidos);
                if (c.ultimoError() != null) limpiarError();
            }
        } catch (GoogleError e) {
            if (e.accesoPerdido()) {
                marcarDesconectado();
            } else {
                log.warn("Google Drive: {}", e.getMessage());
                guardarError(e.motivo());
            }
        } catch (RuntimeException e) {
            guardarError(e.toString());
        } finally {
            sincronizando.set(false);
        }
    }

    /** Si la carpeta elegida ya no existe o no se puede escribir, se pausa todo (sin crear otra por su cuenta) y se
     * avisa en el panel para que el admin elija otra. */
    private boolean carpetaDestinoSirve(String token, Config c) {
        GoogleDriveClient.Carpeta carpeta;
        try {
            carpeta = google.carpeta(token, c.carpetaId());
        } catch (GoogleError e) {
            if (e.status() != 404) throw e;
            carpeta = null;
        }
        if (carpeta != null && carpeta.esCarpeta() && !carpeta.enPapelera() && carpeta.sePuedenAgregar()) return true;
        jdbc.update("UPDATE drive_config SET carpeta_id = NULL, ultimo_error = ?, ultimo_error_en = ? WHERE id = 1",
                "La carpeta \"" + (c.carpetaNombre() == null ? "elegida" : c.carpetaNombre())
                        + "\" ya no existe en Drive o no se puede escribir en ella: elige otra",
                LocalDateTime.now());
        return false;
    }

    /** Lo que hace falta de un archivo para subirlo, leído en una transacción corta (sin llamar a Google dentro). */
    private record Pendiente(
            Long itemId, Path ruta, String extension, String nombre, Long clienteId, String carpetaCliente, String nombreCarpeta) {}

    /** Sube un archivo. false si ya no aplica o no se pudo (y quedó registrado). */
    private boolean subirArchivo(Long itemId, String token, String destino) throws IOException {
        Pendiente p = tx.execute(s -> leerPendiente(itemId));
        if (p == null) return false;
        String etiqueta = entorno + "-" + itemId;

        // ¿Ya está en Drive? (se subió pero el sistema se cortó antes de anotarlo): se anota y listo, sin duplicar.
        String archivoId = google.buscarPorEtiqueta(token, ETIQUETA_ARCHIVO, etiqueta);
        if (archivoId == null) {
            if (!Files.exists(p.ruta())) {
                rendirse(itemId, "El archivo ya no está en el servidor ni en Drive");
                return false;
            }
            String carpeta = carpetaDelCliente(p, token, destino);
            try {
                archivoId = google.subirArchivo(token, p.nombre(), carpeta,
                        WeFormService.mediaTypeDe(p.extension()).toString(), Files.readAllBytes(p.ruta()),
                        Map.of(ETIQUETA_ARCHIVO, etiqueta));
            } catch (GoogleError e) {
                // La carpeta del cliente ya no existe (la borraron): se olvida y en el reintento se busca o crea otra.
                if (e.status() == 404 && p.clienteId() != null) {
                    tx.executeWithoutResult(s -> clienteRepository.findById(p.clienteId())
                            .ifPresent(cl -> cl.olvidarCarpetaDrive(GoogleDriveClient.urlDeCarpeta(cl.getDriveCarpetaId()))));
                }
                throw e;
            }
        }

        String subido = archivoId;
        tx.executeWithoutResult(s -> itemRepository.findById(itemId).ifPresent(i -> i.marcarSubidoADrive(subido)));
        // Ya confirmado por Google y anotado: el servidor deja de guardar su copia.
        try {
            Files.deleteIfExists(p.ruta());
        } catch (IOException e) {
            log.warn("Subido a Drive pero no se pudo borrar {} del servidor: {}", p.ruta(), e.toString());
        }
        return true;
    }

    /** La carpeta del cliente: la guardada; si no hay, la que lleve su etiqueta (creada antes de un corte) y si no,
     * una nueva dentro de la carpeta elegida. Se guarda en el cliente en cuanto existe. */
    private String carpetaDelCliente(Pendiente p, String token, String destino) {
        if (p.clienteId() == null) return destino;
        if (p.carpetaCliente() != null) return p.carpetaCliente();
        String etiqueta = entorno + "-" + p.clienteId();
        String carpeta = google.buscarPorEtiqueta(token, ETIQUETA_CLIENTE, etiqueta);
        if (carpeta == null) {
            carpeta = google.crearCarpeta(token, p.nombreCarpeta(), destino, Map.of(ETIQUETA_CLIENTE, etiqueta));
        }
        String id = carpeta;
        tx.executeWithoutResult(s -> clienteRepository.findById(p.clienteId())
                .ifPresent(cl -> cl.asignarCarpetaDrive(id, GoogleDriveClient.urlDeCarpeta(id))));
        return carpeta;
    }

    private Pendiente leerPendiente(Long itemId) {
        WeFormRespuestaItem item = itemRepository.findById(itemId).orElse(null);
        if (item == null || item.getArchivoExtension() == null || item.getDriveArchivoId() != null) return null;
        if (item.getDriveIntentos() >= MAX_INTENTOS) return null;
        WeFormRespuesta r = item.getRespuesta();
        Cliente cliente = r.getCliente();
        return new Pendiente(
                item.getId(),
                WeFormService.rutaArchivo(uploadsPrivadosDir, r.getId(), item.getId(), item.getArchivoExtension()),
                item.getArchivoExtension(),
                nombreDelArchivo(r, item),
                cliente == null ? null : cliente.getId(),
                cliente == null ? null : cliente.getDriveCarpetaId(),
                cliente == null ? null : nombreDeCarpeta(cliente));
    }

    /** Después de cada fallo espera más antes de reintentar: 2, 4, 8… minutos, hasta 6 horas. */
    static boolean leToca(WeFormRespuestaItem item) {
        if (item.getDriveIntentos() == 0 || item.getDriveUltimoIntento() == null) return true;
        long espera = Math.min(1L << Math.min(item.getDriveIntentos(), 20), 360);
        return item.getDriveUltimoIntento().plusMinutes(espera).isBefore(LocalDateTime.now());
    }

    /** "#0012 · Laura Méndez Ruiz" (por número de cliente: renombrarlo o un homónimo no la confunden). */
    static String nombreDeCarpeta(Cliente c) {
        String nombre = recortar(c.nombreCompleto(), 180);
        return c.getNumero() == null ? nombre : String.format("#%04d · %s", c.getNumero(), nombre);
    }

    /** "Venta #0148 · INE frente.jpg"; si la venta se eliminó, "Registro 37 · INE frente.jpg". */
    static String nombreDelArchivo(WeFormRespuesta r, WeFormRespuestaItem item) {
        String titulo = recortar(item.getPreguntaTitulo(), 150);
        String prefijo = r.getVenta() != null && r.getVenta().getNumero() != null
                ? String.format("Venta #%04d", r.getVenta().getNumero())
                : "Registro " + r.getId();
        return prefijo + " · " + titulo + "." + item.getArchivoExtension();
    }

    private static String recortar(String s, int max) {
        String t = s == null ? "" : s.trim().replaceAll("\\s+", " ");
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    private void registrarFallo(Long itemId, String motivo) {
        log.warn("Google Drive: no se pudo subir el archivo {}: {}", itemId, motivo);
        tx.executeWithoutResult(s -> itemRepository.findById(itemId).ifPresent(i -> i.registrarFalloDrive(motivo)));
    }

    /** Un archivo que ya no se puede subir nunca (no está en el servidor ni en Drive): sale de los pendientes. */
    private void rendirse(Long itemId, String motivo) {
        tx.executeWithoutResult(s -> itemRepository.findById(itemId).ifPresent(i -> {
            while (i.getDriveIntentos() < MAX_INTENTOS) i.registrarFalloDrive(motivo);
        }));
    }

    // ---------------------------------------------------------------- tokens y configuración

    private String token(Config c) {
        if (accessToken != null && Instant.now().isBefore(accessTokenVence)) return accessToken;
        GoogleDriveClient.Tokens t = google.renovar(c.refreshToken());
        guardarAccessToken(t);
        return t.accessToken();
    }

    /** El token para una acción del panel; si Google ya no acepta la conexión, se marca y se avisa claro. */
    private String tokenParaPanel(Config c) {
        try {
            return token(c);
        } catch (GoogleError e) {
            if (e.accesoPerdido()) {
                marcarDesconectado();
                throw new ConflictException("Google ya no acepta la conexión: vuelve a conectar la cuenta");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google no respondió; intenta de nuevo");
        }
    }

    private void guardarAccessToken(GoogleDriveClient.Tokens t) {
        accessToken = t.accessToken();
        // Un minuto de margen para no usarlo justo cuando vence.
        accessTokenVence = Instant.now().plusSeconds(Math.max(t.expiraEnSegundos() - 60, 60));
    }

    private void olvidarAccessToken() {
        accessToken = null;
        accessTokenVence = Instant.EPOCH;
    }

    /** Google ya no acepta la llave (quitaron el acceso, cambió la cuenta o caducó): hay que volver a conectar. */
    private void marcarDesconectado() {
        olvidarAccessToken();
        jdbc.update("UPDATE drive_config SET refresh_token = NULL, ultimo_error = ?, ultimo_error_en = ? WHERE id = 1",
                "Google ya no acepta la conexión: vuelve a conectar la cuenta de Drive", LocalDateTime.now());
    }

    private void guardarError(String mensaje) {
        log.warn("Google Drive: {}", mensaje);
        String m = mensaje != null && mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje;
        jdbc.update("UPDATE drive_config SET ultimo_error = ?, ultimo_error_en = ? WHERE id = 1", m, LocalDateTime.now());
    }

    private void limpiarError() {
        jdbc.update("UPDATE drive_config SET ultimo_error = NULL, ultimo_error_en = NULL WHERE id = 1");
    }

    private Config exigirConectado() {
        Config c = config();
        if (!c.conectado()) throw new ConflictException("Primero conecta la cuenta de Google Drive");
        return c;
    }

    private Config config() {
        List<Config> filas = jdbc.query("""
                SELECT refresh_token, cuenta_email, conectado_en, carpeta_id, carpeta_nombre, ultimo_error, ultimo_error_en
                FROM drive_config WHERE id = 1""",
                (rs, n) -> new Config(
                        rs.getString("refresh_token"),
                        rs.getString("cuenta_email"),
                        rs.getObject("conectado_en", LocalDateTime.class),
                        rs.getString("carpeta_id"),
                        rs.getString("carpeta_nombre"),
                        rs.getString("ultimo_error"),
                        rs.getObject("ultimo_error_en", LocalDateTime.class)));
        if (!filas.isEmpty()) return filas.get(0);
        // La fila se crea la primera vez que se necesita; luego solo se actualiza.
        jdbc.update("INSERT INTO drive_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING");
        return new Config(null, null, null, null, null, null, null);
    }

    private Usuario exigirAdmin() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (actual.getRol() != Role.ADMIN) {
            throw new ForbiddenOperationException("Solo el administrador puede manejar la conexión con Google Drive");
        }
        return actual;
    }
}
