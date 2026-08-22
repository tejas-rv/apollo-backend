package com.apollo.elevators.notification;

import com.apollo.elevators.notification.dto.NotificationResponse;
import com.apollo.elevators.notification.dto.WhatsAppMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
public class NotificationAdminController {

    private final NotificationService notificationService;

    @PostMapping("/whatsapp")
    public ResponseEntity<NotificationResponse> sendWhatsApp(
            @Valid @RequestBody WhatsAppMessageRequest request
    ) {
        return ResponseEntity.ok(notificationService.sendWhatsAppMessage(request));
    }
}
