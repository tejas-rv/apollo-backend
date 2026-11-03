package com.apollo.elevator.customer.dataexchange.dto;

import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;

public record CustomerExportResponse(
        String fileName,
        int customerCount,
        DriveFileMetadata driveFile
) {
}
