package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EsqueciSenhaRequest(@NotBlank @Size(min = 10, max = 20) String whatsapp) {
}
