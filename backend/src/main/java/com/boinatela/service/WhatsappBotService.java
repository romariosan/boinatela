package com.boinatela.service;

import com.boinatela.config.WhatsappProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/** Dispara mensagens pelo WhatsApp do Boi na Tela, conectado na Evolution API. */
@Service
public class WhatsappBotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappBotService.class);

    private final WhatsappProperties propriedades;
    private final RestClient http;

    public WhatsappBotService(WhatsappProperties propriedades, RestClient.Builder builder) {
        this.propriedades = propriedades;
        this.http = builder.build();
    }

    /** @param whatsapp número do cadastro (DDD + número) ou JID do WhatsApp; o DDI 55 é acrescentado. */
    public void enviar(String whatsapp, String mensagem) {
        String numero = "55" + TelefoneBr.nacional(whatsapp);
        if (!propriedades.getBotUrl().isBlank()) {
            try {
                http.post()
                        .uri(propriedades.getBotUrl() + "/message/sendText/{instancia}", propriedades.getBotInstancia())
                        .header("apikey", propriedades.getBotToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("number", numero, "text", mensagem))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientException e) {
                log.error("Evolution API recusou ou não respondeu: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível enviar a mensagem.");
            }
        }
        if (propriedades.isEnvioNoLog()) {
            log.warn("[DEV - bot desligado] Mensagem para {}: {}", numero, mensagem);
            return;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envio pelo WhatsApp não configurado.");
    }

    /** Para respostas de cortesia: uma falha no envio não pode derrubar quem chamou. */
    public void tentarEnviar(String whatsapp, String mensagem) {
        try {
            enviar(whatsapp, mensagem);
        } catch (ResponseStatusException e) {
            log.warn("Resposta pelo WhatsApp não enviada: {}", e.getReason());
        }
    }
}
