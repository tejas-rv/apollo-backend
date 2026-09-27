package com.apollo.elevator.googledrive.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.services.drive.Drive;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GoogleDriveConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(GoogleDriveConfiguration.class);

    @Test
    void doesNotCreateDriveClientWhenIntegrationIsDisabled() {
        contextRunner
                .withPropertyValues("google.drive.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(Drive.class);
                });
    }

    @Test
    void failsClearlyWhenEnabledWithoutFolderId() {
        contextRunner
                .withPropertyValues(
                        "google.drive.enabled=true",
                        "google.drive.application-name=apollo-elevators",
                        "google.drive.oauth-client-path=C:\\missing\\oauth-client.json",
                        "google.drive.token-directory=C:\\missing\\google-drive-tokens",
                        "google.drive.folder-id="
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "google.drive.folder-id must be configured when Google Drive is enabled");
                });
    }
}
