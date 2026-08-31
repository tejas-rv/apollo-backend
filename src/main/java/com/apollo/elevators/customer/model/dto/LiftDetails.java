package com.apollo.elevators.customer.model.dto;

import com.apollo.elevators.customer.model.enums.DoorType;
import com.apollo.elevators.customer.model.enums.DriveType;
import com.apollo.elevators.customer.model.enums.LiftType;
import com.apollo.elevators.customer.model.validation.ValidLiftDetails;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidLiftDetails
public class LiftDetails {

    private Long id;

    private LiftType liftType;

    private DriveType driveType;

    @Min(value = 1, message = "Number of floors must be at least 1")
    private Integer numberOfFloors;

    private Integer capacityInKg;

    private Integer capacityInPersons;

    @Size(max = 100, message = "Brand must not exceed 100 characters")
    @JsonAlias("make")
    private String brand;

    @Size(max = 100, message = "Lift model must not exceed 100 characters")
    private String liftModel;

    @Size(max = 100, message = "Installation type must not exceed 100 characters")
    private String installationType;

    private Integer yearOfInstallation;

    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    private DoorType doorType;

    @Size(max = 100, message = "Machine type must not exceed 100 characters")
    private String machineType;

    @Size(max = 100, message = "Machine name must not exceed 100 characters")
    private String machineName;

    @DecimalMin(value = "0.0", inclusive = true, message = "KW cannot be negative")
    private BigDecimal kw;

    @DecimalMin(value = "0.0", inclusive = true, message = "Amps cannot be negative")
    private BigDecimal amps;

    @DecimalMin(value = "0.0", inclusive = true, message = "Speed cannot be negative")
    private BigDecimal speed;

    @DecimalMin(value = "0.0", inclusive = true, message = "Voltage cannot be negative")
    private BigDecimal voltage;

    @DecimalMin(value = "0.0", inclusive = true, message = "Frequency cannot be negative")
    private BigDecimal frequency;

    @Size(max = 100, message = "OSG type must not exceed 100 characters")
    private String osgType;

    @DecimalMin(value = "0.0", inclusive = true, message = "Rated speed cannot be negative")
    private BigDecimal ratedSpeed;

    @DecimalMin(value = "0.0", inclusive = true, message = "Tripping speed cannot be negative")
    private BigDecimal trippingSpeed;

    private Boolean isUpsPresent;

    @Size(max = 100, message = "UPS type must not exceed 100 characters")
    private String upsType;

    @DecimalMin(value = "0.0", inclusive = true, message = "KVA cannot be negative")
    private BigDecimal kva;

    @Valid
    @JsonAlias("machineDetails")
    private MachineDetails machineDetails;

    @Valid
    @JsonAlias("osg")
    private OsgDetails osg;

    @Valid
    private UpsDetails ups;

    @Valid
    private List<AmcDetails> amcDetails;
}
