package com.werealestate.backend.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Catálogo de países (nombres en español) de donde se elige el país de residencia, leído de
 * resources/paises.txt (uno por línea). La misma lista vive en el frontend (core/data/paises.ts); si se
 * edita aquí hay que editarla allá. Se compara sin importar mayúsculas ni acentos y siempre se guarda el
 * nombre tal como está en la lista. */
public final class Paises {

    private static final Map<String, String> POR_CLAVE = cargar();

    private Paises() {}

    /** El nombre oficial de la lista que corresponde al texto, o null si no es un país de la lista. */
    public static String canonico(String texto) {
        return texto == null ? null : POR_CLAVE.get(clave(texto));
    }

    public static List<String> todos() {
        return List.copyOf(POR_CLAVE.values());
    }

    private static String clave(String texto) {
        return Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> cargar() {
        try (InputStream in = Paises.class.getResourceAsStream("/paises.txt")) {
            if (in == null) throw new IllegalStateException("Falta resources/paises.txt");
            Map<String, String> mapa = new LinkedHashMap<>();
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    if (!linea.isBlank()) mapa.put(clave(linea), linea.trim());
                }
            }
            return mapa;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer resources/paises.txt", e);
        }
    }
}
