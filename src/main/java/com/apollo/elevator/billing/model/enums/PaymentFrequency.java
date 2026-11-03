package com.apollo.elevator.billing.model.enums;

import java.util.Locale;

/** How the yearly AMC amount is split into instalments. */
public enum PaymentFrequency {
    YEARLY(1, 12, "Yearly"),
    HALF_YEARLY(2, 6, "Half-yearly"),
    QUARTERLY(4, 3, "Quarterly"),
    MONTHLY(12, 1, "Monthly");

    private final int instalments;
    private final int monthsPerInstalment;
    private final String label;

    PaymentFrequency(int instalments, int monthsPerInstalment, String label) {
        this.instalments = instalments;
        this.monthsPerInstalment = monthsPerInstalment;
        this.label = label;
    }

    public int instalments() {
        return instalments;
    }

    public int monthsPerInstalment() {
        return monthsPerInstalment;
    }

    public String label() {
        return label;
    }

    /** Lenient parse of values stored on AMC contracts (e.g. "ANNUAL", "Half-yearly"); null when unknown. */
    public static PaymentFrequency parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (v) {
            case "YEARLY", "ANNUAL", "ANNUALLY", "YEAR" -> YEARLY;
            case "HALF_YEARLY", "HALFYEARLY", "SEMI_ANNUAL", "SEMI_ANNUALLY" -> HALF_YEARLY;
            case "QUARTERLY", "QUARTER" -> QUARTERLY;
            case "MONTHLY", "MONTH" -> MONTHLY;
            default -> null;
        };
    }
}
