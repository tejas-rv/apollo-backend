package com.apollo.elevators.engineer.model.dto;

import com.apollo.elevators.customer.model.enums.DoorType;
import com.apollo.elevators.customer.model.enums.DriveType;
import com.apollo.elevators.customer.model.enums.LiftType;

import java.math.BigDecimal;
import java.util.List;

/** Lift details visible to engineer — includes operational/technical specs, excludes financial data */
public record EngineerLiftDto(
        Long id,
        LiftType liftType,
        DriveType driveType,
        Integer numberOfFloors,
        Integer capacityInKg,
        Integer capacityInPersons,
        String brand,
        String liftModel,
        String installationType,
        Integer yearOfInstallation,
        String serialNumber,
        DoorType doorType,
        String machineType,
        String machineName,
        BigDecimal kw,
        BigDecimal amps,
        BigDecimal speed,
        BigDecimal voltage,
        BigDecimal frequency,
        String osgType,
        BigDecimal ratedSpeed,
        BigDecimal trippingSpeed,
        Boolean isUpsPresent,
        String upsType,
        BigDecimal kva,
        List<EngineerAmcDto> amcContracts
) {}
