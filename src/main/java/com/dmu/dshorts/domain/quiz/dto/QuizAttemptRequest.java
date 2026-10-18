package com.dmu.dshorts.domain.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record QuizAttemptRequest(@NotEmpty List<@Valid Answer> answers) {
    public record Answer(@NotNull Integer questionOrder,@NotNull Integer selectedOption) {}
}
