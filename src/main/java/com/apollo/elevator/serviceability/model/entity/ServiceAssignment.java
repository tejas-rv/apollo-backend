package com.apollo.elevator.serviceability.model.entity;

import com.apollo.elevator.serviceability.model.enums.AssignmentMethod;
import com.apollo.elevator.serviceability.model.enums.AssignmentStatus;
import com.apollo.elevator.serviceability.model.enums.ServicePriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "service_assignment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_visit_id", nullable = false)
    private Long serviceVisitId;

    @Column(name = "engineer_id", nullable = false)
    private Long engineerId;

    @Column(name = "zone_id", nullable = false, length = 30)
    private String zoneId;

    @Column(name = "assignment_date", nullable = false)
    private LocalDate assignmentDate;

    @Column(name = "service_due_date", nullable = false)
    private LocalDate serviceDueDate;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServicePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ACTIVE;

    @Column(name = "assigned_by", nullable = false, length = 100)
    private String assignedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_method", nullable = false, length = 20)
    private AssignmentMethod assignmentMethod;

    @Column(name = "assignment_remarks", length = 500)
    private String assignmentRemarks;

    @Column(name = "visit_sequence", nullable = false)
    @Builder.Default
    private int visitSequence = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
