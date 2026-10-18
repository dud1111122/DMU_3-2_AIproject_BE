package com.dmu.dshorts.domain.user.dto;
import jakarta.validation.constraints.Pattern;
public record PreferenceUpdateRequest(@Pattern(regexp="BASIC|INSTAGRAM|YOUTUBE",message="mode는 BASIC, INSTAGRAM, YOUTUBE 중 하나여야 합니다.") String mode) {}
