package com.apollo.elevators.dto.notification;

public record ContractPdfResponse(
        String contractNumber,
        String pdfFileName,
        long pdfSizeBytes,
        String emailStatus,
        String emailNotificationId,
        String message
) {}
