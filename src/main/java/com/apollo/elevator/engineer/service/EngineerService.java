package com.apollo.elevator.engineer.service;

import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.authorization.repository.UserRepository;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.repository.AmcContractRepository;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.documents.service.PdfTemplateService;
import com.apollo.elevator.engineer.model.dto.*;
import com.apollo.elevator.engineer.model.entity.ServiceCheckItem;
import com.apollo.elevator.engineer.model.entity.ServiceReport;
import com.apollo.elevator.engineer.model.enums.ReportStatus;
import com.apollo.elevator.engineer.repository.ServiceReportRepository;
import com.apollo.elevator.notification.email.model.dto.EmailMessageRequest;
import com.apollo.elevator.notification.email.service.EmailProperties;
import com.apollo.elevator.notification.service.NotificationService;
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

import static com.apollo.elevator.engineer.model.enums.AnswerType.DESCRIPTIVE;
import static com.apollo.elevator.engineer.model.enums.AnswerType.YES_NO_NA;

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
    private final EmailProperties emailProperties;

    /**
     * Resolves user ID from username (for JWT auth context).
     */
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

    /**
     * Submit a new service report. Generates PDF and emails admin immediately.
     */
    public ServiceReportResponse submitReport(Long engineerUserId, ServiceReportRequest req) {
        User engineer = userRepository.findById(engineerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Engineer not found: " + engineerUserId));

        Customer customer = customerRepository.findById(req.customerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + req.customerId()));

        Long resolvedAmcContractId = resolveServiceContractId(customer, req.amcContractId());

        ServiceReport report = ServiceReport.builder()
                .amcContractId(resolvedAmcContractId)
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

    /**
     * Generate PDF for a previously submitted report (for re-download).
     */
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

        Map<String, Object> dash = new LinkedHashMap<>();
        dash.put("servicesToday", todayReports.size());
        dash.put("servicesThisMonth", monthReports.size());
        dash.put("totalSubmitted", totalSubmitted);
        dash.put("recentReports", monthReports.stream().limit(5).map(this::toResponse).collect(Collectors.toList()));
        return dash;
    }

    // -------------------------------------------------------------------------
    // Default checklist template
    // -------------------------------------------------------------------------

    /**
     * Returns the standard service checklist pre-filled with questions (no answers yet)
     */
    public List<ServiceCheckItemDto> getDefaultChecklist() {
        return DEFAULT_CHECKLIST;
    }

    private static final List<ServiceCheckItemDto> DEFAULT_CHECKLIST = List.of(
            new ServiceCheckItemDto(null, 1, "Cleaning of Parts", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 2, "Oil Level in Gear Box", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 3, "Brake Adjustment", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 4, "Speed Governor Operation", YES_NO_NA, null, null),

            new ServiceCheckItemDto(null, 5, "Loose Connections in controller checked / Tightened", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 6, "Earthing Wire Connection", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 7, "Fuse Wires checking & replacement", YES_NO_NA, null, null),

            new ServiceCheckItemDto(null, 8, "Shaft Lights", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 9, "UP & DN limit switch operation", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 10, "JC1 & JC2 (Final slow DN) switches operation", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 11, "Cleaning of Pit & Pit pully Greasing", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 12, "Pit Switch", YES_NO_NA, null, null),

            new ServiceCheckItemDto(null, 13, "J.T Switch Operation", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 14, "MNT Board Fixing & Operation", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 15, "Car Guide Shoe & Counter weight Guide Shoe", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 16, "Rope Balance Checking", YES_NO_NA, null, null),

            new ServiceCheckItemDto(null, 17, "Light & Fan Functioning", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 18, "Car Call Buttons", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 19, "Stop Button", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 20, "D.C. Alarm & D.C. Buzzer", YES_NO_NA, null, null),
            new ServiceCheckItemDto(null, 21, "Safety Edge Operation", YES_NO_NA, null, null),

            new ServiceCheckItemDto(null, 22, "Describe work performed during this visit:", DESCRIPTIVE, null, null),
            new ServiceCheckItemDto(null, 23, "Any spare parts replaced? If yes, list them:", DESCRIPTIVE, null, null),
            new ServiceCheckItemDto(null, 24, "Issues found that need follow-up:", DESCRIPTIVE, null, null)
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
        Set<String> recipientEmails = new LinkedHashSet<>();

        userRepository.findAll().stream()
                .filter(u -> u.getRole().name().equals("ADMIN"))
                .map(User::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .map(String::toLowerCase)
                .forEach(recipientEmails::add);

        if (emailProperties.getAdminRecipients() != null) {
            emailProperties.getAdminRecipients().stream()
                    .filter(email -> email != null && !email.isBlank())
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .forEach(recipientEmails::add);
        }

        if (recipientEmails.isEmpty()) {
            log.warn("No admin email recipients configured — skipping report email. reportId={}", report.getId());
            return;
        }

        String base64 = Base64.getEncoder().encodeToString(pdfBytes);
        String fileName = "ServiceReport-" + report.getId() + "-" + report.getVisitDate() + ".pdf";
        String subject = "Service Report: " + report.getCustomerName() + " | " + report.getVisitDate() + " | " + report.getEngineerName();
        String body = String.format(
                "Dear Admin,\n\nPlease find attached the service visit report.\n\n" +
                        "Engineer   : %s\nCustomer   : %s\nVisit Date : %s\nReport ID  : %d\n\n" +
                        "This is an automated email from Apollo Elevator management system.\n\nRegards,\nApollo Elevator",
                report.getEngineerName(), report.getCustomerName(), report.getVisitDate(), report.getId()
        );

        for (String adminEmail : recipientEmails) {
            try {
                EmailMessageRequest emailReq = new EmailMessageRequest(
                        adminEmail,
                        subject,
                        body,
                        "service-report-" + report.getId(),
                        List.of(new EmailMessageRequest.EmailAttachmentRequest(fileName, "application/pdf", base64))
                );
                notificationService.sendEmailMessage(emailReq);
                log.info("Service report emailed to admin. adminEmail={}, reportId={}", adminEmail, report.getId());
            } catch (Exception ex) {
                log.error("Failed to email report to admin. adminEmail={}, reportId={}", adminEmail, report.getId(), ex);
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
                l.getInstallationType(), l.getYearOfInstallation(), l.getSerialNumber(),
                l.getDoorType(), l.getMachineType(), l.getMachineName(), l.getKw(), l.getAmps(),
                l.getSpeed(), l.getVoltage(), l.getFrequency(), l.getOsgType(), l.getRatedSpeed(),
                l.getTrippingSpeed(), l.getIsUpsPresent(), l.getUpsType(), l.getKva(),
                l.getManufacturedBy(), l.getYearOfManufacture(), l.getNoOfGrooves(),
                l.getFrictionSheaveDiameter(), l.getNoOfRopes(), l.getDiaOfTheRopeMm(),
                l.getLengthOfTheRopeMm(), l.getIsDeflectorPulley(), l.getDeflectorPulleyDiameter(),
                l.getDeflectorPulleyNoOfGrooves(), l.getRoping(),
                l.getMainMotorKw(), l.getMainMotorAmps(), l.getMainMotorSpeed(),
                l.getMainMotorVoltage(), l.getMainMotorFrequency(), l.getMainMotorNoOfPoles(),
                l.getBatteryMake(), l.getBatteryVoltage(), l.getBatteryNoOfBatteries()
        );
    }

    private ServiceReportResponse toResponse(ServiceReport r) {
        return new ServiceReportResponse(
                r.getId(), r.getCustomerId(), r.getCustomerName(),
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

    private Long resolveServiceContractId(Customer customer, Long requestedAmcContractId) {
        if (requestedAmcContractId != null) {
            if (customer.getLifts() != null) {
                for (Lift lift : customer.getLifts()) {
                    if (lift.getAmcContracts() == null) continue;
                    boolean matches = lift.getAmcContracts().stream()
                            .anyMatch(contract -> requestedAmcContractId.equals(contract.getId()));
                    if (matches) {
                        return requestedAmcContractId;
                    }
                }
            }
        }

        if (customer.getLifts() == null) {
            return null;
        }

        return customer.getLifts().stream()
                .filter(Objects::nonNull)
                .map(Lift::getAmcContracts)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .findFirst()
                .map(AmcContract::getId)
                .orElse(null);
    }

    private ServiceReport findReport(Long id) {
        return reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service report not found: " + id));
    }
}
