package com.werealestate.backend.service;

import com.werealestate.backend.exception.ForbiddenOperationException;
import com.werealestate.backend.model.Role;
import com.werealestate.backend.model.Usuario;
import com.werealestate.backend.security.CurrentUserProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Quién puede gestionar ventas: admin y administración (líder de área). Lo comparten todos los servicios de ventas. */
@Component
public class AutorizacionVentas {

    private final CurrentUserProvider currentUserProvider;
    private final PasswordEncoder passwordEncoder;

    public AutorizacionVentas(CurrentUserProvider currentUserProvider, PasswordEncoder passwordEncoder) {
        this.currentUserProvider = currentUserProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario exigirAdminOLider() {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (!esAdminOLider(actual)) {
            throw new ForbiddenOperationException("Solo un administrador o personal de administración puede gestionar ventas");
        }
        return actual;
    }

    /** Para lo destructivo: admin o administración y, además, su contraseña. */
    public Usuario exigirAdminOLiderConContrasena(String mensajeSiNoEsAdmin, String password) {
        Usuario actual = currentUserProvider.getUsuarioActual();
        if (!esAdminOLider(actual)) {
            throw new ForbiddenOperationException(mensajeSiNoEsAdmin);
        }
        exigirContrasena(actual, password);
        return actual;
    }

    /** La contraseña de quien hace una acción delicada (corregir un monto, cambiar un número de contrato). */
    public void exigirContrasena(Usuario actual, String password) {
        if (!passwordEncoder.matches(password, actual.getPassword())) {
            throw new ForbiddenOperationException("Contraseña incorrecta");
        }
    }

    private static boolean esAdminOLider(Usuario usuario) {
        return usuario.getRol() == Role.ADMIN || usuario.getRol() == Role.LIDER_AREA;
    }
}
