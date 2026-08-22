package com.apollo.elevators.dto.notification;

public record HtmlToPdfNotificationResponse(
        String pdfFileName,
        long pdfSizeBytes,
        NotificationResponse emailNotification,
        NotificationResponse whatsappNotification
) {}
