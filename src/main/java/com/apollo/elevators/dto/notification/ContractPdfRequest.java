package com.apollo.elevators.dto.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ContractPdfRequest(

        @NotNull(message = "Customer details are required")
        @Valid
        CustomerDetails customer,

        @NotNull(message = "Contract details are required")
        @Valid
        ContractDetails contract,

        @Valid
        List<ServiceHistoryEntry> serviceHistory,

        @Email(message = "Email recipient must be valid")
        @Size(max = 255)
        String email,

        @Size(max = 200)
        String emailSubject,

        @Size(max = 100)
        String referenceKey

) {

    public record CustomerDetails(
            @NotBlank(message = "Customer name is required")
            String name,

            String address,
            String city,
            String state,
            String pincode,
            String mobileNumber,
            String email,
            String customerCode
    ) {}

    public record ContractDetails(
            @NotBlank(message = "Contract number is required")
            String contractNumber,

            String liftType,
            String driveType,
            String brand,
            String liftModel,
            String serialNumber,
            Integer numberOfFloors,
            Integer capacityInPersons,
            Integer capacityInKg,
            String installationType,
            Integer yearOfInstallation,

            String amcType,
            String status,
            String startDate,
            String endDate,
            Double amcAmount,
            String paymentFrequency,
            String nextPaymentDate,
            String nextServiceDate,

            Integer totalServices,
            Integer completedServices,
            Integer remainingServices,

            String termsAndConditions,
            String remarks
    ) {}

    public record ServiceHistoryEntry(
            String serviceDate,
            String engineerName,
            String workDone,
            String status
    ) {}
}
