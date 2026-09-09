package com.apollo.elevator.customer.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OsgDetails {

    @Size(max = 100)
    private String make;

    private Integer diaOfTheRope;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal ratedSpeed;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal trippingSpeed;
}
