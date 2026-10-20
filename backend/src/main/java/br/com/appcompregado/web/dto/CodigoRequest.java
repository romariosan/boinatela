package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CodigoRequest(@NotBlank @Size(min = 6, max = 6) String codigo) {
}
