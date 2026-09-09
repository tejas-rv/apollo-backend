package com.apollo.elevator.customer.model.dto;

import com.apollo.elevator.customer.model.enums.ServiceVisitStatus;
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

    @NotBlank(message = "Engineer name is required")
    @Size(max = 100, message = "Engineer name must not exceed 100 characters")
    private String engineerName;

    @NotBlank(message = "Work done is required")
    @Size(max = 500, message = "Work done must not exceed 500 characters")
    private String workDone;

    @NotNull(message = "Service visit status is required")
    private ServiceVisitStatus status;
}
