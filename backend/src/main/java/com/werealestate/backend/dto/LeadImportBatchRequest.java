package com.werealestate.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record LeadImportBatchRequest(
        Long asesorId,
        @NotEmpty @Valid List<LeadImportRequest> leads) {
}
