package com.apollo.elevators.notification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {

    private boolean enabled;
    private String baseUrl = "https://graph.facebook.com";
    private String apiVersion = "v20.0";
    private String phoneNumberId;
    private String accessToken;
}
