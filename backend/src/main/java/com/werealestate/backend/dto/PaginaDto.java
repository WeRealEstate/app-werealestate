package com.werealestate.backend.dto;

import java.util.List;

/** Página de resultados para listas que cargan por lotes ("Cargar más"): trae el contenido de
 * esta página y si hay una siguiente, sin necesidad de calcular el total de registros. */
public record PaginaDto<T>(List<T> contenido, boolean hayMas) {
}
