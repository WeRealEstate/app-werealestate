package com.werealestate.backend.service;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.model.Usuario;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.Subscription;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificaciones push del teléfono (Web Push con llaves VAPID). Las llaves del servidor se generan
 * la primera vez que se necesitan y se guardan en push_config. Qué ya se envió a cada usuario vive
 * en push_enviada, para no repetir un aviso.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final String SUJETO = "mailto:admin@weinversiones.com";

    private final JdbcTemplate jdbc;
    private final NotificacionService notificacionService;
    private volatile nl.martijndwars.webpush.PushService cliente;
    private volatile String clavePublica;

    public PushService(JdbcTemplate jdbc, NotificacionService notificacionService) {
        this.jdbc = jdbc;
        this.notificacionService = notificacionService;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** Clave pública VAPID que el navegador necesita para suscribirse. */
    public String clavePublica() {
        inicializar();
        return clavePublica;
    }

    private synchronized void inicializar() {
        if (cliente != null) return;
        List<Map<String, Object>> filas = jdbc.queryForList("select public_key, private_key from push_config where id = 1");
        String publica;
        String privada;
        if (filas.isEmpty()) {
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("ECDSA", BouncyCastleProvider.PROVIDER_NAME);
                gen.initialize(new ECGenParameterSpec("prime256v1"));
                KeyPair par = gen.generateKeyPair();
                Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
                publica = b64.encodeToString(((ECPublicKey) par.getPublic()).getQ().getEncoded(false));
                // BigInteger puede traer un 0 al inicio: VAPID espera exactamente 32 bytes.
                byte[] d = ((ECPrivateKey) par.getPrivate()).getD().toByteArray();
                if (d.length > 32) d = java.util.Arrays.copyOfRange(d, d.length - 32, d.length);
                if (d.length < 32) {
                    byte[] relleno = new byte[32];
                    System.arraycopy(d, 0, relleno, 32 - d.length, d.length);
                    d = relleno;
                }
                privada = b64.encodeToString(d);
                jdbc.update("insert into push_config (id, public_key, private_key) values (1, ?, ?) on conflict do nothing", publica, privada);
            } catch (Exception e) {
                throw new IllegalStateException("No se pudieron generar las llaves VAPID", e);
            }
            filas = jdbc.queryForList("select public_key, private_key from push_config where id = 1");
        }
        publica = (String) filas.get(0).get("public_key");
        privada = (String) filas.get(0).get("private_key");
        try {
            cliente = new nl.martijndwars.webpush.PushService(publica, privada, SUJETO);
        } catch (Exception e) {
            throw new IllegalStateException("Llaves VAPID inválidas", e);
        }
        clavePublica = publica;
    }

    /** Registra (o reasigna) el dispositivo del usuario y marca como ya enviado lo que hoy tiene
     * pendiente, para que solo lleguen avisos nuevos y no una avalancha al activarlos. */
    @Transactional
    public void suscribir(Usuario usuario, String endpoint, String p256dh, String auth, String userAgent) {
        jdbc.update(
                "insert into push_suscripcion (usuario_id, endpoint, p256dh, auth, user_agent) values (?, ?, ?, ?, ?) "
                        + "on conflict (endpoint) do update set usuario_id = excluded.usuario_id, p256dh = excluded.p256dh, "
                        + "auth = excluded.auth, user_agent = excluded.user_agent",
                usuario.getId(), endpoint, p256dh, auth, userAgent == null ? null : userAgent.substring(0, Math.min(300, userAgent.length())));
        if (jdbc.queryForObject("select count(*) from push_enviada where usuario_id = ?", Integer.class, usuario.getId()) == 0) {
            for (NotificacionDto n : notificacionService.listarPara(usuario)) {
                jdbc.update("insert into push_enviada (usuario_id, clave) values (?, ?) on conflict do nothing", usuario.getId(), claveDe(n));
            }
        }
    }

    @Transactional
    public void desuscribir(Usuario usuario, String endpoint) {
        jdbc.update("delete from push_suscripcion where endpoint = ? and usuario_id = ?", endpoint, usuario.getId());
    }

    public boolean tieneSuscripcion(Long usuarioId, String endpoint) {
        return jdbc.queryForObject(
                "select count(*) from push_suscripcion where usuario_id = ? and endpoint = ?", Integer.class, usuarioId, endpoint) > 0;
    }

    public List<Long> usuariosConSuscripcion() {
        return jdbc.queryForList("select distinct usuario_id from push_suscripcion", Long.class);
    }

    /** Envía a los dispositivos del usuario lo que tiene pendiente y aún no se le había avisado. */
    public void enviarPendientes(Usuario usuario) {
        List<NotificacionDto> nuevas = notificacionService.listarPara(usuario).stream()
                .filter(n -> jdbc.queryForObject(
                                "select count(*) from push_enviada where usuario_id = ? and clave = ?", Integer.class, usuario.getId(), claveDe(n))
                        == 0)
                .toList();
        if (nuevas.isEmpty()) return;

        if (nuevas.size() <= 3) {
            for (NotificacionDto n : nuevas) {
                enviar(usuario.getId(), titulo(n), n.mensaje(), rutaDe(n), claveDe(n));
            }
        } else {
            enviar(usuario.getId(), "We Real Estate", "Tienes " + nuevas.size() + " notificaciones nuevas.", "/panel", "resumen");
        }
        for (NotificacionDto n : nuevas) {
            jdbc.update("insert into push_enviada (usuario_id, clave) values (?, ?) on conflict do nothing", usuario.getId(), claveDe(n));
        }
    }

    private void enviar(Long usuarioId, String titulo, String cuerpo, String url, String tag) {
        inicializar();
        String payload = "{\"title\":" + json(titulo) + ",\"body\":" + json(cuerpo) + ",\"url\":" + json(url) + ",\"tag\":" + json(tag) + "}";
        for (Map<String, Object> s : jdbc.queryForList(
                "select endpoint, p256dh, auth from push_suscripcion where usuario_id = ?", usuarioId)) {
            String endpoint = (String) s.get("endpoint");
            try {
                var respuesta = cliente.send(new Notification(
                        new Subscription(endpoint, new Subscription.Keys((String) s.get("p256dh"), (String) s.get("auth"))), payload), Encoding.AES128GCM);
                int codigo = respuesta.getStatusLine().getStatusCode();
                if (codigo == 404 || codigo == 410) {
                    jdbc.update("delete from push_suscripcion where endpoint = ?", endpoint);
                } else if (codigo >= 400) {
                    log.warn("Push rechazado ({}) para el usuario {}", codigo, usuarioId);
                }
            } catch (Exception e) {
                log.warn("No se pudo enviar push al usuario {}: {}", usuarioId, e.toString());
            }
        }
    }

    private static String json(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : texto.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String claveDe(NotificacionDto n) {
        Long id = n.leadId() != null ? n.leadId() : n.tareaId() != null ? n.tareaId() : n.eventoId() != null ? n.eventoId() : n.movimientoId();
        return NotificacionService.clave(n.tipo(), id, n.firma());
    }

    private static String titulo(NotificacionDto n) {
        return switch (n.tipo()) {
            case "SEGUIMIENTO_PENDIENTE" -> "Seguimiento pendiente";
            case "TAREA_PENDIENTE" -> "Tarea pendiente";
            case "EVENTO_PENDIENTE" -> "Recordatorio de calendario";
            case "LOTE_APARTADO" -> "Lote apartado";
            case "LOTE_DESAPARTADO" -> "Lote liberado";
            default -> "We Real Estate";
        };
    }

    private static String rutaDe(NotificacionDto n) {
        return switch (n.tipo()) {
            case "SEGUIMIENTO_PENDIENTE" -> "/panel/leads";
            case "EVENTO_PENDIENTE" -> "/panel/calendario";
            case "LOTE_APARTADO", "LOTE_DESAPARTADO" -> "/panel/lotes";
            default -> "/panel";
        };
    }
}
