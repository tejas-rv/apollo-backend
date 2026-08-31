package com.apollo.elevators.engineer.service;

import com.apollo.elevators.authorization.model.entity.User;
import com.apollo.elevators.authorization.repository.UserRepository;
import com.apollo.elevators.common.exception.ResourceNotFoundException;
import com.apollo.elevators.customer.model.entity.AmcContract;
import com.apollo.elevators.customer.model.entity.Customer;
import com.apollo.elevators.customer.model.entity.Lift;
import com.apollo.elevators.customer.repository.AmcContractRepository;
import com.apollo.elevators.customer.repository.CustomerRepository;
import com.apollo.elevators.documents.service.PdfTemplateService;
import com.apollo.elevators.engineer.model.dto.*;
import com.apollo.elevators.engineer.model.entity.ServiceCheckItem;
import com.apollo.elevators.engineer.model.entity.ServiceReport;
import com.apollo.elevators.engineer.model.enums.ReportStatus;
import com.apollo.elevators.engineer.repository.ServiceReportRepository;
import com.apollo.elevators.notification.email.model.dto.EmailMessageRequest;
import com.apollo.elevators.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class EngineerService {

    private final ServiceReportRepository reportRepository;
    private final CustomerRepository customerRepository;
    private final AmcContractRepository amcContractRepository;
    private final UserRepository userRepository;
    private final PdfTemplateService pdfTemplateService;
    private final NotificationService notificationService;

    /** Resolves user ID from username (for JWT auth context). */
    @Transactional(readOnly = true)
    public Long resolveEngineerUserId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer user not found: " + username))
                .getId();
    }

    // -------------------------------------------------------------------------
    // Customer read (restricted — no AMC amounts)
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<EngineerCustomerDto> getAllCustomers(Pageable pageable) {
        return customerRepository.findAll(pageable).map(this::toEngineerCustomer);
    }

    @Transactional(readOnly = true)
    public Page<EngineerCustomerDto> searchCustomers(String query, Pageable pageable) {
        if (query == null || query.isBlank()) return getAllCustomers(pageable);
        return customerRepository.searchCustomers(query.trim(), pageable).map(this::toEngineerCustomer);
    }

    @Transactional(readOnly = true)
    public EngineerCustomerDto getCustomerById(Long customerId) {
        Customer c = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));
        return toEngineerCustomer(c);
    }

    // -------------------------------------------------------------------------
    // Service reports
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<ServiceReportResponse> getMyReports(Long engineerUserId, Pageable pageable) {
        return reportRepository.findByEngineerUserIdOrderByVisitDateDesc(engineerUserId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public ServiceReportResponse getReportById(Long reportId) {
        ServiceReport report = findReport(reportId);
        return toResponse(report);
    }

    /** Submit a new service report. Generates PDF and emails admin immediately. */
    public ServiceReportResponse submitReport(Long engineerUserId, ServiceReportRequest req) {
        User engineer = userRepository.findById(engineerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer not found: " + engineerUserId));

        // Validate AMC contract exists
        amcContractRepository.findById(req.amcContractId())
                .orElseThrow(() -> new ResourceNotFoundException("AMC contract not found: " + req.amcContractId()));

        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.customerId()));

        ServiceReport report = ServiceReport.builder()
                .amcContractId(req.amcContractId())
                .engineerUserId(engineerUserId)
                .engineerName(engineer.getUsername())
                .customerId(customer.getId())
                .customerName(customer.getCustomerName())
                .visitDate(req.visitDate() != null ? req.visitDate() : LocalDate.now())
                .overallNotes(req.overallNotes())
                .status(ReportStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .checkItems(mapCheckItems(req.checkItems()))
                .build();

        ServiceReport saved = reportRepository.save(report);
        log.info("Service report submitted. reportId={}, engineerId={}, customerId={}", saved.getId(), engineerUserId, req.customerId());

        // Generate PDF and email admin
        try {
            byte[] pdfBytes = buildReportPdf(saved, customer);
            emailReportToAdmin(saved, pdfBytes);
            saved.setPdfSentAt(LocalDateTime.now());
            saved.setStatus(ReportStatus.PDF_SENT);
            saved = reportRepository.save(saved);
            log.info("Service report PDF emailed to admin. reportId={}", saved.getId());
        } catch (Exception ex) {
            log.error("Failed to send service report PDF to admin. reportId={}", saved.getId(), ex);
            // Report is still saved as SUBMITTED even if email fails
        }

        return toResponse(saved);
    }

    /** Generate PDF for a previously submitted report (for re-download). */
    @Transactional(readOnly = true)
    public byte[] generateReportPdf(Long reportId) {
        ServiceReport report = findReport(reportId);
        Customer customer = customerRepository.findById(report.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        return buildReportPdf(report, customer);
    }

    // -------------------------------------------------------------------------
    // Dashboard stats for engineer
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Map<String, Object> getEngineerDashboard(Long engineerUserId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        List<ServiceReport> todayReports = reportRepository.findByEngineerUserIdAndVisitDate(engineerUserId, today);
        List<ServiceReport> monthReports = reportRepository.findByEngineerUserIdAndDateRange(engineerUserId, monthStart, today);
        long totalSubmitted = reportRepository.countByEngineerUserIdAndStatus(engineerUserId, ReportStatus.SUBMITTED)
                + reportRepository.countByEngineerUserIdAndStatus(engineerUserId, ReportStatus.PDF_SENT);

        // Services due in next 7 days for this engineer's customers (all active AMCs with nextServiceDate)
        // We use all customers for upcoming services since there's no engineer-to-customer assignment
        List<Map<String, Object>> upcomingServices = getUpcomingServices();

        Map<String, Object> dash = new LinkedHashMap<>();
        dash.put("servicesToday", todayReports.size());
        dash.put("servicesThisMonth", monthReports.size());
        dash.put("totalSubmitted", totalSubmitted);
        dash.put("upcomingServices", upcomingServices);
        dash.put("recentReports", monthReports.stream().limit(5).map(this::toResponse).collect(Collectors.toList()));
        return dash;
    }

    private List<Map<String, Object>> getUpcomingServices() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(30);
        return customerRepository.findAll().stream()
                .flatMap(c -> c.getLifts().stream()
                        .flatMap(l -> l.getAmcContracts().stream()
                                .filter(a -> a.getNextServiceDate() != null
                                        && !a.getNextServiceDate().isBefore(from)
                                        && !a.getNextServiceDate().isAfter(to))
                                .map(a -> {
                                    Map<String, Object> m = new LinkedHashMap<>();
                                    m.put("customerId", c.getId());
                                    m.put("customerName", c.getCustomerName());
                                    m.put("contractNumber", a.getContractNumber());
                                    m.put("amcContractId", a.getId());
                                    m.put("nextServiceDate", a.getNextServiceDate().toString());
                                    m.put("liftBrand", l.getBrand());
                                    m.put("serialNumber", l.getSerialNumber());
                                    return m;
                                })))
                .sorted(Comparator.comparing(m -> (String) m.get("nextServiceDate")))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Default checklist template
    // -------------------------------------------------------------------------

    /** Returns the standard service checklist pre-filled with questions (no answers yet) */
    public List<ServiceCheckItemDto> getDefaultChecklist() {
        return DEFAULT_CHECKLIST;
    }

    private static final List<ServiceCheckItemDto> DEFAULT_CHECKLIST = List.of(
            new ServiceCheckItemDto(null, 1,  "Is the machine room clean and free of obstructions?",     com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 2,  "Are all safety devices (buffers, interlocks) functional?",  com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 3,  "Is the door closing mechanism working correctly?",          com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 4,  "Is the emergency lighting/alarm operational?",              com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 5,  "Is the oil level in the machine adequate?",                 com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 6,  "Are the guide rails lubricated?",                           com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 7,  "Is the brake functioning properly?",                        com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 8,  "Is the speed governor set correctly?",                      com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 9,  "Are all floor level indicators working?",                   com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 10, "Is the cabin light working?",                               com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 11, "Is the intercom/phone working?",                            com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 12, "Is the UPS / ARD (Auto Rescue Device) operational?",        com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 13, "Is the pit clean and dry?",                                 com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 14, "Are there any unusual sounds or vibrations during operation?", com.apollo.elevators.engineer.model.enums.AnswerType.YES_NO, null, null),
            new ServiceCheckItemDto(null, 15, "Describe work performed during this visit:",                com.apollo.elevators.engineer.model.enums.AnswerType.DESCRIPTIVE, null, null),
            new ServiceCheckItemDto(null, 16, "Any spare parts replaced? If yes, list them:",              com.apollo.elevators.engineer.model.enums.AnswerType.DESCRIPTIVE, null, null),
            new ServiceCheckItemDto(null, 17, "Issues found that need follow-up:",                         com.apollo.elevators.engineer.model.enums.AnswerType.DESCRIPTIVE, null, null),
            new ServiceCheckItemDto(null, 18, "Customer feedback / remarks:",                              com.apollo.elevators.engineer.model.enums.AnswerType.DESCRIPTIVE, null, null)
    );

    // -------------------------------------------------------------------------
    // PDF generation
    // -------------------------------------------------------------------------

    private byte[] buildReportPdf(ServiceReport report, Customer customer) {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("report", report);
        vars.put("customer", customer);
        vars.put("generatedAt", LocalDateTime.now().toString().replace("T", " ").substring(0, 19));
        return pdfTemplateService.renderToPdf("pdf/service-report", vars);
    }

    private void emailReportToAdmin(ServiceReport report, byte[] pdfBytes) {
        // Look for all ADMIN users to find their emails
        List<User> admins = userRepository.findAll().stream()
                .filter(u -> u.getRole().name().equals("ADMIN") && u.getEmail() != null && !u.getEmail().isBlank())
                .collect(Collectors.toList());

        if (admins.isEmpty()) {
            log.warn("No admin users with email found — skipping report email. reportId={}", report.getId());
            return;
        }

        String base64 = Base64.getEncoder().encodeToString(pdfBytes);
        String fileName = "ServiceReport-" + report.getId() + "-" + report.getVisitDate() + ".pdf";
        String subject = "Service Report: " + report.getCustomerName() + " | " + report.getVisitDate() + " | " + report.getEngineerName();
        String body = String.format(
                "Dear Admin,\n\nPlease find attached the service visit report.\n\n" +
                "Engineer   : %s\nCustomer   : %s\nVisit Date : %s\nReport ID  : %d\n\n" +
                "This is an automated email from Apollo Elevators management system.\n\nRegards,\nApollo Elevators",
                report.getEngineerName(), report.getCustomerName(), report.getVisitDate(), report.getId()
        );

        for (User admin : admins) {
            try {
                EmailMessageRequest emailReq = new EmailMessageRequest(
                        admin.getEmail(),
                        subject,
                        body,
                        "service-report-" + report.getId(),
                        List.of(new EmailMessageRequest.EmailAttachmentRequest(fileName, "application/pdf", base64))
                );
                notificationService.sendEmailMessage(emailReq);
                log.info("Service report emailed to admin. adminEmail={}, reportId={}", admin.getEmail(), report.getId());
            } catch (Exception ex) {
                log.error("Failed to email report to admin. adminEmail={}, reportId={}", admin.getEmail(), report.getId(), ex);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private EngineerCustomerDto toEngineerCustomer(Customer c) {
        return new EngineerCustomerDto(
                c.getId(), c.getCustomerCode(), c.getCustomerName(),
                c.getMobileNumber(), c.getAddress(), c.getCity(), c.getState(), c.getPincode(),
                c.getLifts() == null ? List.of() :
                        c.getLifts().stream().map(this::toEngineerLift).collect(Collectors.toList())
        );
    }

    private EngineerLiftDto toEngineerLift(Lift l) {
        return new EngineerLiftDto(
                l.getId(), l.getLiftType(), l.getDriveType(), l.getNumberOfFloors(),
                l.getCapacityInKg(), l.getCapacityInPersons(), l.getBrand(), l.getLiftModel(),
                l.getSerialNumber(), l.getDoorType(), l.getYearOfInstallation(),
                l.getAmcContracts() == null ? List.of() :
                        l.getAmcContracts().stream().map(this::toEngineerAmc).collect(Collectors.toList())
        );
    }

    private EngineerAmcDto toEngineerAmc(AmcContract a) {
        return new EngineerAmcDto(
                a.getId(), a.getContractNumber(), a.getStatus(),
                a.getStartDate(), a.getEndDate(), a.getContractType(),
                a.getPaymentFrequency(), a.getNextServiceDate(),
                a.getTotalServices(), a.getCompletedServices()
        );
    }

    private ServiceReportResponse toResponse(ServiceReport r) {
        return new ServiceReportResponse(
                r.getId(), r.getAmcContractId(), r.getCustomerId(), r.getCustomerName(),
                r.getEngineerUserId(), r.getEngineerName(), r.getVisitDate(),
                r.getOverallNotes(), r.getStatus(), r.getSubmittedAt(), r.getPdfSentAt(),
                r.getCheckItems() == null ? List.of() :
                        r.getCheckItems().stream()
                                .sorted(Comparator.comparingInt(ServiceCheckItem::getItemOrder))
                                .map(i -> new ServiceCheckItemDto(i.getId(), i.getItemOrder(),
                                        i.getQuestion(), i.getAnswerType(), i.getAnswerYn(), i.getAnswerText()))
                                .collect(Collectors.toList())
        );
    }

    private List<ServiceCheckItem> mapCheckItems(List<ServiceCheckItemDto> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream().map(d -> ServiceCheckItem.builder()
                .itemOrder(d.itemOrder())
                .question(d.question())
                .answerType(d.answerType())
                .answerYn(d.answerYn())
                .answerText(d.answerText())
                .build()).collect(Collectors.toList());
    }

    private ServiceReport findReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service report not found: " + id));
    }
}
