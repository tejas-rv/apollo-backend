package com.apollo.elevator.documents.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HtmlToPdfDocumentRequest(

        @NotBlank(message = "HTML content is required")
        String htmlContent,

        @Size(max = 255, message = "File name must not exceed 255 characters")
        String fileName
) {}
