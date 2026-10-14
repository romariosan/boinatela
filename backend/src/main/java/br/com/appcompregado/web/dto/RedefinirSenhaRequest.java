package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequest(
        @NotBlank @Size(min = 10, max = 20) String whatsapp,
        @NotBlank @Pattern(regexp = "\\d{4}") String codigo,
        @NotBlank @Size(min = 4, max = 72) String novaSenha) {
}
