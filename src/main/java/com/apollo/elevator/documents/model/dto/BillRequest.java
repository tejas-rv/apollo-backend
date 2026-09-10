package com.apollo.elevator.documents.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request DTO for generating GST Bill PDFs.
 */
public record BillRequest(

        @NotNull(message = "Entity is required")
        BillingEntity entity,

        @NotNull(message = "Bill details are required")
        @Valid
        BillDetails bill,

        @NotNull(message = "Bill-to party details are required")
        @Valid
        BillToParty billTo,

        @Valid
        List<BillLineItem> lineItems

) {

    public enum BillingEntity {
        APOLLO_ELEVATOR,
        APOLLO_ELEVATOR_SERVICES
    }

    public record BillDetails(
            @NotBlank(message = "Invoice number is required")
            String invoiceNumber,

            @NotBlank(message = "Invoice date is required")
            String invoiceDate,

            String state,
            String pincode,
            String mobile,
            String panNumber,
            String gstin,

           @NotNull(message = "GST percentage is required")
           Double gstPercentage,

           Double sgstPercentage,
           Double cgstPercentage
    ) {}

    public record BillToParty(
            @NotBlank(message = "Customer name is required")
            String name,

            String address,
            String poNumber,
            String jobNumber,
            String projectName,
            String gstin,
            String state
    ) {}

    public record BillLineItem(
            Integer slNo,
            @NotBlank(message = "Description is required")
            String description,
            String hsnCode,
            String quantity,
            Double rate,
            Double amount
    ) {}
}
