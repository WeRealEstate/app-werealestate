package com.werealestate.backend.config;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sirve los archivos subidos (por ahora, solo los planos de los desarrollos) como estáticos bajo
 * /uploads/**; ver SecurityConfig para el permitAll y el cache.disable() de esa ruta (si no, el
 * header writer de Security pisa el Cache-Control de aquí con uno de "no-cache"). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadsDir;

    public WebConfig(@Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = "file:" + Path.of(uploadsDir).toAbsolutePath() + "/";
        // El plano cambia solo cuando un admin lo reemplaza, y la URL guardada lleva un query de
        // cache-busting (?v=timestamp) que cambia junto con él — así que un caché "para siempre"
        // en el navegador es seguro y evita volver a descargar varios MB en cada visita al link
        // público, que antes tardaba 20-30s por venir siempre con "no-cache".
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(ubicacion)
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
    }
}
