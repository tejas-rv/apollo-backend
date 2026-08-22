package com.apollo.elevators.dto;

import com.apollo.elevators.enums.DriveType;
import com.apollo.elevators.enums.LiftType;
import com.apollo.elevators.validation.ValidLiftDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private String brand;

    @Size(max = 100, message = "Lift model must not exceed 100 characters")
    private String liftModel;

    @Size(max = 100, message = "Installation type must not exceed 100 characters")
    private String installationType;

    private Integer yearOfInstallation;

    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    @Valid
    private List<AmcDetails> amcContracts;
}
