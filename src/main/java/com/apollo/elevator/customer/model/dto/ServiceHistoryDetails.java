package com.apollo.elevator.customer.model.dto;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHistoryDetails {

    private Long id;

    @NotNull(message = "Service date is required")
    private LocalDate serviceDate;

    /** Engineer account assigned to the visit. When set, engineerName is filled from the account. */
    private Long assignedEngineerId;

    @Size(max = 100, message = "Engineer name must not exceed 100 characters")
    private String engineerName;

    @NotBlank(message = "Work done is required")
    @Size(max = 500, message = "Work done must not exceed 500 characters")
    private String workDone;

    @NotNull(message = "Service visit status is required")
    private ServiceVisitStatus status;

    @JsonIgnore
    @AssertTrue(message = "Engineer is required")
    public boolean isEngineerSpecified() {
        return assignedEngineerId != null || (engineerName != null && !engineerName.isBlank());
    }
}
