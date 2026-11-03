package com.apollo.elevator.serviceability.model.dto;

import java.util.List;

public record PincodeLookupResponse(
        String pincode,
        boolean serviceable,
        boolean ambiguous,
        String message,
        List<PincodeServiceabilityResponse> mappings
) {
}
