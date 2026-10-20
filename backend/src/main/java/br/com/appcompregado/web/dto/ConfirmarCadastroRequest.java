package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmarCadastroRequest(
        @NotBlank @Size(min = 10, max = 20) String whatsapp,
        @NotBlank @Size(min = 6, max = 6) String codigo) {
}
