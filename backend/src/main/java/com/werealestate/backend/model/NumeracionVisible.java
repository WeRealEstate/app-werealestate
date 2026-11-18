package com.werealestate.backend.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Números de venta consecutivos para la cuenta que no ve las ventas en efectivo ({@link VisibilidadVentas}), decisión
 * del usuario (2026-11-18): si S-5 es en efectivo, esa cuenta ve la S-6 real como S-5, la S-7 como S-6, etc. (cada
 * desarrollo por separado). Los demás siguen viendo el número real.
 *
 * <p>Se aplica en {@link Venta#getEtiqueta()}, así todo lo que muestra el número de una venta (Ventas, Clientes,
 * Finanzas, Comisiones, Lotes, avisos, We Forms) lo cambia sin tocar cada DTO. Lo que se GUARDA o sale hacia afuera
 * (notas del historial de lotes, la etiqueta de una venta eliminada en su comisión, los correos al cliente y a
 * finanzas) usa {@link Venta#getEtiquetaReal()} o corre dentro de {@link #sinRenumerar}. El buscador, la dirección
 * /panel/ventas/S-5 y el "siguiente número" del formulario traducen con {@link #realDe} y {@link #siguienteVisible}.
 *
 * <p>El mapa se calcula una vez por petición (una consulta de 4 columnas de todas las ventas) y se olvida al terminar
 * (WebConfig) o cuando cambian las ventas ({@link #olvidar}).
 */
public final class NumeracionVisible {

    /** Filas (id de venta, nombre del desarrollo de numeración, número en el desarrollo, modalidad de pago), en orden de
     * desarrollo y número. Lo pone VentaRepository al arrancar (ver la configuración en el paquete service). */
    public interface Cargador {
        List<Object[]> filas();
    }

    private static volatile Cargador cargador;

    private record Mapa(Map<Long, Long> visiblePorVenta, Map<String, Long> realPorVisible, Map<String, Long> cuantasPorDesarrollo) {
    }

    private static final ThreadLocal<Mapa> MAPA = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUSPENDIDA = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FORZADA = new ThreadLocal<>();

    private NumeracionVisible() {}

    public static void usarCargador(Cargador c) {
        cargador = c;
    }

    /** true si en esta petición los números se muestran consecutivos sin las ventas en efectivo. */
    public static boolean activa() {
        if (Boolean.TRUE.equals(SUSPENDIDA.get()) || cargador == null) return false;
        return Boolean.TRUE.equals(FORZADA.get()) || VisibilidadVentas.ocultaEfectivo();
    }

    /** El número que ve quien pregunta, o null si es el real (numeración normal, o una venta en efectivo). */
    public static Long visibleDe(Venta venta) {
        if (venta == null || venta.getId() == null || !activa()) return null;
        return mapa().visiblePorVenta().get(venta.getId());
    }

    /** El número real de la venta que quien pregunta ve con ese número en ese desarrollo (null si no hay ninguna).
     * Sin la numeración activa devuelve el mismo número. */
    public static Long realDe(String desarrollo, long visible) {
        if (!activa()) return visible;
        return mapa().realPorVisible().get(clave(desarrollo, visible));
    }

    /** El siguiente número que verá quien pregunta en ese desarrollo (sus ventas visibles + 1). */
    public static long siguienteVisible(String desarrollo) {
        return mapa().cuantasPorDesarrollo().getOrDefault(desarrollo, 0L) + 1;
    }

    /** Vuelve a calcular en la siguiente consulta (después de crear, modificar o eliminar una venta, o al terminar la
     * petición). */
    public static void olvidar() {
        MAPA.remove();
    }

    /** Corre algo con los números reales aunque lo pida esta cuenta: lo que sale al cliente o a finanzas. */
    public static <T> T sinRenumerar(Supplier<T> accion) {
        Boolean antes = SUSPENDIDA.get();
        SUSPENDIDA.set(Boolean.TRUE);
        try {
            return accion.get();
        } finally {
            if (antes == null) SUSPENDIDA.remove(); else SUSPENDIDA.set(antes);
        }
    }

    /** Corre algo como lo vería ese usuario, sin sesión de por medio (los avisos push se arman en segundo plano). */
    public static <T> T comoLoVe(Usuario usuario, Supplier<T> accion) {
        if (!VisibilidadVentas.ocultaEfectivoPara(usuario)) return accion.get();
        Boolean antes = FORZADA.get();
        Mapa mapaAntes = MAPA.get();
        FORZADA.set(Boolean.TRUE);
        try {
            return accion.get();
        } finally {
            if (antes == null) FORZADA.remove(); else FORZADA.set(antes);
            if (mapaAntes == null) MAPA.remove(); else MAPA.set(mapaAntes);
        }
    }

    private static String clave(String desarrollo, long numero) {
        return desarrollo + "#" + numero;
    }

    private static Mapa mapa() {
        Mapa m = MAPA.get();
        if (m != null) return m;
        Map<Long, Long> visible = new HashMap<>();
        Map<String, Long> real = new HashMap<>();
        Map<String, Long> cuantas = new HashMap<>();
        for (Object[] fila : cargador.filas()) {
            Long ventaId = (Long) fila[0];
            String desarrollo = (String) fila[1];
            Long numero = (Long) fila[2];
            ModalidadPago modalidad = (ModalidadPago) fila[3];
            if (desarrollo == null || numero == null || modalidad == ModalidadPago.EFECTIVO) continue;
            long n = cuantas.merge(desarrollo, 1L, Long::sum);
            visible.put(ventaId, n);
            real.put(clave(desarrollo, n), numero);
        }
        m = new Mapa(visible, real, cuantas);
        MAPA.set(m);
        return m;
    }
}
