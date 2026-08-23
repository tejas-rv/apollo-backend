package com.apollo.elevators.customer.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AmcServiceHistoryRequest(

        @NotNull(message = "serviceHistory is required")
        List<@Valid ServiceHistoryDetails> serviceHistory
) {
}
