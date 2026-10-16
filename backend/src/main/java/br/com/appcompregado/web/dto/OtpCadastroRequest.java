package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OtpCadastroRequest(
        @NotBlank @Size(min = 10, max = 20) String whatsapp,
        Long anuncioId) {
}
