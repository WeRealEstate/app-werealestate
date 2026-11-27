package com.werealestate.backend.service;

import com.werealestate.backend.dto.PromocionCreateRequest;
import com.werealestate.backend.dto.NivelBono;
import com.werealestate.backend.dto.PromocionDto;
import com.werealestate.backend.dto.PromocionUpdateRequest;
import com.werealestate.backend.exception.ConflictException;
import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.exception.ResourceNotFoundException;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.ModalidadPromocion;
import com.werealestate.backend.model.Promocion;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.TipoPrecioPromocion;
import com.werealestate.backend.repository.PromocionRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromocionService {

    private static final SecureRandom AZAR = new SecureRandom();

    private final PromocionRepository promocionRepository;

    private final CurrentUserProvider currentUserProvider;

    public PromocionService(PromocionRepository promocionRepository, CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
        this.promocionRepository = promocionRepository;
    }

    @Transactional
    public List<PromocionDto> listarActivas() {
        actualizarVigencias();
        return promocionRepository.findByActivaTrue().stream()
                .map(PromocionDto::fromEntity)
                .toList();
    }

    @Transactional
    public List<PromocionDto> listar() {
        actualizarVigencias();
        // El código solo lo ve el admin: cualquier sesión puede pedir esta lista por API.
        boolean admin = esAdmin();
        return promocionRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(p -> admin ? PromocionDto.conCodigo(p) : PromocionDto.fromEntity(p))
                .toList();
    }

    /** Pone a la promoción un código nuevo al azar (botón «Generar otro»): el anterior deja de servir. */
    @Transactional
    public PromocionDto generarCodigo(Long id) {
        exigirAdmin();
        Promocion p = promocionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
        p.setCodigo(codigoAlAzar());
        return PromocionDto.conCodigo(promocionRepository.save(p));
    }

    @Transactional
    public PromocionDto crear(PromocionCreateRequest request) {
        exigirAdmin();
        TipoPrecioPromocion tipoPrecio = request.tipoPrecio() != null ? request.tipoPrecio() : TipoPrecioPromocion.LOTE;
        ModalidadPromocion modalidad = request.modalidad() != null ? request.modalidad() : ModalidadPromocion.MENSUALIDAD_FIJA;

        Promocion p = new Promocion();
        p.setNombre(request.nombre().trim());
        p.setProyecto(request.proyecto().trim());
        p.setTipoPrecio(tipoPrecio);
        p.setModalidad(modalidad);
        aplicarMontosSegunModalidad(
                p,
                modalidad,
                request.mensualidadFija(),
                request.bonos(),
                request.plazoFijo()
        );
        p.setDescripcion(limpiarDescripcion(request.descripcion()));
        p.setFechaFin(request.fechaFin());
        // Sin fecha de inicio empieza al crearla (decisión del usuario).
        p.setFechaInicio(request.fechaInicio() != null ? request.fechaInicio() : LocalDateTime.now());
        validarFechas(p);
        // Código: el que escribió el admin o uno al azar (decisión del usuario).
        p.setCodigo(request.codigo() == null || request.codigo().isBlank()
                ? codigoAlAzar()
                : codigoLibre(request.codigo(), null));

        ajustarPorInicio(p, request.activa() == null || request.activa());
        if (p.isActiva()) {
            desactivarOtrasDelProyecto(p.getProyecto(), p.getTipoPrecio(), p.getModalidad(), null);
        }

        return PromocionDto.conCodigo(promocionRepository.save(p));
    }

    @Transactional
    public PromocionDto actualizar(Long id, PromocionUpdateRequest request) {
        exigirAdmin();
        Promocion p = promocionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));

        // El nombre no se cambia (decisión del usuario): las ventas guardan la promoción por su nombre.
        if (request.nombre() != null && !request.nombre().isBlank() && !request.nombre().trim().equals(p.getNombre())) {
            throw new ValidationException("El nombre de una promoción no se puede cambiar; crea una promoción nueva");
        }
        if (request.proyecto() != null && !request.proyecto().isBlank()) {
            p.setProyecto(request.proyecto().trim());
        }
        if (request.tipoPrecio() != null) {
            p.setTipoPrecio(request.tipoPrecio());
        }
        ModalidadPromocion modalidad = request.modalidad() != null
                ? request.modalidad()
                : (p.getModalidad() != null ? p.getModalidad() : ModalidadPromocion.MENSUALIDAD_FIJA);
        p.setModalidad(modalidad);

        BigDecimal mensualidadFija = request.mensualidadFija() != null ? request.mensualidadFija() : p.getMensualidadFija();
        java.util.List<NivelBono> bonos = request.bonos() != null ? request.bonos() : NivelBono.de(p);
        Integer plazoFijo = request.plazoFijo() != null ? request.plazoFijo() : p.getPlazoFijo();

        aplicarMontosSegunModalidad(p, modalidad, mensualidadFija, bonos, plazoFijo);

        if (request.descripcion() != null) {
            p.setDescripcion(limpiarDescripcion(request.descripcion()));
        }
        if (Boolean.TRUE.equals(request.limpiarFechaFin())) {
            p.setFechaFin(null);
        } else if (request.fechaFin() != null) {
            p.setFechaFin(request.fechaFin());
        }
        if (request.fechaInicio() != null) {
            p.setFechaInicio(request.fechaInicio());
        }
        if (request.codigo() != null && !request.codigo().isBlank()) {
            p.setCodigo(codigoLibre(request.codigo(), p));
        }
        validarFechas(p);
        // Una programada cuenta como «quiere estar activa».
        boolean quiereActiva = request.activa() != null ? request.activa() : (p.isActiva() || !p.isInicioAplicado());
        ajustarPorInicio(p, quiereActiva);

        if (p.isActiva()) {
            desactivarOtrasDelProyecto(p.getProyecto(), p.getTipoPrecio(), p.getModalidad(), p.getId());
        }

        return PromocionDto.conCodigo(promocionRepository.save(p));
    }

    @Transactional
    public PromocionDto cambiarEstado(Long id, boolean activa) {
        exigirAdmin();
        Promocion p = promocionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));

        if (activa && p.getFechaFin() != null && !p.getFechaFin().isAfter(LocalDateTime.now())) {
            throw new ValidationException("La fecha de fin ya venció; amplíala o quítala antes de activar la promoción.");
        }

        // Activar una que aún no empieza la deja programada; desactivar una programada la cancela.
        ajustarPorInicio(p, activa);
        if (p.isActiva()) {
            ModalidadPromocion modalidad = p.getModalidad() != null ? p.getModalidad() : ModalidadPromocion.MENSUALIDAD_FIJA;
            desactivarOtrasDelProyecto(p.getProyecto(), p.getTipoPrecio(), modalidad, p.getId());
        }

        return PromocionDto.conCodigo(promocionRepository.save(p));
    }

    @Transactional
    public void eliminar(Long id) {
        exigirAdmin();
        if (!promocionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Promoción no encontrada");
        }
        promocionRepository.deleteById(id);
    }

    /** Ningún nivel del bono puede pasar de este número de lotes (igual que el tope de lotes del registro de venta). */
    static final int MAX_LOTES_NIVEL = 20;

    private void aplicarMontosSegunModalidad(
            Promocion p,
            ModalidadPromocion modalidad,
            BigDecimal mensualidadFija,
            java.util.List<NivelBono> bonos,
            Integer plazoFijo
    ) {
        if (modalidad == ModalidadPromocion.BONO_LOTES) {
            java.util.Map<Integer, BigDecimal> niveles = new java.util.TreeMap<>();
            for (NivelBono n : bonos == null ? java.util.List.<NivelBono>of() : bonos) {
                if (n == null || n.lotes() == null || n.lotes() < 1 || n.lotes() > MAX_LOTES_NIVEL) {
                    throw new ValidationException("Cada nivel del bono debe ser de 1 a " + MAX_LOTES_NIVEL + " lotes");
                }
                if (n.bono() == null || n.bono().signum() <= 0) {
                    throw new ValidationException("El bono para " + n.lotes() + " lote(s) debe ser mayor a 0");
                }
                if (niveles.put(n.lotes(), n.bono()) != null) {
                    throw new ValidationException("Hay dos niveles del bono para " + n.lotes() + " lote(s)");
                }
            }
            if (niveles.isEmpty()) {
                throw new ValidationException("Agrega al menos un nivel: cuántos lotes y de cuánto es el bono");
            }
            int plazo = plazoFijo != null ? plazoFijo : 60;
            if (plazo <= 0) {
                throw new ValidationException("El plazo fijo debe ser mayor a 0");
            }
            p.setMensualidadFija(null);
            p.setBonos(niveles);
            p.setPlazoFijo(plazo);
        } else {
            if (mensualidadFija == null || mensualidadFija.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("La mensualidad fija debe ser mayor a 0");
            }
            p.setMensualidadFija(mensualidadFija);
            p.setBonos(null);
            p.setPlazoFijo(null);
        }
    }

    /**
     * Pone al día el estado por fechas: las programadas cuya fecha de inicio ya llegó se activan solas (y, como al
     * activarlas a mano, apagan a la otra activa del mismo proyecto, tarifa y modalidad) y las vencidas se apagan.
     * Lo corre PromocionScheduler cada minuto y también al listar.
     */
    @Transactional
    public void actualizarVigencias() {
        LocalDateTime ahora = LocalDateTime.now();
        for (Promocion p : promocionRepository.findByInicioAplicadoFalse()) {
            if (p.getFechaInicio() != null && p.getFechaInicio().isAfter(ahora)) continue;
            p.setInicioAplicado(true);
            boolean vencida = p.getFechaFin() != null && !p.getFechaFin().isAfter(ahora);
            p.setActiva(!vencida);
            promocionRepository.save(p);
            if (!vencida) {
                desactivarOtrasDelProyecto(p.getProyecto(), p.getTipoPrecio(), p.getModalidad(), p.getId());
            }
        }
        desactivarVencidas();
    }

    /** Activa o no según su fecha de inicio: si aún no empieza, queda programada (si se quería activa) y apagada. */
    private static void ajustarPorInicio(Promocion p, boolean quiereActiva) {
        LocalDateTime ahora = LocalDateTime.now();
        if (p.getFechaInicio() != null && p.getFechaInicio().isAfter(ahora)) {
            p.setActiva(false);
            p.setInicioAplicado(!quiereActiva);
            return;
        }
        p.setInicioAplicado(true);
        boolean vencida = p.getFechaFin() != null && !p.getFechaFin().isAfter(ahora);
        p.setActiva(quiereActiva && !vencida);
    }

    private static void validarFechas(Promocion p) {
        if (p.getFechaInicio() != null && p.getFechaFin() != null && !p.getFechaFin().isAfter(p.getFechaInicio())) {
            throw new ValidationException("La fecha de fin debe ser posterior a la fecha de inicio");
        }
    }

    /** Un código escrito por el admin: de 5 a 7 letras o números y que no lo tenga otra promoción. */
    private String codigoLibre(String escrito, Promocion propia) {
        String codigo = normalizarCodigo(escrito);
        if (!codigo.matches("[A-Z0-9]{5,7}")) {
            throw new ValidationException("El código de promoción debe tener de 5 a 7 letras o números");
        }
        promocionRepository.findByCodigo(codigo)
                .filter(otra -> propia == null || !otra.getId().equals(propia.getId()))
                .ifPresent(otra -> {
                    throw new ConflictException("Ese código ya lo usa la promoción «" + otra.getNombre() + "»");
                });
        return codigo;
    }

    /** Letras y números del código al azar, sin los que se confunden (0/O, 1/I). */
    private static final String ALFABETO_CODIGO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /** Los códigos no distinguen mayúsculas: se guardan y se buscan en mayúsculas y sin espacios. */
    public static String normalizarCodigo(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** 6 letras o números al azar que ninguna otra promoción tenga. */
    private String codigoAlAzar() {
        for (int intento = 0; intento < 50; intento++) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(ALFABETO_CODIGO.charAt(AZAR.nextInt(ALFABETO_CODIGO.length())));
            String codigo = sb.toString();
            if (!promocionRepository.existsByCodigo(codigo)) return codigo;
        }
        throw new IllegalStateException("No se encontró un código de promoción libre");
    }

    private boolean esAdmin() {
        return currentUserProvider.getUsuarioActual().getRol() == Role.ADMIN;
    }

    /** Crear, editar, activar o eliminar promociones: solo admin (decisión del usuario 12.17.0). */
    private void exigirAdmin() {
        if (!esAdmin()) {
            throw new ForbiddenOperationException("Solo un administrador puede crear o cambiar promociones");
        }
    }

    private void desactivarVencidas() {
        LocalDateTime ahora = LocalDateTime.now();
        List<Promocion> activas = promocionRepository.findByActivaTrue();
        for (Promocion p : activas) {
            if (p.getFechaFin() != null && !p.getFechaFin().isAfter(ahora)) {
                p.setActiva(false);
                promocionRepository.save(p);
            }
        }
    }

    private void desactivarOtrasDelProyecto(
            String proyecto,
            TipoPrecioPromocion tipoPrecio,
            ModalidadPromocion modalidad,
            Long excludeId
    ) {
        List<Promocion> activas = promocionRepository.findByProyectoAndTipoPrecioAndModalidadAndActivaTrue(
                proyecto,
                tipoPrecio,
                modalidad
        );
        for (Promocion otra : activas) {
            if (excludeId == null || !otra.getId().equals(excludeId)) {
                otra.setActiva(false);
                promocionRepository.save(otra);
            }
        }
    }

    private String limpiarDescripcion(String descripcion) {
        if (descripcion == null) {
            return null;
        }
        String limpia = descripcion.trim();
        return limpia.isEmpty() ? null : limpia;
    }
}
