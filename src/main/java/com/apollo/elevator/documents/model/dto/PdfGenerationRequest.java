package com.apollo.elevator.documents.model.dto;

import com.apollo.elevator.documents.model.enums.DocumentType;
import jakarta.validation.constraints.NotNull;

public record PdfGenerationRequest(

        @NotNull
        DocumentType documentType,

        Long customerId,

        BillRequest billRequest

) {
}
