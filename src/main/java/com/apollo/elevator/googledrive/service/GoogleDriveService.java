package com.apollo.elevator.googledrive.service;

import com.apollo.elevator.googledrive.model.DriveDocumentCategory;
import com.apollo.elevator.googledrive.model.dto.DriveDownload;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

public interface GoogleDriveService {

    DriveFileMetadata uploadFile(
            Long customerId,
            DriveDocumentCategory category,
            LocalDate documentDate,
            MultipartFile file
    );

    /**
     * Uploads an in-memory generated file (e.g. a system-generated PDF) into the configured
     * customer/category Drive folder, without requiring a {@link MultipartFile} wrapper.
     */
    DriveFileMetadata uploadGeneratedFile(
            Long customerId,
            DriveDocumentCategory category,
            LocalDate documentDate,
            String fileName,
            String contentType,
            byte[] content
    );

    DriveDownload downloadFile(String fileId);

    List<DriveFileMetadata> listFiles();

    List<DriveFileMetadata> listCustomerFiles(Long customerId, DriveDocumentCategory category);

    DriveFileMetadata getFileMetadata(String fileId);

    void deleteFile(String fileId);

    DriveFileMetadata createSubfolder(String parentFolderId, String folderName);

    /**
     * Uploads a lift photo into the customer's "Lift Images" folder. The stored filename is
     * prefixed with the lift ID (e.g. {@code Lift_7_front-view.jpg}) so multiple photos per
     * lift can coexist, and files can be grouped/filtered by lift.
     */
    DriveFileMetadata uploadLiftImage(Long customerId, Long liftId, MultipartFile file);

    /**
     * Lists only the images belonging to the given lift within the customer's "Lift Images"
     * folder (filtered by the {@code Lift_<liftId>_} filename prefix).
     */
    List<DriveFileMetadata> listLiftImages(Long customerId, Long liftId);

    /**
     * Finds a (non-folder) file directly inside the configured root Drive folder by exact name.
     */
    Optional<DriveFileMetadata> findFileInRootFolder(String fileName);

    /**
     * Uploads a file directly into the configured root Drive folder, overwriting an existing
     * file with the same name in place (preserving its file ID).
     */
    DriveFileMetadata uploadFileToRootFolder(String fileName, String contentType, byte[] content);
}
