package com.apollo.elevator.enquiry.model.dto;

public record CustomerConversionResponse(
        Long enquiryId,
        Long customerId,
        String status
) {}
