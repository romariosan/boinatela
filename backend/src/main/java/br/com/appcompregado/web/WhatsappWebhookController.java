package br.com.appcompregado.web;

import br.com.appcompregado.config.WhatsappProperties;
import br.com.appcompregado.service.EvolutionWebhookService;
import br.com.appcompregado.service.VerificacaoWhatsappService;
import br.com.appcompregado.web.dto.WhatsappWebhookRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Público na Security (o bot não tem JWT), mas protegido pelo segredo compartilhado: sem ele, qualquer um
 * poderia "confirmar" um número falso alegando ser o remetente.
 */
@RestController
public class WhatsappWebhookController {

    private final WhatsappProperties propriedades;
    private final VerificacaoWhatsappService verificacao;
    private final EvolutionWebhookService evolution;

    public WhatsappWebhookController(
            WhatsappProperties propriedades,
            VerificacaoWhatsappService verificacao,
            EvolutionWebhookService evolution) {
        this.propriedades = propriedades;
        this.verificacao = verificacao;
        this.evolution = evolution;
    }

    /** Formato simples, usado para simular o bot. Responde 200 mesmo sem código válido. */
    @PostMapping("/api/webhook/whatsapp")
    public Map<String, Boolean> receber(
            @RequestHeader(name = "X-Webhook-Token", required = false) String token,
            @Valid @RequestBody WhatsappWebhookRequest request) {
        validarToken(token);
        return Map.of("verificado", verificacao.processarMensagem(request.remetente(), request.mensagem()));
    }

    /** Webhook da Evolution API (evento MESSAGES_UPSERT), com o segredo em um header customizado. */
    @PostMapping("/api/webhook/evolution")
    public Map<String, Boolean> receberEvolution(
            @RequestHeader(name = "X-Webhook-Token", required = false) String token,
            @RequestBody JsonNode evento) {
        validarToken(token);
        return Map.of("verificado", evolution.processar(evento));
    }

    private void validarToken(String token) {
        String esperado = propriedades.getWebhookToken();
        if (esperado == null || esperado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook do WhatsApp não configurado.");
        }
        if (token == null || !MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
