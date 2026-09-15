package com.werealestate.backend.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sirve los archivos subidos (por ahora, solo los planos de los desarrollos) como estáticos bajo
 * /uploads/**; ver SecurityConfig para el permitAll de esa ruta. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String uploadsDir;

    public WebConfig(@Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.uploadsDir = uploadsDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = "file:" + Path.of(uploadsDir).toAbsolutePath() + "/";
        registry.addResourceHandler("/uploads/**").addResourceLocations(ubicacion);
    }
}
