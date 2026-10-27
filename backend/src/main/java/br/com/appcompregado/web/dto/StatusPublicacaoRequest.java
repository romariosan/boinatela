package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.enums.StatusPublicacao;
import jakarta.validation.constraints.NotNull;

public record StatusPublicacaoRequest(@NotNull StatusPublicacao status) {
}
