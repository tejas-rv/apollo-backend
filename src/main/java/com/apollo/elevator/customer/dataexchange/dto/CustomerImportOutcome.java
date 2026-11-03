package com.apollo.elevator.customer.dataexchange.dto;

import java.util.List;

/**
 * Result for one customer (one row of the {@code customers} sheet, together with its linked
 * representative, lift, AMC contract and service history rows).
 *
 * @param customerKey  the {@code customerId} link key used in the file
 * @param customerCode the customer code from the file
 * @param status       IMPORTED (new customer), UPDATED (existing customer code, changes applied),
 *                     SKIPPED (existing customer code, nothing changed) or FAILED (validation/save error)
 * @param customerId   the database ID of the imported/updated customer, otherwise {@code null}
 * @param messages     validation errors or the skip reason
 */
public record CustomerImportOutcome(
        String customerKey,
        String customerCode,
        CustomerImportStatus status,
        Long customerId,
        int liftCount,
        int amcContractCount,
        List<String> messages
) {
}
