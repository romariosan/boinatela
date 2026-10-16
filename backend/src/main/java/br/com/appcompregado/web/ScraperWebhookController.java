package br.com.appcompregado.web;

import br.com.appcompregado.config.WhatsappProperties;
import br.com.appcompregado.service.ScraperAnuncioService;
import br.com.appcompregado.service.ScraperAnuncioService.ResultadoCaptura;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Chip 2. Público na Security, fechado pelo mesmo segredo dos outros webhooks. Não envia resposta no grupo. */
@RestController
public class ScraperWebhookController {

    private final WhatsappProperties propriedades;
    private final ScraperAnuncioService scraper;

    public ScraperWebhookController(WhatsappProperties propriedades, ScraperAnuncioService scraper) {
        this.propriedades = propriedades;
        this.scraper = scraper;
    }

    @PostMapping("/api/webhook/whatsapp-scraper")
    public ResultadoCaptura receber(
            @RequestHeader(name = "X-Webhook-Token", required = false) String token,
            @RequestBody JsonNode corpo) {
        validarToken(token);
        return scraper.ingerir(corpo);
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
