package com.boinatela.web;

import com.boinatela.config.WhatsappProperties;
import com.boinatela.service.VerificacaoWhatsappService;
import com.boinatela.web.dto.WhatsappWebhookRequest;
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

    public WhatsappWebhookController(WhatsappProperties propriedades, VerificacaoWhatsappService verificacao) {
        this.propriedades = propriedades;
        this.verificacao = verificacao;
    }

    /** Responde 200 mesmo sem código válido: o bot não deve reenviar mensagens comuns. */
    @PostMapping("/api/webhook/whatsapp")
    public Map<String, Boolean> receber(
            @RequestHeader(name = "X-Webhook-Token", required = false) String token,
            @Valid @RequestBody WhatsappWebhookRequest request) {
        String esperado = propriedades.getWebhookToken();
        if (esperado == null || esperado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Webhook do WhatsApp não configurado.");
        }
        if (token == null || !MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return Map.of("verificado", verificacao.processarMensagem(request.remetente(), request.mensagem()));
    }
}
