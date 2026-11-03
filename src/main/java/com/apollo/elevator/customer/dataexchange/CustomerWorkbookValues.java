package com.apollo.elevator.customer.dataexchange;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;

/**
 * Converts workbook cells to normalized text and text to typed entity values.
 */
public final class CustomerWorkbookValues {

    private static final DateTimeFormatter DAY_MONTH_YEAR =
            DateTimeFormatter.ofPattern("d-M-uuuu").withResolverStyle(ResolverStyle.STRICT);

    private CustomerWorkbookValues() {
    }

    /**
     * Returns the trimmed text of a cell, or {@code null} when the cell is blank. Numeric cells
     * are rendered without scientific notation or trailing zeros, and date-formatted cells as
     * {@code dd-MM-yyyy}.
     */
    public static String readCell(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = evaluator == null ? cell.getCachedFormulaResultType() : evaluator.evaluateFormulaCell(cell);
        }
        String text = switch (type) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? CustomerWorkbookSchema.DATE_FORMAT.format(cell.getLocalDateTimeCellValue().toLocalDate())
                    : BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> cell.getBooleanCellValue() ? "TRUE" : "FALSE";
            case STRING -> cell.getStringCellValue();
            default -> null;
        };
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * Converts non-blank text to the given property type.
     *
     * @throws IllegalArgumentException with a user-facing message when the value is invalid
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object convert(String raw, Class<?> type) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (type == String.class) {
            return value;
        }
        if (type == Integer.class || type == int.class) {
            return parseInteger(value);
        }
        if (type == BigDecimal.class) {
            return parseDecimal(value);
        }
        if (type == Boolean.class || type == boolean.class) {
            return parseBoolean(value);
        }
        if (type == LocalDate.class) {
            return parseDate(value);
        }
        if (type.isEnum()) {
            return parseEnum(value, (Class<? extends Enum>) type);
        }
        throw new IllegalArgumentException("has an unsupported type " + type.getSimpleName());
    }

    public static LocalDate parseDate(String value) {
        String normalized = value.trim().replace('/', '-').replace('.', '-');
        try {
            return LocalDate.parse(normalized, DAY_MONTH_YEAR);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(normalized);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(
                        "must be a valid date in DD-MM-YYYY or DD/MM/YYYY format but was '" + value + "'");
            }
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return parseDecimal(value).intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("must be a whole number but was '" + value + "'");
        }
    }

    private static BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("must be a number but was '" + value + "'");
        }
    }

    private static Boolean parseBoolean(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "TRUE", "YES", "Y", "1" -> Boolean.TRUE;
            case "FALSE", "NO", "N", "0" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("must be TRUE or FALSE but was '" + value + "'");
        };
    }

    private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
        String normalized = value.toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        try {
            return Enum.valueOf(type, normalized);
        } catch (IllegalArgumentException exception) {
            String allowed = Arrays.stream(type.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "must be one of [" + allowed + "] but was '" + value + "'");
        }
    }
}
