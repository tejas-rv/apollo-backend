package com.apollo.elevator.customer.dataexchange;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sheet and column layout of the {@code Apollo_Elevator_Customer_Data.xlsx} workbook shared by
 * customer import and export. Column headers are the Java entity property names; the
 * {@code customerId}, {@code liftId} and {@code amcId} columns relate rows across sheets. Export
 * writes the database IDs; on import an existing customer (matched by {@code customerCode}) has its
 * lifts matched by {@code liftId} (database ID) or {@code serialNumber} and its AMC contracts by
 * {@code amcId} (database ID) or {@code contractNumber}, and service visits by {@code visitId}
 * (database ID) or service date + engineer. Unmatched rows are created as new records; a new customer
 * with a blank {@code customerCode} gets a generated {@code APL-ELV-<initials>-<id>} code.
 */
public final class CustomerWorkbookSchema {

    public static final String FILE_BASE_NAME = "Apollo_Elevator_Customer_Data";
    public static final String FILE_NAME = FILE_BASE_NAME + ".xlsx";
    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    public static final String CUSTOMERS_SHEET = "customers";
    public static final String REPRESENTATIVES_SHEET = "client_representatives";
    public static final String LIFTS_SHEET = "lifts";
    public static final String AMC_CONTRACTS_SHEET = "amc_contracts";
    public static final String SERVICE_HISTORY_SHEET = "service_history";

    public static final String CUSTOMER_KEY = "customerId";
    public static final String LIFT_KEY = "liftId";
    public static final String AMC_KEY = "amcId";
    /** Optional database ID of a service visit; lets edited visits be updated instead of duplicated. */
    public static final String VISIT_KEY = "visitId";

    /**
     * Workbook custom property recording whether COMPLETED visits were exported. An override import
     * of a file exported without them keeps the stored completed visits.
     */
    public static final String INCLUDES_COMPLETED_PROPERTY = "apolloIncludesCompletedVisits";

    /** Export date format; import also accepts {@code dd/MM/yyyy} and real Excel date cells. */
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public static final List<String> CUSTOMER_FIELDS = List.of(
            "customerCode", "customerName", "mobileNumber", "email",
            "address", "city", "state", "pincode"
    );

    public static final List<String> REPRESENTATIVE_FIELDS = List.of("name", "mobileNumber");

    public static final List<String> LIFT_FIELDS = List.of(
            "liftType", "driveType", "numberOfFloors", "capacityInKg", "capacityInPersons",
            "brand", "liftModel", "installationType", "yearOfInstallation", "serialNumber",
            "doorType", "machineType", "machineName", "kw", "amps", "speed", "voltage",
            "frequency", "osgType", "ratedSpeed", "trippingSpeed", "isUpsPresent", "upsType",
            "kva", "batteryMake", "batteryVoltage", "batteryNoOfBatteries", "manufacturedBy",
            "yearOfManufacture", "noOfGrooves", "frictionSheaveDiameter", "noOfRopes",
            "diaOfTheRopeMm", "lengthOfTheRopeMm", "roping", "isDeflectorPulley",
            "deflectorPulleyDiameter", "deflectorPulleyNoOfGrooves", "mainMotorKw",
            "mainMotorAmps", "mainMotorSpeed", "mainMotorVoltage", "mainMotorFrequency",
            "mainMotorNoOfPoles"
    );

    public static final List<String> AMC_FIELDS = List.of(
            "contractNumber", "status", "contractType", "startDate", "endDate", "amcAmount",
            "paymentFrequency", "nextPaymentDate", "nextServiceDate", "totalServices",
            "completedServices"
    );

    public static final List<String> SERVICE_HISTORY_FIELDS = List.of(
            "serviceDate", "engineerName", "workDone", "status"
    );

    public static final List<String> CUSTOMER_COLUMNS = concat(List.of(CUSTOMER_KEY), CUSTOMER_FIELDS);
    public static final List<String> REPRESENTATIVE_COLUMNS = concat(List.of(CUSTOMER_KEY), REPRESENTATIVE_FIELDS);
    public static final List<String> LIFT_COLUMNS = concat(List.of(LIFT_KEY, CUSTOMER_KEY), LIFT_FIELDS);
    public static final List<String> AMC_COLUMNS = concat(List.of(AMC_KEY, CUSTOMER_KEY, LIFT_KEY), AMC_FIELDS);
    public static final List<String> SERVICE_HISTORY_COLUMNS = concat(List.of(VISIT_KEY, AMC_KEY), SERVICE_HISTORY_FIELDS);

    private CustomerWorkbookSchema() {
    }

    private static List<String> concat(List<String> first, List<String> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }
}
