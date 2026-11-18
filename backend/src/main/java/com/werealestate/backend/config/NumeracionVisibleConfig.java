package com.werealestate.backend.config;

import com.werealestate.backend.model.NumeracionVisible;
import com.werealestate.backend.repository.VentaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Conecta {@link NumeracionVisible} con la base y olvida su mapa al empezar y al terminar cada petición (los hilos del
 * servidor se reutilizan: un mapa de una petición anterior no debe servir para la siguiente). */
@Component
public class NumeracionVisibleConfig implements HandlerInterceptor {

    public NumeracionVisibleConfig(VentaRepository ventaRepository) {
        NumeracionVisible.usarCargador(ventaRepository::filasDeNumeracion);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        NumeracionVisible.olvidar();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        NumeracionVisible.olvidar();
    }
}
