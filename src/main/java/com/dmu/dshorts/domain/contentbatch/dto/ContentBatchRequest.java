package com.dmu.dshorts.domain.contentbatch.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
public record ContentBatchRequest(@NotEmpty List<@NotBlank String> topicIds,String personaId) {}
