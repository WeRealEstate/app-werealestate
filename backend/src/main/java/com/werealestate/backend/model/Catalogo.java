package com.werealestate.backend.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Lee una lista de nombres de resources (uno por línea) y los compara sin importar mayúsculas ni
 * acentos; base de Paises y EstadosMexico. */
final class Catalogo {

    private final Map<String, String> porClave;

    Catalogo(String recurso) {
        try (InputStream in = Catalogo.class.getResourceAsStream(recurso)) {
            if (in == null) throw new IllegalStateException("Falta resources" + recurso);
            Map<String, String> mapa = new LinkedHashMap<>();
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    if (!linea.isBlank()) mapa.put(clave(linea), linea.trim());
                }
            }
            this.porClave = mapa;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer resources" + recurso, e);
        }
    }

    /** El nombre de la lista que corresponde al texto, o null si no está. */
    String canonico(String texto) {
        return texto == null ? null : porClave.get(clave(texto));
    }

    java.util.List<String> todos() {
        return java.util.List.copyOf(porClave.values());
    }

    private static String clave(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
