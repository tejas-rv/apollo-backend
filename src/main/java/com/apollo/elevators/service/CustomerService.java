package com.apollo.elevators.service;

import com.apollo.elevators.common.exception.ConflictException;
import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.customer.Customer;
import com.apollo.elevators.dto.LiftCustomerDetails;
import com.apollo.elevators.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Create a new customer.
     */
    public LiftCustomerDetails createCustomer(
            LiftCustomerDetails request) {

        if (customerRepository.existsByCustomerCode(
                request.getCustomerCode())) {

            throw new ConflictException(
                    "Customer code already exists: "
                            + request.getCustomerCode()
            );
        }

        Customer customer = new Customer();

        mapToEntity(request, customer);

        Customer savedCustomer =
                customerRepository.save(customer);

        return mapToDto(savedCustomer);
    }

    /**
     * Get customer by ID.
     */
    @Transactional(readOnly = true)
    public LiftCustomerDetails getCustomerById(Long id) {

        Customer customer = findCustomerById(id);

        return mapToDto(customer);
    }

    /**
     * Get all customers.
     */
    @Transactional(readOnly = true)
    public Page<LiftCustomerDetails> getAllCustomers(
            Pageable pageable) {

        return customerRepository
                .findAll(pageable)
                .map(this::mapToDto);
    }

    /**
     * Search customers.
     */
    @Transactional(readOnly = true)
    public Page<LiftCustomerDetails> searchCustomers(
            String query,
            Pageable pageable) {

        if (query == null || query.isBlank()) {
            return getAllCustomers(pageable);
        }

        return customerRepository
                .searchCustomers(query.trim(), pageable)
                .map(this::mapToDto);
    }

    /**
     * Update customer.
     */
    public LiftCustomerDetails updateCustomer(
            Long id,
            LiftCustomerDetails request) {

        Customer customer = findCustomerById(id);

        /*
         * Customer code is generally an identifier and should not
         * change during a normal update.
         *
         * If you want customerCode to be editable later,
         * we can handle that separately.
         */

        mapToEntity(request, customer);

        // Preserve the existing customer code.
        customer.setCustomerCode(
                customer.getCustomerCode()
        );

        Customer updatedCustomer =
                customerRepository.save(customer);

        return mapToDto(updatedCustomer);
    }

    /**
     * Delete customer.
     */
    public void deleteCustomer(Long id) {

        Customer customer = findCustomerById(id);

        try {
            customerRepository.delete(customer);
            customerRepository.flush();
        } catch (DataIntegrityViolationException e) {

            throw new ConflictException(
                    "Customer cannot be deleted because it is associated "
                            + "with other records."
            );
        }
    }

    /**
     * Find customer by ID.
     */
    private Customer findCustomerById(Long id) {

        return customerRepository
                .findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Customer not found with id: " + id
                        )
                );
    }

    /**
     * Map request DTO to entity.
     */
    private void mapToEntity(
            LiftCustomerDetails request,
            Customer customer) {

        if (request.getCustomerCode() != null
                && !request.getCustomerCode().isBlank()) {

            customer.setCustomerCode(
                    request.getCustomerCode().trim()
            );
        }

        customer.setCustomerName(
                request.getCustomerName().trim()
        );

        customer.setMobileNumber(
                request.getMobileNumber()
        );

        customer.setEmail(
                request.getEmail()
        );

        customer.setAddress(
                request.getAddress()
        );

        customer.setCity(
                request.getCity()
        );

        customer.setState(
                request.getState()
        );

        customer.setPincode(
                request.getPincode()
        );

        customer.setRemarks(
                request.getRemarks()
        );
    }

    /**
     * Map entity to response DTO.
     */
    private LiftCustomerDetails mapToDto(
            Customer customer) {

        return LiftCustomerDetails.builder()
                .id(customer.getId())
                .customerCode(customer.getCustomerCode())
                .customerName(customer.getCustomerName())
                .mobileNumber(customer.getMobileNumber())
                .email(customer.getEmail())
                .address(customer.getAddress())
                .city(customer.getCity())
                .state(customer.getState())
                .pincode(customer.getPincode())
                .remarks(customer.getRemarks())
                .build();
    }
}
