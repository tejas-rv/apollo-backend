package com.apollo.elevator.customer.model.dto;

import com.apollo.elevator.common.validation.IndianFormats;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientRepresentativeDetails {

    @Size(max = 100, message = "Representative name must not exceed 100 characters")
    @Pattern(regexp = IndianFormats.NAME, message = "Representative name " + IndianFormats.NAME_MESSAGE)
    private String name;

    @Pattern(regexp = IndianFormats.OPTIONAL_MOBILE,
            message = "Representative mobile number " + IndianFormats.MOBILE_MESSAGE)
    private String mobileNumber;
}
