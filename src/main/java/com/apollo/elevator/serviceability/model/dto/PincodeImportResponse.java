package com.apollo.elevator.serviceability.model.dto;

import java.util.List;

public record PincodeImportResponse(
        int imported,
        int rejected,
        List<RowError> errors
) {
    public record RowError(int row, String message) {
    }
}
