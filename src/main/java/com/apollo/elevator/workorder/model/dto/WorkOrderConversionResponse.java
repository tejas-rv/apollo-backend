package com.apollo.elevator.workorder.model.dto;

public record WorkOrderConversionResponse(
        Long enquiryId,
        Long quotationId,
        Long workOrderId,
        String status
) {}
