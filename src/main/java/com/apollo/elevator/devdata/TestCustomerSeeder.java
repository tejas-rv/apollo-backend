package com.apollo.elevator.devdata;

import com.apollo.elevator.authorization.model.entity.User;
import com.apollo.elevator.authorization.model.enums.Role;
import com.apollo.elevator.authorization.model.enums.UserType;
import com.apollo.elevator.authorization.repository.UserRepository;
import com.apollo.elevator.customer.model.dto.AmcDetails;
import com.apollo.elevator.customer.model.dto.ClientRepresentativeDetails;
import com.apollo.elevator.customer.model.dto.LiftCustomerDetails;
import com.apollo.elevator.customer.model.dto.LiftDetails;
import com.apollo.elevator.customer.model.dto.MachineDetails;
import com.apollo.elevator.customer.model.dto.OsgDetails;
import com.apollo.elevator.customer.model.dto.ServiceHistoryDetails;
import com.apollo.elevator.customer.model.dto.UpsDetails;
import com.apollo.elevator.customer.model.enums.AmcStatus;
import com.apollo.elevator.customer.model.enums.ContractType;
import com.apollo.elevator.customer.model.enums.DoorType;
import com.apollo.elevator.customer.model.enums.DriveType;
import com.apollo.elevator.customer.model.enums.LiftType;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.customer.service.CustomerService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Opt-in test data: 10 customers (codes TEST-001..TEST-010) covering the main AMC / service-visit scenarios.
 * Enable with SEED_TEST_CUSTOMERS=true. Existing TEST-* codes are skipped, so it is safe to leave on;
 * delete them from the UI and restart to regenerate with dates relative to today.
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apollo.seed", name = "test-customers", havingValue = "true")
public class TestCustomerSeeder implements ApplicationRunner {

    static final String CODE_PREFIX = "TEST-";
    static final String LEGACY_ENGINEER = "Ramesh Kumar (former staff)";

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    @Override
    public void run(ApplicationArguments args) {
        List<Long> engineers = activeEngineerIds();
        if (engineers.isEmpty()) {
            log.warn("Test customer seed: no enabled engineer accounts; visits will use unlinked engineer names");
        }
        int created = 0;
        for (LiftCustomerDetails customer : buildCustomers(LocalDate.now(), engineers)) {
            if (customerRepository.existsByCustomerNameAndMobileNumber(
                    customer.getCustomerName(), customer.getMobileNumber())) {
                continue;
            }
            Set<ConstraintViolation<LiftCustomerDetails>> violations = validator.validate(customer);
            if (!violations.isEmpty()) {
                throw new IllegalStateException("Invalid seed customer " + customer.getCustomerCode() + ": "
                        + violations.stream().map(v -> v.getPropertyPath() + " " + v.getMessage())
                        .collect(Collectors.joining(", ")));
            }
            customerService.createCustomer(customer);
            created++;
        }
        log.info("Test customer seed complete. created={}, engineersUsed={}", created, engineers.size());
    }

    private List<Long> activeEngineerIds() {
        return userRepository.findByRoleOrderByUsernameAsc(Role.ENGINEER).stream()
                .filter(User::isEnabled)
                .filter(user -> user.resolvedUserType() == UserType.ENGINEER)
                .map(User::getId)
                .toList();
    }

    static List<LiftCustomerDetails> buildCustomers(LocalDate today, List<Long> engineerIds) {
        Engineers eng = new Engineers(engineerIds);
        List<LiftCustomerDetails> customers = new ArrayList<>();

        // 1. Active GOLD contract: completed history, one overdue and one upcoming visit.
        customers.add(customer(1, "Sri Sai Residency", "9845012345", "secretary@srisairesidency.in",
                "No. 14, 3rd Cross, JP Nagar 6th Phase", "Bengaluru", "Karnataka", "560078",
                List.of(rep("Prakash Rao", "9845098450"), rep("Meena Iyer", "7760123456")),
                passenger(8, 8, "Johnson Lifts", DriveType.TRACTION, DoorType.AUTO, 2016, "JL-BLR-1604",
                        amc("AMC-T001", AmcStatus.ACTIVE, ContractType.GOLD, today.minusMonths(6),
                                today.plusMonths(6), "48000", "QUARTERLY", today.plusDays(20),
                                visit(today.minusMonths(5), eng.get(0), ServiceVisitStatus.COMPLETED,
                                        "Rope tension adjusted, door sensors cleaned"),
                                visit(today.minusMonths(2), eng.get(0), ServiceVisitStatus.COMPLETED,
                                        "Brake lining checked, lubrication done"),
                                visit(today.minusDays(5), eng.get(0), ServiceVisitStatus.SCHEDULED,
                                        "Quarterly preventive maintenance"),
                                visit(today.plusDays(10), eng.get(0), ServiceVisitStatus.SCHEDULED,
                                        "Quarterly preventive maintenance")))));

        // 2. Hospital lift, PLATINUM: visit due today, a cancelled visit to reschedule, payment overdue.
        customers.add(customer(2, "Lakshmi Multispeciality Hospital", "8023456789", "facilities@lakshmihospital.in",
                "Plot 22, Hunsur Road, Vijayanagar 2nd Stage", "Mysuru", "Karnataka", "570017",
                List.of(rep("Dr. Anand Shetty", "9900112233")),
                lift(LiftType.HOSPITAL, DriveType.HYDRAULIC, DoorType.AUTO, 5, 1360, null, "Schindler", 2019,
                        "SCH-MYS-1907", amc("AMC-T002", AmcStatus.ACTIVE, ContractType.PLATINUM,
                                today.minusMonths(3), today.plusMonths(9), "96000", "MONTHLY", today.minusDays(7),
                                visit(today.minusMonths(2), eng.get(1), ServiceVisitStatus.COMPLETED,
                                        "Hydraulic oil topped up, valve block inspected"),
                                visit(today.minusDays(20), eng.get(1), ServiceVisitStatus.CANCELLED,
                                        "Cancelled - hospital requested no shutdown during audit"),
                                visit(today, eng.get(1), ServiceVisitStatus.SCHEDULED,
                                        "Monthly preventive maintenance"),
                                visit(today.plusDays(30), eng.get(1), ServiceVisitStatus.SCHEDULED,
                                        "Monthly preventive maintenance")))));

        // 3. Two lifts with separate contracts, handled by different engineers.
        customers.add(customer(3, "Green Valley Apartments Owners Association", "9443012345",
                "greenvalley.oa@gmail.com", "Door No. 7, 2nd Avenue, Anna Nagar West", "Chennai", "Tamil Nadu",
                "600040", List.of(rep("K. Saravanan", "9443098765")),
                passenger(12, 10, "OTIS", DriveType.TRACTION, DoorType.AUTO, 2014, "OT-CHN-1401",
                        amc("AMC-T003A", AmcStatus.ACTIVE, ContractType.GOLD, today.minusMonths(4),
                                today.plusMonths(8), "60000", "HALF_YEARLY", today.plusMonths(2),
                                visit(today.minusMonths(3), eng.get(0), ServiceVisitStatus.COMPLETED,
                                        "Controller checked, landing doors aligned"),
                                visit(today.plusDays(3), eng.get(0), ServiceVisitStatus.SCHEDULED,
                                        "Preventive maintenance - passenger lift"))),
                lift(LiftType.GOODS, DriveType.TRACTION, DoorType.MANUAL, 12, 2000, null, "Kone", 2014,
                        "KN-CHN-1402", amc("AMC-T003B", AmcStatus.ACTIVE, ContractType.SILVER,
                                today.minusMonths(4), today.plusMonths(8), "36000", "ANNUAL", today.plusMonths(8),
                                visit(today.minusDays(2), eng.get(1), ServiceVisitStatus.SCHEDULED,
                                        "Preventive maintenance - goods lift"),
                                visit(today.plusDays(25), eng.get(1), ServiceVisitStatus.SCHEDULED,
                                        "Preventive maintenance - goods lift")))));

        // 4. Home lift with UPS; contract not started yet (PENDING), first visits in the future.
        LiftDetails homeLift = lift(LiftType.HOME, DriveType.MRL, DoorType.MANUAL, 3, null, 4, "Nibav", 2024,
                "NB-HYD-2401", amc("AMC-T004", AmcStatus.PENDING, ContractType.SILVER, today.plusDays(15),
                        today.plusDays(15).plusYears(1), "18000", "ANNUAL", today.plusDays(15),
                        visit(today.plusDays(15), eng.get(2), ServiceVisitStatus.SCHEDULED,
                                "First service after AMC start"),
                        visit(today.plusMonths(4), eng.get(2), ServiceVisitStatus.SCHEDULED,
                                "Periodic maintenance")));
        homeLift.setIsUpsPresent(true);
        homeLift.setUps(UpsDetails.builder().upsType("Online").kva(new BigDecimal("3"))
                .battery(UpsDetails.BatteryDetails.builder().make("Exide").voltage("12V").noOfBatteries(8).build())
                .build());
        customers.add(customer(4, "Raghav Reddy", "7095123456", "raghav.reddy@outlook.com",
                "Villa 18, Aparna Serene Park, Kondapur", "Hyderabad", "Telangana", "500084",
                List.of(), homeLift));

        // 5. Expired contract, all visits completed by different engineers - renewal candidate.
        customers.add(customer(5, "Metro Central Mall", "9822012345", "ops@metrocentralmall.co.in",
                "Survey No. 45, FC Road, Shivajinagar", "Pune", "Maharashtra", "411005",
                List.of(rep("Sneha Kulkarni", "9822098220"), rep("Amit Deshpande", "")),
                lift(LiftType.CAR, DriveType.HYDRAULIC, DoorType.AUTO, 4, 3500, null, "ThyssenKrupp", 2012,
                        "TK-PUN-1209", amc("AMC-T005", AmcStatus.EXPIRED, ContractType.GOLD,
                                today.minusMonths(13), today.minusMonths(1), "72000", "QUARTERLY", null,
                                visit(today.minusMonths(12), eng.get(2), ServiceVisitStatus.COMPLETED,
                                        "Platform levelling corrected"),
                                visit(today.minusMonths(8), eng.get(0), ServiceVisitStatus.COMPLETED,
                                        "Hydraulic hoses replaced"),
                                visit(today.minusMonths(4), eng.get(1), ServiceVisitStatus.COMPLETED,
                                        "Final service of contract, all checks OK")))));

        // 6. Goods lift without any AMC (quotation / new contract flow).
        customers.add(customer(6, "Kaveri Tech Park", "9036012345", "admin@kaveritechpark.com",
                "Plot 101, KIADB Industrial Area, Hebbal", "Mysuru", "Karnataka", "570016",
                List.of(rep("Naveen Gowda", "9036098765")),
                lift(LiftType.GOODS, DriveType.TRACTION, DoorType.MANUAL, 6, 1500, null, "Escon", 2021,
                        "ES-MYS-2103")));

        // 7. Minimal record: only mandatory fields, one lift, no contract.
        customers.add(LiftCustomerDetails.builder()
                .customerCode(code(7))
                .customerName("Sunrise Public School")
                .mobileNumber("6282012345")
                .lifts(List.of(lift(LiftType.PASSENGER, DriveType.OTHER, null, 3, null, 6, null, null, null)))
                .build());

        // 8. Legacy visits with an unlinked engineer name (visible to admin, on no engineer dashboard).
        customers.add(customer(8, "Annapoorna Residency Hotel", "9842012345", "gm@annapoornahotel.in",
                "75, Race Course Road, Gopalapuram", "Coimbatore", "Tamil Nadu", "641018",
                List.of(rep("S. Murugan", "9842098420")),
                passenger(5, 13, "Fuji", DriveType.MRL, DoorType.AUTO, 2018, "FJ-CBE-1805",
                        amc("AMC-T008", AmcStatus.ACTIVE, ContractType.SILVER, today.minusMonths(8),
                                today.plusMonths(4), "30000", "HALF_YEARLY", today.plusMonths(4),
                                legacyVisit(today.minusMonths(6), ServiceVisitStatus.COMPLETED,
                                        "Car lighting replaced, ARD tested"),
                                legacyVisit(today.minusDays(12), ServiceVisitStatus.SCHEDULED,
                                        "Half-yearly maintenance (reassign to an active engineer)")))));

        // 9. Contract cancelled mid-term: one completed visit, remaining visits cancelled.
        customers.add(customer(9, "Shanti Nursing Home", "9741012345", "shantinursinghome@yahoo.co.in",
                "2-14/3, Kadri Main Road, Bejai", "Mangaluru", "Karnataka", "575004",
                List.of(rep("Sister Mary D'Souza", "9741098741")),
                lift(LiftType.HOSPITAL, DriveType.TRACTION, DoorType.AUTO, 4, 1600, null, "Johnson Lifts", 2015,
                        "JL-MNG-1510", amc("AMC-T009", AmcStatus.CANCELLED, ContractType.SILVER,
                                today.minusMonths(5), today.plusMonths(7), "42000", "QUARTERLY", null,
                                visit(today.minusMonths(4), eng.get(2), ServiceVisitStatus.COMPLETED,
                                        "Routine maintenance, governor tested"),
                                visit(today.minusMonths(1), eng.get(2), ServiceVisitStatus.CANCELLED,
                                        "Cancelled - contract terminated by customer"),
                                visit(today.plusMonths(2), eng.get(2), ServiceVisitStatus.CANCELLED,
                                        "Cancelled - contract terminated by customer")))));

        // 10. Prospect with no lifts yet (enquiry / quotation stage).
        customers.add(customer(10, "Vasudev Industries Pvt Ltd", "8971012345", "purchase@vasudevindustries.in",
                "Gokul Road Industrial Estate, Unit 5", "Hubballi", "Karnataka", "580030",
                List.of(rep("Vasudev Joshi", "8971098971"))));

        return customers;
    }

    static String code(int n) {
        return CODE_PREFIX + String.format("%03d", n);
    }

    private static LiftCustomerDetails customer(int n, String name, String mobile, String email, String address,
            String city, String state, String pincode, List<ClientRepresentativeDetails> reps, LiftDetails... lifts) {
        return LiftCustomerDetails.builder()
                .customerCode(code(n))
                .customerName(name)
                .mobileNumber(mobile)
                .email(email)
                .address(address)
                .city(city)
                .state(state)
                .pincode(pincode)
                .clientRepresentative(reps)
                .lifts(Arrays.asList(lifts))
                .build();
    }

    private static ClientRepresentativeDetails rep(String name, String mobile) {
        return ClientRepresentativeDetails.builder().name(name).mobileNumber(mobile).build();
    }

    private static LiftDetails passenger(int floors, int persons, String brand, DriveType drive, DoorType door,
            int year, String serial, AmcDetails amc) {
        LiftDetails lift = lift(LiftType.PASSENGER, drive, door, floors, persons * 68, persons, brand, year,
                serial, amc);
        lift.setSpeed(new BigDecimal("1.0"));
        lift.setOsg(OsgDetails.builder().make(brand).diaOfTheRope(8)
                .ratedSpeed(new BigDecimal("1.0")).trippingSpeed(new BigDecimal("1.25")).build());
        lift.setMachineDetails(MachineDetails.builder().manufacturedBy(brand).yearOfManufacture(year)
                .machineType("Geared").noOfRopes(4).diaOfTheRopeMm(10).roping("1:1").build());
        return lift;
    }

    private static LiftDetails lift(LiftType type, DriveType drive, DoorType door, int floors, Integer kg,
            Integer persons, String brand, Integer year, String serial, AmcDetails... amcs) {
        return LiftDetails.builder()
                .liftType(type)
                .driveType(drive)
                .doorType(door)
                .numberOfFloors(floors)
                .capacityInKg(kg)
                .capacityInPersons(persons)
                .brand(brand)
                .yearOfInstallation(year)
                .serialNumber(serial)
                .isUpsPresent(false)
                .amcDetails(Arrays.asList(amcs))
                .build();
    }

    private static AmcDetails amc(String number, AmcStatus status, ContractType type, LocalDate start,
            LocalDate end, String amount, String frequency, LocalDate nextPayment, ServiceHistoryDetails... visits) {
        List<ServiceHistoryDetails> history = Arrays.asList(visits);
        LocalDate nextService = history.stream()
                .filter(v -> v.getStatus() == ServiceVisitStatus.SCHEDULED)
                .map(ServiceHistoryDetails::getServiceDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        return AmcDetails.builder()
                .contractNumber(number)
                .status(status)
                .contractType(type)
                .startDate(start)
                .endDate(end)
                .amcAmount(new BigDecimal(amount))
                .paymentFrequency(frequency)
                .nextPaymentDate(nextPayment)
                .nextServiceDate(nextService)
                .serviceHistory(history)
                .build();
    }

    private static ServiceHistoryDetails visit(LocalDate date, EngineerRef engineer, ServiceVisitStatus status,
            String workDone) {
        return ServiceHistoryDetails.builder()
                .serviceDate(date)
                .assignedEngineerId(engineer.id())
                .engineerName(engineer.name())
                .status(status)
                .workDone(workDone)
                .build();
    }

    private static ServiceHistoryDetails legacyVisit(LocalDate date, ServiceVisitStatus status, String workDone) {
        return visit(date, new EngineerRef(null, LEGACY_ENGINEER), status, workDone);
    }

    record EngineerRef(Long id, String name) {
    }

    /** Spreads visits over the available engineers; falls back to unlinked names when there are none. */
    private record Engineers(List<Long> ids) {
        EngineerRef get(int slot) {
            if (ids.isEmpty()) {
                return new EngineerRef(null, "Unassigned Engineer " + (slot + 1));
            }
            return new EngineerRef(ids.get(slot % ids.size()), null);
        }
    }
}
