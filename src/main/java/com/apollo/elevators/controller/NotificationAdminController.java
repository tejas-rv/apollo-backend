package com.apollo.elevators.controller;

import com.apollo.elevators.common.api.ApiErrorResponse;
import com.apollo.elevators.dto.notification.ContractEmailRequest;
import com.apollo.elevators.dto.notification.ContractWhatsAppRequest;
import com.apollo.elevators.dto.notification.NotificationResponse;
import com.apollo.elevators.dto.notification.PlainEmailRequest;
import com.apollo.elevators.dto.notification.PlainWhatsAppRequest;
import com.apollo.elevators.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notifications", description = "Send email and WhatsApp notifications")
public class NotificationAdminController {

    private final NotificationService notificationService;

    // =========================================================================
    // EMAIL
    // =========================================================================

    @PostMapping("/email")
    @Operation(
            summary = "Send plain email",
            description = "Sends a plain text email with no attachments."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email sent",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> sendPlainEmail(
            @Valid @RequestBody PlainEmailRequest request
    ) {
        log.info("Plain email request received. email={}, subject={}", request.email(), request.subject());
        NotificationResponse response = notificationService.sendPlainEmail(request);
        log.info("Plain email completed. notificationId={}, status={}", response.id(), response.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/contract")
    @Operation(
            summary = "Send AMC contract PDF email",
            description = """
                    Fetches the customer's AMC data by `customerId`, generates a styled multi-page
                    PDF from the server-side template, and sends it as an email attachment.

                    If `subject` or `body` are omitted, sensible defaults are used.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract email sent",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Customer / AMC not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> sendContractEmail(
            @Valid @RequestBody ContractEmailRequest request
    ) {
        log.info("Contract email request received. customerId={}, email={}", request.customerId(), request.email());
        NotificationResponse response = notificationService.sendContractEmail(request);
        log.info("Contract email completed. notificationId={}, status={}", response.id(), response.status());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // WHATSAPP
    // =========================================================================

    @PostMapping("/whatsapp")
    @Operation(
            summary = "Send plain WhatsApp message",
            description = "Sends a plain text WhatsApp message to the given phone number."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "WhatsApp message sent",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> sendPlainWhatsApp(
            @Valid @RequestBody PlainWhatsAppRequest request
    ) {
        log.info("Plain WhatsApp request received. phoneNumber={}", request.phoneNumber());
        NotificationResponse response = notificationService.sendPlainWhatsApp(request);
        log.info("Plain WhatsApp completed. notificationId={}, status={}", response.id(), response.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/whatsapp/contract")
    @Operation(
            summary = "Send AMC contract PDF via WhatsApp",
            description = """
                    Fetches the customer's AMC data by `customerId`, generates a PDF from the
                    server-side template, and sends it as a WhatsApp document message.

                    If `caption` is omitted, a default caption is used.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract WhatsApp document sent",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Customer / AMC not found",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Forbidden",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<NotificationResponse> sendContractWhatsApp(
            @Valid @RequestBody ContractWhatsAppRequest request
    ) {
        log.info("Contract WhatsApp request received. customerId={}, phoneNumber={}", request.customerId(), request.phoneNumber());
        NotificationResponse response = notificationService.sendContractWhatsApp(request);
        log.info("Contract WhatsApp completed. notificationId={}, status={}", response.id(), response.status());
        return ResponseEntity.ok(response);
    }
}
