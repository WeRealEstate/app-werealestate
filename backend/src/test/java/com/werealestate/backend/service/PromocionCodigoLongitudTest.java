package com.werealestate.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.werealestate.backend.dto.NivelBono;
import com.werealestate.backend.dto.PromocionCreateRequest;
import com.werealestate.backend.exception.ValidationException;
import com.werealestate.backend.model.*;
import com.werealestate.backend.repository.PromocionRepository;
import com.werealestate.backend.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PromocionCodigoLongitudTest {
    @Test
    void aceptaCodigosDeCincoASieteAlCrearYAlAplicar() {
        var repo = mock(PromocionRepository.class);
        var actual = mock(CurrentUserProvider.class);
        var usuario = mock(Usuario.class);
        when(usuario.getRol()).thenReturn(Role.ADMIN);
        when(actual.getUsuarioActual()).thenReturn(usuario);
        when(repo.save(any(Promocion.class))).thenAnswer(inv -> inv.getArgument(0));
        var crear = new PromocionService(repo, actual);
        var aplicar = new PromocionVentaService(repo);
        var desarrollo = mock(Desarrollo.class);
        when(desarrollo.getNombre()).thenReturn("SAMAI Campestre");

        for (String codigo : List.of("ABC12", "ABC123", "ABC1234")) {
            var solicitud = new PromocionCreateRequest("Promoción", "samai", TipoPrecioPromocion.LOTE,
                    ModalidadPromocion.BONO_LOTES, null, List.of(new NivelBono(1, BigDecimal.TEN)),
                    60, null, true, null, null, codigo.toLowerCase());
            assertEquals(codigo, crear.crear(solicitud).codigo());
            var promocion = mock(Promocion.class);
            when(promocion.isActiva()).thenReturn(true);
            when(promocion.isInicioAplicado()).thenReturn(true);
            when(promocion.getProyecto()).thenReturn("samai");
            when(repo.findByCodigo(codigo)).thenReturn(Optional.of(promocion));
            assertSame(promocion, aplicar.porCodigo(" " + codigo.toLowerCase() + " ", desarrollo));
        }
        for (String codigo : List.of("AB12", "ABC12345", "AB-123")) {
            var solicitud = new PromocionCreateRequest("Promoción", "samai", TipoPrecioPromocion.LOTE,
                    ModalidadPromocion.BONO_LOTES, null, List.of(new NivelBono(1, BigDecimal.TEN)),
                    60, null, true, null, null, codigo);
            assertThrows(ValidationException.class, () -> crear.crear(solicitud));
        }
        var automatico = new PromocionCreateRequest("Promoción", "samai", TipoPrecioPromocion.LOTE,
                ModalidadPromocion.BONO_LOTES, null, List.of(new NivelBono(1, BigDecimal.TEN)),
                60, null, true, null, null, null);
        assertTrue(crear.crear(automatico).codigo().matches("[A-Z0-9]{6}"));
        assertThrows(ValidationException.class, () -> aplicar.porCodigo("AB12", desarrollo));
        assertThrows(ValidationException.class, () -> aplicar.porCodigo("ABC12345", desarrollo));
        assertThrows(ValidationException.class, () -> aplicar.porCodigo("AB-123", desarrollo));
    }
}
