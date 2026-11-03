package com.apollo.elevator.customer.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.apollo.elevator.common.validation.IndianFormats;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiftCustomerDetails {

    private Long id;

    private String customerCode;

    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name must not exceed 100 characters")
    private String customerName;

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = IndianFormats.MOBILE, message = "Mobile number " + IndianFormats.MOBILE_MESSAGE)
    private String mobileNumber;

    @Email(regexp = IndianFormats.EMAIL, message = "Invalid email address")
    @Size(max = 150, message = "Email must not exceed 150 characters")
    private String email;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    @Pattern(regexp = IndianFormats.OPTIONAL_ADDRESS, message = "Address " + IndianFormats.ADDRESS_MESSAGE)
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    @Pattern(regexp = IndianFormats.NAME, message = "City " + IndianFormats.NAME_MESSAGE)
    private String city;

    @Size(max = 100, message = "State must not exceed 100 characters")
    @Pattern(regexp = IndianFormats.NAME, message = "State " + IndianFormats.NAME_MESSAGE)
    private String state;

    @Pattern(regexp = IndianFormats.OPTIONAL_PINCODE, message = "Pincode " + IndianFormats.PINCODE_MESSAGE)
    private String pincode;

    @Valid
    @JsonAlias("clientRepresentative")
    private List<ClientRepresentativeDetails> clientRepresentative;

    @Valid
    @JsonAlias("lifts")
    private List<LiftDetails> lifts;
}
