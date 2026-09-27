package com.apollo.elevator.googledrive.model.dto;

import java.time.Instant;

public record DriveFileMetadata(
        String id,
        String name,
        String mimeType,
        Long size,
        Instant createdTime,
        Instant modifiedTime
) {}
