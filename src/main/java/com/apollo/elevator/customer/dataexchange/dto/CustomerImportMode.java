package com.apollo.elevator.customer.dataexchange.dto;

/** How an import treats customers whose customerCode already exists. */
public enum CustomerImportMode {
    /** Update matched rows and add new ones; nothing is deleted. */
    MERGE,
    /**
     * The file is the source of truth for each customer it contains: representatives and the
     * service visits of each AMC contract in the file are replaced by the file rows. Lifts and AMC
     * contracts are never deleted because service reports and bills reference them. A backup of
     * the current data is saved to Google Drive first.
     */
    OVERRIDE
}
