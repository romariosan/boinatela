package com.boinatela.web.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String whatsapp,
        @NotBlank String senha) {
}
