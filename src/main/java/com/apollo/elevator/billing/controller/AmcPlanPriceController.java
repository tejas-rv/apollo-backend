package com.apollo.elevator.billing.controller;

import com.apollo.elevator.billing.model.dto.AmcPlanPriceResponse;
import com.apollo.elevator.billing.model.dto.UpdateAmcPlanPriceRequest;
import com.apollo.elevator.billing.service.AmcPlanPriceService;
import com.apollo.elevator.customer.model.enums.ContractType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/amc-plans")
@RequiredArgsConstructor
@Tag(name = "AMC Plans", description = "Fixed yearly AMC price per plan (SILVER / GOLD / PLATINUM), per lift, before GST")
public class AmcPlanPriceController {

    private final AmcPlanPriceService service;

    @GetMapping
    @Operation(summary = "List AMC plan prices")
    public ResponseEntity<List<AmcPlanPriceResponse>> list() {
        return ResponseEntity.ok(service.list());
    }

    @PutMapping("/{contractType}")
    @Operation(summary = "Update the yearly price of an AMC plan")
    public ResponseEntity<AmcPlanPriceResponse> update(@PathVariable ContractType contractType,
                                                       @Valid @RequestBody UpdateAmcPlanPriceRequest request,
                                                       Authentication authentication) {
        return ResponseEntity.ok(service.update(contractType, request.amount(), BillingController.user(authentication)));
    }
}
