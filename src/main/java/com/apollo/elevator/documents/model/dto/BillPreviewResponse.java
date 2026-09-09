package com.apollo.elevator.documents.model.dto;

import com.apollo.elevator.documents.model.enums.DocumentType;

/**
 * Returned by GET /customers/{id}/bill-preview.
 *
 * <p>The {@code billRequest} field is a fully pre-filled {@link BillRequest} that the UI
 * can render for review, optionally edit, and then POST back to
 * POST /bills/generate?documentType=... to produce the final PDF.</p>
 *
 * @param documentType  the bill type that was requested
 * @param billRequest   pre-filled bill data derived from the customer's AMC record
 */
public record BillPreviewResponse(
        DocumentType documentType,
        BillRequest billRequest
) {}
