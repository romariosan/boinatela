package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReivindicarRequest(
        @NotBlank @Size(min = 10, max = 20) String whatsapp,
        @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
}
