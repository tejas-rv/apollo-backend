package com.apollo.elevator.notification.model.dto;

public record HtmlToPdfNotificationResponse(
        String pdfFileName,
        long pdfSizeBytes,
        NotificationResponse emailNotification,
        NotificationResponse whatsappNotification
) {}
