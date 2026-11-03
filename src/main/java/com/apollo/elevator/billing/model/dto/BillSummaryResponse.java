package com.apollo.elevator.billing.model.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Billing figures for a date range. All amounts include GST.
 *
 * @param billed               total of bills whose invoice date is in range
 * @param paid                 money received ("in bank"): bills whose paid date is in range
 * @param outstanding          billed in range but not yet paid
 * @param projectedInstalments AMC instalments not yet billed for AMC years that have started billing
 * @param projected            outstanding + projectedInstalments
 */
public record BillSummaryResponse(
        BigDecimal billed,
        BigDecimal paid,
        BigDecimal outstanding,
        BigDecimal projectedInstalments,
        BigDecimal projected,
        long billCount,
        long paidCount,
        List<MonthPoint> monthly
) {
    public record MonthPoint(String month, BigDecimal billed, BigDecimal paid) {}
}
