package com.apollo.elevators.service;

import com.apollo.elevators.common.exception.ConflictException;
import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.customer.AmcContract;
import com.apollo.elevators.customer.Customer;
import com.apollo.elevators.customer.Lift;
import com.apollo.elevators.dto.AmcDetails;
import com.apollo.elevators.dto.LiftCustomerDetails;
import com.apollo.elevators.dto.LiftDetails;
import com.apollo.elevators.repository.CustomerRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;

    /**
     * Create a new customer.
     */
    public LiftCustomerDetails createCustomer(
        LiftCustomerDetails request) {
        int liftCount = request.getLifts() == null ? 0 : request.getLifts().size();
        log.info(
            "Creating customer. customerCode={}, customerName={}, liftCount={}",
            request.getCustomerCode(),
            request.getCustomerName(),
            liftCount
        );
        logLiftPayload("Create customer payload", request.getLifts());

        if (customerRepository.existsByCustomerCode(
            request.getCustomerCode())) {
            log.warn(
                "Create customer failed due to duplicate customerCode={}",
                request.getCustomerCode()
            );

            throw new ConflictException(
                "Customer code already exists: "
                    + request.getCustomerCode()
            );
        }

        Customer customer = new Customer();

        mapToEntity(request, customer);

        Customer savedCustomer =
            customerRepository.save(customer);
        log.info(
            "Customer created. customerId={}, customerCode={}",
            savedCustomer.getId(),
            savedCustomer.getCustomerCode()
        );

        return mapToDto(savedCustomer);
    }

    /**
     * Get customer by ID.
     */
    @Transactional(readOnly = true)
    public LiftCustomerDetails getCustomerById(Long id) {
        log.info("Fetching customer by id={}", id);

        Customer customer = findCustomerById(id);
        log.debug(
            "Fetched customer by id={}. liftCount={}",
            id,
            customer.getLifts() == null ? 0 : customer.getLifts().size()
        );

        return mapToDto(customer);
    }

    /**
     * Get all customers.
     */
    @Transactional(readOnly = true)
    public Page<LiftCustomerDetails> getAllCustomers(
        Pageable pageable) {
        log.info(
            "Fetching all customers. pageNumber={}, pageSize={}",
            pageable.getPageNumber(),
            pageable.getPageSize()
        );

        Page<LiftCustomerDetails> response = customerRepository
            .findAll(pageable)
            .map(this::mapToDto);
        log.info(
            "Fetched all customers. totalElements={}",
            response.getTotalElements()
        );
        return response;
    }

    /**
     * Search customers.
     */
    @Transactional(readOnly = true)
    public Page<LiftCustomerDetails> searchCustomers(
        String query,
        Pageable pageable) {
        log.info(
            "Searching customers. query='{}', pageNumber={}, pageSize={}",
            query,
            pageable.getPageNumber(),
            pageable.getPageSize()
        );

        if (query == null || query.isBlank()) {
            log.debug("Blank search query received; returning all customers");
            return getAllCustomers(pageable);
        }

        Page<LiftCustomerDetails> response = customerRepository
            .searchCustomers(query.trim(), pageable)
            .map(this::mapToDto);
        log.info(
            "Search completed. query='{}', totalElements={}",
            query,
            response.getTotalElements()
        );
        return response;
    }

    /**
     * Update customer.
     */
    public LiftCustomerDetails updateCustomer(
        Long id,
        LiftCustomerDetails request) {
        int liftCount = request.getLifts() == null ? 0 : request.getLifts().size();
        log.info(
            "Updating customer. customerId={}, requestedCustomerCode={}, liftCount={}",
            id,
            request.getCustomerCode(),
            liftCount
        );
        logLiftPayload("Update customer payload", request.getLifts());

        Customer customer = findCustomerById(id);
        String existingCustomerCode = customer.getCustomerCode();
        if (request.getCustomerCode() != null
            && !request.getCustomerCode().isBlank()
            && !existingCustomerCode.equals(request.getCustomerCode().trim())) {
            log.warn(
                "Requested customerCode differs from persisted customerCode. customerId={}, requestedCustomerCode={}, persistedCustomerCode={}",
                id,
                request.getCustomerCode(),
                existingCustomerCode
            );
        }

        mapToEntity(request, customer);

        // Preserve the existing customer code.
        customer.setCustomerCode(
            existingCustomerCode
        );

        Customer updatedCustomer =
            customerRepository.save(customer);
        log.info(
            "Customer updated. customerId={}, customerCode={}",
            updatedCustomer.getId(),
            updatedCustomer.getCustomerCode()
        );

        return mapToDto(updatedCustomer);
    }

    /**
     * Delete customer.
     */
    public void deleteCustomer(Long id) {
        log.info("Deleting customer. customerId={}", id);

        Customer customer = findCustomerById(id);

        try {
            customerRepository.delete(customer);
            customerRepository.flush();
            log.info("Customer deleted. customerId={}", id);
        } catch (DataIntegrityViolationException e) {
            log.error(
                "Delete customer failed due to integrity constraints. customerId={}",
                id,
                e
            );

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
            .orElseThrow(() -> {
                log.warn("Customer not found. customerId={}", id);
                return new ResourceNotFoundException(
                    "Customer not found with id: " + id
                );
            });
    }

    /**
     * Map request DTO to entity.
     */
    private void mapToEntity(
        LiftCustomerDetails request,
        Customer customer) {
        log.debug(
            "Mapping customer request to entity. customerId={}, customerCode={}, liftCount={}",
            customer.getId(),
            request.getCustomerCode(),
            request.getLifts() == null ? 0 : request.getLifts().size()
        );

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
            trimToNull(request.getMobileNumber())
        );

        customer.setEmail(
            trimToNull(request.getEmail())
        );

        customer.setAddress(
            trimToNull(request.getAddress())
        );

        customer.setCity(
            trimToNull(request.getCity())
        );

        customer.setState(
            trimToNull(request.getState())
        );

        customer.setPincode(
            trimToNull(request.getPincode())
        );

        customer.setRemarks(
            trimToNull(request.getRemarks())
        );

        replaceLiftsInPlace(customer, request.getLifts());
    }

    /**
     * Map entity to response DTO.
     */
    private LiftCustomerDetails mapToDto(
        Customer customer) {
        log.debug(
            "Mapping customer entity to DTO. customerId={}, customerCode={}",
            customer.getId(),
            customer.getCustomerCode()
        );

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
            .lifts(mapLiftDtos(customer.getLifts()))
            .remarks(customer.getRemarks())
            .build();
    }

    private List<Lift> mapLiftEntities(
        List<LiftDetails> liftDetailsList) {

        if (liftDetailsList == null) {
            log.debug("No lifts provided in request payload");
            return new ArrayList<>();
        }
        log.debug("Mapping {} lift(s) to entity", liftDetailsList.size());

        return liftDetailsList.stream()
            .filter(Objects::nonNull)
            .map(this::mapLiftEntity)
            .collect(
                ArrayList::new,
                ArrayList::add,
                ArrayList::addAll
            );
    }

    private void replaceLiftsInPlace(
        Customer customer,
        List<LiftDetails> requestedLifts
    ) {
        List<Lift> targetLifts = customer.getLifts();
        int previousCount = targetLifts == null ? 0 : targetLifts.size();
        int requestedCount = requestedLifts == null ? 0 : requestedLifts.size();
        if (targetLifts == null) {
            targetLifts = new ArrayList<>();
            customer.setLifts(targetLifts);
        }

        log.info(
            "Replacing lifts in-place. customerId={}, previousLiftCount={}, requestedLiftCount={}",
            customer.getId(),
            previousCount,
            requestedCount
        );
        targetLifts.clear();
        targetLifts.addAll(mapLiftEntities(requestedLifts));
        log.info(
            "Lifts replaced in-place. customerId={}, finalLiftCount={}",
            customer.getId(),
            targetLifts.size()
        );
    }

    private Lift mapLiftEntity(
        LiftDetails liftDetails) {
        int amcCount = liftDetails.getAmcDetails() == null ? 0 : liftDetails.getAmcDetails().size();
        log.info(
            "Mapping lift payload to entity. liftId={}, liftType={}, serialNumber={}, amcCount={}",
            liftDetails.getId(),
            liftDetails.getLiftType(),
            liftDetails.getSerialNumber(),
            amcCount
        );

        return Lift.builder()
            .liftType(liftDetails.getLiftType())
            .driveType(liftDetails.getDriveType())
            .numberOfFloors(liftDetails.getNumberOfFloors())
            .capacityInKg(liftDetails.getCapacityInKg())
            .capacityInPersons(liftDetails.getCapacityInPersons())
            .brand(trimToNull(liftDetails.getBrand()))
            .liftModel(trimToNull(liftDetails.getLiftModel()))
            .installationType(trimToNull(liftDetails.getInstallationType()))
            .yearOfInstallation(liftDetails.getYearOfInstallation())
            .serialNumber(trimToNull(liftDetails.getSerialNumber()))
            .amcContracts(
                mapAmcEntities(liftDetails.getAmcDetails())
            )
            .build();
    }

    private List<AmcContract> mapAmcEntities(
        List<AmcDetails> amcDetailsList) {

        if (amcDetailsList == null) {
            log.debug("No AMC details provided for lift");
            return new ArrayList<>();
        }
        log.debug("Mapping {} AMC contract(s) to entity", amcDetailsList.size());

        return amcDetailsList.stream()
            .filter(Objects::nonNull)
            .map(this::mapAmcEntity)
            .collect(
                ArrayList::new,
                ArrayList::add,
                ArrayList::addAll
            );
    }

    private AmcContract mapAmcEntity(
        AmcDetails amcDetails) {
        log.info(
            "Mapping AMC payload to entity. amcId={}, contractNumber={}, status={}, startDate={}, endDate={}",
            amcDetails.getId(),
            amcDetails.getContractNumber(),
            amcDetails.getStatus(),
            amcDetails.getStartDate(),
            amcDetails.getEndDate()
        );

        return AmcContract.builder()
            .contractNumber(trimToNull(amcDetails.getContractNumber()))
            .status(amcDetails.getStatus())
            .startDate(amcDetails.getStartDate())
            .endDate(amcDetails.getEndDate())
            .amcType(trimToNull(amcDetails.getAmcType()))
            .amcAmount(amcDetails.getAmcAmount())
            .paymentFrequency(trimToNull(amcDetails.getPaymentFrequency()))
            .nextPaymentDate(amcDetails.getNextPaymentDate())
            .nextServiceDate(amcDetails.getNextServiceDate())
            .totalServices(amcDetails.getTotalServices())
            .completedServices(amcDetails.getCompletedServices())
            .termsAndConditions(trimToNull(amcDetails.getTermsAndConditions()))
            .remarks(trimToNull(amcDetails.getRemarks()))
            .build();
    }

    private List<LiftDetails> mapLiftDtos(
        List<Lift> lifts) {

        if (lifts == null || lifts.isEmpty()) {
            log.debug("Customer has no lifts to map to DTO");
            return Collections.emptyList();
        }
        log.debug("Mapping {} lift entity record(s) to DTO", lifts.size());

        return lifts.stream()
            .filter(Objects::nonNull)
            .map(this::mapLiftDto)
            .toList();
    }

    private LiftDetails mapLiftDto(
        Lift lift) {

        return LiftDetails.builder()
            .id(lift.getId())
            .liftType(lift.getLiftType())
            .driveType(lift.getDriveType())
            .numberOfFloors(lift.getNumberOfFloors())
            .capacityInKg(lift.getCapacityInKg())
            .capacityInPersons(lift.getCapacityInPersons())
            .brand(lift.getBrand())
            .liftModel(lift.getLiftModel())
            .installationType(lift.getInstallationType())
            .yearOfInstallation(lift.getYearOfInstallation())
            .serialNumber(lift.getSerialNumber())
            .amcDetails(mapAmcDtos(lift.getAmcContracts()))
            .build();
    }

    private List<AmcDetails> mapAmcDtos(
        List<AmcContract> amcContracts) {

        if (amcContracts == null || amcContracts.isEmpty()) {
            log.debug("Lift has no AMC contracts to map to DTO");
            return Collections.emptyList();
        }
        log.debug("Mapping {} AMC contract record(s) to DTO", amcContracts.size());

        return amcContracts.stream()
            .filter(Objects::nonNull)
            .map(this::mapAmcDto)
            .toList();
    }

    private AmcDetails mapAmcDto(
        AmcContract amcContract) {

        return AmcDetails.builder()
            .id(amcContract.getId())
            .contractNumber(amcContract.getContractNumber())
            .status(amcContract.getStatus())
            .startDate(amcContract.getStartDate())
            .endDate(amcContract.getEndDate())
            .amcType(amcContract.getAmcType())
            .amcAmount(amcContract.getAmcAmount())
            .paymentFrequency(amcContract.getPaymentFrequency())
            .nextPaymentDate(amcContract.getNextPaymentDate())
            .nextServiceDate(amcContract.getNextServiceDate())
            .totalServices(amcContract.getTotalServices())
            .completedServices(amcContract.getCompletedServices())
            .termsAndConditions(amcContract.getTermsAndConditions())
            .remarks(amcContract.getRemarks())
            .build();
    }

    private String trimToNull(
        String value) {

        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void logLiftPayload(
        String context,
        List<LiftDetails> lifts
    ) {
        if (lifts == null || lifts.isEmpty()) {
            log.info("{}: no lifts provided", context);
            return;
        }

        log.info("{}: totalLifts={}", context, lifts.size());
        for (int i = 0; i < lifts.size(); i++) {
            LiftDetails lift = lifts.get(i);
            if (lift == null) {
                log.warn("{}: lift[{}] is null", context, i);
                continue;
            }

            int amcCount = lift.getAmcDetails() == null ? 0 : lift.getAmcDetails().size();
            log.info(
                "{}: lift[{}] id={}, type={}, driveType={}, floors={}, capacityPersons={}, capacityKg={}, serialNumber={}, amcCount={}",
                context,
                i,
                lift.getId(),
                lift.getLiftType(),
                lift.getDriveType(),
                lift.getNumberOfFloors(),
                lift.getCapacityInPersons(),
                lift.getCapacityInKg(),
                lift.getSerialNumber(),
                amcCount
            );
        }
    }
}
