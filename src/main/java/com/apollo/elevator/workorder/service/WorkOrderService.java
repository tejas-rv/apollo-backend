package com.apollo.elevator.workorder.service;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.enquiry.model.entity.ContactInquiry;
import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import com.apollo.elevator.enquiry.repository.ContactInquiryRepository;
import com.apollo.elevator.quotation.model.entity.Quotation;
import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import com.apollo.elevator.quotation.repository.QuotationRepository;
import com.apollo.elevator.workorder.model.dto.WorkOrderConversionResponse;
import com.apollo.elevator.workorder.model.dto.WorkOrderResponse;
import com.apollo.elevator.workorder.model.entity.WorkOrder;
import com.apollo.elevator.workorder.model.enums.WorkOrderStatus;
import com.apollo.elevator.workorder.repository.WorkOrderRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final ContactInquiryRepository contactInquiryRepository;
    private final QuotationRepository quotationRepository;

    @Transactional
    public WorkOrderConversionResponse convertToWorkOrder(Long enquiryId, String createdBy) {
        ContactInquiry enquiry = contactInquiryRepository.findById(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inquiry not found with id: " + enquiryId));

        if (enquiry.getStatus() != InquiryStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Work order conversion is only allowed for enquiries in IN_PROGRESS status.");
        }

        Quotation acceptedQuotation = quotationRepository.findFirstByEnquiryIdAndStatusOrderByCreatedAtDesc(enquiryId, QuotationStatus.ACCEPTED)
                .orElseThrow(() -> new ResourceNotFoundException("Accepted quotation not found for enquiry id: " + enquiryId));

        if (workOrderRepository.findByEnquiryId(enquiryId).isPresent()) {
            throw new ConflictException("A work order already exists for enquiry id: " + enquiryId);
        }

        enquiry.setStatus(InquiryStatus.WORK_ORDER);
        enquiry.setModifiedBy(createdBy);
        contactInquiryRepository.save(enquiry);

        WorkOrder workOrder = WorkOrder.builder()
                .workOrderNumber(generateWorkOrderNumber())
                .enquiry(enquiry)
                .quotation(acceptedQuotation)
                .status(WorkOrderStatus.ACTIVE)
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .build();

        WorkOrder saved = workOrderRepository.save(workOrder);
        log.info("Work order created. workOrderId={}, enquiryId={}, quotationId={}, createdBy={}",
                saved.getId(), enquiryId, acceptedQuotation.getId(), createdBy);
        return new WorkOrderConversionResponse(enquiryId, acceptedQuotation.getId(), saved.getId(), "WORK_ORDER");
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderResponse> list(String query, String status, Pageable pageable) {
        String normalizedQuery = query == null ? null : query.trim();
        WorkOrderStatus workOrderStatus = parseStatus(status);
        return workOrderRepository.search(workOrderStatus, normalizedQuery, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public WorkOrder findByEnquiryId(Long enquiryId) {
        return workOrderRepository.findByEnquiryId(enquiryId)
                .orElseThrow(() -> new ResourceNotFoundException("Work order not found for enquiry id: " + enquiryId));
    }

    private WorkOrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WorkOrderStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid work order status: " + status);
        }
    }

    private String generateWorkOrderNumber() {
        int year = LocalDate.now().getYear();
        Long maxSequence = workOrderRepository.findMaxSequenceForYear(year);
        long next = maxSequence + 1L;
        return String.format("WO-%d-%04d", year, next);
    }

    private WorkOrderResponse toResponse(WorkOrder workOrder) {
        return new WorkOrderResponse(
                workOrder.getId(),
                workOrder.getWorkOrderNumber(),
                workOrder.getEnquiry().getId(),
                workOrder.getQuotation().getId(),
                workOrder.getStatus(),
                workOrder.getNotes(),
                workOrder.getCreatedAt(),
                workOrder.getUpdatedAt()
        );
    }
}
