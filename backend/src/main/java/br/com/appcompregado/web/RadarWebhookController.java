package br.com.appcompregado.web;

import br.com.appcompregado.config.WhatsappProperties;
import br.com.appcompregado.service.ScraperAnuncioService;
import br.com.appcompregado.service.ScraperAnuncioService.ResultadoCaptura;
import br.com.appcompregado.web.dto.RadarOfertaRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Chip 2. O robô local manda o texto e as fotos do grupo. A API não responde no grupo. */
@RestController
public class RadarWebhookController {

    private final WhatsappProperties propriedades;
    private final ScraperAnuncioService scraper;
    private final ObjectMapper json;

    public RadarWebhookController(
            WhatsappProperties propriedades, ScraperAnuncioService scraper, ObjectMapper json) {
        this.propriedades = propriedades;
        this.scraper = scraper;
        this.json = json;
    }

    @PostMapping("/api/webhook/radar-ofertas")
    public ResultadoCaptura receber(
            @RequestHeader(name = "X-Webhook-Token", required = false) String token,
            @Valid @RequestBody RadarOfertaRequest oferta) {
        validarToken(token);
        ObjectNode corpo = json.createObjectNode();
        corpo.put("grupo", oferta.grupoId());
        corpo.put("nomeGrupo", oferta.grupoNome() == null ? "" : oferta.grupoNome());
        corpo.put("mensagemId", oferta.mensagemId() == null ? "" : oferta.mensagemId());
        corpo.put("remetente", oferta.produtor());
        corpo.put("texto", oferta.texto() == null ? "" : oferta.texto());
        corpo.put("timestamp", Instant.now().getEpochSecond());
        if (oferta.midias() != null && !oferta.midias().isEmpty()) {
            ArrayNode midias = corpo.putArray("midias");
            oferta.midias().forEach(midia -> {
                if (midia == null || midia.base64() == null || midia.base64().isBlank()) {
                    return;
                }
                ObjectNode item = midias.addObject();
                item.put("tipo", midia.tipo() == null || midia.tipo().isBlank() ? "image/jpeg" : midia.tipo());
                item.put("base64", midia.base64());
            });
        }
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
