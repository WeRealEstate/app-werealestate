package com.werealestate.backend.integracion;

import com.werealestate.backend.dto.VentaCreateRequest;
import com.werealestate.backend.dto.VentaDto;
import com.werealestate.backend.dto.VentaLoteItemRequest;
import com.werealestate.backend.model.Cliente;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.ClienteRepository;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.service.ReciboVentaService;
import com.werealestate.backend.service.ReestructuracionVentaService;
import com.werealestate.backend.service.VentaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Base de las pruebas de caracterización contra la BD real (Postgres local): cada prueba corre dentro de una
 * transacción que se revierte al final, así que lo que crea aquí no queda en la base. No dependen de los datos que ya
 * existan salvo del catálogo de desarrollos (SAMAI Campestre y Aldea Nanuu); cada prueba marca sus lotes y leads con
 * una etiqueta propia ({@link #etiqueta()}) y filtra por ella para no mezclarse con datos reales.
 *
 * El rol de la petición se simula con el SecurityContext (lo leen tanto CurrentUserProvider como VisibilidadLote).
 */
@SpringBootTest
@Transactional
public abstract class PruebaDeIntegracion {

    protected static final String CONTRASENA = "clave-de-prueba-123";

    @Autowired protected UsuarioRepository usuarios;
    @Autowired protected DesarrolloRepository desarrollos;
    @Autowired protected LoteRepository lotes;
    @Autowired protected ClienteRepository clientes;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected VentaService ventaService;
    @Autowired protected ReciboVentaService reciboVentaService;
    @Autowired protected ReestructuracionVentaService reestructuracionVentaService;

    @AfterEach
    void limpiarSesion() {
        SecurityContextHolder.clearContext();
    }

    /** Texto corto y único (mayúsculas y dígitos) para marcar los datos de una prueba. */
    protected static String etiqueta() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    protected Desarrollo samai() {
        return desarrolloPorNombre("SAMAI Campestre");
    }

    protected Desarrollo nanuu() {
        return desarrolloPorNombre("Aldea Nanuu");
    }

    private Desarrollo desarrolloPorNombre(String nombre) {
        return desarrollos.findAll().stream()
                .filter(d -> nombre.equals(d.getNombre()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Falta el desarrollo " + nombre + " en la BD local"));
    }

    /** Un usuario nuevo con ese rol y la contraseña {@link #CONTRASENA}. */
    protected Usuario usuario(Role rol) {
        String correo = "prueba-" + etiqueta().toLowerCase() + "@test.local";
        Usuario u = new Usuario("Prueba " + rol, correo, passwordEncoder.encode(CONTRASENA), rol, null);
        return usuarios.save(u);
    }

    /** Desde aquí la petición en curso la hace ese usuario (con su rol). */
    protected void iniciarSesionComo(Usuario usuario) {
        var autenticacion = new UsernamePasswordAuthenticationToken(
                usuario.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name())));
        autenticacion.setDetails(usuario);
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }

    /** Sin sesión (como el cotizador o los planos públicos). */
    protected void sinSesion() {
        SecurityContextHolder.clearContext();
    }

    protected Lote lote(Desarrollo desarrollo, String manzana, String numero, String superficie, EstadoLote estado) {
        Lote lote = new Lote(desarrollo, manzana, numero, new BigDecimal(superficie));
        if (estado != EstadoLote.DISPONIBLE) {
            lote.cambiarEstado(estado, null);
        }
        return lotes.saveAndFlush(lote);
    }

    protected Cliente cliente(String nombre) {
        Cliente c = new Cliente(nombre, "Prueba", null);
        c.setNumero(clientes.maxNumero() + 1);
        return clientes.saveAndFlush(c);
    }

    /** Una venta de un lote creada por el flujo real (VentaService.crear), como admin ya con sesión. Mensualidad y plazo
     * opcionales (null = contado). El contrato queda sin firmar. */
    protected VentaDto crearVenta(Usuario asesor, Lote lote, String precio, String enganche, String mensualidad, Integer plazo,
            LocalDate fechaVenta) {
        Cliente cliente = cliente("Comprador " + etiqueta());
        var request = new VentaCreateRequest(
                List.of(new VentaLoteItemRequest(lote.getId(), new BigDecimal(precio))),
                cliente.getId(), null, null, asesor.getId(), null,
                "Esquema de prueba", fechaVenta,
                mensualidad == null ? null : new BigDecimal(mensualidad), plazo,
                enganche == null ? null : "Enganche", enganche == null ? null : new BigDecimal(enganche),
                fechaVenta.getDayOfMonth(), true, null, null,
                false, null, null, null, null, null, null, null, null);
        return ventaService.crear(request);
    }
}
