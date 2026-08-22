package com.apollo.elevators.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Apollo Elevators API",
                version = "1.0.0",
                description = """
                        REST API for Apollo Elevators management system.

                        This API provides:
                        - Customer management
                        - Lift management
                        - AMC management
                        - Engineer operations
                        - Authentication and authorization
                        """,
                contact = @Contact(
                        name = "Apollo Elevators"
                ),
                license = @License(
                        name = "Apollo Elevators"
                )
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        description = "Enter JWT token obtained from /api/auth/login",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
