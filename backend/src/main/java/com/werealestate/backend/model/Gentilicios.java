package com.werealestate.backend.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Nacionalidad (gentilicio en femenino: «nacionalidad mexicana») de cada país de {@link Paises}, leída de
 * resources/gentilicios.txt («País|Gentilicio» por línea). La misma lista vive en el frontend (core/data/gentilicios.ts).
 * La usa el registro de venta de We Forms para llenar la nacionalidad a partir del país de nacimiento (12.21.0).
 */
public final class Gentilicios {

    private static final Map<String, String> POR_PAIS = leer();

    private Gentilicios() {}

    /** El gentilicio de un país (nombre tal como está en la lista de países); null si no está. */
    public static String de(String pais) {
        return pais == null ? null : POR_PAIS.get(pais);
    }

    private static Map<String, String> leer() {
        try (InputStream in = Gentilicios.class.getResourceAsStream("/gentilicios.txt")) {
            if (in == null) throw new IllegalStateException("Falta resources/gentilicios.txt");
            Map<String, String> mapa = new HashMap<>();
            try (BufferedReader lector = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String linea;
                while ((linea = lector.readLine()) != null) {
                    int i = linea.indexOf('|');
                    if (i > 0) mapa.put(linea.substring(0, i).trim(), linea.substring(i + 1).trim());
                }
            }
            return mapa;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer resources/gentilicios.txt", e);
        }
    }
}
