package com.apollo.elevators.engineer.model.dto;

import com.apollo.elevators.customer.model.enums.DoorType;
import com.apollo.elevators.customer.model.enums.DriveType;
import com.apollo.elevators.customer.model.enums.LiftType;

import java.util.List;

/** Lift details visible to engineer — includes technical specs, excludes payment info */
public record EngineerLiftDto(
        Long id,
        LiftType liftType,
        DriveType driveType,
        Integer numberOfFloors,
        Integer capacityInKg,
        Integer capacityInPersons,
        String brand,
        String liftModel,
        String serialNumber,
        DoorType doorType,
        Integer yearOfInstallation,
        List<EngineerAmcDto> amcContracts
) {}
