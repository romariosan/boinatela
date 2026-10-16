package br.com.appcompregado.service;

import br.com.appcompregado.domain.AlertaBusca;
import br.com.appcompregado.repository.AlertaBuscaRepository;
import br.com.appcompregado.web.dto.AlertaBuscaRequest;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaBuscaService {

    static final int MAXIMO_POR_NUMERO = 10;

    private final AlertaBuscaRepository alertas;

    public AlertaBuscaService(AlertaBuscaRepository alertas) {
        this.alertas = alertas;
    }

    @Transactional
    public AlertaBusca criar(AlertaBuscaRequest pedido) {
        String whatsapp = AuthService.normalizarWhatsapp(pedido.whatsapp());
        if (!PhoneUtils.celularBrasileiro(whatsapp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um celular com DDD.");
        }
        String raca = limpar(pedido.raca());
        String categoria = limpar(pedido.categoriaPilar());
        String cidade = limpar(pedido.cidade());
        if (raca.isEmpty() && categoria.isEmpty() && cidade.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma raça, categoria ou cidade.");
        }
        return alertas.findByWhatsappAndRacaAndCategoriaPilarAndCidadeAndAtivoTrue(whatsapp, raca, categoria, cidade)
                .orElseGet(() -> novo(whatsapp, raca, categoria, cidade));
    }

    private AlertaBusca novo(String whatsapp, String raca, String categoria, String cidade) {
        if (alertas.countByWhatsappAndAtivoTrue(whatsapp) >= MAXIMO_POR_NUMERO) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Este WhatsApp já tem alertas demais.");
        }
        AlertaBusca alerta = new AlertaBusca();
        alerta.setWhatsapp(whatsapp);
        alerta.setRaca(raca);
        alerta.setCategoriaPilar(categoria);
        alerta.setCidade(cidade);
        alerta.setAtivo(true);
        alerta.setDataCriacao(LocalDateTime.now());
        return alertas.save(alerta);
    }

    private static String limpar(String valor) {
        return valor == null ? "" : valor.trim();
    }
}
