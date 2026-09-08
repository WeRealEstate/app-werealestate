package com.werealestate.backend.dto;

import java.util.List;

public record LeadImportResultado(int creados, List<LeadImportError> errores) {
}
