package com.apollo.elevator.documents.model.dto;

import com.apollo.elevator.common.validation.IndianFormats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
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
        @Size(min = 1, max = 50, message = "Bill must have between 1 and 50 line items")
        List<BillLineItem> lineItems

) {

    /** Pincode as printed on bills: 6 digits, optionally written as "560 058". */
    public static final String BILL_PINCODE = "^$|^[1-9]\\d{2} ?\\d{3}$";

    public enum BillingEntity {
        APOLLO_ELEVATOR
    }

    public record BillDetails(
            @NotBlank(message = "Invoice number is required")
            @Size(max = 50, message = "Invoice number must not exceed 50 characters")
            String invoiceNumber,

            @NotBlank(message = "Invoice date is required")
            @Pattern(regexp = "^\\d{2}/\\d{2}/\\d{4}$", message = "Invoice date must be in dd/MM/yyyy format")
            String invoiceDate,

            @Size(max = 100, message = "State must not exceed 100 characters")
            @Pattern(regexp = IndianFormats.NAME, message = "State " + IndianFormats.NAME_MESSAGE)
            String state,

            @Pattern(regexp = BILL_PINCODE, message = "Pincode " + IndianFormats.PINCODE_MESSAGE)
            String pincode,

            @Pattern(regexp = IndianFormats.OPTIONAL_MOBILE, message = "Mobile " + IndianFormats.MOBILE_MESSAGE)
            String mobile,

            @Pattern(regexp = IndianFormats.OPTIONAL_PAN, message = "PAN " + IndianFormats.PAN_MESSAGE)
            String panNumber,

            @Pattern(regexp = IndianFormats.OPTIONAL_GSTIN, message = "GSTIN " + IndianFormats.GSTIN_MESSAGE)
            String gstin,

            @NotNull(message = "GST percentage is required")
            @DecimalMin(value = "0", message = "GST percentage must be between 0 and 100")
            @DecimalMax(value = "100", message = "GST percentage must be between 0 and 100")
            Double gstPercentage,

            /** Ignored on input; always derived as gstPercentage / 2. */
            Double sgstPercentage,
            /** Ignored on input; always derived as gstPercentage / 2. */
            Double cgstPercentage
    ) {}

    public record BillToParty(
            @NotBlank(message = "Customer name is required")
            @Size(max = 150, message = "Customer name must not exceed 150 characters")
            String name,

            @Size(max = 500, message = "Address must not exceed 500 characters")
            @Pattern(regexp = IndianFormats.OPTIONAL_ADDRESS, message = "Address " + IndianFormats.ADDRESS_MESSAGE)
            String address,

            @Size(max = 50, message = "PO number must not exceed 50 characters")
            String poNumber,
            @Size(max = 50, message = "Job number must not exceed 50 characters")
            String jobNumber,
            @Size(max = 150, message = "Project name must not exceed 150 characters")
            String projectName,

            @Pattern(regexp = IndianFormats.OPTIONAL_GSTIN, message = "Customer GSTIN " + IndianFormats.GSTIN_MESSAGE)
            String gstin,

            @Size(max = 100, message = "State must not exceed 100 characters")
            @Pattern(regexp = IndianFormats.NAME, message = "State " + IndianFormats.NAME_MESSAGE)
            String state
    ) {}

    public record BillLineItem(
            Integer slNo,
            @NotBlank(message = "Description is required")
            @Size(max = 500, message = "Description must not exceed 500 characters")
            String description,
            @Pattern(regexp = "^$|^\\d{4,8}$", message = "HSN/SAC code must be 4 to 8 digits")
            String hsnCode,
            @Size(max = 30, message = "Quantity must not exceed 30 characters")
            String quantity,
            @PositiveOrZero(message = "Rate cannot be negative")
            Double rate,
            @PositiveOrZero(message = "Amount cannot be negative")
            Double amount
    ) {}
}