package com.apollo.elevator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.service.CustomerService;
import com.apollo.elevator.enquiry.model.entity.ContactInquiry;
import com.apollo.elevator.enquiry.model.enums.InquiryStatus;
import com.apollo.elevator.enquiry.repository.ContactInquiryRepository;
import com.apollo.elevator.enquiry.service.ContactInquiryService;
import com.apollo.elevator.notification.email.service.EmailProperties;
import com.apollo.elevator.notification.service.NotificationService;
import com.apollo.elevator.quotation.model.enums.QuotationStatus;
import com.apollo.elevator.quotation.repository.QuotationRepository;
import com.apollo.elevator.workorder.repository.WorkOrderRepository;
import com.apollo.elevator.workorder.service.WorkOrderService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketingWorkflowTest {

    @Mock
    private ContactInquiryRepository contactInquiryRepository;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailProperties emailProperties;

    @Test
    void new_to_in_progress_is_allowed() {
        assertTrue(InquiryStatus.NEW.canTransitionTo(InquiryStatus.IN_PROGRESS));
    }

    @Test
    void work_order_to_completed_is_rejected_directly() {
        assertFalse(InquiryStatus.WORK_ORDER.canTransitionTo(InquiryStatus.COMPLETED));
    }

    @Test
    void in_progress_to_work_order_requires_accepted_quotation() {
        ContactInquiryService service = new ContactInquiryService(
                contactInquiryRepository,
                quotationRepository,
                workOrderRepository,
                customerService,
                notificationService,
                emailProperties
        );

        ContactInquiry inquiry = ContactInquiry.builder()
                .id(1L)
                .fullName("Customer Name")
                .phoneNumber("9876543210")
                .status(InquiryStatus.IN_PROGRESS)
                .build();

        when(contactInquiryRepository.findById(1L)).thenReturn(Optional.of(inquiry));
        when(quotationRepository.existsByEnquiryIdAndStatus(1L, QuotationStatus.ACCEPTED)).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus(1L, InquiryStatus.WORK_ORDER, "admin")
        );

        assertTrue(exception.getMessage().toLowerCase().contains("accepted"));
    }

    @Test
    void quotation_status_transitions_are_enforced() {
        assertTrue(QuotationStatus.DRAFT.canTransitionTo(QuotationStatus.SENT));
        assertFalse(QuotationStatus.SENT.canTransitionTo(QuotationStatus.DRAFT));
        assertFalse(QuotationStatus.ACCEPTED.canTransitionTo(QuotationStatus.EXPIRED));
    }

    @Test
    void work_order_conversion_requires_accepted_quotation() {
        WorkOrderService service = new WorkOrderService(workOrderRepository, contactInquiryRepository, quotationRepository);

        ContactInquiry inquiry = ContactInquiry.builder()
                .id(2L)
                .fullName("Customer Name")
                .phoneNumber("9876543210")
                .status(InquiryStatus.IN_PROGRESS)
                .build();

        when(contactInquiryRepository.findById(2L)).thenReturn(Optional.of(inquiry));
        when(quotationRepository.findFirstByEnquiryIdAndStatusOrderByCreatedAtDesc(2L, QuotationStatus.ACCEPTED))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.convertToWorkOrder(2L, "admin")
        );

        assertTrue(exception.getMessage().contains("Accepted quotation"));
    }
}
