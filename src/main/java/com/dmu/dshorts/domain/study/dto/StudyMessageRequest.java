package com.dmu.dshorts.domain.study.dto;
import jakarta.validation.constraints.NotBlank;
public record StudyMessageRequest(@NotBlank String content) {}
