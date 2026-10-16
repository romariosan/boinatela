package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ContatoLeadRequest(@NotBlank String whatsapp) {
}
