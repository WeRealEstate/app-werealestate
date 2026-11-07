package com.werealestate.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.werealestate.backend.dto.NotificacionDto;
import com.werealestate.backend.model.EstadoLead;
import com.werealestate.backend.model.Lead;
import com.werealestate.backend.model.NotificacionLeida;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Seguimiento;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.repository.EventoCalendarioRepository;
import com.werealestate.backend.repository.LoteRepository;
import com.werealestate.backend.repository.MovimientoLoteRepository;
import com.werealestate.backend.repository.NotificacionLeidaRepository;
import com.werealestate.backend.repository.SeguimientoRepository;
import com.werealestate.backend.repository.TareaRepository;
import com.werealestate.backend.repository.VentaLoteRepository;
import com.werealestate.backend.repository.VentaRepository;
import com.werealestate.backend.repository.WeFormRespuestaRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Avisos de seguimiento pendiente: salen de una sola consulta con el último seguimiento vencido de cada lead. */
class NotificacionSeguimientosTest {

    private static final LocalDateTime PROXIMO = LocalDateTime.of(2026, 11, 2, 10, 0);

    private final SeguimientoRepository seguimientos = mock(SeguimientoRepository.class);
    private final NotificacionLeidaRepository leidas = mock(NotificacionLeidaRepository.class);
    private NotificacionService service;

    @BeforeEach
    void preparar() {
        service = new NotificacionService(
                seguimientos,
                mock(TareaRepository.class),
                mock(EventoCalendarioRepository.class),
                leidas,
                mock(CurrentUserProvider.class),
                mock(MovimientoLoteRepository.class),
                mock(VentaRepository.class),
                mock(VentaLoteRepository.class),
                mock(WeFormRespuestaRepository.class),
                mock(LoteRepository.class));
    }

    private static Usuario usuario(Role rol) {
        Usuario u = mock(Usuario.class);
        when(u.getId()).thenReturn(7L);
        when(u.getRol()).thenReturn(rol);
        return u;
    }

    private static Seguimiento seguimiento(long leadId, String cliente) {
        Lead lead = mock(Lead.class);
        when(lead.getId()).thenReturn(leadId);
        when(lead.getNombreCliente()).thenReturn(cliente);
        Seguimiento s = mock(Seguimiento.class);
        when(s.getLead()).thenReturn(lead);
        when(s.getProximoSeguimiento()).thenReturn(PROXIMO);
        return s;
    }

    private List<NotificacionDto> seguimientosPendientes(Usuario usuario) {
        return service.listarPara(usuario).stream().filter(n -> n.tipo().equals("SEGUIMIENTO_PENDIENTE")).toList();
    }

    @Test
    void unAvisoPorLeadEnElOrdenDeLaConsulta() {
        Seguimiento ana = seguimiento(1L, "Ana");
        // Dos seguimientos de Beto con la misma fecha exacta: un solo aviso.
        Seguimiento beto = seguimiento(2L, "Beto");
        Seguimiento betoEmpate = seguimiento(2L, "Beto");
        when(seguimientos.ultimosVencidos(any(), any())).thenReturn(List.of(ana, beto, betoEmpate));

        List<NotificacionDto> avisos = seguimientosPendientes(usuario(Role.ADMIN));

        assertThat(avisos).extracting(NotificacionDto::mensaje)
                .containsExactly("Seguimiento pendiente con Ana.", "Seguimiento pendiente con Beto.");
        assertThat(avisos).extracting(NotificacionDto::leadId).containsExactly(1L, 2L);
        assertThat(avisos.get(0).firma()).isEqualTo(PROXIMO.toString());
    }

    @Test
    void elAdminVeTodosYLosDemasSoloLosSuyos() {
        seguimientosPendientes(usuario(Role.ADMIN));
        verify(seguimientos).ultimosVencidos(eq(List.of(EstadoLead.CERRADO_GANADO, EstadoLead.CERRADO_PERDIDO)), any());

        Seguimiento suyo = seguimiento(3L, "Carla");
        when(seguimientos.ultimosVencidosDeAsesor(eq(7L), any(), any())).thenReturn(List.of(suyo));
        assertThat(seguimientosPendientes(usuario(Role.ASESOR))).extracting(NotificacionDto::leadId).containsExactly(3L);
        verify(seguimientos, never()).ultimosVencidosDeAsesor(eq(99L), any(), any());
    }

    @Test
    void elQueYaSeLeyoNoVuelveConLaMismaFirma() {
        Seguimiento ana = seguimiento(1L, "Ana");
        when(seguimientos.ultimosVencidos(any(), any())).thenReturn(List.of(ana));
        NotificacionLeida leida = mock(NotificacionLeida.class);
        when(leida.getTipo()).thenReturn("SEGUIMIENTO_PENDIENTE");
        when(leida.getEntidadId()).thenReturn(1L);
        when(leida.getFirma()).thenReturn(PROXIMO.toString());
        when(leidas.findByUsuarioId(anyLong())).thenReturn(List.of(leida));

        assertThat(seguimientosPendientes(usuario(Role.ADMIN))).isEmpty();
    }
}
