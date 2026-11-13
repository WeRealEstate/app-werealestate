package com.werealestate.backend.service;

import com.werealestate.backend.dto.ActualizarPoligonoMapaRequest;
import com.werealestate.backend.dto.ApartarVariosPublicoRequest;
import com.werealestate.backend.dto.ApartarVariosRequest;
import com.werealestate.backend.dto.CambiarEstadoLotePublicoRequest;
import com.werealestate.backend.dto.CambiarEstadoLoteRequest;
import com.werealestate.backend.dto.LoteCreateRequest;
import com.werealestate.backend.dto.LimpiarMapaRequest;
import com.werealestate.backend.dto.LoteDto;
import com.werealestate.backend.dto.LoteImportBatchRequest;
import com.werealestate.backend.dto.LoteImportError;
import com.werealestate.backend.dto.LoteImportRequest;
import com.werealestate.backend.dto.LoteImportResultado;
import com.werealestate.backend.dto.LoteUpdateRequest;
import com.werealestate.backend.dto.PoligonoMapaJson;
import com.werealestate.backend.dto.PuntoMapaDto;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.AsesorExterno;
import com.werealestate.backend.model.Desarrollo;
import com.werealestate.backend.model.EstadoLote;
import com.werealestate.backend.model.Lote;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.AsesorExternoRepository;
import com.werealestate.backend.repository.DesarrolloRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.UsuarioRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Los estados APARTADO_A_PLAZO, APARTADO_CON_DINERO, EN_PROCESO_DE_FIRMA y VENDIDO (comprometidos
 * con dinero real o en firma) solo los puede establecer un admin o un líder de área; DISPONIBLE y
 * APARTADO los puede mover cualquiera (es el uso diario de un asesor). EN_PROCESO_DE_FIRMA y VENDIDO,
 * además, los pone la venta del lote y no se cambian a mano (ver validarContraVenta). Dar de
 * alta/editar/importar lotes es exclusivo de admin. */
@Service
@Transactional
public class LoteService {

    private static final Set<EstadoLote> ESTADOS_ADMIN_O_LIDER = Set.of(
            EstadoLote.APARTADO_A_PLAZO,
            EstadoLote.APARTADO_CON_DINERO,
            EstadoLote.EN_PROCESO_DE_FIRMA,
            EstadoLote.RESERVADO_WE,
            EstadoLote.VENDIDO);

    /** Mismo usuario "de sistema" (ver migración V17) que atribuye las cotizaciones generadas desde
     * /cotizador-publico: aquí se reutiliza para atribuir los cambios de estado que haga un
     * visitante en un plano público (/samai, /aldea-nanuu), donde tampoco hay una sesión real detrás. */
    private static final String EMAIL_USUARIO_PUBLICO = "cotizador-publico@weinversiones.com";

    private final LoteRepository loteRepository;
    private final DesarrolloRepository desarrolloRepository;
    private final UsuarioRepository usuarioRepository;
    private final AsesorExternoRepository asesorExternoRepository;
    private final VentaLoteRepository ventaLoteRepository;
    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;
    private final LoteConsultaService consulta;
    private final HistorialLoteService historial;
    private final AutorizacionLotes autorizacion;

    public LoteService(
            LoteRepository loteRepository,
            DesarrolloRepository desarrolloRepository,
            UsuarioRepository usuarioRepository,
            AsesorExternoRepository asesorExternoRepository,
            VentaLoteRepository ventaLoteRepository,
            CurrentUserProvider currentUserProvider,
            PasswordEncoder passwordEncoder,
            LoteConsultaService consulta,
            HistorialLoteService historial,
            AutorizacionLotes autorizacion) {
        this.loteRepository = loteRepository;
        this.desarrolloRepository = desarrolloRepository;
        this.usuarioRepository = usuarioRepository;
        this.asesorExternoRepository = asesorExternoRepository;
        this.ventaLoteRepository = ventaLoteRepository;
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
        this.consulta = consulta;
        this.historial = historial;
        this.autorizacion = autorizacion;
    }

    /** Igual que {@link #cambiarEstado}, pero para los planos públicos: sin sesión iniciada,
     * así que un visitante nunca puede tocar un lote exclusivo de admin (ni para entrar ni para
     * salir de esos estados), el cambio se atribuye al usuario de sistema, apartar exige el
     * nombre del asesor que atendió al visitante y el del cliente que apartó, y liberar un lote ya
     * apartado no está permitido desde aquí (solo desde /panel/lotes o /panel/plano) — evita que
     * cualquiera con el link pueda dejar disponible un lote que un asesor ya comprometió. */
    public LoteDto cambiarEstadoPublico(Long id, CambiarEstadoLotePublicoRequest request) {
        Lote lote = consulta.obtenerEntidad(id);

        boolean tocaEstadoRestringido =
                ESTADOS_ADMIN_O_LIDER.contains(request.estado()) || ESTADOS_ADMIN_O_LIDER.contains(lote.getEstado());
        // Un lote con venta tampoco, aunque haya quedado en otro estado (datos de antes de que lo mandara la venta).
        if (tocaEstadoRestringido || ventaLoteRepository.existsByLoteId(lote.getId())) {
            throw new ForbiddenOperationException("Este lote no se puede modificar desde la disponibilidad pública");
        }
        if (request.estado() == EstadoLote.DISPONIBLE && lote.getEstado() == EstadoLote.APARTADO) {
            throw new ForbiddenOperationException("Un lote apartado no se puede liberar desde la disponibilidad pública");
        }

        String nombreAsesor = request.nombreAsesor() == null ? null : request.nombreAsesor().trim();
        String nombreCliente = request.nombreCliente() == null ? null : request.nombreCliente().trim();
        if (request.estado() == EstadoLote.APARTADO) {
            if (nombreAsesor == null || nombreAsesor.isBlank()) {
                throw new ValidationException("El nombre del asesor es obligatorio para apartar un lote");
            }
            if (nombreCliente == null || nombreCliente.isBlank()) {
                throw new ValidationException("El nombre del cliente es obligatorio para apartar un lote");
            }
        }
        String nota = request.nota() == null || request.nota().isBlank() ? null : request.nota().trim();

        Usuario sistema = usuarioRepository
                .findByEmail(EMAIL_USUARIO_PUBLICO)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el usuario de sistema del cotizador público (migración V17)"));

        historial.cambiarEstadoConHistorial(lote, request.estado(), sistema, nombreAsesor, nombreCliente, null, nota);
        return LoteDto.paraPublico(lote);
    }

    /** Tope de lotes en un apartado múltiple (el plano interno y el público). */
    public static final int MAX_LOTES_APARTADO_MULTIPLE = 5;

    /** Aparta varios lotes a la vez (estado APARTADO simple) con los mismos datos, desde el plano interno. Cada
     * lote pasa por {@link #cambiarEstado}, así que valen las mismas reglas de rol (nota obligatoria para admin y
     * administración, asesor al que pertenece el apartado) y queda su movimiento en el historial. Todo o nada: se
     * revisa primero que todos estén disponibles y, si algo falla después, la transacción deshace lo demás. */
    public List<LoteDto> apartarVarios(ApartarVariosRequest request) {
        String cliente = request.nombreCliente() == null ? "" : request.nombreCliente().trim();
        if (cliente.isEmpty()) {
            throw new ValidationException("El nombre del cliente es obligatorio para apartar");
        }
        List<Lote> lotes = lotesDisponiblesParaApartar(request.loteIds());
        List<LoteDto> apartados = new ArrayList<>();
        for (Lote lote : lotes) {
            apartados.add(cambiarEstado(
                    lote.getId(),
                    new CambiarEstadoLoteRequest(
                            EstadoLote.APARTADO, null, request.nota(), cliente, null,
                            request.usuarioAsesorId(), request.asesorExternoId())));
        }
        return apartados;
    }

    /** Lo mismo desde el plano público (sin sesión): cada lote pasa por {@link #cambiarEstadoPublico}. */
    public List<LoteDto> apartarVariosPublico(ApartarVariosPublicoRequest request) {
        List<Lote> lotes = lotesDisponiblesParaApartar(request.loteIds());
        List<LoteDto> apartados = new ArrayList<>();
        for (Lote lote : lotes) {
            apartados.add(cambiarEstadoPublico(
                    lote.getId(),
                    new CambiarEstadoLotePublicoRequest(
                            EstadoLote.APARTADO, request.nombreAsesor(), request.nombreCliente(), request.nota())));
        }
        return apartados;
    }

    /** Los lotes pedidos, en el mismo orden, si hay entre 1 y el tope, sin repetir y todos DISPONIBLES. */
    private List<Lote> lotesDisponiblesParaApartar(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ValidationException("Elige al menos un lote");
        }
        if (ids.size() > MAX_LOTES_APARTADO_MULTIPLE) {
            throw new ValidationException("Máximo " + MAX_LOTES_APARTADO_MULTIPLE + " lotes por apartado");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new ValidationException("Hay un lote repetido");
        }
        List<Lote> lotes = new ArrayList<>();
        for (Long id : ids) {
            Lote lote = consulta.obtenerEntidad(id);
            if (lote.getEstado() != EstadoLote.DISPONIBLE) {
                throw new ConflictException(
                        "El lote Mz " + lote.getManzana() + " / Lote " + lote.getNumeroLote() + " ya no está disponible");
            }
            lotes.add(lote);
        }
        return lotes;
    }

    public LoteDto obtener(Long id) {
        return LoteDto.from(consulta.obtenerEntidad(id));
    }

    public LoteDto crear(LoteCreateRequest request) {
        autorizacion.exigirAdmin("crear lotes");
        Desarrollo desarrollo = desarrolloRepository
                .findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
        validarUnico(request.desarrolloId(), request.manzana(), request.numeroLote(), null);

        Lote lote = new Lote(desarrollo, request.manzana().trim(), request.numeroLote().trim(), request.superficie());
        lote.setPrecioM2(request.precioM2());
        return LoteDto.from(loteRepository.save(lote));
    }

    public LoteDto actualizar(Long id, LoteUpdateRequest request) {
        autorizacion.exigirAdmin("editar lotes");
        Lote lote = consulta.obtenerEntidad(id);
        validarUnico(lote.getDesarrollo().getId(), request.manzana(), request.numeroLote(), id);

        lote.setManzana(request.manzana().trim());
        lote.setNumeroLote(request.numeroLote().trim());
        lote.setSuperficie(request.superficie());
        lote.setPrecioM2(request.precioM2());
        return LoteDto.from(lote);
    }

    /** Pone (o quita, con null) el precio por m² propio de todos los lotes de una manzana, p. ej. las
     * hectáreas. Devuelve cuántos lotes se actualizaron. */
    public int actualizarPrecioManzana(Long desarrolloId, String manzana, BigDecimal precioM2) {
        autorizacion.exigirAdmin("cambiar precios");
        List<Lote> lotes =
                loteRepository.findByDesarrolloIdAndManzanaIgnoreCase(desarrolloId, manzana.trim());
        if (lotes.isEmpty()) throw new ResourceNotFoundException("No hay lotes en esa manzana");
        lotes.forEach(l -> l.setPrecioM2(precioM2));
        return lotes.size();
    }

    public void eliminar(Long id) {
        autorizacion.exigirAdmin("eliminar lotes");
        loteRepository.delete(consulta.obtenerEntidad(id));
    }

    public LoteDto cambiarEstado(Long id, CambiarEstadoLoteRequest request) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        Lote lote = consulta.obtenerEntidad(id);

        // No solo se restringe ENTRAR a un estado comprometido con dinero real o en firma: una vez
        // que un lote ya está ahí, solo un admin o un líder de área puede moverlo a cualquier otro
        // estado. Si no, cualquier asesor podría "liberar" un lote que ya fue apartado en firme con
        // solo marcarlo de vuelta a Disponible.
        boolean requiereAdminOLider =
                ESTADOS_ADMIN_O_LIDER.contains(request.estado()) || ESTADOS_ADMIN_O_LIDER.contains(lote.getEstado());
        if (requiereAdminOLider && !AutorizacionLotes.esAdminOLider(actual)) {
            throw new ForbiddenOperationException(
                    "Solo un administrador o personal de administración puede cambiar el estado de este lote");
        }
        validarContraVenta(lote, request.estado(), actual);

        validarDatosDelApartado(request);

        String nota = request.nota() == null || request.nota().isBlank() ? null : request.nota().trim();
        // Un admin o líder de área puede mover un lote a cualquier estado (incluidos los
        // comprometidos con dinero real o en firma), así que se le pide justificar cada cambio; un
        // asesor solo se mueve entre Disponible/Apartado en su operación diaria y no necesita nota.
        if (AutorizacionLotes.esAdminOLider(actual) && nota == null) {
            throw new ValidationException("Agrega una nota explicando el motivo del cambio de estado");
        }

        String nombreCliente = request.nombreCliente() == null || request.nombreCliente().isBlank()
                ? null
                : request.nombreCliente().trim();
        BigDecimal montoApartado =
                request.estado() == EstadoLote.APARTADO_CON_DINERO ? request.montoApartado() : null;
        // Un apartado que pone administración debe quedar a nombre del asesor al que pertenece: así
        // ese asesor lo ve (y solo él) al armar un registro de venta en We Forms.
        String nombreAsesor = null;
        if (EstadosDeLote.APARTADO.contains(request.estado())
                && AutorizacionLotes.esAdminOLider(actual)) {
            nombreAsesor = resolverAsesor(request.usuarioAsesorId(), request.asesorExternoId());
        }
        historial.cambiarEstadoConHistorial(lote, request.estado(), actual, nombreAsesor, nombreCliente, montoApartado, nota);
        lote.setFechaExpiraApartado(
                request.estado() == EstadoLote.APARTADO_A_PLAZO ? request.fechaExpiraApartado() : null);
        lote.setMontoApartado(montoApartado);
        return consulta.conVentas(lote);
    }

    private static void validarDatosDelApartado(CambiarEstadoLoteRequest request) {
        if (request.estado() == EstadoLote.APARTADO_A_PLAZO
                && (request.fechaExpiraApartado() == null
                        || !request.fechaExpiraApartado().isAfter(LocalDateTime.now()))) {
            throw new ValidationException("Para apartar a plazo hay que indicar una fecha de vencimiento futura");
        }
        if (request.estado() == EstadoLote.APARTADO_CON_DINERO
                && (request.montoApartado() == null || request.montoApartado().signum() <= 0)) {
            throw new ValidationException("Indica con cuánto dinero se apartó este lote");
        }
    }

    /** El estado de un lote con venta lo manda la venta (ver VentaService): En proceso de firma sin el contrato
     * firmado, Vendido ya firmado. Así que a mano no se saca de ahí (para liberarlo se elimina la venta) y a esos dos
     * estados solo se llega registrando la venta, que es la que los pone. Un lote con venta que quedó en otro estado
     * (datos de antes de esta regla) solo puede volver a uno de venta. */
    private void validarContraVenta(Lote lote, EstadoLote nuevoEstado, Usuario actual) {
        String numeroVenta = ventaLoteRepository.findFirstByLoteIdOrderByIdDesc(lote.getId())
                .map(vl -> vl.getVenta().getEtiqueta())
                .orElse(null);
        boolean adminOLider = AutorizacionLotes.esAdminOLider(actual);
        if (numeroVenta != null) {
            if (!adminOLider) {
                throw new ConflictException("Este lote ya tiene una venta registrada: pide a administración que lo revise");
            }
            if (EstadosDeLote.DE_VENTA.contains(lote.getEstado())) {
                throw new ConflictException("Este lote está en la venta " + numeroVenta + ": su estado lo manda la venta "
                        + "(Vendido con el contrato firmado, En proceso de firma sin firmar). Para liberarlo, elimina la venta.");
            }
            if (!EstadosDeLote.DE_VENTA.contains(nuevoEstado)) {
                throw new ConflictException(
                        "Este lote está en la venta " + numeroVenta + ": solo puede pasar a Vendido o En proceso de firma");
            }
            return;
        }
        if (EstadosDeLote.DE_VENTA.contains(nuevoEstado)) {
            throw new ValidationException("Para marcar este lote como "
                    + (nuevoEstado == EstadoLote.VENDIDO ? "Vendido" : "En proceso de firma")
                    + ", registra su venta en Ventas: el lote cambia solo al guardarla");
        }
    }

    /** Nombre del asesor (interno o externo) elegido al apartar un lote desde el panel; exactamente uno. */
    private String resolverAsesor(Long usuarioAsesorId, Long asesorExternoId) {
        if ((usuarioAsesorId == null) == (asesorExternoId == null)) {
            throw new ValidationException("Elige a qué asesor pertenece este apartado");
        }
        if (usuarioAsesorId != null) {
            return usuarioRepository
                    .findById(usuarioAsesorId)
                    .filter(Usuario::isActivo)
                    .map(Usuario::getNombre)
                    .orElseThrow(() -> new ValidationException("El asesor elegido no existe o está inactivo"));
        }
        return asesorExternoRepository
                .findById(asesorExternoId)
                .filter(AsesorExterno::isActivo)
                .map(AsesorExterno::getNombre)
                .orElseThrow(() -> new ValidationException("El asesor elegido no existe o está inactivo"));
    }

    private void validarUnico(Long desarrolloId, String manzana, String numeroLote, Long idAExcluir) {
        boolean existe = loteRepository.existsByDesarrolloIdAndManzanaIgnoreCaseAndNumeroLoteIgnoreCase(
                desarrolloId, manzana.trim(), numeroLote.trim());
        if (existe) {
            // Si es edición y el duplicado encontrado es el mismo registro, no es un conflicto real.
            if (idAExcluir != null) {
                Lote actual = consulta.obtenerEntidad(idAExcluir);
                boolean esElMismo = actual.getDesarrollo().getId().equals(desarrolloId)
                        && actual.getManzana().equalsIgnoreCase(manzana.trim())
                        && actual.getNumeroLote().equalsIgnoreCase(numeroLote.trim());
                if (esElMismo) {
                    return;
                }
            }
            throw new ConflictException("Ya existe un lote con esa manzana y número en este desarrollo");
        }
    }

    /** Delimita (o borra, con una lista vacía o nula) el polígono de un lote sobre el plano de su
     * desarrollo; exclusivo de admin, igual que dar de alta/editar/importar lotes. */
    public LoteDto actualizarPoligonoMapa(Long id, ActualizarPoligonoMapaRequest request) {
        autorizacion.exigirAdmin("delimitar lotes en el plano");
        List<PuntoMapaDto> puntos = request.puntos();
        if (puntos != null && !puntos.isEmpty() && puntos.size() < 3) {
            throw new ValidationException("Un polígono necesita al menos 3 puntos");
        }
        Lote lote = consulta.obtenerEntidad(id);
        lote.actualizarPoligonoMapa(PoligonoMapaJson.serializar(puntos));
        return consulta.conVentas(lote);
    }

    /** Borra la delimitación de TODOS los lotes de un desarrollo. Es destructivo (hay que volver a
     * dibujar cada polígono), así que además de ser exclusivo de admin exige su contraseña. Devuelve
     * cuántos lotes tenían delimitación. */
    public int limpiarMapa(LimpiarMapaRequest request) {
        Usuario actual = autorizacion.exigirAdmin("quitar la delimitación de todos los lotes");
        if (!passwordEncoder.matches(request.password(), actual.getPassword())) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
        desarrolloRepository
                .findById(request.desarrolloId())
                .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));

        int limpiados = 0;
        for (Lote lote : loteRepository.findByDesarrolloId(request.desarrolloId())) {
            if (lote.getMapaPoligonoJson() != null) {
                lote.actualizarPoligonoMapa(null);
                limpiados++;
            }
        }
        return limpiados;
    }

    public LoteImportResultado importar(LoteImportBatchRequest request) {
        autorizacion.exigirAdmin("importar lotes");

        int creados = 0;
        List<LoteImportError> errores = new ArrayList<>();
        // Detecta duplicados DENTRO del mismo archivo sin depender de que Hibernate haga flush de
        // los inserts anteriores antes de la siguiente validación (evitaría además dejar la
        // transacción entera inutilizable si esa validación llegara a fallar a mitad del lote).
        Set<String> vistosEnEsteLote = new HashSet<>();
        int fila = 0;
        for (LoteImportRequest item : request.lotes()) {
            fila++;
            try {
                String clave = (item.desarrolloId() + ":" + item.manzana() + ":" + item.numeroLote()).toLowerCase();
                if (!vistosEnEsteLote.add(clave)) {
                    throw new ConflictException("Manzana y número de lote repetidos en el archivo");
                }

                Desarrollo desarrollo = desarrolloRepository
                        .findById(item.desarrolloId())
                        .orElseThrow(() -> new ResourceNotFoundException("Desarrollo no encontrado"));
                validarUnico(item.desarrolloId(), item.manzana(), item.numeroLote(), null);

                Lote lote = new Lote(desarrollo, item.manzana().trim(), item.numeroLote().trim(), item.superficie());
                lote.setPrecioM2(item.precioM2());
                loteRepository.save(lote);
                creados++;
            } catch (Exception e) {
                errores.add(new LoteImportError(fila, item.manzana(), item.numeroLote(), e.getMessage()));
            }
        }
        return new LoteImportResultado(creados, errores);
    }
}
