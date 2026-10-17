package com.werealestate.backend.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Resuelve la IP real del cliente para los limitadores de intentos de PIN.
 *
 * <p>{@code X-Forwarded-For} lo puede escribir cualquiera, así que solo se lee cuando la conexión directa
 * viene de un proxy nuestro (loopback o red privada). Aun así no se toma la primera IP de la lista (la manda
 * el cliente y se puede inventar para esquivar el tope de 5 fallos): se recorre de derecha a izquierda y se
 * toma la primera que no sea de nuestra propia infraestructura, que es la que añadió el proxy. */
public final class IpCliente {

    private static final String CABECERA = "X-Forwarded-For";

    private IpCliente() {}

    public static String de(HttpServletRequest http) {
        return resolver(http.getRemoteAddr(), http.getHeader(CABECERA));
    }

    static String resolver(String remota, String reenviada) {
        if (reenviada == null || reenviada.isBlank() || !esProxyConfiable(remota)) return remota;
        String[] saltos = reenviada.split(",");
        for (int i = saltos.length - 1; i >= 0; i--) {
            String salto = saltos[i].trim();
            if (salto.isEmpty()) continue;
            if (!esIp(salto)) return remota;
            if (!esProxyConfiable(salto)) return salto;
        }
        return remota;
    }

    /** Solo caracteres de una IP literal: evita resolver nombres de host y claves basura en el limitador. */
    private static boolean esIp(String ip) {
        return ip != null && !ip.isBlank() && ip.matches("[0-9a-fA-F:.]+");
    }

    /** Loopback, red privada o link-local: direcciones que un cliente de internet no puede tener como origen. */
    private static boolean esProxyConfiable(String ip) {
        if (!esIp(ip)) return false;
        try {
            InetAddress a = InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
