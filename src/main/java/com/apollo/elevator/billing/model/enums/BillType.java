package com.apollo.elevator.billing.model.enums;

public enum BillType {
    /** First (or only) instalment of an AMC year, billed at the plan (tier) price. */
    AMC,
    /** Subsequent recurring instalment of an AMC year. */
    AMC_INSTALMENT,
    /** Free-form maintenance / repair work. */
    MAINTENANCE
}
