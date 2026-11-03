package com.apollo.elevator.serviceability.model.dto;

import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import java.time.LocalDate;

public record PincodeServiceabilityResponse(
        Long id,
        String pincode,
        String locality,
        String district,
        String state,
        String postalDivision,
        String zoneId,
        String zoneName,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String source,
        PostalVerificationStatus verificationStatus,
        LocalDate lastVerifiedDate
) {
}
