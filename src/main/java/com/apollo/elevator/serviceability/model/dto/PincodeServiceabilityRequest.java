package com.apollo.elevator.serviceability.model.dto;

import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PincodeServiceabilityRequest(
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "PIN code must contain exactly six digits") String pincode,
        @NotBlank @Size(max = 120) String locality,
        @Size(max = 100) String district,
        @Size(max = 100) String state,
        @Size(max = 120) String postalDivision,
        @Size(max = 30) String zoneId,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotBlank @Size(max = 200) String source,
        PostalVerificationStatus verificationStatus
) {
}
