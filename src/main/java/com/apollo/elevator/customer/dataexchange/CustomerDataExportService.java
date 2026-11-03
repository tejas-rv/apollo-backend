package com.apollo.elevator.customer.dataexchange;

import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.AMC_COLUMNS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.AMC_CONTRACTS_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.AMC_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.CUSTOMERS_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.CUSTOMER_COLUMNS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.CUSTOMER_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.LIFTS_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.LIFT_COLUMNS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.LIFT_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.REPRESENTATIVES_SHEET;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.REPRESENTATIVE_COLUMNS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.REPRESENTATIVE_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.SERVICE_HISTORY_COLUMNS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.SERVICE_HISTORY_FIELDS;
import static com.apollo.elevator.customer.dataexchange.CustomerWorkbookSchema.SERVICE_HISTORY_SHEET;

import com.apollo.elevator.customer.model.entity.AmcContract;
import com.apollo.elevator.customer.model.entity.ClientRepresentative;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.model.entity.ServiceHistory;
import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.customer.repository.CustomerRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exports all customers into the customer data workbook, using the same layout the importer
 * reads. Database IDs are written as the customerId/liftId/amcId link keys and dates as
 * {@code DD-MM-YYYY} text.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerDataExportService {

    private final CustomerRepository customerRepository;

    public record ExportedWorkbook(byte[] content, int customerCount) {
    }

    @Transactional(readOnly = true)
    public ExportedWorkbook exportWorkbook() {
        return exportWorkbook(true);
    }

    /**
     * @param includeCompleted when false, COMPLETED service visits are left out of the
     *                         Service History sheet (scheduled and cancelled visits are always kept).
     */
    @Transactional(readOnly = true)
    public ExportedWorkbook exportWorkbook(boolean includeCompleted) {
        List<Customer> customers = customerRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getProperties().getCustomProperties()
                    .addProperty(CustomerWorkbookSchema.INCLUDES_COMPLETED_PROPERTY, includeCompleted);
            CellStyle headerStyle = headerStyle(workbook);
            SheetWriter customerSheet = new SheetWriter(workbook, CUSTOMERS_SHEET, CUSTOMER_COLUMNS, headerStyle);
            SheetWriter representativeSheet =
                    new SheetWriter(workbook, REPRESENTATIVES_SHEET, REPRESENTATIVE_COLUMNS, headerStyle);
            SheetWriter liftSheet = new SheetWriter(workbook, LIFTS_SHEET, LIFT_COLUMNS, headerStyle);
            SheetWriter amcSheet = new SheetWriter(workbook, AMC_CONTRACTS_SHEET, AMC_COLUMNS, headerStyle);
            SheetWriter historySheet =
                    new SheetWriter(workbook, SERVICE_HISTORY_SHEET, SERVICE_HISTORY_COLUMNS, headerStyle);

            for (Customer customer : customers) {
                customerSheet.write(List.of(customer.getId()), customer, CUSTOMER_FIELDS);
                for (ClientRepresentative representative : customer.getClientRepresentatives()) {
                    representativeSheet.write(List.of(customer.getId()), representative, REPRESENTATIVE_FIELDS);
                }
                for (Lift lift : customer.getLifts()) {
                    liftSheet.write(List.of(lift.getId(), customer.getId()), lift, LIFT_FIELDS);
                    for (AmcContract contract : lift.getAmcContracts()) {
                        amcSheet.write(List.of(contract.getId(), customer.getId(), lift.getId()), contract, AMC_FIELDS);
                        for (ServiceHistory history : contract.getServiceHistory()) {
                            if (!includeCompleted && history.getStatus() == ServiceVisitStatus.COMPLETED) {
                                continue;
                            }
                            historySheet.write(Arrays.asList(history.getId(), contract.getId()), history,
                                    SERVICE_HISTORY_FIELDS);
                        }
                    }
                }
            }

            workbook.write(output);
            log.info("Customer data export generated. customerCount={}, sizeBytes={}", customers.size(), output.size());
            return new ExportedWorkbook(output.toByteArray(), customers.size());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to generate the customer data workbook", exception);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static final class SheetWriter {
        private final Sheet sheet;
        private int nextRow = 1;

        SheetWriter(Workbook workbook, String name, List<String> columns, CellStyle headerStyle) {
            sheet = workbook.createSheet(name);
            Row header = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, Math.max(14, columns.get(i).length() + 4) * 256);
            }
            sheet.createFreezePane(0, 1);
        }

        void write(List<Long> keys, Object bean, List<String> fields) {
            List<Object> values = new ArrayList<>(keys);
            BeanWrapper wrapper = new BeanWrapperImpl(bean);
            for (String field : fields) {
                values.add(wrapper.getPropertyValue(field));
            }

            Row row = sheet.createRow(nextRow++);
            for (int i = 0; i < values.size(); i++) {
                setValue(row, i, values.get(i));
            }
        }

        private void setValue(Row row, int column, Object value) {
            if (value == null) {
                return;
            }
            Cell cell = row.createCell(column);
            switch (value) {
                case BigDecimal decimal -> cell.setCellValue(decimal.doubleValue());
                case Number number -> cell.setCellValue(number.doubleValue());
                case Boolean bool -> cell.setCellValue(bool);
                case LocalDate date -> cell.setCellValue(CustomerWorkbookSchema.DATE_FORMAT.format(date));
                case Enum<?> enumValue -> cell.setCellValue(enumValue.name());
                default -> cell.setCellValue(value.toString());
            }
        }
    }
}
