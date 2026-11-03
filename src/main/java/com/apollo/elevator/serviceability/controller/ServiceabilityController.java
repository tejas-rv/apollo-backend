package com.apollo.elevator.serviceability.controller;

import com.apollo.elevator.serviceability.model.dto.AssignmentBatchRequest;
import com.apollo.elevator.serviceability.model.dto.AssignmentBatchResponse;
import com.apollo.elevator.serviceability.model.dto.DailyPlanResponse;
import com.apollo.elevator.serviceability.model.dto.EngineerServiceabilityResponse;
import com.apollo.elevator.serviceability.model.dto.EngineerServiceabilityUpdateRequest;
import com.apollo.elevator.serviceability.model.dto.EngineerZoneAssignmentRequest;
import com.apollo.elevator.serviceability.model.dto.EngineerZoneAssignmentResponse;
import com.apollo.elevator.serviceability.model.dto.ManualServiceAssignmentRequest;
import com.apollo.elevator.serviceability.model.dto.PincodeImportResponse;
import com.apollo.elevator.serviceability.model.dto.PincodeLookupResponse;
import com.apollo.elevator.serviceability.model.dto.PincodeServiceabilityRequest;
import com.apollo.elevator.serviceability.model.dto.PincodeServiceabilityResponse;
import com.apollo.elevator.serviceability.model.dto.ReassignServiceRequest;
import com.apollo.elevator.serviceability.model.dto.ServiceAssignmentResponse;
import com.apollo.elevator.serviceability.model.dto.ServiceabilityDashboardResponse;
import com.apollo.elevator.serviceability.model.dto.ServiceabilityZoneRequest;
import com.apollo.elevator.serviceability.model.dto.ServiceabilityZoneResponse;
import com.apollo.elevator.serviceability.model.enums.AssignmentStatus;
import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import com.apollo.elevator.serviceability.service.EngineerServiceabilityService;
import com.apollo.elevator.serviceability.service.PincodeServiceabilityService;
import com.apollo.elevator.serviceability.service.ServiceAllocationService;
import com.apollo.elevator.serviceability.service.ServiceabilityZoneService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/serviceability")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ServiceabilityController {

    private final ServiceabilityZoneService zoneService;
    private final PincodeServiceabilityService pincodeService;
    private final EngineerServiceabilityService engineerService;
    private final ServiceAllocationService allocationService;

    @GetMapping("/pincode/{pincode}")
    public ResponseEntity<PincodeLookupResponse> lookupPincode(@PathVariable String pincode) {
        return ResponseEntity.ok(pincodeService.lookup(pincode));
    }

    @GetMapping("/zones")
    public ResponseEntity<java.util.List<ServiceabilityZoneResponse>> zones() {
        return ResponseEntity.ok(zoneService.list());
    }

    @GetMapping("/zones/{zoneId}")
    public ResponseEntity<ServiceabilityZoneResponse> zone(@PathVariable String zoneId) {
        return ResponseEntity.ok(zoneService.get(zoneId));
    }

    @PostMapping("/zones")
    public ResponseEntity<ServiceabilityZoneResponse> createZone(
            @Valid @RequestBody ServiceabilityZoneRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(zoneService.save(request, true));
    }

    @PutMapping("/zones/{zoneId}")
    public ResponseEntity<ServiceabilityZoneResponse> updateZone(
            @PathVariable String zoneId,
            @Valid @RequestBody ServiceabilityZoneRequest request
    ) {
        if (!zoneId.equals(request.zoneId())) {
            throw new IllegalArgumentException("Zone ID in the path must match the request");
        }
        return ResponseEntity.ok(zoneService.save(request, false));
    }

    @GetMapping("/pincodes")
    public ResponseEntity<Page<PincodeServiceabilityResponse>> pincodes(
            @RequestParam(required = false) String pincode,
            @RequestParam(required = false) String locality,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) PostalVerificationStatus verificationStatus,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(pincodeService.search(
                pincode, locality, district, zoneId, verificationStatus, active, pageRequest(page, size, "pincode")));
    }

    @PostMapping("/pincodes")
    public ResponseEntity<PincodeServiceabilityResponse> createPincode(
            @Valid @RequestBody PincodeServiceabilityRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pincodeService.create(request, actor(authentication)));
    }

    @PutMapping("/pincodes/{id}")
    public ResponseEntity<PincodeServiceabilityResponse> updatePincode(
            @PathVariable Long id,
            @Valid @RequestBody PincodeServiceabilityRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pincodeService.update(id, request, actor(authentication)));
    }

    @PostMapping(value = "/pincodes/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PincodeImportResponse> importPincodes(
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return ResponseEntity.ok(pincodeService.importFile(file, actor(authentication)));
    }

    @GetMapping("/engineers")
    public ResponseEntity<java.util.List<EngineerServiceabilityResponse>> engineers(
            @RequestParam(required = false) String zoneId
    ) {
        return ResponseEntity.ok(engineerService.engineers(zoneId));
    }

    @PostMapping("/engineer-zone-assignments")
    public ResponseEntity<EngineerZoneAssignmentResponse> assignEngineerToZone(
            @Valid @RequestBody EngineerZoneAssignmentRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(engineerService.createZoneAssignment(request));
    }

    @PutMapping("/engineer-zone-assignments/{id}")
    public ResponseEntity<EngineerZoneAssignmentResponse> updateEngineerZoneAssignment(
            @PathVariable Long id,
            @Valid @RequestBody EngineerZoneAssignmentRequest request
    ) {
        return ResponseEntity.ok(engineerService.updateZoneAssignment(id, request));
    }

    @PutMapping("/engineers/{engineerId}")
    public ResponseEntity<EngineerServiceabilityResponse> updateEngineerServiceability(
            @PathVariable Long engineerId,
            @Valid @RequestBody EngineerServiceabilityUpdateRequest request
    ) {
        return ResponseEntity.ok(engineerService.updateServiceability(engineerId, request));
    }

    @GetMapping("/engineer-zone-assignments")
    public ResponseEntity<java.util.List<EngineerZoneAssignmentResponse>> engineerZoneAssignments(
            @RequestParam(required = false) Long engineerId
    ) {
        return ResponseEntity.ok(engineerService.zoneAssignments(engineerId));
    }

    @PostMapping("/assign")
    public ResponseEntity<AssignmentBatchResponse> allocate(
            @Valid @RequestBody AssignmentBatchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(allocationService.allocate(request, actor(authentication)));
    }

    @GetMapping("/assignments")
    public ResponseEntity<Page<ServiceAssignmentResponse>> assignments(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long engineerId,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(allocationService.assignments(
                date, engineerId, zoneId, status, pageRequest(page, size, "scheduledDate")));
    }

    @PostMapping("/assignments/manual")
    public ResponseEntity<ServiceAssignmentResponse> assignManually(
            @Valid @RequestBody ManualServiceAssignmentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(allocationService.assignManually(request, actor(authentication)));
    }

    @PutMapping("/assignments/{id}/reassign")
    public ResponseEntity<ServiceAssignmentResponse> reassign(
            @PathVariable Long id,
            @Valid @RequestBody ReassignServiceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(allocationService.reassign(id, request, actor(authentication)));
    }

    @PutMapping("/visits/{visitId}/reassign")
    public ResponseEntity<ServiceAssignmentResponse> reassignVisit(
            @PathVariable Long visitId,
            @Valid @RequestBody ReassignServiceRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(allocationService.reassignVisit(visitId, request, actor(authentication)));
    }

    @PutMapping("/assignments/{id}/sequence")
    public ResponseEntity<ServiceAssignmentResponse> updateSequence(
            @PathVariable Long id,
            @RequestParam int sequence
    ) {
        return ResponseEntity.ok(allocationService.updateSequence(id, sequence));
    }

    @GetMapping("/daily-plan")
    public ResponseEntity<DailyPlanResponse> dailyPlan(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(allocationService.dailyPlan(date));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<ServiceabilityDashboardResponse> dashboard() {
        return ResponseEntity.ok(allocationService.dashboard());
    }

    private static PageRequest pageRequest(int page, int size, String sort) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sort));
    }

    private static String actor(Authentication authentication) {
        return authentication == null ? "system" : authentication.getName();
    }
}
