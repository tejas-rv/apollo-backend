package com.apollo.elevator.customer.model.dto;

import jakarta.validation.Valid;
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
public class UpsDetails {

    @Size(max = 100)
    private String upsType;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal kva;

    @Valid
    private BatteryDetails battery;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatteryDetails {
        @Size(max = 100)
        private String make;

        private String voltage;

        private Integer noOfBatteries;
    }
}
