package com.apollo.elevator.authorization.model.dto;

import com.apollo.elevator.common.validation.IndianFormats;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UserProfileRequest(
        @NotBlank(message = "employeeNumber is required")
        @Size(max = 30, message = "employeeNumber must not exceed 30 characters")
        @Pattern(regexp = "^[A-Za-z0-9/_-]+$", message = "employeeNumber may contain only letters, digits, '/', '_' and '-'")
        String employeeNumber,

        @NotBlank(message = "fullName is required")
        @Size(max = 100, message = "fullName must not exceed 100 characters")
        @Pattern(regexp = IndianFormats.NAME, message = "fullName " + IndianFormats.NAME_MESSAGE)
        String fullName,

        @Pattern(regexp = IndianFormats.OPTIONAL_MOBILE, message = "phone " + IndianFormats.MOBILE_MESSAGE)
        String phone,

        @Email(regexp = IndianFormats.EMAIL, message = "email must be a valid email address")
        @Size(max = 150, message = "email must not exceed 150 characters")
        String email,

        @Size(max = 500, message = "address must not exceed 500 characters")
        @Pattern(regexp = IndianFormats.OPTIONAL_ADDRESS, message = "address " + IndianFormats.ADDRESS_MESSAGE)
        String address,

        @Size(max = 100, message = "designation must not exceed 100 characters")
        String designation,

        @PastOrPresent(message = "dateOfJoining cannot be in the future")
        LocalDate dateOfJoining,

        @Size(max = 200, message = "specialization must not exceed 200 characters")
        String specialization,

        @Size(max = 100, message = "emergencyContactName must not exceed 100 characters")
        @Pattern(regexp = IndianFormats.NAME, message = "emergencyContactName " + IndianFormats.NAME_MESSAGE)
        String emergencyContactName,

        @Pattern(regexp = IndianFormats.OPTIONAL_MOBILE,
                message = "emergencyContactPhone " + IndianFormats.MOBILE_MESSAGE)
        String emergencyContactPhone,

        @Size(max = 1000, message = "notes must not exceed 1000 characters")
        String notes
) {}
