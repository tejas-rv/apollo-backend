package com.apollo.elevator.engineer.model.dto;

import com.apollo.elevator.engineer.model.enums.ReportStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ServiceReportResponse(
        Long id,
        Long amcContractId,
        Long customerId,
        String customerName,
        Long engineerUserId,
        String engineerName,
        LocalDate visitDate,
        String overallNotes,
        ReportStatus status,
        LocalDateTime submittedAt,
        LocalDateTime pdfSentAt,
        List<ServiceCheckItemDto> checkItems
) {}
