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

/** Dispara mensagens pelo robô de WhatsApp. */
@Service
public class WhatsappBotService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappBotService.class);

    private final WhatsappProperties propriedades;
    private final RestClient http;

    public WhatsappBotService(WhatsappProperties propriedades, RestClient.Builder builder) {
        this.propriedades = propriedades;
        this.http = builder.build();
    }

    /** @param whatsapp número como está no cadastro (DDD + número, sem DDI). */
    public void enviar(String whatsapp, String mensagem) {
        String numero = "55" + TelefoneBr.nacional(whatsapp);
        if (!propriedades.getBotUrl().isBlank()) {
            try {
                http.post()
                        .uri(propriedades.getBotUrl())
                        .header("X-Bot-Token", propriedades.getBotToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("numero", numero, "mensagem", mensagem))
                        .retrieve()
                        .toBodilessEntity();
                return;
            } catch (RestClientException e) {
                log.error("Bot de WhatsApp recusou ou não respondeu: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível enviar a mensagem.");
            }
        }
        if (propriedades.isEnvioNoLog()) {
            log.warn("[DEV - bot desligado] Mensagem para {}: {}", numero, mensagem);
            return;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envio pelo WhatsApp não configurado.");
    }
}
