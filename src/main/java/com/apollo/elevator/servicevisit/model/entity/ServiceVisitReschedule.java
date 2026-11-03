package com.apollo.elevator.servicevisit.model.entity;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.apollo.elevator.servicevisit.model.enums.IssueRaisedBy;
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
@Table(name = "service_visit_reschedule")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceVisitReschedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "amc_contract_id", nullable = false)
    private Long amcContractId;

    /** The visit the issue was raised against. */
    @Column(name = "service_visit_id")
    private Long serviceVisitId;

    /** The visit now scheduled: the same visit when moved in place, otherwise the follow-up visit. */
    @Column(name = "new_service_visit_id")
    private Long newServiceVisitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "raised_by", nullable = false, length = 20)
    private IssueRaisedBy raisedBy;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20)
    private ServiceVisitStatus previousStatus;

    @Column(name = "previous_date", nullable = false)
    private LocalDate previousDate;

    @Column(name = "new_date", nullable = false)
    private LocalDate newDate;

    @Column(name = "previous_engineer_name", length = 100)
    private String previousEngineerName;

    @Column(name = "new_engineer_name", length = 100)
    private String newEngineerName;

    @Column(name = "rescheduled_by", nullable = false, length = 100)
    private String rescheduledBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
