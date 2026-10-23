package com.werealestate.backend.service;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Llamadas HTTP a Google (OAuth y API de Drive v3) con el cliente REST de Spring, sin la librería de Google. Solo
 * pide el permiso {@code drive.file}: no sensible, sin revisión de Google, y la app solo ve las carpetas y archivos
 * que ella misma crea. Las credenciales (client id y secret) vienen de variables de entorno; sin ellas la
 * integración queda apagada.
 */
@Component
public class GoogleDriveClient {

    static final String PERMISO = "https://www.googleapis.com/auth/drive.file";
    static final String TIPO_CARPETA = "application/vnd.google-apps.folder";
    private static final String AUTORIZAR = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final String API = "https://www.googleapis.com/drive/v3";
    private static final String SUBIDA = "https://www.googleapis.com/upload/drive/v3/files";

    private final RestClient http = RestClient.create();
    private final ObjectMapper json;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final String pickerApiKey;

    public GoogleDriveClient(
            ObjectMapper json,
            @Value("${app.google.client-id:}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret,
            @Value("${app.google.drive-redirect-uri:}") String redirectUri,
            @Value("${app.google.picker-api-key:}") String pickerApiKey) {
        this.json = json;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
        this.redirectUri = redirectUri == null ? "" : redirectUri.trim();
        this.pickerApiKey = pickerApiKey == null ? "" : pickerApiKey.trim();
    }

    /** Un error de Google con su código HTTP (401 = token vencido o revocado, 404 = no existe o no es de la app;
     * 0 = no hubo respuesta: sin red o Google no contestó). */
    public static class GoogleError extends RuntimeException {
        private final int status;
        private final String motivo;

        GoogleError(int status, String mensaje) {
            this(status, mensaje, null);
        }

        GoogleError(int status, String mensaje, String motivo) {
            super(mensaje);
            this.status = status;
            this.motivo = motivo;
        }

        public int status() { return status; }

        /** Para mostrar en el panel: la explicación de Google ("The OAuth client was not found.") en vez del JSON
         * completo, que se queda en el mensaje (y en el log) para reconocer el tipo de error. */
        public String motivo() {
            if (status == 401 && getMessage() != null && getMessage().contains("invalid_client")) {
                return "Google no reconoce las credenciales del servidor (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET)";
            }
            if (status == 0) return "Google no respondió (sin conexión)";
            return motivo != null && !motivo.isBlank() ? motivo : getMessage();
        }

        /** La llave ya no sirve (revocada, vencida o de otro cliente): hay que volver a conectar la cuenta. */
        public boolean accesoPerdido() { return status == 400 && getMessage() != null && getMessage().contains("invalid_grant"); }

        /** Falla de Google o de la red, no del archivo: sin red, 5xx, 429 o límite de uso. No debe contar como intento
         * fallido de cada archivo (si no, una caída de unas horas haría que todos se rindieran). */
        public boolean esTemporal() {
            if (status == 0 || status == 429 || status >= 500) return true;
            String m = getMessage() == null ? "" : getMessage();
            return status == 403 && (m.contains("ateLimitExceeded") || m.contains("quotaExceeded") || m.contains("storageQuotaExceeded"));
        }
    }

    public record Tokens(String accessToken, String refreshToken, long expiraEnSegundos) {}

    public boolean configurado() {
        return !clientId.isEmpty() && !clientSecret.isEmpty() && !redirectUri.isEmpty();
    }

    /** El selector de Google (Picker) pide además una clave de API del navegador. */
    public boolean selectorConfigurado() {
        return configurado() && !pickerApiKey.isEmpty();
    }

    public String pickerApiKey() {
        return pickerApiKey;
    }

    /** El "App ID" que pide el selector para que la carpeta elegida quede autorizada para esta app: es el número de
     * proyecto de Google Cloud, que es justo lo que va antes del primer guion en el client id. */
    public String appId() {
        int guion = clientId.indexOf('-');
        return guion > 0 ? clientId.substring(0, guion) : "";
    }

    /** La pantalla de Google para elegir la cuenta y aceptar. {@code prompt=consent} y {@code access_type=offline}
     * aseguran que regrese un refresh token aunque la cuenta ya hubiera aceptado antes. */
    public String urlDeAutorizacion(String state) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("client_id", clientId);
        p.put("redirect_uri", redirectUri);
        p.put("response_type", "code");
        p.put("scope", PERMISO);
        p.put("access_type", "offline");
        p.put("prompt", "consent");
        p.put("state", state);
        StringBuilder url = new StringBuilder(AUTORIZAR).append('?');
        p.forEach((k, v) -> url.append(k).append('=').append(URLEncoder.encode(v, StandardCharsets.UTF_8)).append('&'));
        return url.substring(0, url.length() - 1);
    }

    public Tokens canjearCodigo(String code) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("code", code);
        f.add("client_id", clientId);
        f.add("client_secret", clientSecret);
        f.add("redirect_uri", redirectUri);
        f.add("grant_type", "authorization_code");
        JsonNode r = postForm(TOKEN, f);
        return new Tokens(texto(r, "access_token"), texto(r, "refresh_token"), r.path("expires_in").asLong(3600));
    }

    public Tokens renovar(String refreshToken) {
        MultiValueMap<String, String> f = new LinkedMultiValueMap<>();
        f.add("refresh_token", refreshToken);
        f.add("client_id", clientId);
        f.add("client_secret", clientSecret);
        f.add("grant_type", "refresh_token");
        JsonNode r = postForm(TOKEN, f);
        return new Tokens(texto(r, "access_token"), refreshToken, r.path("expires_in").asLong(3600));
    }

    /** Correo de la cuenta conectada, para mostrar en el panel con cuál se está guardando. */
    public String correoDeLaCuenta(String accessToken) {
        JsonNode r = get(API + "/about?fields=user(emailAddress)", accessToken);
        return r.path("user").path("emailAddress").asText(null);
    }

    /** Crea una carpeta. {@code etiqueta} se guarda como appProperty (invisible para el usuario) para poder
     * encontrarla después con {@link #buscarPorEtiqueta} y no crear otra igual si algo falló a la mitad. */
    public String crearCarpeta(String accessToken, String nombre, String padreId, Map<String, String> etiqueta) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", nombre);
        meta.put("mimeType", TIPO_CARPETA);
        if (padreId != null) meta.put("parents", List.of(padreId));
        if (etiqueta != null && !etiqueta.isEmpty()) meta.put("appProperties", etiqueta);
        try {
            String respuesta = http.post()
                    .uri(API + "/files?fields=id")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json.writeValueAsString(meta))
                    .retrieve()
                    .body(String.class);
            return texto(json.readTree(respuesta), "id");
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (ResourceAccessException e) {
            throw sinRespuesta(e);
        }
    }

    /** El id de un archivo o carpeta de la app que lleva esa etiqueta (appProperty) y no está en la papelera, o null. */
    public String buscarPorEtiqueta(String accessToken, String clave, String valor) {
        String q = "appProperties has { key='" + clave + "' and value='" + valor + "' } and trashed = false";
        JsonNode r = get(API + "/files?pageSize=1&fields=files(id)&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8), accessToken);
        JsonNode files = r.path("files");
        return files.isArray() && !files.isEmpty() ? texto(files.get(0), "id") : null;
    }

    /** Datos de una carpeta: para revisar la que eligió el admin (que exista, sea carpeta y se pueda escribir). */
    public record Carpeta(String id, String nombre, boolean esCarpeta, boolean enPapelera, boolean sePuedenAgregar) {}

    public Carpeta carpeta(String accessToken, String id) {
        JsonNode r = get(API + "/files/" + id + "?fields=id,name,mimeType,trashed,capabilities(canAddChildren)", accessToken);
        return new Carpeta(
                texto(r, "id"),
                texto(r, "name"),
                TIPO_CARPETA.equals(texto(r, "mimeType")),
                r.path("trashed").asBoolean(false),
                r.path("capabilities").path("canAddChildren").asBoolean(false));
    }

    /** true si la carpeta o archivo sigue existiendo y no está en la papelera. */
    public boolean existe(String accessToken, String id) {
        try {
            JsonNode r = get(API + "/files/" + id + "?fields=id,trashed", accessToken);
            return !r.path("trashed").asBoolean(false);
        } catch (GoogleError e) {
            if (e.status() == 404) return false;
            throw e;
        }
    }

    /** Sube un archivo a una carpeta en una sola petición (multipart/related: metadatos + contenido) y devuelve su id.
     * {@code etiqueta}: appProperty para reconocerlo después (ver {@link #buscarPorEtiqueta}). */
    public String subirArchivo(
            String accessToken, String nombre, String carpetaId, String tipoMime, byte[] contenido, Map<String, String> etiqueta) {
        String limite = "we-" + UUID.randomUUID();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("name", nombre);
        meta.put("parents", List.of(carpetaId));
        if (etiqueta != null && !etiqueta.isEmpty()) meta.put("appProperties", etiqueta);
        ByteArrayOutputStream cuerpo = new ByteArrayOutputStream(contenido.length + 512);
        escribir(cuerpo, "--" + limite + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n");
        escribir(cuerpo, json.writeValueAsString(meta));
        escribir(cuerpo, "\r\n--" + limite + "\r\nContent-Type: " + tipoMime + "\r\n\r\n");
        cuerpo.writeBytes(contenido);
        escribir(cuerpo, "\r\n--" + limite + "--");
        try {
            String respuesta = http.post()
                    .uri(SUBIDA + "?uploadType=multipart&fields=id")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .contentType(MediaType.parseMediaType("multipart/related; boundary=" + limite))
                    .body(cuerpo.toByteArray())
                    .retrieve()
                    .body(String.class);
            return texto(json.readTree(respuesta), "id");
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (ResourceAccessException e) {
            throw sinRespuesta(e);
        }
    }

    public byte[] descargar(String accessToken, String archivoId) {
        try {
            return http.get()
                    .uri(API + "/files/" + archivoId + "?alt=media")
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .body(byte[].class);
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (ResourceAccessException e) {
            throw sinRespuesta(e);
        }
    }

    public static String urlDeCarpeta(String carpetaId) {
        return "https://drive.google.com/drive/folders/" + carpetaId;
    }

    // ---------------------------------------------------------------- utilidades

    private JsonNode get(String url, String accessToken) {
        try {
            return json.readTree(http.get().uri(URI.create(url)).headers(h -> h.setBearerAuth(accessToken)).retrieve().body(String.class));
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (ResourceAccessException e) {
            throw sinRespuesta(e);
        }
    }

    private JsonNode postForm(String url, MultiValueMap<String, String> form) {
        try {
            String r = http.post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(String.class);
            return json.readTree(r == null || r.isBlank() ? "{}" : r);
        } catch (RestClientResponseException e) {
            throw error(e);
        } catch (ResourceAccessException e) {
            throw sinRespuesta(e);
        }
    }

    /** El cuerpo de error de Google trae el motivo ("invalid_grant", "File not found"…); se guarda recortado. */
    private GoogleError error(RestClientResponseException e) {
        HttpStatusCode codigo = e.getStatusCode();
        String cuerpo = e.getResponseBodyAsString();
        String mensaje = cuerpo == null || cuerpo.isBlank() ? e.getMessage() : cuerpo.replaceAll("\\s+", " ");
        if (mensaje != null && mensaje.length() > 400) mensaje = mensaje.substring(0, 400);
        return new GoogleError(codigo.value(), mensaje, motivoDe(cuerpo));
    }

    /** "error_description" (OAuth) o "error.message" (API de Drive) del cuerpo de error, si viene en JSON. */
    private String motivoDe(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) return null;
        try {
            JsonNode n = json.readTree(cuerpo);
            String d = texto(n, "error_description");
            if (d != null) return d;
            JsonNode err = n.get("error");
            return err != null && err.isObject() ? texto(err, "message") : null;
        } catch (RuntimeException noEsJson) {
            return null;
        }
    }

    private static GoogleError sinRespuesta(ResourceAccessException e) {
        return new GoogleError(0, "Sin respuesta de Google: " + e.getMessage());
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n.get(campo);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static void escribir(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
