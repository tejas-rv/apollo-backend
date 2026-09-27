package com.apollo.elevator.googledrive.model.dto;

import java.util.List;

/**
 * Result of generating a customer's AMC contract, bill, sample work order, sample quotation,
 * and service-history PDFs and uploading each to its category folder in Google Drive.
 *
 * <p>{@code contract} and {@code bill} are {@code null} when the customer has no
 * corresponding data to generate (e.g. no active AMC contract). {@code workOrder} and
 * {@code quotation} are placeholder/sample documents (no dedicated generation module exists
 * yet) and are always generated. {@code serviceHistory} contains one entry per past service
 * visit found for the customer and may be empty.</p>
 */
public record CustomerDriveGenerationResponse(
        Long customerId,
        DriveFileMetadata contract,
        DriveFileMetadata bill,
        DriveFileMetadata workOrder,
        DriveFileMetadata quotation,
        List<DriveFileMetadata> serviceHistory
) {}
