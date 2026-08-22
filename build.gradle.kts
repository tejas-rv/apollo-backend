import java.io.File

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.apollo"
version = "0.0.1-SNAPSHOT"
description = "Apollo Elevators - Admin & Engineer Backend API"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

val externalBuildDirectory = System.getenv("APOLLO_BUILD_DIR")
    ?: System.getenv("LOCALAPPDATA")?.let {
        "$it\\ApolloElevators\\gradle-build\\${rootProject.name}"
    }

if (!externalBuildDirectory.isNullOrBlank()) {
    layout.buildDirectory.set(File(externalBuildDirectory))
}

repositories {
    mavenCentral()
}

dependencies {
    // Core web + data
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Needed in Spring Boot 4 for RestClient.Builder / RestTemplateBuilder auto-config
    implementation("org.springframework.boot:spring-boot-starter-restclient")

    // Security
    implementation("org.springframework.boot:spring-boot-starter-security")

    // JWT (jjwt)
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // DB migrations — must use the STARTER on Spring Boot 4, not flyway-core alone.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Postgres driver
    runtimeOnly("org.postgresql:postgresql")

    // Boilerplate reduction
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")

    // Thymeleaf (standalone template engine for PDF rendering)
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
