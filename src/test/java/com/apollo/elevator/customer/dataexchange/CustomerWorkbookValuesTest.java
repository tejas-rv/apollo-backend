package com.apollo.elevator.customer.dataexchange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CustomerWorkbookValuesTest {

    enum Sample { ACTIVE, EXPIRED }

    @Test
    void parsesDashAndSlashDates() {
        assertEquals(LocalDate.of(2025, 4, 1), CustomerWorkbookValues.parseDate("01-04-2025"));
        assertEquals(LocalDate.of(2025, 4, 1), CustomerWorkbookValues.parseDate("01/04/2025"));
        assertEquals(LocalDate.of(2025, 4, 1), CustomerWorkbookValues.parseDate("1-4-2025"));
    }

    @Test
    void rejectsInvalidDate() {
        assertThrows(IllegalArgumentException.class, () -> CustomerWorkbookValues.parseDate("31-02-2025"));
        assertThrows(IllegalArgumentException.class, () -> CustomerWorkbookValues.parseDate("not-a-date"));
    }

    @Test
    void convertsScalarTypes() {
        assertEquals(12, CustomerWorkbookValues.convert("12", Integer.class));
        assertEquals(new BigDecimal("1500.50"), CustomerWorkbookValues.convert("1500.50", BigDecimal.class));
        assertEquals(Boolean.TRUE, CustomerWorkbookValues.convert("yes", Boolean.class));
        assertEquals(Boolean.FALSE, CustomerWorkbookValues.convert("FALSE", Boolean.class));
        assertEquals(Sample.ACTIVE, CustomerWorkbookValues.convert("active", Sample.class));
        assertNull(CustomerWorkbookValues.convert("", Integer.class));
    }

    @Test
    void rejectsUnknownEnumWithAllowedValues() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> CustomerWorkbookValues.convert("PAUSED", Sample.class));
        assertTrue(error.getMessage().contains("ACTIVE"));
    }
}
