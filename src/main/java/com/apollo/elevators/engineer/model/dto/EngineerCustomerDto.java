package com.apollo.elevators.engineer.model.dto;

import java.util.List;

/** Customer details visible to engineer — no AMC amount, no financial data */
public record EngineerCustomerDto(
        Long id,
        String customerCode,
        String customerName,
        String mobileNumber,
        String address,
        String city,
        String state,
        String pincode,
        List<EngineerLiftDto> lifts
) {}
