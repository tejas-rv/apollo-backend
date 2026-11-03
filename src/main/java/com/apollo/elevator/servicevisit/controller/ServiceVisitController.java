package com.apollo.elevator.servicevisit.controller;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.servicevisit.model.dto.RescheduleHistoryEntry;
import com.apollo.elevator.servicevisit.model.dto.RescheduleVisitRequest;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitFilter;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitRow;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitSummary;
import com.apollo.elevator.servicevisit.service.ServiceVisitService;
import com.apollo.elevator.serviceability.service.ServiceAllocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/services")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Services", description = "AMC service visits across customers; reschedule visits with a raised issue (ADMIN only)")
public class ServiceVisitController {

    private final ServiceVisitService serviceVisitService;
    private final ServiceAllocationService serviceAllocationService;

    @GetMapping
    @Operation(summary = "Search AMC service visits (newest first)")
    public ResponseEntity<Page<ServiceVisitRow>> search(
            @RequestParam(required = false) ServiceVisitStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "false") boolean rescheduledOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        ServiceVisitFilter filter = new ServiceVisitFilter(status, from, to, query, overdueOnly, rescheduledOnly);
        return ResponseEntity.ok(serviceVisitService.search(filter, page, size));
    }

    @GetMapping("/summary")
    @Operation(summary = "Visit counts: scheduled, overdue, completed, cancelled, rescheduled")
    public ResponseEntity<ServiceVisitSummary> summary() {
        return ResponseEntity.ok(serviceVisitService.summary());
    }

    @GetMapping("/{visitId}/reschedules")
    @Operation(summary = "Reschedule history of a visit (issues raised and date/engineer changes)")
    public ResponseEntity<List<RescheduleHistoryEntry>> history(@PathVariable Long visitId) {
        return ResponseEntity.ok(serviceVisitService.history(visitId));
    }

    @PostMapping("/{visitId}/reschedule")
    @Operation(summary = "Reschedule a visit after an issue was raised by the customer or an admin. "
            + "SCHEDULED visits move to the new date; COMPLETED/CANCELLED visits get a follow-up visit")
    public ResponseEntity<ServiceVisitRow> reschedule(@PathVariable Long visitId,
                                                      @Valid @RequestBody RescheduleVisitRequest request,
                                                      Authentication authentication) {
        String user = authentication == null ? null : authentication.getName();
        ServiceVisitRow row = serviceVisitService.reschedule(visitId, request, user);
        if (row.id().equals(visitId)) {
            serviceAllocationService.syncRescheduledVisit(
                    visitId, row.serviceDate(), row.assignedEngineerId(), user);
        }
        return ResponseEntity.ok(row);
    }
}
