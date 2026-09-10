package com.apollo.elevator.workorder.controller;

import com.apollo.elevator.workorder.model.dto.WorkOrderResponse;
import com.apollo.elevator.workorder.service.WorkOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Work Orders", description = "Admin work order tracking and listing")
public class WorkOrderController {

    private final WorkOrderService workOrderService;

    @GetMapping("/work-orders")
    @Operation(summary = "List work orders", description = "Lists active work orders linked to enquiries in WORK_ORDER status")
    public ResponseEntity<Page<WorkOrderResponse>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.info("Work order list requested. query={}, status={}, page={}, size={}", query, status, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(workOrderService.list(query, status, pageable));
    }
}
