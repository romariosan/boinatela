package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.enums.StatusContato;
import jakarta.validation.constraints.NotNull;

public record StatusContatoRequest(@NotNull StatusContato status) {
}
