package com.apollo.elevators.engineer.model.dto;

import com.apollo.elevators.customer.model.enums.AmcStatus;
import com.apollo.elevators.customer.model.enums.ContractType;

import java.time.LocalDate;
import java.util.List;

/** AMC details visible to engineer — amcAmount deliberately excluded */
public record EngineerAmcDto(
        Long id,
        String contractNumber,
        AmcStatus status,
        LocalDate startDate,
        LocalDate endDate,
        ContractType contractType,
        String paymentFrequency,
        LocalDate nextServiceDate,
        Integer totalServices,
        Integer completedServices
) {}
