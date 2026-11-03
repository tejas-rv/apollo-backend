package com.apollo.elevator.devdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.apollo.elevator.customer.model.dto.AmcDetails;
import com.apollo.elevator.customer.model.dto.LiftCustomerDetails;
import com.apollo.elevator.customer.model.dto.ServiceHistoryDetails;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class TestCustomerSeederTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 15);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void buildsTenValidCustomersWithUniqueCodes() {
        for (List<Long> engineers : List.of(List.of(11L, 12L, 13L), List.of(11L), List.<Long>of())) {
            List<LiftCustomerDetails> customers = TestCustomerSeeder.buildCustomers(TODAY, engineers);

            assertThat(customers).hasSize(10);
            assertThat(customers).extracting(LiftCustomerDetails::getCustomerCode).doesNotHaveDuplicates()
                    .allMatch(code -> code.startsWith(TestCustomerSeeder.CODE_PREFIX));
            customers.forEach(c -> assertThat(validator.validate(c)).as(c.getCustomerCode()).isEmpty());
        }
    }

    @Test
    void coversOverdueTodayUpcomingCancelledAndLegacyVisits() {
        List<ServiceHistoryDetails> visits = visits(TestCustomerSeeder.buildCustomers(TODAY, List.of(11L, 12L)));

        assertThat(visits).anyMatch(v -> scheduled(v) && v.getServiceDate().isBefore(TODAY));
        assertThat(visits).anyMatch(v -> scheduled(v) && v.getServiceDate().isEqual(TODAY));
        assertThat(visits).anyMatch(v -> scheduled(v) && v.getServiceDate().isAfter(TODAY));
        assertThat(visits).anyMatch(v -> v.getStatus() == ServiceVisitStatus.CANCELLED);
        assertThat(visits).anyMatch(v -> v.getStatus() == ServiceVisitStatus.COMPLETED);
        assertThat(visits).anyMatch(v -> v.getAssignedEngineerId() == null
                && TestCustomerSeeder.LEGACY_ENGINEER.equals(v.getEngineerName()));
        assertThat(visits).extracting(ServiceHistoryDetails::getAssignedEngineerId).contains(11L, 12L);
    }

    private static boolean scheduled(ServiceHistoryDetails visit) {
        return visit.getStatus() == ServiceVisitStatus.SCHEDULED;
    }

    private static List<ServiceHistoryDetails> visits(List<LiftCustomerDetails> customers) {
        return customers.stream()
                .flatMap(c -> c.getLifts() == null ? Stream.empty() : c.getLifts().stream())
                .flatMap(l -> l.getAmcDetails() == null ? Stream.empty() : l.getAmcDetails().stream())
                .map(AmcDetails::getServiceHistory)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .toList();
    }
}
