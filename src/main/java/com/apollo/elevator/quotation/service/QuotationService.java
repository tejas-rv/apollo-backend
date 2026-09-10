package com.apollo.elevator.quotation.service;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.enquiry.model.entity.ContactInquiry;
import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import com.apollo.elevator.enquiry.repository.ContactInquiryRepository;
import com.apollo.elevator.quotation.model.dto.CreateQuotationRequest;
import com.apollo.elevator.quotation.model.dto.QuotationPendingResponse;
import com.apollo.elevator.quotation.model.dto.QuotationResponse;
import com.apollo.elevator.quotation.model.entity.Quotation;
import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import com.apollo.elevator.quotation.repository.QuotationRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuotationService {

    private final QuotationRepository quotationRepository;
    private final ContactInquiryRepository contactInquiryRepository;

    @Transactional(readOnly = true)
    public Page<QuotationResponse> list(String status, String enquiryStatus, String query, Pageable pageable) {
        QuotationStatus quotationStatus = parseStatus(status);
        InquiryStatus inquiryStatus = parseInquiryStatus(enquiryStatus);
        String normalizedQuery = query == null ? null : query.trim();

        log.info("Listing quotations. status={}, enquiryStatus={}, query='{}', page={}, size={}",
                quotationStatus, inquiryStatus, normalizedQuery, pageable.getPageNumber(), pageable.getPageSize());

        return quotationRepository.search(quotationStatus, inquiryStatus, normalizedQuery, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<QuotationPendingResponse> listPendingQuotations(String query, Pageable pageable) {
        String normalizedQuery = query == null ? null : query.trim();
        Page<ContactInquiry> enquiries = contactInquiryRepository.searchPendingQuotationEnquiries(InquiryStatus.IN_PROGRESS, normalizedQuery, pageable);
        return enquiries.map(this::toPendingResponse);
    }

    @Transactional
    public QuotationResponse create(CreateQuotationRequest request, String createdBy) {
        if (request == null) {
            throw new IllegalArgumentException("Quotation request is required");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Quotation amount must be greater than zero.");
        }

        ContactInquiry enquiry = contactInquiryRepository.findById(request.enquiryId())
                .orElseThrow(() -> new ResourceNotFoundException("Enquiry not found with id: " + request.enquiryId()));

        if (enquiry.getStatus() != InquiryStatus.NEW && enquiry.getStatus() != InquiryStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    "Quotations can only be created for enquiries in NEW or IN_PROGRESS status. Current status: " + enquiry.getStatus());
        }

        if (quotationRepository.existsActiveQuotationForEnquiry(enquiry.getId(), Set.of(QuotationStatus.DRAFT, QuotationStatus.SENT))) {
            throw new ConflictException("An active quotation already exists for enquiry id: " + enquiry.getId());
        }

        if (enquiry.getStatus() == InquiryStatus.NEW) {
            enquiry.setStatus(InquiryStatus.IN_PROGRESS);
            enquiry.setModifiedBy(createdBy);
            contactInquiryRepository.save(enquiry);
        }

        String quotationNumber = generateQuotationNumber();
        Quotation quotation = Quotation.builder()
                .quotationNumber(quotationNumber)
                .enquiry(enquiry)
                .amount(request.amount())
                .notes(request.notes() == null ? null : request.notes().trim())
                .status(QuotationStatus.DRAFT)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        try {
            Quotation savedQuotation = quotationRepository.saveAndFlush(quotation);
            log.info("Quotation created. quotationId={}, quotationNumber={}, enquiryId={}, createdBy={}",
                    savedQuotation.getId(), savedQuotation.getQuotationNumber(), enquiry.getId(), createdBy);
            return toResponse(savedQuotation);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Duplicate quotation number encountered while creating quotation for enquiryId={}. Retrying generation.", enquiry.getId(), exception);
            String retryNumber = generateQuotationNumber();
            Quotation retryQuotation = Quotation.builder()
                    .quotationNumber(retryNumber)
                    .enquiry(enquiry)
                    .amount(request.amount())
                    .notes(request.notes() == null ? null : request.notes().trim())
                    .status(QuotationStatus.DRAFT)
                    .createdBy(createdBy)
                    .updatedBy(createdBy)
                    .build();
            Quotation saved = quotationRepository.save(retryQuotation);
            log.info("Quotation created after retry. quotationId={}, quotationNumber={}, enquiryId={}, createdBy={}",
                    saved.getId(), saved.getQuotationNumber(), enquiry.getId(), createdBy);
            return toResponse(saved);
        }
    }

    @Transactional
    public QuotationResponse updateStatus(Long id, QuotationStatus status, String updatedBy) {
        Quotation quotation = quotationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quotation not found with id: " + id));

        QuotationStatus currentStatus = quotation.getStatus();
        if (!currentStatus.canTransitionTo(status)) {
            throw new IllegalArgumentException(currentStatus.invalidTransitionMessage(status));
        }

        quotation.setStatus(status);
        quotation.setUpdatedBy(updatedBy);
        quotation = quotationRepository.save(quotation);

        log.info("Quotation status updated. quotationId={}, status={}, updatedBy={}", quotation.getId(), quotation.getStatus(), updatedBy);
        return toResponse(quotation);
    }

    private QuotationStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return QuotationStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid quotation status: " + status);
        }
    }

    private InquiryStatus parseInquiryStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InquiryStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid enquiry status: " + status);
        }
    }

    private String generateQuotationNumber() {
        int year = LocalDate.now().getYear();
        Long maxSequence = quotationRepository.findMaxSequenceForYear(year);
        long next = maxSequence + 1L;
        return String.format("QT-%d-%04d", year, next);
    }

    private QuotationResponse toResponse(Quotation quotation) {
        return new QuotationResponse(
                quotation.getId(),
                quotation.getQuotationNumber(),
                quotation.getEnquiry().getId(),
                quotation.getEnquiry().getFullName(),
                quotation.getEnquiry().getPhoneNumber(),
                quotation.getEnquiry().getRequirementType(),
                quotation.getAmount(),
                quotation.getNotes(),
                quotation.getStatus(),
                quotation.getEnquiry().getStatus().name(),
                quotation.getStatus().name(),
                quotation.getCreatedAt(),
                quotation.getUpdatedAt()
        );
    }

    private QuotationPendingResponse toPendingResponse(ContactInquiry inquiry) {
        return new QuotationPendingResponse(
                inquiry.getId(),
                inquiry.getFullName(),
                inquiry.getPhoneNumber(),
                inquiry.getRequirementType(),
                inquiry.getStatus(),
                "NOT_CREATED"
        );
    }
}
