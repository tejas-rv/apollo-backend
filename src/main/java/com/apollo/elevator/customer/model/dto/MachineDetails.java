package com.apollo.elevator.customer.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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
public class MachineDetails {

    @Size(max = 100)
    private String manufacturedBy;

    private Integer yearOfManufacture;

    @Size(max = 50)
    @JsonAlias("machineType")
    private String machineType;

    private Integer noOfGrooves;

    private Integer frictionSheaveDiameter;

    private Integer noOfRopes;

    private Integer diaOfTheRopeMm;

    private Integer lengthOfTheRopeMm;

    private Boolean isDeflectorPulley;

    @Valid
    private DeflectorPulleyDetails deflectorPulley;

    @Valid
    private MainMotorDetails mainMotor;

    @Size(max = 50)
    private String roping;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeflectorPulleyDetails {
        private Integer diameter;
        private Integer noOfGrooves;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MainMotorDetails {
        private String kw;
        private String amps;
        private String speed;
        private String voltage;
        private String frequency;
        private Integer noOfPoles;
    }
}
