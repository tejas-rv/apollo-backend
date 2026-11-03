package com.apollo.elevator.customer.dataexchange.dto;

import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import java.util.List;

/**
 * Summary of a customer data import.
 *
 * @param unlinkedRowErrors errors for rows that could not be linked to any customer in the file
 *                          (for example a lift row whose customerId does not exist)
 * @param backupFile        the Google Drive backup taken before an OVERRIDE import, otherwise null
 */
public record CustomerImportResponse(
        String fileName,
        CustomerDataSource source,
        CustomerImportMode mode,
        int totalCustomers,
        int importedCount,
        int updatedCount,
        int skippedCount,
        int failedCount,
        List<CustomerImportOutcome> customers,
        List<String> unlinkedRowErrors,
        DriveFileMetadata backupFile
) {

    public CustomerImportResponse withBackup(DriveFileMetadata backup) {
        return new CustomerImportResponse(fileName, source, mode, totalCustomers, importedCount, updatedCount,
                skippedCount, failedCount, customers, unlinkedRowErrors, backup);
    }
}
