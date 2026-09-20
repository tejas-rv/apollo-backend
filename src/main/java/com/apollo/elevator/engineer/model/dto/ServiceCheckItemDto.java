package com.apollo.elevator.engineer.model.dto;

import com.apollo.elevator.engineer.model.enums.AnswerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceCheckItemDto(
        Long id,
        @NotNull Integer itemOrder,
        @NotBlank String question,
        @NotNull AnswerType answerType,
        String answer,
        String answerText
) {}
