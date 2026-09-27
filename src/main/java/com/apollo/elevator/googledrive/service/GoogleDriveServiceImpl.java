package com.apollo.elevator.googledrive.service;

import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.customer.model.entity.Customer;
import com.apollo.elevator.customer.model.entity.Lift;
import com.apollo.elevator.customer.repository.CustomerRepository;
import com.apollo.elevator.googledrive.config.GoogleDriveProperties;
import com.apollo.elevator.googledrive.exception.GoogleDriveAuthenticationException;
import com.apollo.elevator.googledrive.exception.GoogleDriveOperationException;
import com.apollo.elevator.googledrive.exception.GoogleDrivePermissionException;
import com.apollo.elevator.googledrive.model.DriveDocumentCategory;
import com.apollo.elevator.googledrive.model.dto.DriveDownload;
import com.apollo.elevator.googledrive.model.dto.DriveFileMetadata;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "google.drive", name = "enabled", havingValue = "true")
public class GoogleDriveServiceImpl implements GoogleDriveService {

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final String FILE_FIELDS = "id,name,mimeType,size,createdTime,modifiedTime,parents,trashed";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH);

    private final Drive drive;
    private final GoogleDriveProperties properties;
    private final CustomerRepository customerRepository;

    @Override
    public DriveFileMetadata uploadFile(
            Long customerId,
            DriveDocumentCategory category,
            LocalDate documentDate,
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty file is required");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return uploadContent(
                    customerId,
                    category,
                    documentDate,
                    file.getOriginalFilename(),
                    resolveContentType(file),
                    inputStream,
                    file.getSize()
            );
        } catch (IOException exception) {
            throw translateException("upload file", exception);
        }
    }

    @Override
    public DriveFileMetadata uploadGeneratedFile(
            Long customerId,
            DriveDocumentCategory category,
            LocalDate documentDate,
            String fileName,
            String contentType,
            byte[] content
    ) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Generated file content must not be empty");
        }

        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            return uploadContent(
                    customerId,
                    category,
                    documentDate,
                    fileName,
                    StringUtils.hasText(contentType) ? contentType : "application/octet-stream",
                    inputStream,
                    content.length
            );
        } catch (IOException exception) {
            throw translateException("upload file", exception);
        }
    }

    private DriveFileMetadata uploadContent(
            Long customerId,
            DriveDocumentCategory category,
            LocalDate documentDate,
            String originalFilename,
            String contentType,
            InputStream inputStream,
            long length
    ) throws IOException {
        if (category == null) {
            throw new IllegalArgumentException("Document category is required");
        }

        Customer customer = findCustomer(customerId);
        String customerFolderId = findOrCreateFolder(
                properties.getFolderId(),
                customerFolderName(customer)
        ).getId();
        String categoryFolderId = findOrCreateFolder(customerFolderId, category.getFolderName()).getId();
        String storedFileName = buildStoredFileName(originalFilename, category, documentDate);

        return uploadOrOverwrite(customerId, category, categoryFolderId, storedFileName, contentType, inputStream, length);
    }

    /**
     * Uploads content into an already-resolved Drive folder, overwriting an existing file with
     * the same name in place (preserving its file ID) or creating a new file if none exists.
     * Shared by document uploads ({@link #uploadContent}) and lift image uploads.
     */
    private DriveFileMetadata uploadOrOverwrite(
            Long customerId,
            DriveDocumentCategory category,
            String parentFolderId,
            String storedFileName,
            String contentType,
            InputStream inputStream,
            long length
    ) throws IOException {
        InputStreamContent content = new InputStreamContent(contentType, inputStream);
        content.setLength(length);

        Optional<com.google.api.services.drive.model.File> existing =
                findFileInFolder(parentFolderId, storedFileName);

        com.google.api.services.drive.model.File uploaded;
        if (existing.isPresent()) {
            com.google.api.services.drive.model.File updateMetadata =
                    new com.google.api.services.drive.model.File();
            uploaded = drive.files()
                    .update(existing.get().getId(), updateMetadata, content)
                    .setSupportsAllDrives(true)
                    .setFields(FILE_FIELDS)
                    .execute();
            log.info(
                    "Google Drive file overwritten. fileId={}, customerId={}, category={}, sizeBytes={}",
                    uploaded.getId(), customerId, category, uploaded.getSize()
            );
            return toMetadata(uploaded);
        }

        com.google.api.services.drive.model.File metadata =
                new com.google.api.services.drive.model.File()
                        .setName(storedFileName)
                        .setParents(List.of(parentFolderId));

        uploaded = drive.files()
                .create(metadata, content)
                .setSupportsAllDrives(true)
                .setFields(FILE_FIELDS)
                .execute();
        log.info(
                "Google Drive file uploaded. fileId={}, customerId={}, category={}, sizeBytes={}",
                uploaded.getId(), customerId, category, uploaded.getSize()
        );
        return toMetadata(uploaded);
    }

    @Override
    public DriveDownload downloadFile(String fileId) {
        com.google.api.services.drive.model.File file = getAuthorizedFile(fileId);
        if (FOLDER_MIME_TYPE.equals(file.getMimeType())) {
            throw new IllegalArgumentException("Google Drive folders cannot be downloaded as files");
        }

        try {
            InputStream content = drive.files()
                    .get(fileId)
                    .setSupportsAllDrives(true)
                    .executeMediaAsInputStream();
            return new DriveDownload(toMetadata(file), content);
        } catch (IOException exception) {
            throw translateException("download file", exception);
        }
    }

    @Override
    public List<DriveFileMetadata> listFiles() {
        return listChildren(properties.getFolderId()).stream()
                .map(this::toMetadata)
                .toList();
    }

    @Override
    public List<DriveFileMetadata> listCustomerFiles(
            Long customerId,
            DriveDocumentCategory category
    ) {
        if (category == null) {
            throw new IllegalArgumentException("Document category is required when listing customer files");
        }

        Customer customer = findCustomer(customerId);
        Optional<com.google.api.services.drive.model.File> customerFolder =
                findFolder(properties.getFolderId(), customerFolderName(customer));
        if (customerFolder.isEmpty()) {
            return List.of();
        }

        Optional<com.google.api.services.drive.model.File> categoryFolder =
                findFolder(customerFolder.get().getId(), category.getFolderName());
        if (categoryFolder.isEmpty()) {
            return List.of();
        }

        return listChildren(categoryFolder.get().getId()).stream()
                .filter(file -> !FOLDER_MIME_TYPE.equals(file.getMimeType()))
                .map(this::toMetadata)
                .toList();
    }

    @Override
    public DriveFileMetadata getFileMetadata(String fileId) {
        return toMetadata(getAuthorizedFile(fileId));
    }

    @Override
    public void deleteFile(String fileId) {
        validateFileId(fileId);
        if (properties.getFolderId().equals(fileId)) {
            throw new IllegalArgumentException("The configured Google Drive root folder cannot be deleted");
        }
        getAuthorizedFile(fileId);

        try {
            drive.files()
                    .delete(fileId)
                    .setSupportsAllDrives(true)
                    .execute();
            log.info("Google Drive file deleted. fileId={}", fileId);
        } catch (IOException exception) {
            throw translateException("delete file", exception);
        }
    }

    @Override
    public DriveFileMetadata createSubfolder(String parentFolderId, String folderName) {
        validateFileId(parentFolderId);
        if (!StringUtils.hasText(folderName)) {
            throw new IllegalArgumentException("Folder name is required");
        }

        com.google.api.services.drive.model.File parent = getAuthorizedFile(parentFolderId);
        if (!FOLDER_MIME_TYPE.equals(parent.getMimeType())) {
            throw new IllegalArgumentException("Parent ID must reference a Google Drive folder");
        }
        return toMetadata(findOrCreateFolder(parentFolderId, sanitizeFolderName(folderName)));
    }

    @Override
    public DriveFileMetadata uploadLiftImage(Long customerId, Long liftId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A non-empty image file is required");
        }
        if (!isImageContentType(file)) {
            throw new IllegalArgumentException("Only image files (JPEG/PNG/WEBP/HEIC) can be uploaded as lift photos");
        }

        Customer customer = findCustomer(customerId);
        findLift(customer, liftId);

        String customerFolderId = findOrCreateFolder(
                properties.getFolderId(),
                customerFolderName(customer)
        ).getId();
        String imagesFolderId = findOrCreateFolder(
                customerFolderId,
                DriveDocumentCategory.LIFT_IMAGES.getFolderName()
        ).getId();
        String storedFileName = buildLiftImageFileName(liftId, file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            return uploadOrOverwrite(
                    customerId,
                    DriveDocumentCategory.LIFT_IMAGES,
                    imagesFolderId,
                    storedFileName,
                    resolveContentType(file),
                    inputStream,
                    file.getSize()
            );
        } catch (IOException exception) {
            throw translateException("upload lift image", exception);
        }
    }

    @Override
    public List<DriveFileMetadata> listLiftImages(Long customerId, Long liftId) {
        Customer customer = findCustomer(customerId);
        findLift(customer, liftId);

        Optional<com.google.api.services.drive.model.File> customerFolder =
                findFolder(properties.getFolderId(), customerFolderName(customer));
        if (customerFolder.isEmpty()) {
            return List.of();
        }
        Optional<com.google.api.services.drive.model.File> imagesFolder =
                findFolder(customerFolder.get().getId(), DriveDocumentCategory.LIFT_IMAGES.getFolderName());
        if (imagesFolder.isEmpty()) {
            return List.of();
        }

        String prefix = liftImageFilePrefix(liftId);
        return listChildren(imagesFolder.get().getId()).stream()
                .filter(f -> !FOLDER_MIME_TYPE.equals(f.getMimeType()))
                .filter(f -> f.getName() != null && f.getName().startsWith(prefix))
                .map(this::toMetadata)
                .toList();
    }

    private Lift findLift(Customer customer, Long liftId) {
        if (liftId == null) {
            throw new IllegalArgumentException("Lift ID is required");
        }
        return (customer.getLifts() == null ? List.<Lift>of() : customer.getLifts()).stream()
                .filter(lift -> liftId.equals(lift.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Lift not found with id: " + liftId + " for customer id: " + customer.getId()));
    }

    private String liftImageFilePrefix(Long liftId) {
        return "Lift_" + liftId + "_";
    }

    private String buildLiftImageFileName(Long liftId, String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("The uploaded image must have a filename");
        }
        String cleanName = Paths.get(originalFilename).getFileName().toString();
        return liftImageFilePrefix(liftId) + sanitizeFolderName(cleanName);
    }

    private boolean isImageContentType(MultipartFile file) {
        String contentType = file.getContentType();
        return StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
    }

    private Customer findCustomer(Long customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required");
        }
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Customer not found with id: " + customerId));
    }

    private String customerFolderName(Customer customer) {
        return customer.getId() + "_" + sanitizeFolderName(customer.getCustomerName());
    }

    private synchronized com.google.api.services.drive.model.File findOrCreateFolder(
            String parentFolderId,
            String folderName
    ) {
        return findFolder(parentFolderId, folderName).orElseGet(() -> {
            com.google.api.services.drive.model.File metadata =
                    new com.google.api.services.drive.model.File()
                            .setName(folderName)
                            .setMimeType(FOLDER_MIME_TYPE)
                            .setParents(List.of(parentFolderId));
            try {
                com.google.api.services.drive.model.File folder = drive.files()
                        .create(metadata)
                        .setSupportsAllDrives(true)
                        .setFields(FILE_FIELDS)
                        .execute();
                log.info("Google Drive folder created. folderId={}, folderName={}",
                        folder.getId(), folderName);
                return folder;
            } catch (IOException exception) {
                throw translateException("create folder", exception);
            }
        });
    }

    private Optional<com.google.api.services.drive.model.File> findFolder(
            String parentFolderId,
            String folderName
    ) {
        String query = "'" + escapeQueryValue(parentFolderId) + "' in parents"
                + " and name = '" + escapeQueryValue(folderName) + "'"
                + " and mimeType = '" + FOLDER_MIME_TYPE + "'"
                + " and trashed = false";
        List<com.google.api.services.drive.model.File> matches = executeList(query, 2);
        if (matches.size() > 1) {
            log.warn("Multiple Google Drive folders matched parent and name. Using folderId={}",
                    matches.get(0).getId());
        }
        return matches.stream().findFirst();
    }

    private Optional<com.google.api.services.drive.model.File> findFileInFolder(
            String parentFolderId,
            String fileName
    ) {
        String query = "'" + escapeQueryValue(parentFolderId) + "' in parents"
                + " and name = '" + escapeQueryValue(fileName) + "'"
                + " and mimeType != '" + FOLDER_MIME_TYPE + "'"
                + " and trashed = false";
        List<com.google.api.services.drive.model.File> matches = executeList(query, 2);
        if (matches.size() > 1) {
            log.warn("Multiple Google Drive files matched parent and name. Overwriting fileId={}",
                    matches.get(0).getId());
        }
        return matches.stream().findFirst();
    }

    private List<com.google.api.services.drive.model.File> listChildren(String parentFolderId) {
        String query = "'" + escapeQueryValue(parentFolderId) + "' in parents and trashed = false";
        return executeList(query, 1000);
    }

    private List<com.google.api.services.drive.model.File> executeList(String query, int pageSize) {
        List<com.google.api.services.drive.model.File> files = new ArrayList<>();
        String pageToken = null;
        try {
            do {
                FileList page = drive.files()
                        .list()
                        .setQ(query)
                        .setSpaces("drive")
                        .setCorpora("allDrives")
                        .setIncludeItemsFromAllDrives(true)
                        .setSupportsAllDrives(true)
                        .setPageSize(pageSize)
                        .setPageToken(pageToken)
                        .setFields("nextPageToken,files(" + FILE_FIELDS + ")")
                        .execute();
                files.addAll(page.getFiles() == null ? List.of() : page.getFiles());
                pageToken = page.getNextPageToken();
            } while (pageToken != null);
            return files;
        } catch (IOException exception) {
            throw translateException("list files", exception);
        }
    }

    private com.google.api.services.drive.model.File getAuthorizedFile(String fileId) {
        validateFileId(fileId);
        com.google.api.services.drive.model.File file = getRawFile(fileId);
        if (!belongsToConfiguredFolder(file)) {
            throw new GoogleDrivePermissionException(
                    "The requested Google Drive item is outside the configured folder", null);
        }
        return file;
    }

    private com.google.api.services.drive.model.File getRawFile(String fileId) {
        try {
            return drive.files()
                    .get(fileId)
                    .setSupportsAllDrives(true)
                    .setFields(FILE_FIELDS)
                    .execute();
        } catch (IOException exception) {
            throw translateException("get file metadata", exception);
        }
    }

    private boolean belongsToConfiguredFolder(com.google.api.services.drive.model.File file) {
        if (properties.getFolderId().equals(file.getId())) {
            return true;
        }

        ArrayDeque<String> pendingParents = new ArrayDeque<>(
                file.getParents() == null ? List.of() : file.getParents());
        Set<String> visited = new HashSet<>();
        while (!pendingParents.isEmpty()) {
            String parentId = pendingParents.removeFirst();
            if (properties.getFolderId().equals(parentId)) {
                return true;
            }
            if (visited.add(parentId)) {
                com.google.api.services.drive.model.File parent = getRawFile(parentId);
                if (parent.getParents() != null) {
                    pendingParents.addAll(parent.getParents());
                }
            }
        }
        return false;
    }

    String buildStoredFileName(
            String originalFilename,
            DriveDocumentCategory category,
            LocalDate documentDate
    ) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("The uploaded file must have a filename");
        }

        String cleanName = Paths.get(originalFilename).getFileName().toString();
        int extensionIndex = cleanName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? cleanName.substring(0, extensionIndex) : cleanName;
        String extension = extensionIndex > 0 ? cleanName.substring(extensionIndex) : "";
        LocalDate effectiveDate = Objects.requireNonNullElseGet(documentDate, LocalDate::now);
        String suffix = category == DriveDocumentCategory.AMC_CONTRACT
                ? Integer.toString(effectiveDate.getYear())
                : FILE_DATE_FORMAT.format(effectiveDate);
        if (baseName.endsWith("_" + suffix)) {
            // The generated filename already carries this exact suffix (e.g. contract PDFs
            // generated with the year baked in) — avoid appending it a second time.
            return baseName + extension;
        }
        return baseName + "_" + suffix + extension;
    }

    private String resolveContentType(MultipartFile file) {
        if (StringUtils.hasText(file.getContentType())) {
            return file.getContentType();
        }
        String filename = file.getOriginalFilename();
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    private String sanitizeFolderName(String value) {
        String sanitized = value == null ? "" : value.trim()
                .replaceAll("[\\\\/\\p{Cntrl}]", "_");
        if (!StringUtils.hasText(sanitized)) {
            throw new IllegalArgumentException("Folder name cannot be blank");
        }
        return sanitized;
    }

    private String escapeQueryValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private void validateFileId(String fileId) {
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("Google Drive file ID is required");
        }
    }

    private DriveFileMetadata toMetadata(com.google.api.services.drive.model.File file) {
        return new DriveFileMetadata(
                file.getId(),
                file.getName(),
                file.getMimeType(),
                file.getSize(),
                toInstant(file.getCreatedTime()),
                toInstant(file.getModifiedTime())
        );
    }

    private Instant toInstant(com.google.api.client.util.DateTime value) {
        return value == null ? null : Instant.ofEpochMilli(value.getValue());
    }

    private RuntimeException translateException(String operation, IOException exception) {
        if (exception instanceof GoogleJsonResponseException googleException) {
            int statusCode = googleException.getStatusCode();
            String reason = extractReason(googleException);
            String googleMessage = googleException.getDetails() != null
                    ? googleException.getDetails().getMessage()
                    : googleException.getMessage();

            if (statusCode == 401) {
                return new GoogleDriveAuthenticationException(
                        "Google Drive authentication failed", exception);
            }
            if (statusCode == 403) {
                if ("storageQuotaExceeded".equals(reason)) {
                    log.error(
                            "Google Drive upload rejected: storageQuotaExceeded. Service accounts have no "
                                    + "personal Drive storage quota, so file creation fails even when the "
                                    + "folder is shared as Editor. Move the target folder into a Shared Drive "
                                    + "(Team Drive) with the service account added as a member, or use domain-wide "
                                    + "delegation to impersonate a real user. operation={}, googleMessage={}",
                            operation, googleMessage
                    );
                    return new GoogleDrivePermissionException(
                            "Google Drive rejected the request: storageQuotaExceeded. Service accounts have no "
                                    + "Drive storage quota of their own — move the configured folder into a "
                                    + "Shared Drive or use domain-wide delegation.", exception);
                }
                log.error(
                        "Google Drive permission denied. operation={}, reason={}, googleMessage={}",
                        operation, reason, googleMessage
                );
                return new GoogleDrivePermissionException(
                        "The service account does not have permission to " + operation
                                + (reason != null ? " (reason: " + reason + ")" : ""), exception);
            }
            if (statusCode == 404) {
                return new ResourceNotFoundException("Google Drive item was not found");
            }
            log.error(
                    "Google Drive API error. operation={}, statusCode={}, reason={}, googleMessage={}",
                    operation, statusCode, reason, googleMessage
            );
        }
        return new GoogleDriveOperationException(
                "Google Drive could not " + operation, exception);
    }

    private String extractReason(GoogleJsonResponseException exception) {
        if (exception.getDetails() == null
                || exception.getDetails().getErrors() == null
                || exception.getDetails().getErrors().isEmpty()) {
            return null;
        }
        return exception.getDetails().getErrors().get(0).getReason();
    }
}
