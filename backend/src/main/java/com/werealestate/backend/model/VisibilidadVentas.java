package com.werealestate.backend.model;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ventas en efectivo ocultas para una cuenta en particular (decisión de la empresa, 2026-11-18): el usuario
 * «We Real Estate (Dirección)» no debe ver ninguna venta cuya modalidad de pago sea {@link ModalidadPago#EFECTIVO},
 * en ninguna parte de la app (Ventas, Clientes, Finanzas, Comisiones, egresos de comisión, Score, Lotes, avisos,
 * reportes). Para él es como si esas ventas no existieran. Las ventas sin modalidad definida sí las ve.
 *
 * <p>Se decide en el servidor, por el correo de la sesión en curso (el principal que pone JwtAuthFilter), igual que
 * {@link VisibilidadLote} decide por el rol: así ningún endpoint lo deja salir por descuido. Es fijo en el código por
 * decisión del usuario (no hay casilla en Usuarios); si algún día se necesita para otra cuenta, aquí es donde cambia.
 */
public final class VisibilidadVentas {

    /** La cuenta a la que se le ocultan las ventas en efectivo. */
    public static final String CORREO_SIN_EFECTIVO = "ventas.weinmobiliaria@gmail.com";

    private VisibilidadVentas() {}

    /** true si quien hace la petición no debe ver las ventas en efectivo. */
    public static boolean ocultaEfectivo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) return false;
        return CORREO_SIN_EFECTIVO.equalsIgnoreCase(auth.getName().trim());
    }

    /** Lo mismo para un usuario dado, sin sesión de por medio: los avisos push se calculan en segundo plano
     * (PushScheduler), donde no hay petición, así que NotificacionService pregunta por el usuario destinatario. */
    public static boolean ocultaEfectivoPara(Usuario usuario) {
        return usuario != null && usuario.getEmail() != null && CORREO_SIN_EFECTIVO.equalsIgnoreCase(usuario.getEmail().trim());
    }

    /** true si ese usuario puede ver la venta (ver {@link #ocultaEfectivoPara}). */
    public static boolean visiblePara(Usuario usuario, Venta venta) {
        return !(ocultaEfectivoPara(usuario) && esEfectivo(venta));
    }

    /** true si la venta es en efectivo (independiente de quién pregunta). */
    public static boolean esEfectivo(Venta venta) {
        return venta != null && venta.getModalidadPago() == ModalidadPago.EFECTIVO;
    }

    /** true si quien hace la petición puede ver esta venta. Una venta null (p. ej. ya eliminada) no se oculta: lo que
     * cuelga de ella se decide con lo que haya quedado guardado. */
    public static boolean visible(Venta venta) {
        return !(ocultaEfectivo() && esEfectivo(venta));
    }
}
