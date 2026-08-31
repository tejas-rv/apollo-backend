package com.apollo.elevators.notification.email.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

    private boolean enabled;
    private String from;
    private List<String> adminRecipients = new ArrayList<>();
}
