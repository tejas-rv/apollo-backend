package com.apollo.elevator.serviceability.service;

import com.apollo.elevator.common.exception.ConflictException;
import com.apollo.elevator.common.exception.ResourceNotFoundException;
import com.apollo.elevator.serviceability.model.dto.PincodeLookupResponse;
import com.apollo.elevator.serviceability.model.dto.PincodeServiceabilityRequest;
import com.apollo.elevator.serviceability.model.dto.PincodeServiceabilityResponse;
import com.apollo.elevator.serviceability.model.entity.PincodeServiceability;
import com.apollo.elevator.serviceability.model.entity.PincodeServiceabilityAudit;
import com.apollo.elevator.serviceability.model.entity.ServiceabilityZone;
import com.apollo.elevator.serviceability.model.enums.PostalVerificationStatus;
import com.apollo.elevator.serviceability.repository.PincodeServiceabilityAuditRepository;
import com.apollo.elevator.serviceability.repository.PincodeServiceabilityRepository;
import com.apollo.elevator.serviceability.repository.ServiceabilityZoneRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PincodeServiceabilityService {

    private final PincodeServiceabilityRepository pincodeRepository;
    private final PincodeServiceabilityAuditRepository auditRepository;
    private final ServiceabilityZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public Page<PincodeServiceabilityResponse> search(
            String pincode,
            String locality,
            String district,
            String zoneId,
            PostalVerificationStatus verificationStatus,
            Boolean active,
            Pageable pageable
    ) {
        Page<PincodeServiceabilityResponse> result = pincodeRepository.search(
                        clean(pincode), clean(locality), clean(district), clean(zoneId),
                        verificationStatus, active, pageable)
                .map(PincodeServiceabilityService::response);
        log.debug("PIN-code serviceability search completed. page={}, resultCount={}, totalElements={}",
                pageable.getPageNumber(), result.getNumberOfElements(), result.getTotalElements());
        return result;
    }

    @Transactional(readOnly = true)
    public PincodeLookupResponse lookup(String pincode) {
        List<PincodeServiceabilityResponse> mappings = pincodeRepository
                .findCurrentMappings(pincode, LocalDate.now())
                .stream()
                .map(PincodeServiceabilityService::response)
                .toList();
        if (mappings.isEmpty()) {
            return new PincodeLookupResponse(pincode, false, false, "No active locality mapping is configured.", mappings);
        }

        List<PincodeServiceabilityResponse> verified = mappings.stream()
                .filter(mapping -> mapping.verificationStatus() == PostalVerificationStatus.VERIFIED)
                .toList();
        long zoneCount = verified.stream().map(PincodeServiceabilityResponse::zoneId)
                .filter(zone -> zone != null).distinct().count();
        boolean ambiguous = zoneCount > 1
                || mappings.stream().anyMatch(mapping -> mapping.verificationStatus() == PostalVerificationStatus.AMBIGUOUS);
        boolean serviceable = !ambiguous && verified.size() == mappings.size()
                && zoneCount == 1;
        String message = ambiguous
                ? "PIN code maps to multiple Apollo zones; supervisor review is required."
                : serviceable
                        ? "PIN code has verified Apollo serviceability."
                        : "PIN code data is unverified or incomplete; supervisor review is required.";
        log.debug("PIN-code lookup completed. mappingCount={}, verifiedCount={}, serviceable={}, ambiguous={}",
                mappings.size(), verified.size(), serviceable, ambiguous);
        return new PincodeLookupResponse(pincode, serviceable, ambiguous, message, mappings);
    }

    @Transactional
    public PincodeServiceabilityResponse create(PincodeServiceabilityRequest request, String actor) {
        if (pincodeRepository.existsByPincodeAndLocalityIgnoreCaseAndActiveTrue(
                request.pincode(), request.locality().trim())) {
            throw new ConflictException("An active mapping already exists for this PIN code and locality.");
        }
        PincodeServiceability mapping = new PincodeServiceability();
        apply(mapping, request);
        PincodeServiceability saved = pincodeRepository.save(mapping);
        auditRepository.save(audit(saved, actor, "Mapping created", null, snapshot(saved)));
        log.info("PIN-code serviceability mapping created. mappingId={}, pincode={}, verificationStatus={}",
                saved.getId(), saved.getPincode(), saved.getVerificationStatus());
        return response(saved);
    }

    @Transactional
    public PincodeServiceabilityResponse update(
            Long id,
            PincodeServiceabilityRequest request,
            String actor
    ) {
        PincodeServiceability mapping = pincodeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PIN-code mapping not found: " + id));
        if (request.active()
                && pincodeRepository.existsByPincodeAndLocalityIgnoreCaseAndActiveTrue(
                        request.pincode(), request.locality().trim())
                && !(mapping.getPincode().equals(request.pincode())
                        && mapping.getLocality().equalsIgnoreCase(request.locality().trim())
                        && mapping.isActive())) {
            throw new ConflictException("An active mapping already exists for this PIN code and locality.");
        }
        String before = snapshot(mapping);
        apply(mapping, request);
        PincodeServiceability saved = pincodeRepository.save(mapping);
        auditRepository.save(audit(saved, actor, "Mapping updated", before, snapshot(saved)));
        log.info("PIN-code serviceability mapping updated. mappingId={}, pincode={}, verificationStatus={}",
                saved.getId(), saved.getPincode(), saved.getVerificationStatus());
        return response(saved);
    }

    @Transactional
    public com.apollo.elevator.serviceability.model.dto.PincodeImportResponse importFile(
            MultipartFile file,
            String actor
    ) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A CSV or Excel file is required");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("PIN-code import file must not exceed 5 MB");
        }

        List<List<String>> rows = parseFile(file);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The import file has no rows");
        }
        List<String> headers = rows.getFirst().stream().map(PincodeServiceabilityService::header).toList();
        int pinColumn = requiredColumn(headers, "pincode");
        int localityColumn = requiredColumn(headers, "locality");
        int sourceColumn = requiredColumn(headers, "source");
        int districtColumn = headers.indexOf("district");
        int stateColumn = headers.indexOf("state");
        int divisionColumn = headers.indexOf("postaldivision");
        int zoneColumn = headers.indexOf("zoneid");

        int imported = 0;
        List<com.apollo.elevator.serviceability.model.dto.PincodeImportResponse.RowError> errors =
                new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 1; index < rows.size(); index++) {
            List<String> row = rows.get(index);
            int rowNumber = index + 1;
            String pincode = cell(row, pinColumn).trim();
            String locality = cell(row, localityColumn).trim();
            String source = cell(row, sourceColumn).trim();
            if (!pincode.matches("[0-9]{6}")) {
                errors.add(rowError(rowNumber, "PIN code must contain exactly six digits"));
                continue;
            }
            if (locality.isBlank() || source.isBlank()) {
                errors.add(rowError(rowNumber, "Locality and source are required"));
                continue;
            }
            String key = pincode + ":" + locality.toLowerCase(Locale.ROOT);
            if (!seen.add(key) || pincodeRepository.existsByPincodeAndLocalityIgnoreCaseAndActiveTrue(
                    pincode, locality)) {
                errors.add(rowError(rowNumber, "Duplicate active PIN-code and locality mapping"));
                continue;
            }
            String zoneId = cell(row, zoneColumn).trim();
            if (!zoneId.isBlank() && !zoneRepository.existsById(zoneId)) {
                errors.add(rowError(rowNumber, "Unknown Apollo zone: " + zoneId));
                continue;
            }

            PincodeServiceability mapping = PincodeServiceability.builder()
                    .pincode(pincode)
                    .locality(locality)
                    .district(blankToNull(cell(row, districtColumn)))
                    .state(blankToNull(cell(row, stateColumn)))
                    .postalDivision(blankToNull(cell(row, divisionColumn)))
                    .zone(zoneId.isBlank() ? null : zoneRepository.getReferenceById(zoneId))
                    .active(true)
                    .effectiveFrom(LocalDate.now())
                    .source(source)
                    .verificationStatus(PostalVerificationStatus.UNVERIFIED)
                    .build();
            PincodeServiceability saved = pincodeRepository.save(mapping);
            auditRepository.save(audit(saved, actor, "Imported as unverified", null, snapshot(saved)));
            imported++;
        }
        log.info("PIN-code serviceability import completed. imported={}, rejected={}", imported, errors.size());
        return new com.apollo.elevator.serviceability.model.dto.PincodeImportResponse(
                imported, errors.size(), errors);
    }

    private static List<List<String>> parseFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IllegalArgumentException("Import file name is required");
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        try {
            if (lowerName.endsWith(".csv")) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                    return reader.lines().map(PincodeServiceabilityService::parseCsvRow).toList();
                }
            }
            if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                    if (workbook.getNumberOfSheets() == 0) {
                        return List.of();
                    }
                    var sheet = workbook.getSheetAt(0);
                    DataFormatter formatter = new DataFormatter();
                    List<List<String>> result = new ArrayList<>();
                    for (Row row : sheet) {
                        List<String> cells = new ArrayList<>();
                        for (int column = 0; column < row.getLastCellNum(); column++) {
                            cells.add(formatter.formatCellValue(row.getCell(column)));
                        }
                        result.add(cells);
                    }
                    return result;
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the PIN-code import file", exception);
        }
        throw new IllegalArgumentException("PIN-code import supports .csv, .xls and .xlsx files");
    }

    private static List<String> parseCsvRow(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("Malformed CSV: unmatched quote");
        }
        cells.add(cell.toString());
        return cells;
    }

    private static String header(String value) {
        return value.replace("\uFEFF", "").replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static int requiredColumn(List<String> headers, String name) {
        int index = headers.indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("Import file is missing required column: " + name);
        }
        return index;
    }

    private static String cell(List<String> row, int index) {
        return index < 0 || index >= row.size() || row.get(index) == null ? "" : row.get(index);
    }

    private static com.apollo.elevator.serviceability.model.dto.PincodeImportResponse.RowError rowError(
            int row,
            String message
    ) {
        return new com.apollo.elevator.serviceability.model.dto.PincodeImportResponse.RowError(row, message);
    }

    private static String blankToNull(String value) {
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private void apply(PincodeServiceability mapping, PincodeServiceabilityRequest request) {
        mapping.setPincode(request.pincode().trim());
        mapping.setLocality(request.locality().trim());
        mapping.setDistrict(clean(request.district()));
        mapping.setState(clean(request.state()));
        mapping.setPostalDivision(clean(request.postalDivision()));
        mapping.setZone(resolveZone(request.zoneId()));
        mapping.setActive(request.active());
        mapping.setEffectiveFrom(request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom());
        mapping.setEffectiveTo(request.effectiveTo());
        mapping.setSource(request.source().trim());
        mapping.setVerificationStatus(request.verificationStatus() == null
                ? PostalVerificationStatus.UNVERIFIED : request.verificationStatus());
        mapping.setLastVerifiedDate(mapping.getVerificationStatus() == PostalVerificationStatus.VERIFIED
                ? LocalDate.now() : null);
    }

    private ServiceabilityZone resolveZone(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return null;
        }
        return zoneRepository.findById(zoneId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Serviceability zone not found: " + zoneId));
    }

    private static PincodeServiceabilityResponse response(PincodeServiceability mapping) {
        return new PincodeServiceabilityResponse(
                mapping.getId(), mapping.getPincode(), mapping.getLocality(), mapping.getDistrict(),
                mapping.getState(), mapping.getPostalDivision(),
                mapping.getZone() == null ? null : mapping.getZone().getZoneId(),
                mapping.getZone() == null ? null : mapping.getZone().getZoneName(),
                mapping.isActive(), mapping.getEffectiveFrom(), mapping.getEffectiveTo(),
                mapping.getSource(), mapping.getVerificationStatus(), mapping.getLastVerifiedDate());
    }

    private static PincodeServiceabilityAudit audit(
            PincodeServiceability mapping,
            String actor,
            String summary,
            String previous,
            String current
    ) {
        return PincodeServiceabilityAudit.builder()
                .pincodeRecordId(mapping.getId())
                .changedBy(actor == null ? "system" : actor)
                .changeSummary(summary)
                .previousValue(previous)
                .newValue(current)
                .build();
    }

    private static String snapshot(PincodeServiceability mapping) {
        return String.join("|",
                value(mapping.getPincode()),
                value(mapping.getLocality()),
                value(mapping.getDistrict()),
                value(mapping.getState()),
                value(mapping.getPostalDivision()),
                value(mapping.getZone() == null ? null : mapping.getZone().getZoneId()),
                Boolean.toString(mapping.isActive()),
                value(mapping.getEffectiveFrom()),
                value(mapping.getEffectiveTo()),
                value(mapping.getSource()),
                value(mapping.getVerificationStatus()),
                value(mapping.getLastVerifiedDate()));
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString().replace("|", "\\|");
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
