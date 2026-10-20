package br.com.appcompregado.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RadarOfertaRequest(
        @NotBlank String grupoId,
        String grupoNome,
        String mensagemId,
        @NotBlank String produtor,
        String texto,
        @Valid @Size(max = 5) List<Midia> midias) {

    public record Midia(String tipo, String base64) {
    }
}
