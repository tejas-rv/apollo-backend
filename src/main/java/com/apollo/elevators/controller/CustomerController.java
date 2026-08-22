package com.apollo.elevators.controller;

import com.apollo.elevators.dto.LiftCustomerDetails;
import com.apollo.elevators.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    /**
     * Create a new customer.
     */
    @PostMapping
    public ResponseEntity<LiftCustomerDetails> createCustomer(
            @Valid @RequestBody LiftCustomerDetails request) {

        LiftCustomerDetails response = customerService.createCustomer(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Get customer by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<LiftCustomerDetails> getCustomer(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                customerService.getCustomerById(id)
        );
    }

    /**
     * Get all customers with pagination.
     */
    @GetMapping
    public ResponseEntity<Page<LiftCustomerDetails>> getCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = buildPageable(page, size);

        return ResponseEntity.ok(
                customerService.getAllCustomers(pageable)
        );
    }

    /**
     * Search customers.
     *
     * Example:
     * /api/admin/customers/search?query=apollo&page=0&size=20
     */
    @GetMapping("/search")
    public ResponseEntity<Page<LiftCustomerDetails>> searchCustomers(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = buildPageable(page, size);

        return ResponseEntity.ok(
                customerService.searchCustomers(query, pageable)
        );
    }

    /**
     * Update an existing customer.
     */
    @PutMapping("/{id}")
    public ResponseEntity<LiftCustomerDetails> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody LiftCustomerDetails request) {

        return ResponseEntity.ok(
                customerService.updateCustomer(id, request)
        );
    }

    /**
     * Delete a customer.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(
            @PathVariable Long id) {

        customerService.deleteCustomer(id);

        return ResponseEntity.noContent().build();
    }

    private Pageable buildPageable(int page, int size) {

        int pageSize = Math.min(Math.max(size, 1), 100);

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
