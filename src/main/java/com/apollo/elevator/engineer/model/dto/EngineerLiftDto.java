package com.apollo.elevator.engineer.model.dto;

import com.apollo.elevator.customer.model.enums.DoorType;
import com.apollo.elevator.customer.model.enums.DriveType;
import com.apollo.elevator.customer.model.enums.LiftType;

import java.math.BigDecimal;

/** Lift details visible to engineer — includes operational/technical specs, excludes AMC data */
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
        String manufacturedBy,
        Integer yearOfManufacture,
        Integer noOfGrooves,
        Integer frictionSheaveDiameter,
        Integer noOfRopes,
        Integer diaOfTheRopeMm,
        Integer lengthOfTheRopeMm,
        Boolean isDeflectorPulley,
        Integer deflectorPulleyDiameter,
        Integer deflectorPulleyNoOfGrooves,
        String roping,
        String mainMotorKw,
        String mainMotorAmps,
        String mainMotorSpeed,
        String mainMotorVoltage,
        String mainMotorFrequency,
        Integer mainMotorNoOfPoles,
        String batteryMake,
        String batteryVoltage,
        Integer batteryNoOfBatteries
) {}
