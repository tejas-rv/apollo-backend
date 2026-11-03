package com.apollo.elevator.billing.model.dto;

import com.apollo.elevator.billing.model.enums.PaymentFrequency;
import com.apollo.elevator.customer.model.enums.ContractType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Creates an AMC contract on one of the customer's lifts straight from billing.
 * Amount comes from the plan price; end / next payment / next service dates are derived from {@code startDate}.
 * {@code contractNumber} is generated when blank; {@code paymentFrequency} defaults to YEARLY and
 * {@code startDate} to today.
 */
public record NewAmcContractRequest(
        @NotNull(message = "Select the lift for the AMC contract") Long liftId,
        @NotNull(message = "Choose an AMC plan (SILVER / GOLD / PLATINUM)") ContractType contractType,
        PaymentFrequency paymentFrequency,
        LocalDate startDate,
        @Size(max = 50, message = "Contract number must be at most 50 characters") String contractNumber
) {
}
