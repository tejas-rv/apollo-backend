package com.apollo.elevator.googledrive.config;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Builds the Google Drive client using OAuth 2.0 user authorization (installed-app flow)
 * instead of a service account. The application acts on behalf of the personal Google
 * account that authorizes it in the browser the first time it runs; the resulting
 * refresh token is persisted under {@code google.drive.token-directory} so subsequent
 * restarts reuse it without requiring the user to sign in again.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GoogleDriveProperties.class)
@ConditionalOnProperty(prefix = "google.drive", name = "enabled", havingValue = "true")
public class GoogleDriveConfiguration {

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final List<String> SCOPES = List.of(DriveScopes.DRIVE);
    private static final String AUTHORIZED_USER_ID = "apollo-elevator-drive-user";

    @Bean
    public Drive googleDriveClient(GoogleDriveProperties properties) {
        validateProperties(properties);

        try {
            NetHttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
            Credential credential = authorize(properties, transport, jsonFactory);

            Drive drive = new Drive.Builder(transport, jsonFactory, credential)
                    .setApplicationName(properties.getApplicationName())
                    .build();

            validateConfiguredFolder(drive, properties.getFolderId());
            return drive;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Unable to initialize Google Drive. Check the configured OAuth client credentials.", exception);
        }
    }

    /**
     * Loads the OAuth client secrets and runs the installed-app authorization flow.
     * A previously stored token in {@code token-directory} is reused automatically via
     * {@link FileDataStoreFactory}; only the first run — or a run after the token is
     * revoked/deleted — opens a browser window for the user to grant consent.
     */
    private Credential authorize(
            GoogleDriveProperties properties,
            NetHttpTransport transport,
            JsonFactory jsonFactory
    ) throws IOException {
        Path tokenDirectory = Path.of(properties.getTokenDirectory());
        try {
            Files.createDirectories(tokenDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create the Google Drive token directory: " + tokenDirectory, exception);
        }

        GoogleClientSecrets clientSecrets;
        try (Reader reader = new InputStreamReader(
                Files.newInputStream(Path.of(properties.getOauthClientPath())), StandardCharsets.UTF_8)) {
            clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);
        }

        FileDataStoreFactory dataStoreFactory = new FileDataStoreFactory(tokenDirectory.toFile());

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                transport, jsonFactory, clientSecrets, SCOPES)
                .setDataStoreFactory(dataStoreFactory)
                .setAccessType("offline")
                .build();

        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
                .authorize(AUTHORIZED_USER_ID);
        // Safe to log: this reports only whether a refresh token is present, never its value.
        log.info(
                "Google Drive OAuth authorization ready. tokenDirectory={}, hasRefreshToken={}",
                tokenDirectory, credential.getRefreshToken() != null
        );
        return credential;
    }

    private void validateProperties(GoogleDriveProperties properties) {
        if (!StringUtils.hasText(properties.getApplicationName())) {
            throw new IllegalStateException("google.drive.application-name must be configured");
        }
        if (!StringUtils.hasText(properties.getFolderId())) {
            throw new IllegalStateException(
                    "google.drive.folder-id must be configured when Google Drive is enabled");
        }
        if (!StringUtils.hasText(properties.getOauthClientPath())) {
            throw new IllegalStateException(
                    "google.drive.oauth-client-path must be configured when Google Drive is enabled");
        }
        if (!StringUtils.hasText(properties.getTokenDirectory())) {
            throw new IllegalStateException(
                    "google.drive.token-directory must be configured when Google Drive is enabled");
        }

        try {
            Path oauthClientPath = Path.of(properties.getOauthClientPath());
            if (!Files.isRegularFile(oauthClientPath) || !Files.isReadable(oauthClientPath)) {
                throw new IllegalStateException(
                        "Google Drive OAuth client JSON does not exist or is not readable: " + oauthClientPath);
            }
        } catch (InvalidPathException exception) {
            throw new IllegalStateException("google.drive.oauth-client-path is invalid", exception);
        }

        try {
            Path.of(properties.getTokenDirectory());
        } catch (InvalidPathException exception) {
            throw new IllegalStateException("google.drive.token-directory is invalid", exception);
        }
    }

    private void validateConfiguredFolder(Drive drive, String folderId) {
        try {
            com.google.api.services.drive.model.File folder = drive.files()
                    .get(folderId)
                    .setSupportsAllDrives(true)
                    .setFields("id,name,mimeType,trashed,capabilities,parents,driveId")
                    .execute();

            com.google.api.services.drive.model.File.Capabilities capabilities = folder.getCapabilities();
            log.info(
                    "Configured Google Drive folder resolved. folderId={}, name={}, mimeType={}, "
                            + "trashed={}, canEdit={}, canAddChildren={}, parents={}, driveId={}",
                    folder.getId(),
                    folder.getName(),
                    folder.getMimeType(),
                    folder.getTrashed(),
                    capabilities == null ? null : capabilities.getCanEdit(),
                    capabilities == null ? null : capabilities.getCanAddChildren(),
                    folder.getParents(),
                    folder.getDriveId()
            );

            if (!FOLDER_MIME_TYPE.equals(folder.getMimeType()) || Boolean.TRUE.equals(folder.getTrashed())) {
                throw new IllegalStateException(
                        "google.drive.folder-id must reference an accessible, non-trashed Google Drive folder");
            }
            if (capabilities != null && Boolean.FALSE.equals(capabilities.getCanAddChildren())) {
                log.warn(
                        "The authorized Google account cannot add children (files/subfolders) to the configured "
                                + "folder. folderId={}. Verify the folder is owned by, or shared with edit access "
                                + "to, the account that completed the OAuth consent screen.",
                        folder.getId()
                );
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to access the configured Google Drive folder with the authorized account", exception);
        }
    }
}
