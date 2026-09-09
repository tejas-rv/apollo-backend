package com.apollo.elevator.customer.model.dto;

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
    private String name;

    @Size(max = 20, message = "Representative mobile number must not exceed 20 characters")
    private String mobileNumber;
}
