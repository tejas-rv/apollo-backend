package com.apollo.elevators.controller;

import com.apollo.elevators.common.api.ApiErrorResponse;
import com.apollo.elevators.dto.LiftCustomerDetails;
import com.apollo.elevators.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Customer Management", description = "Customer, lift, and AMC management endpoints")
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Create a new customer.
     */
    @PostMapping("/create")
    @Operation(
        summary = "Create customer",
        description = "Creates a customer with optional lift and AMC details"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Customer created successfully",
            content = @Content(schema = @Schema(implementation = LiftCustomerDetails.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Customer code already exists",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LiftCustomerDetails> createCustomer(
        @Valid @RequestBody LiftCustomerDetails request) {
        int liftCount = request.getLifts() == null ? 0 : request.getLifts().size();
        log.info(
            "Create customer request received. customerCode={}, customerName={}, liftCount={}",
            request.getCustomerCode(),
            request.getCustomerName(),
            liftCount
        );

        LiftCustomerDetails response = customerService.createCustomer(request);
        log.info(
            "Create customer completed. customerId={}, customerCode={}",
            response.getId(),
            response.getCustomerCode()
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response);
    }

    /**
     * Get customer by ID.
     */
    @GetMapping("/getCustomerUsingId/{id}")
    @Operation(summary = "Get customer by ID", description = "Fetches a single customer with lifts and AMC details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer fetched successfully",
            content = @Content(schema = @Schema(implementation = LiftCustomerDetails.class))),
        @ApiResponse(responseCode = "404", description = "Customer not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LiftCustomerDetails> getCustomer(
        @Parameter(description = "Customer ID", example = "2")
        @PathVariable Long id) {
        log.info("Get customer request received. customerId={}", id);

        LiftCustomerDetails response = customerService.getCustomerById(id);
        log.info(
            "Get customer completed. customerId={}, customerCode={}",
            response.getId(),
            response.getCustomerCode()
        );
        return ResponseEntity.ok(
            response
        );
    }

    /**
     * Get all customers with pagination.
     */
    @GetMapping("/getAllCustomers")
    @Operation(summary = "List customers", description = "Fetches customers using pagination")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customers fetched successfully")
    })
    public ResponseEntity<Page<LiftCustomerDetails>> getCustomers(
        @Parameter(description = "Page number (0-based)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size (1-100)", example = "20")
        @RequestParam(defaultValue = "20") int size) {
        log.info("Get customers request received. page={}, size={}", page, size);

        Pageable pageable = buildPageable(page, size);
        Page<LiftCustomerDetails> response = customerService.getAllCustomers(pageable);
        log.info(
            "Get customers completed. page={}, size={}, totalElements={}",
            page,
            size,
            response.getTotalElements()
        );

        return ResponseEntity.ok(
            response
        );
    }

    /**
     * Search customers.
     * <p>
     * Example: /api/admin/customers/search?query=apollo&page=0&size=20
     */
    @GetMapping("/search")
    @Operation(summary = "Search customers", description = "Search by code, name, mobile, email, city, or state")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully")
    })
    public ResponseEntity<Page<LiftCustomerDetails>> searchCustomers(
        @Parameter(description = "Search text", example = "apollo")
        @RequestParam(required = false, defaultValue = "") String query,
        @Parameter(description = "Page number (0-based)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size (1-100)", example = "20")
        @RequestParam(defaultValue = "20") int size) {
        log.info(
            "Search customers request received. query='{}', page={}, size={}",
            query,
            page,
            size
        );

        Pageable pageable = buildPageable(page, size);
        Page<LiftCustomerDetails> response = customerService.searchCustomers(query, pageable);
        log.info(
            "Search customers completed. query='{}', totalElements={}",
            query,
            response.getTotalElements()
        );

        return ResponseEntity.ok(
            response
        );
    }

    /**
     * Update an existing customer.
     */
    @PutMapping("/updateCustomerUsingId/{id}")
    @Operation(
        summary = "Update customer",
        description = "Updates customer details including nested lifts and AMC contracts"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Customer updated successfully",
            content = @Content(schema = @Schema(implementation = LiftCustomerDetails.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request payload",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Customer not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Conflict while updating customer",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<LiftCustomerDetails> updateCustomer(
        @Parameter(description = "Customer ID", example = "2")
        @PathVariable Long id,
        @Valid @RequestBody LiftCustomerDetails request) {
        int liftCount = request.getLifts() == null ? 0 : request.getLifts().size();
        log.info(
            "Update customer request received. customerId={}, customerCode={}, liftCount={}",
            id,
            request.getCustomerCode(),
            liftCount
        );
        LiftCustomerDetails response = customerService.updateCustomer(id, request);
        log.info(
            "Update customer completed. customerId={}, customerCode={}",
            response.getId(),
            response.getCustomerCode()
        );

        return ResponseEntity.ok(
            response
        );
    }

    /**
     * Delete a customer.
     */
    @DeleteMapping("/deleteCustomerUsingId/{id}")
    @Operation(summary = "Delete customer", description = "Deletes a customer by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Customer deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Customer not found",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Customer cannot be deleted due to dependent records",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> deleteCustomer(
        @Parameter(description = "Customer ID", example = "2")
        @PathVariable Long id) {
        log.info("Delete customer request received. customerId={}", id);

        customerService.deleteCustomer(id);
        log.info("Delete customer completed. customerId={}", id);

        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size) {

        int pageSize = Math.clamp(size, 1, 100);

        int pageNumber = Math.max(page, 0);

        return PageRequest.of(
            pageNumber,
            pageSize,
            Sort.by(
                Sort.Direction.ASC,
                "customerName"
            )
        );
    }
}
