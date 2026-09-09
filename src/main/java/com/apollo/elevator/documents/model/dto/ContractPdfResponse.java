package com.apollo.elevator.documents.model.dto;

public record ContractPdfResponse(
        String contractNumber,
        String pdfFileName,
        long pdfSizeBytes,
        String emailStatus,
        String emailNotificationId,
        String message
) {}
