package com.werealestate.backend.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** El PDF vectorial del plano interno (/api/plano/{id}/pdf) exige el módulo PLANO; el público no pasa por ahí (usa PIN). */
class ModuloAccesoPlanoPdfTest {

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ModuloAccesoInterceptor conUsuario(String modulos) {
        Usuario u = new Usuario("Asesor", "a@test.com", "hash", Role.ASESOR, null);
        u.setModulos(modulos);
        CurrentUserProvider proveedor = mock(CurrentUserProvider.class);
        when(proveedor.getUsuarioActual()).thenReturn(u);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(
                        "a@test.com", null, List.of(new SimpleGrantedAuthority("ROLE_ASESOR"))));
        return new ModuloAccesoInterceptor(proveedor);
    }

    private boolean pasa(ModuloAccesoInterceptor i, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        return i.preHandle(req, new MockHttpServletResponse(), new Object());
    }

    @Test
    void conElModuloPlanoPasa() {
        assertTrue(pasa(conUsuario("PLANO"), "/api/plano/1/pdf"));
    }

    @Test
    void sinElModuloPlanoSeRechaza() {
        ModuloAccesoInterceptor i = conUsuario("LEADS");
        assertThrows(ForbiddenOperationException.class, () -> pasa(i, "/api/plano/1/pdf"));
    }

    @Test
    void laRutaPublicaNoExigeModulo() {
        assertTrue(pasa(conUsuario("LEADS"), "/api/plano-publico/samai/pdf"));
    }
}
