package com.apollo.elevator.serviceability.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pincode_serviceability_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PincodeServiceabilityAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pincode_record_id", nullable = false)
    private Long pincodeRecordId;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "change_summary", nullable = false, length = 1000)
    private String changeSummary;

    @Column(name = "previous_value", columnDefinition = "TEXT")
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "changed_at", nullable = false)
    @Builder.Default
    private LocalDateTime changedAt = LocalDateTime.now();
}
