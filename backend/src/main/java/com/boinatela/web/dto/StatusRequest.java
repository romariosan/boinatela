package com.boinatela.web.dto;

import com.boinatela.domain.enums.AnuncioStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull AnuncioStatus status) {
}
