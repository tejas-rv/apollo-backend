package com.apollo.elevator.customer.dataexchange;

import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.AMC_CONTRACTS_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.AMC_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.AMC_KEY;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.CUSTOMERS_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.CUSTOMER_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.CUSTOMER_KEY;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.LIFTS_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.LIFT_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.LIFT_KEY;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.REPRESENTATIVES_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.REPRESENTATIVE_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.SERVICE_HISTORY_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.SERVICE_HISTORY_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.VISIT_KEY;

import com.apollo.elevator.common.validation.IndianFormats;
import com.apollo.elevator.customer.dataexchange.dto.CustomerDataSource;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportMode;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportOutcome;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportResponse;
import com.apollo.elevator.customer.dataexchange.dto.CustomerImportStatus;
import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.ClientRepresentative;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.LiftType;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.customer.service.CustomerCodeGenerator;
import com.apollo.elevator.engineer.service.EngineerDirectory;
import jakarta.persistence.Column;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperty;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Imports customers (with representatives, lifts, AMC contracts and service history) from the
 * customer data workbook. Each customer is validated and saved independently in its own
 * transaction: customers with errors are FAILED, new customer codes are IMPORTED, and existing
 * customer codes are merged into the stored customer (UPDATED, or SKIPPED when nothing changed).
 *
 * <p>Merge rules for an existing customer: lifts are matched by {@code liftId} (database ID) or
 * {@code serialNumber}, AMC contracts by {@code amcId} (database ID) or {@code contractNumber},
 * representatives by mobile number or name, and service visits by {@code visitId} (database ID)
 * or, when the row has no visitId, by service date and engineer. Unmatched rows are added and
 * blank cells keep the stored value. In {@link CustomerImportMode#MERGE} nothing is deleted; in
 * {@link CustomerImportMode#OVERRIDE} the representatives and the visits of each AMC contract in
 * the file are replaced by the file rows.
 */
@Service
@Slf4j
public class CustomerDataImportService {

    private static final Pattern MOBILE_PATTERN = Pattern.compile(IndianFormats.MOBILE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(IndianFormats.EMAIL);
    private static final Pattern PINCODE_PATTERN = Pattern.compile(IndianFormats.PINCODE);

    private final CustomerRepository customerRepository;
    private final EngineerDirectory engineerDirectory;
    private final TransactionTemplate transactionTemplate;

    public CustomerDataImportService(
            CustomerRepository customerRepository,
            EngineerDirectory engineerDirectory,
            PlatformTransactionManager transactionManager
    ) {
        this.customerRepository = customerRepository;
        this.engineerDirectory = engineerDirectory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CustomerImportResponse importWorkbook(byte[] content, String fileName, CustomerDataSource source) {
        return importWorkbook(content, fileName, source, CustomerImportMode.MERGE);
    }

    public CustomerImportResponse importWorkbook(
            byte[] content,
            String fileName,
            CustomerDataSource source,
            CustomerImportMode mode
    ) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("The import file is empty");
        }
        CustomerImportMode importMode = mode == null ? CustomerImportMode.MERGE : mode;

        ParsedWorkbook parsed = parse(content);
        EngineerDirectory.Lookup engineers = engineerDirectory.lookup();
        List<CustomerImportOutcome> outcomes = new ArrayList<>();
        for (CustomerGroup group : parsed.customers().values()) {
            assignEngineers(group.customer, engineers);
            outcomes.add(persist(group, importMode, parsed.includesCompletedVisits()));
        }

        int imported = countByStatus(outcomes, CustomerImportStatus.IMPORTED);
        int updated = countByStatus(outcomes, CustomerImportStatus.UPDATED);
        int skipped = countByStatus(outcomes, CustomerImportStatus.SKIPPED);
        int failed = countByStatus(outcomes, CustomerImportStatus.FAILED);
        log.info(
                "Customer data import completed. fileName={}, source={}, mode={}, total={}, imported={}, updated={}, skipped={}, failed={}, unlinkedRowErrors={}",
                fileName, source, importMode, outcomes.size(), imported, updated, skipped, failed,
                parsed.unlinkedRowErrors().size()
        );

        return new CustomerImportResponse(
                fileName,
                source,
                importMode,
                outcomes.size(),
                imported,
                updated,
                skipped,
                failed,
                outcomes,
                parsed.unlinkedRowErrors(),
                null
        );
    }

    ParsedWorkbook parse(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<String> unlinked = new ArrayList<>();

            Map<String, CustomerGroup> customers = readCustomers(
                    readSheet(workbook, CUSTOMERS_SHEET, List.of(CUSTOMER_KEY), true, evaluator));
            readRepresentatives(
                    readSheet(workbook, REPRESENTATIVES_SHEET, List.of(CUSTOMER_KEY), false, evaluator),
                    customers, unlinked);
            Map<String, LiftRef> lifts = readLifts(
                    readSheet(workbook, LIFTS_SHEET, List.of(LIFT_KEY, CUSTOMER_KEY), false, evaluator),
                    customers, unlinked);
            Map<String, AmcRef> contracts = readAmcContracts(
                    readSheet(workbook, AMC_CONTRACTS_SHEET, List.of(AMC_KEY, LIFT_KEY), false, evaluator),
                    customers, lifts, unlinked);
            readServiceHistory(
                    readSheet(workbook, SERVICE_HISTORY_SHEET, List.of(AMC_KEY), false, evaluator),
                    contracts, unlinked);

            return new ParsedWorkbook(customers, unlinked, includesCompletedVisits(workbook));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgument) {
                throw illegalArgument;
            }
            throw new IllegalArgumentException(
                    "The file could not be read as an Excel (.xlsx) workbook: " + exception.getMessage(), exception);
        }
    }

    private Map<String, CustomerGroup> readCustomers(List<RowData> rows) {
        Map<String, CustomerGroup> customers = new LinkedHashMap<>();
        Map<String, CustomerGroup> byCode = new HashMap<>();

        for (RowData row : rows) {
            String key = row.value(CUSTOMER_KEY);
            if (key == null) {
                key = "(row " + row.rowNumber() + ")";
            }
            CustomerGroup existing = customers.get(key);
            if (existing != null) {
                existing.errors.add(row.describe() + ": duplicate customerId '" + key
                        + "' (already used at row " + existing.row.rowNumber() + ")");
                continue;
            }

            Customer customer = new Customer();
            CustomerGroup group = new CustomerGroup(key, row, customer);
            customers.put(key, group);

            if (row.value(CUSTOMER_KEY) == null) {
                group.errors.add(row.describe() + ": customerId is required");
            }
            applyFields(customer, CUSTOMER_FIELDS, row, group.errors);
            validateCustomer(customer, row, group.errors);

            if (customer.getCustomerCode() != null) {
                CustomerGroup sameCode = byCode.putIfAbsent(customer.getCustomerCode(), group);
                if (sameCode != null) {
                    String message = "customerCode '" + customer.getCustomerCode()
                            + "' is used more than once in the file (rows "
                            + sameCode.row.rowNumber() + " and " + row.rowNumber() + ")";
                    sameCode.errors.add(message);
                    group.errors.add(message);
                }
            }
        }
        return customers;
    }

    private void validateCustomer(Customer customer, RowData row, List<String> errors) {
        if (customer.getCustomerName() == null) {
            errors.add(row.describe() + ": customerName is required");
        }
        if (customer.getMobileNumber() == null) {
            errors.add(row.describe() + ": mobileNumber is required");
        } else if (!MOBILE_PATTERN.matcher(customer.getMobileNumber()).matches()) {
            errors.add(row.describe() + ": mobileNumber must be a valid 10-digit Indian mobile number");
        }
        if (customer.getEmail() != null && !EMAIL_PATTERN.matcher(customer.getEmail()).matches()) {
            errors.add(row.describe() + ": email is not a valid email address");
        }
        if (customer.getPincode() != null && !PINCODE_PATTERN.matcher(customer.getPincode()).matches()) {
            errors.add(row.describe() + ": pincode must be a valid 6-digit Indian pincode");
        }
    }

    private void readRepresentatives(
            List<RowData> rows,
            Map<String, CustomerGroup> customers,
            List<String> unlinked
    ) {
        for (RowData row : rows) {
            CustomerGroup group = findCustomer(row, row.value(CUSTOMER_KEY), customers, unlinked);
            if (group == null) {
                continue;
            }
            ClientRepresentative representative = new ClientRepresentative();
            applyFields(representative, REPRESENTATIVE_FIELDS, row, group.errors);
            if (representative.getName() == null && representative.getMobileNumber() == null) {
                group.errors.add(row.describe() + ": name or mobileNumber is required");
            }
            if (representative.getMobileNumber() != null
                    && !MOBILE_PATTERN.matcher(representative.getMobileNumber()).matches()) {
                group.errors.add(row.describe() + ": mobileNumber must be a valid 10-digit Indian mobile number");
            }
            group.customer.getClientRepresentatives().add(representative);
        }
    }

    private Map<String, LiftRef> readLifts(
            List<RowData> rows,
            Map<String, CustomerGroup> customers,
            List<String> unlinked
    ) {
        Map<String, LiftRef> lifts = new HashMap<>();
        for (RowData row : rows) {
            CustomerGroup group = findCustomer(row, row.value(CUSTOMER_KEY), customers, unlinked);
            if (group == null) {
                continue;
            }
            String liftKey = row.value(LIFT_KEY);
            if (liftKey == null) {
                group.errors.add(row.describe() + ": liftId is required");
                continue;
            }
            if (lifts.containsKey(liftKey)) {
                group.errors.add(row.describe() + ": duplicate liftId '" + liftKey + "'");
                lifts.get(liftKey).group().errors.add(row.describe() + ": duplicate liftId '" + liftKey + "'");
                continue;
            }

            Lift lift = new Lift();
            applyFields(lift, LIFT_FIELDS, row, group.errors);
            validateLift(lift, row, group.errors);
            group.customer.getLifts().add(lift);
            group.sources.put(lift, new RowSource(liftKey, row));
            lifts.put(liftKey, new LiftRef(group, lift));
        }
        return lifts;
    }

    private void validateLift(Lift lift, RowData row, List<String> errors) {
        LiftType type = lift.getLiftType();
        if (type == LiftType.PASSENGER && !isPositive(lift.getCapacityInPersons())) {
            errors.add(row.describe() + ": capacityInPersons is required for PASSENGER lifts");
        }
        if ((type == LiftType.GOODS || type == LiftType.HOSPITAL || type == LiftType.CAR)
                && !isPositive(lift.getCapacityInKg())) {
            errors.add(row.describe() + ": capacityInKg is required for " + type + " lifts");
        }
    }

    private Map<String, AmcRef> readAmcContracts(
            List<RowData> rows,
            Map<String, CustomerGroup> customers,
            Map<String, LiftRef> lifts,
            List<String> unlinked
    ) {
        Map<String, AmcRef> contracts = new HashMap<>();
        for (RowData row : rows) {
            String liftKey = row.value(LIFT_KEY);
            LiftRef liftRef = liftKey == null ? null : lifts.get(liftKey);
            if (liftRef == null) {
                String message = row.describe() + ": liftId '" + liftKey + "' does not match any row in the lifts sheet";
                CustomerGroup group = customers.get(row.value(CUSTOMER_KEY));
                if (group != null) {
                    group.errors.add(message);
                } else {
                    unlinked.add(message);
                }
                continue;
            }

            CustomerGroup group = liftRef.group();
            String customerKey = row.value(CUSTOMER_KEY);
            if (customerKey != null && !customerKey.equals(group.key)) {
                group.errors.add(row.describe() + ": customerId '" + customerKey
                        + "' does not match the customer of liftId '" + liftKey + "' (" + group.key + ")");
            }

            String amcKey = row.value(AMC_KEY);
            if (amcKey == null) {
                group.errors.add(row.describe() + ": amcId is required");
                continue;
            }
            if (contracts.containsKey(amcKey)) {
                group.errors.add(row.describe() + ": duplicate amcId '" + amcKey + "'");
                contracts.get(amcKey).group().errors.add(row.describe() + ": duplicate amcId '" + amcKey + "'");
                continue;
            }

            AmcContract contract = new AmcContract();
            applyFields(contract, AMC_FIELDS, row, group.errors);
            validateContract(contract, row, group.errors);
            liftRef.lift().getAmcContracts().add(contract);
            group.sources.put(contract, new RowSource(amcKey, row));
            group.amcContractCount++;
            contracts.put(amcKey, new AmcRef(group, contract));
        }
        return contracts;
    }

    private void validateContract(AmcContract contract, RowData row, List<String> errors) {
        if (contract.getStartDate() != null && contract.getEndDate() != null
                && contract.getEndDate().isBefore(contract.getStartDate())) {
            errors.add(row.describe() + ": endDate must not be before startDate");
        }
        if (contract.getAmcAmount() != null && contract.getAmcAmount().compareTo(BigDecimal.ZERO) < 0) {
            errors.add(row.describe() + ": amcAmount must not be negative");
        }
        if (contract.getTotalServices() != null && contract.getCompletedServices() != null
                && contract.getCompletedServices() > contract.getTotalServices()) {
            errors.add(row.describe() + ": completedServices must not exceed totalServices");
        }
    }

    private void readServiceHistory(List<RowData> rows, Map<String, AmcRef> contracts, List<String> unlinked) {
        for (RowData row : rows) {
            String amcKey = row.value(AMC_KEY);
            AmcRef amcRef = amcKey == null ? null : contracts.get(amcKey);
            if (amcRef == null) {
                unlinked.add(row.describe() + ": amcId '" + amcKey + "' does not match any row in the amc_contracts sheet");
                continue;
            }
            List<String> errors = amcRef.group().errors;
            ServiceHistory history = new ServiceHistory();
            applyFields(history, SERVICE_HISTORY_FIELDS, row, errors);
            for (String field : SERVICE_HISTORY_FIELDS) {
                if (row.value(field) == null) {
                    errors.add(row.describe() + ": " + field + " is required");
                }
            }
            amcRef.contract().getServiceHistory().add(history);
            amcRef.group().sources.put(history, new RowSource(row.value(VISIT_KEY), row));
        }
    }

    /** False only when the file was exported with completed visits left out. */
    private static boolean includesCompletedVisits(Workbook workbook) {
        if (!(workbook instanceof XSSFWorkbook xssf)) {
            return true;
        }
        CTProperty property = xssf.getProperties().getCustomProperties()
                .getProperty(CustomerWorkbookSchema.INCLUDES_COMPLETED_PROPERTY);
        return property == null || !property.isSetBool() || property.getBool();
    }

    /** Links each visit's engineerName (username, full name or employee number) to a unique engineer account. */
    private static void assignEngineers(Customer customer, EngineerDirectory.Lookup engineers) {
        for (Lift lift : customer.getLifts()) {
            for (AmcContract contract : lift.getAmcContracts()) {
                for (ServiceHistory history : contract.getServiceHistory()) {
                    history.setAssignedEngineerId(engineers.resolve(history.getEngineerName()));
                }
            }
        }
    }

    private CustomerGroup findCustomer(
            RowData row,
            String customerKey,
            Map<String, CustomerGroup> customers,
            List<String> unlinked
    ) {
        CustomerGroup group = customerKey == null ? null : customers.get(customerKey);
        if (group == null) {
            unlinked.add(row.describe() + ": customerId '" + customerKey
                    + "' does not match any row in the customers sheet");
        }
        return group;
    }

    private CustomerImportOutcome persist(CustomerGroup group, CustomerImportMode mode, boolean includesCompleted) {
        String code = group.customer.getCustomerCode();
        if (!group.errors.isEmpty()) {
            return outcome(group, CustomerImportStatus.FAILED, null, group.errors);
        }

        try {
            return transactionTemplate.execute(status -> {
                Optional<Customer> existing = code == null
                        ? Optional.empty()
                        : customerRepository.findByCustomerCode(code);
                if (existing.isEmpty()) {
                    Customer saved;
                    if (code == null) {
                        // The generated code embeds the database id, so insert with a placeholder first.
                        group.customer.setCustomerCode("TMP-" + UUID.randomUUID());
                        saved = customerRepository.save(group.customer);
                        saved.setCustomerCode(
                                CustomerCodeGenerator.generate(saved.getCustomerName(), saved.getId()));
                        customerRepository.flush();
                    } else {
                        saved = customerRepository.save(group.customer);
                    }
                    log.info("Customer imported. customerId={}, customerCode={}, liftCount={}",
                            saved.getId(), saved.getCustomerCode(), group.customer.getLifts().size());
                    return outcome(group, CustomerImportStatus.IMPORTED, saved.getId(), List.of());
                }

                Customer target = existing.get();
                List<String> errors = new ArrayList<>();
                boolean changed = mergeCustomer(group, target, errors, mode, includesCompleted);
                if (!errors.isEmpty()) {
                    status.setRollbackOnly();
                    return outcome(group, CustomerImportStatus.FAILED, target.getId(), errors);
                }
                if (!changed) {
                    return outcome(group, CustomerImportStatus.SKIPPED, target.getId(),
                            List.of("No changes for customer code '" + code + "'"));
                }
                Customer saved = customerRepository.save(target);
                log.info("Customer updated from import. customerId={}, customerCode={}", saved.getId(), code);
                return outcome(group, CustomerImportStatus.UPDATED, saved.getId(), List.of());
            });
        } catch (DataAccessException | TransactionException exception) {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(exception);
            log.warn("Customer import failed while saving. customerCode={}, reason={}",
                    code, com.apollo.elevator.common.util.LogMaskingUtil.mask("reason", cause.getMessage()));
            group.customer.setCustomerCode(code);
            return outcome(group, CustomerImportStatus.FAILED, null,
                    List.of("Could not be saved: " + cause.getMessage()));
        }
    }

    /** Merges the customer parsed from the file into the stored customer; returns whether anything changed. */
    private boolean mergeCustomer(
            CustomerGroup group,
            Customer target,
            List<String> errors,
            CustomerImportMode mode,
            boolean includesCompleted
    ) {
        Customer incoming = group.customer;
        boolean override = mode == CustomerImportMode.OVERRIDE;
        boolean changed = copyFields(incoming, target, CUSTOMER_FIELDS);

        Set<Object> matched = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ClientRepresentative representative : incoming.getClientRepresentatives()) {
            ClientRepresentative match = findMatch(target.getClientRepresentatives(), matched,
                    existing -> representative.getMobileNumber() != null
                            && representative.getMobileNumber().equals(existing.getMobileNumber()),
                    existing -> representative.getName() != null
                            && representative.getName().equalsIgnoreCase(existing.getName()));
            changed |= mergeOrAdd(representative, match, target.getClientRepresentatives(), REPRESENTATIVE_FIELDS,
                    matched);
        }
        if (override) {
            changed |= target.getClientRepresentatives().removeIf(existing -> !matched.contains(existing));
        }

        for (Lift lift : incoming.getLifts()) {
            RowSource source = group.sources.get(lift);
            Lift match = findMatch(target.getLifts(), matched,
                    existing -> sameId(existing.getId(), source.key()),
                    existing -> equalsIgnoreCase(lift.getSerialNumber(), existing.getSerialNumber()));
            if (match == null) {
                target.getLifts().add(lift);
                matched.add(lift);
                changed = true;
                continue;
            }
            changed |= copyFields(lift, match, LIFT_FIELDS);
            validateLift(match, source.row(), errors);
            changed |= mergeContracts(group, lift, match, matched, errors, override, includesCompleted);
        }
        return changed;
    }

    private boolean mergeContracts(
            CustomerGroup group,
            Lift incoming,
            Lift target,
            Set<Object> matched,
            List<String> errors,
            boolean override,
            boolean includesCompleted
    ) {
        boolean changed = false;
        for (AmcContract contract : incoming.getAmcContracts()) {
            RowSource source = group.sources.get(contract);
            AmcContract match = findMatch(target.getAmcContracts(), matched,
                    existing -> sameId(existing.getId(), source.key()),
                    existing -> equalsIgnoreCase(contract.getContractNumber(), existing.getContractNumber()));
            if (match == null) {
                target.getAmcContracts().add(contract);
                matched.add(contract);
                changed = true;
                continue;
            }
            changed |= copyFields(contract, match, AMC_FIELDS);
            validateContract(match, source.row(), errors);
            for (ServiceHistory history : contract.getServiceHistory()) {
                RowSource visitSource = group.sources.get(history);
                String visitKey = visitSource == null ? null : visitSource.key();
                ServiceHistory historyMatch = findMatch(match.getServiceHistory(), matched,
                        existing -> sameId(existing.getId(), visitKey),
                        existing -> visitKey == null
                                && Objects.equals(history.getServiceDate(), existing.getServiceDate())
                                && equalsIgnoreCase(history.getEngineerName(), existing.getEngineerName()));
                boolean engineerChanged = historyMatch != null
                        && !equalsIgnoreCase(history.getEngineerName(), historyMatch.getEngineerName());
                changed |= mergeOrAdd(history, historyMatch, match.getServiceHistory(), SERVICE_HISTORY_FIELDS,
                        matched);
                if (historyMatch != null && (history.getAssignedEngineerId() != null || engineerChanged)
                        && !Objects.equals(history.getAssignedEngineerId(), historyMatch.getAssignedEngineerId())) {
                    historyMatch.setAssignedEngineerId(history.getAssignedEngineerId());
                    changed = true;
                }
            }
            if (override) {
                // A file exported without completed visits must not delete the stored ones.
                changed |= match.getServiceHistory().removeIf(existing -> !matched.contains(existing)
                        && (includesCompleted || existing.getStatus() != ServiceVisitStatus.COMPLETED));
            }
        }
        return changed;
    }

    private <T> boolean mergeOrAdd(
            T incoming,
            T match,
            List<T> targetList,
            List<String> fields,
            Set<Object> matched
    ) {
        if (match == null) {
            targetList.add(incoming);
            matched.add(incoming);
            return true;
        }
        return copyFields(incoming, match, fields);
    }

    /** Returns the first not-yet-matched element satisfying the primary rule, else the secondary rule. */
    private <T> T findMatch(List<T> candidates, Set<Object> matched, Predicate<T> primary, Predicate<T> secondary) {
        for (Predicate<T> rule : List.of(primary, secondary)) {
            for (T candidate : candidates) {
                if (!matched.contains(candidate) && rule.test(candidate)) {
                    matched.add(candidate);
                    return candidate;
                }
            }
        }
        return null;
    }

    /** Copies the non-null file values onto the stored entity; returns whether any value changed. */
    private boolean copyFields(Object source, Object target, List<String> fields) {
        BeanWrapper from = new BeanWrapperImpl(source);
        BeanWrapper to = new BeanWrapperImpl(target);
        boolean changed = false;
        for (String field : fields) {
            Object value = from.getPropertyValue(field);
            if (value != null && !sameValue(to.getPropertyValue(field), value)) {
                to.setPropertyValue(field, value);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean sameValue(Object current, Object value) {
        if (current instanceof BigDecimal currentDecimal && value instanceof BigDecimal decimal) {
            return currentDecimal.compareTo(decimal) == 0;
        }
        return Objects.equals(current, value);
    }

    private static boolean sameId(Long id, String key) {
        if (id == null || key == null) {
            return false;
        }
        try {
            return new BigDecimal(key.trim()).compareTo(BigDecimal.valueOf(id)) == 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean equalsIgnoreCase(String first, String second) {
        return first != null && second != null && first.trim().equalsIgnoreCase(second.trim());
    }

    private CustomerImportOutcome outcome(
            CustomerGroup group,
            CustomerImportStatus status,
            Long customerId,
            List<String> messages
    ) {
        return new CustomerImportOutcome(
                group.key,
                group.customer.getCustomerCode(),
                status,
                customerId,
                group.customer.getLifts().size(),
                group.amcContractCount,
                List.copyOf(messages)
        );
    }

    private void applyFields(Object target, List<String> fields, RowData row, List<String> errors) {
        BeanWrapper wrapper = new BeanWrapperImpl(target);
        for (String field : fields) {
            String raw = row.value(field);
            if (raw == null) {
                continue;
            }
            try {
                Object value = CustomerWorkbookValues.convert(raw, wrapper.getPropertyType(field));
                if (value instanceof String text) {
                    int maxLength = maxLength(target.getClass(), field);
                    if (text.length() > maxLength) {
                        throw new IllegalArgumentException("must not exceed " + maxLength + " characters");
                    }
                }
                wrapper.setPropertyValue(field, value);
            } catch (IllegalArgumentException exception) {
                errors.add(row.describe() + ": " + field + " " + exception.getMessage());
            }
        }
    }

    private int maxLength(Class<?> type, String fieldName) {
        try {
            Field field = type.getDeclaredField(fieldName);
            Column column = field.getAnnotation(Column.class);
            return column == null ? 255 : column.length();
        } catch (NoSuchFieldException exception) {
            return 255;
        }
    }

    private List<RowData> readSheet(
            Workbook workbook,
            String sheetName,
            List<String> requiredColumns,
            boolean sheetRequired,
            FormulaEvaluator evaluator
    ) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) {
            if (sheetRequired) {
                throw new IllegalArgumentException("The workbook must contain a '" + sheetName + "' sheet");
            }
            return List.of();
        }

        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            if (sheetRequired) {
                throw new IllegalArgumentException("The '" + sheetName + "' sheet has no header row");
            }
            return List.of();
        }

        DataFormatter formatter = new DataFormatter();
        Map<String, Integer> columns = new HashMap<>();
        for (int i = headerRow.getFirstCellNum(); i >= 0 && i < headerRow.getLastCellNum(); i++) {
            String header = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (!header.isEmpty()) {
                columns.putIfAbsent(header.toLowerCase(Locale.ROOT), i);
            }
        }
        for (String required : requiredColumns) {
            if (!columns.containsKey(required.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(
                        "The '" + sheetName + "' sheet is missing the required column '" + required + "'");
            }
        }

        List<RowData> rows = new ArrayList<>();
        for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, String> values = new HashMap<>();
            for (Map.Entry<String, Integer> column : columns.entrySet()) {
                String value = CustomerWorkbookValues.readCell(row.getCell(column.getValue()), evaluator);
                if (value != null) {
                    values.put(column.getKey(), value);
                }
            }
            if (!values.isEmpty()) {
                rows.add(new RowData(sheetName, r + 1, values));
            }
        }
        return rows;
    }

    private static boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private static int countByStatus(List<CustomerImportOutcome> outcomes, CustomerImportStatus status) {
        return (int) outcomes.stream().filter(outcome -> outcome.status() == status).count();
    }

    record ParsedWorkbook(
            Map<String, CustomerGroup> customers,
            List<String> unlinkedRowErrors,
            boolean includesCompletedVisits
    ) {
    }

    record RowData(String sheet, int rowNumber, Map<String, String> values) {

        String value(String column) {
            return values.get(column.toLowerCase(Locale.ROOT));
        }

        String describe() {
            return sheet + " row " + rowNumber;
        }
    }

    static final class CustomerGroup {
        final String key;
        final RowData row;
        final Customer customer;
        final List<String> errors = new ArrayList<>();
        final Map<Object, RowSource> sources = new IdentityHashMap<>();
        int amcContractCount;

        CustomerGroup(String key, RowData row, Customer customer) {
            this.key = key;
            this.row = row;
            this.customer = customer;
        }
    }

    /** The link key and source row of a lift, AMC contract or service visit parsed from the file. */
    private record RowSource(String key, RowData row) {
    }

    private record LiftRef(CustomerGroup group, Lift lift) {
    }

    private record AmcRef(CustomerGroup group, AmcContract contract) {
    }
}
