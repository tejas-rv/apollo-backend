package com.apollo.elevator.engineer.model.dto;

import java.time.LocalDate;

public record EngineerScheduledVisit(
        Long id,
        LocalDate serviceDate,
        String workDone,
        Long customerId,
        String customerName,
        String city,
        String contractNumber,
        Long amcContractId,
        String liftBrand,
        String serialNumber
) {}
