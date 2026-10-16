package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlertaBuscaRequest(
        @NotBlank @Size(min = 10, max = 20) String whatsapp,
        @Size(max = 40) String raca,
        @Size(max = 40) String categoriaPilar,
        @Size(max = 80) String cidade) {
}
