package com.apollo.elevator.documents.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@Service
@Slf4j
public class UpiQrCodeService {

    @Getter
    @Value("${apollo.upi.vpa}")
    private String vpa;

    @Value("${apollo.upi.payee-name}")
    private String payeeName;

    /** Returns a data:image/png;base64,... URI, or null if generation fails. */
    public String buildUpiQrDataUri(String invoiceNumber, BigDecimal totalAfterTax) {
        if (totalAfterTax == null || totalAfterTax.signum() <= 0) {
            return null;
        }
        try {
            String upiUri = buildUpiUri(invoiceNumber, totalAfterTax);

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix matrix = new QRCodeWriter()
                    .encode(upiUri, BarcodeFormat.QR_CODE, 240, 240, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            log.warn("UPI QR generation failed. invoiceNumber={}", invoiceNumber, e);
            return null;   // invoice still renders, just without the QR
        }
    }

    private String buildUpiUri(String invoiceNumber, BigDecimal amount) {
        return "upi://pay"
                + "?pa=" + enc(vpa)
                + "&pn=" + enc(payeeName)
                + "&am=" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
                + "&cu=INR"
                + "&tn=" + enc("Apollo Elevator - Invoice " + (invoiceNumber == null ? "" : invoiceNumber));
    }

    private String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8)
                .replace("+", "%20");   // UPI apps dislike '+' for spaces
    }

}