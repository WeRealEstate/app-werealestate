package com.werealestate.backend.service;

import com.werealestate.backend.dto.ConfigCorreosDto;
import com.werealestate.backend.dto.CorreosVentaDto;
import com.werealestate.backend.model.CorreoAutomatico;
import com.werealestate.backend.model.CorreoAutomaticoConfig;
import com.werealestate.backend.model.EstadoCorreoAutomatico;
import com.werealestate.backend.model.TipoCorreoAutomatico;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.model.Venta;
import com.werealestate.backend.repository.BienvenidaClienteRepository;
import com.werealestate.backend.repository.CorreoAutomaticoConfigRepository;
import com.werealestate.backend.repository.CorreoAutomaticoRepository;
import com.werealestate.backend.repository.LiquidacionAvisoRepository;
import com.werealestate.backend.repository.RecordatorioPagoRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lo que se ve y se cambia desde la pantalla: interruptores, registro de correos de una venta y su exclusión del
 * recordatorio automático. Solo admin y Administración. */
@Service
@Transactional
public class CorreoVentaConsultaService {

    private final AutorizacionVentas autorizacion;
    private final ConsultasVenta consultas;
    private final CorreoAutomaticoRepository automaticos;
    private final CorreoAutomaticoConfigRepository config;
    private final BienvenidaClienteRepository bienvenidas;
    private final LiquidacionAvisoRepository liquidaciones;
    private final RecordatorioPagoRepository recordatorios;

    public CorreoVentaConsultaService(
            AutorizacionVentas autorizacion,
            ConsultasVenta consultas,
            CorreoAutomaticoRepository automaticos,
            CorreoAutomaticoConfigRepository config,
            BienvenidaClienteRepository bienvenidas,
            LiquidacionAvisoRepository liquidaciones,
            RecordatorioPagoRepository recordatorios) {
        this.autorizacion = autorizacion;
        this.consultas = consultas;
        this.automaticos = automaticos;
        this.config = config;
        this.bienvenidas = bienvenidas;
        this.liquidaciones = liquidaciones;
        this.recordatorios = recordatorios;
    }

    public ConfigCorreosDto configuracion() {
        autorizacion.exigirAdminOLider();
        return dto();
    }

    public ConfigCorreosDto cambiar(TipoCorreoAutomatico tipo, boolean activo) {
        Usuario actual = autorizacion.exigirAdminOLider();
        CorreoAutomaticoConfig c = config.findById(tipo).orElseGet(() -> new CorreoAutomaticoConfig(tipo));
        c.cambiar(activo, actual);
        config.save(c);
        return dto();
    }

    private ConfigCorreosDto dto() {
        return new ConfigCorreosDto(activo(TipoCorreoAutomatico.BIENVENIDA), activo(TipoCorreoAutomatico.LIQUIDACION),
                activo(TipoCorreoAutomatico.RECORDATORIO));
    }

    private boolean activo(TipoCorreoAutomatico tipo) {
        return config.findById(tipo).map(CorreoAutomaticoConfig::isActivo).orElse(false);
    }

    public CorreosVentaDto deVenta(Long ventaId) {
        autorizacion.exigirAdminOLider();
        return registro(consultas.obtenerEntidad(ventaId));
    }

    public CorreosVentaDto excluirRecordatorio(Long ventaId, boolean excluido) {
        autorizacion.exigirAdminOLider();
        Venta venta = consultas.obtenerEntidad(ventaId);
        venta.setSinRecordatorioAutomatico(excluido);
        return registro(venta);
    }

    private CorreosVentaDto registro(Venta venta) {
        List<CorreosVentaDto.Envio> envios = new ArrayList<>();
        bienvenidas.findByVentaId(venta.getId()).forEach(r -> envios.add(enviado(
                r.getEnviadoEn(), TipoCorreoAutomatico.BIENVENIDA, r.getUsuario(), "A " + r.getCorreoDestino())));
        recordatorios.findByVentaId(venta.getId()).forEach(r -> envios.add(enviado(
                r.getEnviadoEn(), TipoCorreoAutomatico.RECORDATORIO, r.getUsuario(), "A " + r.getCorreoDestino())));
        liquidaciones.findByVentaId(venta.getId()).forEach(r -> {
            // Solo los envíos de antes de que la liquidación fuera exclusiva de finanzas tienen correo del cliente.
            String detalle = r.getCorreoCliente() == null ? "A finanzas"
                    : r.isClienteEnviado() ? "A finanzas y a " + r.getCorreoCliente()
                    : "A finanzas; falló el correo a " + r.getCorreoCliente();
            envios.add(enviado(r.getEnviadoEn(), TipoCorreoAutomatico.LIQUIDACION, r.getUsuario(), detalle));
        });
        List<CorreosVentaDto.Pendiente> pendientes = new ArrayList<>();
        for (CorreoAutomatico c : automaticos.findByVentaIdOrderByCreadoEnDescIdDesc(venta.getId())) {
            if (c.getEstado() == EstadoCorreoAutomatico.PENDIENTE) {
                pendientes.add(new CorreosVentaDto.Pendiente(c.getTipo(), c.getEnviarDesde()));
            } else if (c.getEstado() == EstadoCorreoAutomatico.FALLIDO || c.getEstado() == EstadoCorreoAutomatico.NO_ENVIADO) {
                envios.add(new CorreosVentaDto.Envio(c.getResueltoEn(), c.getTipo(), false, true, null, c.getDetalle()));
            }
        }
        envios.sort(Comparator.comparing(CorreosVentaDto.Envio::fecha).reversed());
        return new CorreosVentaDto(venta.isCorreosAutomaticos(), venta.isSinRecordatorioAutomatico(), pendientes, envios);
    }

    private static CorreosVentaDto.Envio enviado(java.time.LocalDateTime fecha, TipoCorreoAutomatico tipo, Usuario usuario,
            String detalle) {
        return new CorreosVentaDto.Envio(fecha, tipo, true, usuario == null,
                usuario == null ? null : usuario.getNombre(), detalle);
    }
}
