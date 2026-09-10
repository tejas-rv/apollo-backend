package com.apollo.elevator.enquiry.service;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.NotificationDeliveryException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.dto.LiftCustomerDetails;
import com.apollo.elevator.customer.service.CustomerService;
import com.apollo.elevator.enquiry.model.dto.ContactInquiryRequest;
import com.apollo.elevator.enquiry.model.dto.ContactInquiryResponse;
import com.apollo.elevator.enquiry.model.dto.CustomerConversionResponse;
import com.apollo.elevator.enquiry.model.dto.SubmitInquiryResponse;
import com.apollo.elevator.enquiry.model.entity.ContactInquiry;
import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import com.apollo.elevator.enquiry.repository.ContactInquiryRepository;
import com.apollo.elevator.notification.email.model.dto.PlainEmailRequest;
import com.apollo.elevator.notification.email.service.EmailProperties;
import com.apollo.elevator.notification.service.NotificationService;
import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import com.apollo.elevator.quotation.repository.QuotationRepository;
import com.apollo.elevator.workorder.model.entity.WorkOrder;
import com.apollo.elevator.workorder.model.enums.WorkOrderStatus;
import com.apollo.elevator.workorder.repository.WorkOrderRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactInquiryService {

    private final ContactInquiryRepository contactInquiryRepository;
    private final QuotationRepository quotationRepository;
    private final WorkOrderRepository workOrderRepository;
    private final CustomerService customerService;
    private final NotificationService notificationService;
    private final EmailProperties emailProperties;

    @Transactional
    public SubmitInquiryResponse submit(ContactInquiryRequest request) {
        String normalizedPhone = normalizePhone(request.phoneNumber());
        if (normalizedPhone.length() < 10) {
            throw new IllegalArgumentException("Phone number must have at least 10 digits");
        }
        if (request.consentAccepted() == null || !request.consentAccepted()) {
            throw new IllegalArgumentException("Consent must be accepted");
        }

        ContactInquiry inquiry = ContactInquiry.builder()
                .inquiryType(request.inquiryType())
                .fullName(request.fullName().trim())
                .phoneNumber(normalizedPhone)
                .email(request.email() == null ? null : request.email().trim())
                .city(request.city() == null ? null : request.city().trim())
                .requirementType(request.requirementType())
                .message(request.message())
                .consentAccepted(request.consentAccepted())
                .sourcePage(request.sourcePage())
                .status(InquiryStatus.NEW)
                .build();

        inquiry = contactInquiryRepository.save(inquiry);
        log.info(
                "Contact inquiry submitted. inquiryId={}, inquiryType={}, fullName={}",
                inquiry.getId(),
                inquiry.getInquiryType(),
                inquiry.getFullName()
        );

        sendAdminNotification(inquiry);
        sendCustomerAcknowledgement(inquiry);

        return new SubmitInquiryResponse(true, "Inquiry submitted successfully.");
    }

    public Page<ContactInquiryResponse> search(
            String name,
            String phone,
            InquiryStatus status,
            LocalDateTime from,
            LocalDateTime to,
            Pageable pageable
    ) {
        return contactInquiryRepository.search(name, phone, status, from, to, pageable)
                .map(this::toResponse);
    }

    public ContactInquiryResponse findById(Long id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public ContactInquiryResponse updateStatus(Long id, InquiryStatus status, String modifiedBy) {
        ContactInquiry inquiry = loadOrThrow(id);
        InquiryStatus currentStatus = inquiry.getStatus();

        if (status == null) {
            throw new IllegalArgumentException("Status is required");
        }
        if (currentStatus == InquiryStatus.COMPLETED || currentStatus == InquiryStatus.CLOSED) {
            throw new IllegalArgumentException("Status changes are not allowed after the enquiry is completed or closed.");
        }
        if (status == InquiryStatus.COMPLETED) {
            throw new IllegalArgumentException("An enquiry cannot be marked COMPLETED directly from this endpoint. Use the customer conversion flow.");
        }
        if (!currentStatus.canTransitionTo(status)) {
            throw new IllegalArgumentException(currentStatus.invalidTransitionMessage(status));
        }
        if (status == InquiryStatus.WORK_ORDER && !hasAcceptedQuotation(id)) {
            throw new IllegalArgumentException("IN_PROGRESS enquiries can only move to WORK_ORDER when a quotation has been accepted.");
        }

        inquiry.setStatus(status);
        inquiry.setModifiedBy(modifiedBy);
        inquiry = contactInquiryRepository.save(inquiry);
        log.info("Contact inquiry status updated. inquiryId={}, status={}, modifiedBy={}", inquiry.getId(), inquiry.getStatus(), modifiedBy);
        return toResponse(inquiry);
    }

    @Transactional
    public CustomerConversionResponse convertToCustomer(Long enquiryId, LiftCustomerDetails request, String modifiedBy) {
        if (request == null) {
            throw new IllegalArgumentException("Customer payload is required");
        }

        ContactInquiry inquiry = loadOrThrow(enquiryId);
        if (inquiry.getStatus() != InquiryStatus.WORK_ORDER) {
            throw new IllegalArgumentException("Only enquiries in WORK_ORDER status can be converted to customer.");
        }
        if (inquiry.getCustomerId() != null || inquiry.getStatus() == InquiryStatus.COMPLETED) {
            throw new ConflictException("Customer conversion already completed for enquiry id: " + enquiryId);
        }

        if (request.getCustomerName() == null || request.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        if (request.getMobileNumber() == null || request.getMobileNumber().isBlank()) {
            throw new IllegalArgumentException("Mobile number is required");
        }
        if (request.getLifts() == null || request.getLifts().isEmpty()) {
            throw new IllegalArgumentException("At least one lift is required");
        }
        for (var lift : request.getLifts()) {
            if (lift == null || lift.getNumberOfFloors() == null || lift.getNumberOfFloors() < 1) {
                throw new IllegalArgumentException("Each lift must include a valid number of floors");
            }
        }

        WorkOrder workOrder = workOrderRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found for enquiry id: " + enquiryId));
        if (workOrder.getStatus() != WorkOrderStatus.ACTIVE && workOrder.getStatus() != WorkOrderStatus.APPROVED) {
            throw new IllegalArgumentException("Work order must be active or approved before converting to customer.");
        }

        String customerCode = request.getCustomerCode() == null || request.getCustomerCode().isBlank()
                ? generateCustomerCode(request.getCustomerName())
                : request.getCustomerCode().trim();
        request.setCustomerCode(customerCode);

        Long customerId = customerService.createCustomer(request).getId();
        inquiry.setCustomerId(customerId);
        inquiry.setStatus(InquiryStatus.COMPLETED);
        inquiry.setModifiedBy(modifiedBy);
        contactInquiryRepository.save(inquiry);

        workOrder.setStatus(WorkOrderStatus.COMPLETED);
        workOrder.setUpdatedBy(modifiedBy);
        workOrderRepository.save(workOrder);

        log.info("Enquiry converted to customer. enquiryId={}, customerId={}, modifiedBy={}",
                enquiryId, customerId, modifiedBy);
        return new CustomerConversionResponse(enquiryId, customerId, "COMPLETED");
    }

    private ContactInquiry loadOrThrow(Long id) {
        return contactInquiryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found with id: " + id));
    }

    private boolean hasAcceptedQuotation(Long enquiryId) {
        return quotationRepository.existsByEnquiryIdAndStatus(enquiryId, QuotationStatus.ACCEPTED);
    }

    private String generateCustomerCode(String fullName) {
        String normalized = fullName == null ? "CUSTOMER" : fullName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (normalized.isBlank()) {
            normalized = "CUSTOMER";
        }
        String prefix = normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
        return prefix + "-" + System.currentTimeMillis();
    }

    private void sendAdminNotification(ContactInquiry inquiry) {
        if (emailProperties.getAdminRecipients() == null || emailProperties.getAdminRecipients().isEmpty()) {
            return;
        }
        String subject = "New " + inquiry.getInquiryType() + " Inquiry — " + inquiry.getFullName();
        String body = "A new inquiry has been submitted.\n\n"
                + "Name: " + inquiry.getFullName() + "\n"
                + "Phone: " + inquiry.getPhoneNumber() + "\n"
                + "Email: " + nullSafe(inquiry.getEmail()) + "\n"
                + "City: " + nullSafe(inquiry.getCity()) + "\n"
                + "Requirement: " + nullSafe(inquiry.getRequirementType()) + "\n"
                + "Message: " + nullSafe(inquiry.getMessage());

        for (String recipient : emailProperties.getAdminRecipients()) {
            try {
                notificationService.sendPlainEmail(new PlainEmailRequest(
                        recipient, subject, body, "contact-inquiry-" + inquiry.getId()
                ));
            } catch (NotificationDeliveryException exception) {
                log.warn(
                        "Admin notification email failed. inquiryId={}, recipient={}, reason={}",
                        inquiry.getId(), recipient, exception.getMessage()
                );
            }
        }
    }

    private void sendCustomerAcknowledgement(ContactInquiry inquiry) {
        if (inquiry.getEmail() == null || inquiry.getEmail().isBlank()) {
            return;
        }
        try {
            notificationService.sendPlainEmail(new PlainEmailRequest(
                    inquiry.getEmail(),
                    "We've received your enquiry — Apollo Elevator",
                    "Hi " + inquiry.getFullName() + ",\n\n"
                            + "Thanks for reaching out to Apollo Elevator. We've received your enquiry and "
                            + "someone from our team will contact you on " + inquiry.getPhoneNumber()
                            + " within a business day.\n\nRegards,\nApollo Elevator Team",
                    "contact-inquiry-ack-" + inquiry.getId()
            ));
        } catch (NotificationDeliveryException exception) {
            log.warn(
                    "Customer acknowledgement email failed. inquiryId={}, email={}, reason={}",
                    inquiry.getId(), inquiry.getEmail(), exception.getMessage()
            );
        }
    }

    private String normalizePhone(String phoneNumber) {
        return phoneNumber == null ? "" : phoneNumber.replaceAll("\\D", "");
    }

    private String nullSafe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private ContactInquiryResponse toResponse(ContactInquiry inquiry) {
        return new ContactInquiryResponse(
                inquiry.getId(),
                inquiry.getInquiryType(),
                inquiry.getFullName(),
                inquiry.getPhoneNumber(),
                inquiry.getEmail(),
                inquiry.getCity(),
                inquiry.getRequirementType(),
                inquiry.getMessage(),
                inquiry.getConsentAccepted(),
                inquiry.getSourcePage(),
                inquiry.getStatus(),
                inquiry.getCreatedAt(),
                inquiry.getUpdatedAt(),
                inquiry.getModifiedBy()
        );
    }
}
