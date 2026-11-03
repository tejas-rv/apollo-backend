package com.apollo.elevator.serviceability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apollo.serviceability")
public class ServiceabilityProperties {

    private int mediumDueWithinDays = 3;
    private int normalDueWithinDays = 7;
    private int defaultDailyCapacity = 5;

    public int getMediumDueWithinDays() {
        return mediumDueWithinDays;
    }

    public void setMediumDueWithinDays(int mediumDueWithinDays) {
        this.mediumDueWithinDays = mediumDueWithinDays;
    }

    public int getNormalDueWithinDays() {
        return normalDueWithinDays;
    }

    public void setNormalDueWithinDays(int normalDueWithinDays) {
        this.normalDueWithinDays = normalDueWithinDays;
    }

    public int getDefaultDailyCapacity() {
        return defaultDailyCapacity;
    }

    public void setDefaultDailyCapacity(int defaultDailyCapacity) {
        this.defaultDailyCapacity = defaultDailyCapacity;
    }
}
