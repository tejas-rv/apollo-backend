package com.apollo.elevator.common.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;

@Slf4j
@Configuration
public class DataSourceConfig {

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${app.datasource.primary.url}")
    private String primaryUrl;

    @Value("${app.datasource.primary.username}")
    private String primaryUsername;

    @Value("${app.datasource.primary.password}")
    private String primaryPassword;

    @Value("${app.datasource.fallback.url}")
    private String fallbackUrl;

    @Value("${app.datasource.fallback.username}")
    private String fallbackUsername;

    @Value("${app.datasource.fallback.password}")
    private String fallbackPassword;

    @Value("${app.datasource.connect-timeout-seconds:5}")
    private int connectTimeoutSeconds;

    @Bean
    public DataSource dataSource() {
        String url = primaryUrl;
        String username = primaryUsername;
        String password = primaryPassword;

        if (isReachable(primaryUrl, primaryUsername, primaryPassword)) {
            log.info("Connected to primary (dev) database");
        } else {
            log.warn("Primary (dev) database unreachable, falling back to local database");
            url = fallbackUrl;
            username = fallbackUsername;
            password = fallbackPassword;
        }

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    private boolean isReachable(String url, String username, String password) {
        try {
            DriverManager.setLoginTimeout(connectTimeoutSeconds);
            try (Connection connection = DriverManager.getConnection(url, username, password)) {
                return connection.isValid(connectTimeoutSeconds);
            }
        } catch (Exception e) {
            log.warn("Failed to connect to database at {}: {}", url, e.getMessage());
            return false;
        }
    }
}
