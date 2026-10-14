package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.enums.AnuncioStatus;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull AnuncioStatus status) {
}
