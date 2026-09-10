package com.apollo.elevator.workorder.model.dto;

import com.apollo.elevator.workorder.model.enums.WorkOrderStatus;
import java.time.LocalDateTime;

public record WorkOrderResponse(
        Long id,
        String workOrderNumber,
        Long enquiryId,
        Long quotationId,
        WorkOrderStatus status,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
