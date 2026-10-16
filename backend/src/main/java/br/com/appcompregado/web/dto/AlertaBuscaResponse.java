package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.AlertaBusca;

public record AlertaBuscaResponse(Long id, String whatsapp, String raca, String categoriaPilar, String cidade) {

    public static AlertaBuscaResponse from(AlertaBusca alerta) {
        return new AlertaBuscaResponse(
                alerta.getId(),
                alerta.getWhatsapp(),
                alerta.getRaca(),
                alerta.getCategoriaPilar(),
                alerta.getCidade());
    }
}
