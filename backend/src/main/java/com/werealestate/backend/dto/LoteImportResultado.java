package com.werealestate.backend.dto;

import java.util.List;

public record LoteImportResultado(int creados, List<LoteImportError> errores) {
}
