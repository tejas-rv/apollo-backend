package com.apollo.elevator.engineer.service;

import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.engineer.model.entity.ServiceCheckItem;
import com.apollo.elevator.engineer.model.entity.ServiceReport;
import com.apollo.elevator.engineer.model.enums.AnswerType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Records a submitted service report on the AMC contract's service history: the engineer's
 * matching SCHEDULED visit becomes COMPLETED, or a new COMPLETED visit is added when none
 * was planned. Keeps the contract's service counters and next service date in sync.
 */
final class ServiceVisitCompletion {

    static final int WORK_DONE_MAX = 500;

    private ServiceVisitCompletion() {
    }

    /** @return the updated or created visit, or empty when the report has no AMC contract. */
    static Optional<ServiceHistory> complete(
            Customer customer, ServiceReport report, Set<String> engineerNames) {
        AmcContract contract = findContract(customer, report.getAmcContractId());
        if (contract == null) {
            return Optional.empty();
        }
        if (contract.getServiceHistory() == null) {
            contract.setServiceHistory(new ArrayList<>());
        }
        List<ServiceHistory> history = contract.getServiceHistory();

        ServiceHistory visit = pickScheduledVisit(history, report, engineerNames)
                .orElseGet(() -> {
                    ServiceHistory created = new ServiceHistory();
                    history.add(created);
                    return created;
                });
        visit.setServiceDate(report.getVisitDate());
        visit.setAssignedEngineerId(report.getEngineerUserId());
        visit.setEngineerName(report.getEngineerName());
        visit.setWorkDone(workDone(report));
        visit.setStatus(ServiceVisitStatus.COMPLETED);

        contract.setTotalServices(history.size());
        contract.setCompletedServices((int) history.stream()
                .filter(entry -> entry.getStatus() == ServiceVisitStatus.COMPLETED)
                .count());
        history.stream()
                .filter(entry -> entry.getStatus() == ServiceVisitStatus.SCHEDULED)
                .map(ServiceHistory::getServiceDate)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .ifPresent(contract::setNextServiceDate);
        return Optional.of(visit);
    }

    /**
     * Prefers a visit assigned to this engineer, then an unassigned one carrying their name,
     * then any unassigned visit; within a tier the one closest to the visit date wins.
     */
    static Optional<ServiceHistory> pickScheduledVisit(
            List<ServiceHistory> history, ServiceReport report, Set<String> engineerNames) {
        Long engineerId = report.getEngineerUserId();
        List<Predicate<ServiceHistory>> tiers = List.of(
                entry -> engineerId.equals(entry.getAssignedEngineerId()),
                entry -> entry.getAssignedEngineerId() == null
                        && engineerNames.contains(normalise(entry.getEngineerName())),
                entry -> entry.getAssignedEngineerId() == null);
        LocalDate visitDate = report.getVisitDate();
        for (Predicate<ServiceHistory> tier : tiers) {
            Optional<ServiceHistory> match = history.stream()
                    .filter(Objects::nonNull)
                    .filter(entry -> entry.getStatus() == ServiceVisitStatus.SCHEDULED)
                    .filter(tier)
                    .min(Comparator.comparingLong(entry -> entry.getServiceDate() == null
                            ? Long.MAX_VALUE
                            : Math.abs(ChronoUnit.DAYS.between(visitDate, entry.getServiceDate()))));
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /** Summary of the work performed, taken from the report's descriptive answers or notes. */
    static String workDone(ServiceReport report) {
        String details = report.getCheckItems() == null ? "" : report.getCheckItems().stream()
                .filter(item -> item.getAnswerType() == AnswerType.DESCRIPTIVE)
                .sorted(Comparator.comparingInt(ServiceCheckItem::getItemOrder))
                .map(ServiceCheckItem::getAnswerText)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim)
                .collect(Collectors.joining("; "));
        if (details.isEmpty() && report.getOverallNotes() != null) {
            details = report.getOverallNotes().trim();
        }
        String text = "Report #" + report.getId() + (details.isEmpty() ? " submitted" : ": " + details);
        return text.length() <= WORK_DONE_MAX ? text : text.substring(0, WORK_DONE_MAX - 1) + "…";
    }

    private static AmcContract findContract(Customer customer, Long contractId) {
        if (contractId == null || customer.getLifts() == null) {
            return null;
        }
        return customer.getLifts().stream()
                .filter(Objects::nonNull)
                .map(Lift::getAmcContracts)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(contract -> contractId.equals(contract.getId()))
                .findFirst()
                .orElse(null);
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
