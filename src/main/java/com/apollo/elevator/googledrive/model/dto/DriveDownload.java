package com.apollo.elevator.googledrive.model.dto;

import java.io.InputStream;

public record DriveDownload(
        DriveFileMetadata metadata,
        InputStream content
) {}
