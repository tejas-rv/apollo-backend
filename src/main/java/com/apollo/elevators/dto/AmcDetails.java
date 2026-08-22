package com.apollo.elevators.dto;

import com.apollo.elevators.enums.AmcStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmcDetails {

    private Long id;

    @Size(max = 50, message = "Contract number must not exceed 50 characters")
    private String contractNumber;

    private AmcStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 50, message = "AMC type must not exceed 50 characters")
    private String amcType;

    @DecimalMin(
            value = "0.0",
            inclusive = true,
            message = "AMC amount cannot be negative"
    )
    private BigDecimal amcAmount;

    @Size(max = 20, message = "Payment frequency must not exceed 20 characters")
    private String paymentFrequency;

    private LocalDate nextPaymentDate;

    private LocalDate nextServiceDate;

    private Integer totalServices;

    private Integer completedServices;

    @Size(max = 500, message = "Terms and conditions must not exceed 500 characters")
    private String termsAndConditions;

    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;
}
