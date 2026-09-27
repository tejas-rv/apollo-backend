package com.apollo.elevator.googledrive.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "google.drive")
public class GoogleDriveProperties {

    private boolean enabled = true;
    private String applicationName = "apollo-elevators";
    private String oauthClientPath;
    private String tokenDirectory;
    private String folderId;
}
