package com.apollo.elevator.servicevisit.service;

import com.apollo.elevator.common.util.LogMaskingUtil;
import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.AmcStatus;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.customer.repository.AmcContractRepository;
import com.apollo.elevator.engineer.service.EngineerDirectory;
import com.apollo.elevator.servicevisit.model.dto.RescheduleHistoryEntry;
import com.apollo.elevator.servicevisit.model.dto.RescheduleVisitRequest;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitFilter;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitRow;
import com.apollo.elevator.servicevisit.model.dto.ServiceVisitSummary;
import com.apollo.elevator.servicevisit.model.entity.ServiceVisitReschedule;
import com.apollo.elevator.servicevisit.repository.ServiceVisitQueryRepository;
import com.apollo.elevator.servicevisit.repository.ServiceVisitRescheduleRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin view of AMC service visits. When a service was not done properly (issue raised by the
 * customer or an admin) the visit can be rescheduled: a SCHEDULED visit is moved to the new
 * date, a COMPLETED/CANCELLED visit gets a follow-up SCHEDULED visit. Every reschedule is audited.
 */
@Service
@Slf4j
public class ServiceVisitService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int WORK_DONE_MAX = 500;

    private final ServiceVisitQueryRepository queryRepository;
    private final ServiceVisitRescheduleRepository rescheduleRepository;
    private final AmcContractRepository amcContractRepository;
    private final EngineerDirectory engineerDirectory;
    private final Clock clock;

    @Autowired
    public ServiceVisitService(ServiceVisitQueryRepository queryRepository,
                               ServiceVisitRescheduleRepository rescheduleRepository,
                               AmcContractRepository amcContractRepository,
                               EngineerDirectory engineerDirectory) {
        this(queryRepository, rescheduleRepository, amcContractRepository, engineerDirectory, Clock.systemDefaultZone());
    }

    ServiceVisitService(ServiceVisitQueryRepository queryRepository,
                        ServiceVisitRescheduleRepository rescheduleRepository,
                        AmcContractRepository amcContractRepository,
                        EngineerDirectory engineerDirectory,
                        Clock clock) {
        this.queryRepository = queryRepository;
        this.rescheduleRepository = rescheduleRepository;
        this.amcContractRepository = amcContractRepository;
        this.engineerDirectory = engineerDirectory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<ServiceVisitRow> search(ServiceVisitFilter filter, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return queryRepository.search(filter, today(), Math.max(page, 0), safeSize);
    }

    @Transactional(readOnly = true)
    public ServiceVisitSummary summary() {
        return queryRepository.summary(today());
    }

    @Transactional(readOnly = true)
    public List<RescheduleHistoryEntry> history(Long visitId) {
        queryRepository.findContractIdOfVisit(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Service visit not found: " + visitId));
        return rescheduleRepository.findHistory(visitId).stream().map(RescheduleHistoryEntry::from).toList();
    }

    @Transactional
    public ServiceVisitRow reschedule(Long visitId, RescheduleVisitRequest request, String rescheduledBy) {
        Long contractId = queryRepository.findContractIdOfVisit(visitId)
                .orElseThrow(() -> new ResourceNotFoundException("Service visit not found: " + visitId));
        AmcContract contract = amcContractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("AMC contract not found: " + contractId));
        if (contract.getStatus() == AmcStatus.CANCELLED) {
            throw new IllegalArgumentException("Visits of a cancelled AMC contract cannot be rescheduled");
        }
        ServiceHistory visit = contract.getServiceHistory().stream()
                .filter(entry -> visitId.equals(entry.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Service visit not found: " + visitId));

        String reason = trimToNull(request.reason());
        if (reason == null) {
            throw new IllegalArgumentException("Reason is required");
        }
        LocalDate newDate = request.newDate();
        if (newDate == null) {
            throw new IllegalArgumentException("New service date is required");
        }
        if (newDate.isBefore(today())) {
            throw new IllegalArgumentException("New service date cannot be in the past");
        }
        if (request.raisedBy() == null) {
            throw new IllegalArgumentException("Raised by is required (CUSTOMER or ADMIN)");
        }

        Long engineerId = visit.getAssignedEngineerId();
        String engineerName = visit.getEngineerName();
        if (request.assignedEngineerId() != null) {
            EngineerDirectory.Lookup engineers = engineerDirectory.lookup();
            if (!engineers.isEngineer(request.assignedEngineerId())) {
                throw new IllegalArgumentException("Assigned engineer not found: " + request.assignedEngineerId());
            }
            engineerId = request.assignedEngineerId();
            engineerName = engineers.displayName(engineerId);
        }

        ServiceVisitStatus previousStatus = visit.getStatus();
        LocalDate previousDate = visit.getServiceDate();
        String previousEngineer = visit.getEngineerName();
        ServiceHistory target;

        if (previousStatus == ServiceVisitStatus.SCHEDULED) {
            if (newDate.equals(previousDate) && Objects.equals(engineerId, visit.getAssignedEngineerId())
                    && Objects.equals(engineerName, previousEngineer)) {
                throw new IllegalArgumentException("Choose a different date or engineer to reschedule this visit");
            }
            visit.setServiceDate(newDate);
            visit.setAssignedEngineerId(engineerId);
            visit.setEngineerName(engineerName);
            target = visit;
        } else {
            rescheduleRepository.findOpenFollowUp(visitId).ifPresent(openFollowUp -> {
                throw new ConflictException("A re-service visit (#" + openFollowUp
                        + ") is already scheduled for this visit. Reschedule that visit instead.");
            });
            String workDone = trimToNull(request.workDone());
            target = ServiceHistory.builder()
                    .serviceDate(newDate)
                    .engineerName(engineerName)
                    .assignedEngineerId(engineerId)
                    .workDone(truncate(workDone == null ? "Re-service: " + reason : workDone))
                    .status(ServiceVisitStatus.SCHEDULED)
                    .build();
            if (contract.getServiceHistory() == null) {
                contract.setServiceHistory(new ArrayList<>());
            }
            contract.getServiceHistory().add(target);
            syncServiceSummary(contract);
        }

        LocalDate nextService = contract.getNextServiceDate();
        if (nextService == null || newDate.isBefore(nextService) || nextService.equals(previousDate)) {
            contract.setNextServiceDate(newDate);
        }

        // The contract is managed: flushing cascades the new visit and assigns its id on this instance.
        amcContractRepository.flush();
        if (target.getId() == null) {
            throw new IllegalStateException("Rescheduled visit was not saved");
        }

        rescheduleRepository.save(ServiceVisitReschedule.builder()
                .amcContractId(contractId)
                .serviceVisitId(visitId)
                .newServiceVisitId(target.getId())
                .raisedBy(request.raisedBy())
                .reason(reason)
                .previousStatus(previousStatus)
                .previousDate(previousDate)
                .newDate(newDate)
                .previousEngineerName(previousEngineer)
                .newEngineerName(engineerName)
                .rescheduledBy(rescheduledBy == null ? "system" : rescheduledBy)
                .build());

        log.info("Service visit rescheduled. visitId={}, previousStatus={}, newVisitId={}, newDate={}, raisedBy={}, by={}",
                visitId,
                previousStatus,
                target.getId(),
                newDate,
                LogMaskingUtil.mask("name", request.raisedBy()),
                LogMaskingUtil.mask("username", rescheduledBy));

        return queryRepository.findRow(target.getId())
                .orElseThrow(() -> new IllegalStateException("Rescheduled visit not found: " + target.getId()));
    }

    private static void syncServiceSummary(AmcContract contract) {
        List<ServiceHistory> entries = contract.getServiceHistory();
        contract.setTotalServices(entries.size());
        contract.setCompletedServices((int) entries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.getStatus() == ServiceVisitStatus.COMPLETED)
                .count());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static String truncate(String value) {
        return value.length() <= WORK_DONE_MAX ? value : value.substring(0, WORK_DONE_MAX);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
