package br.com.appcompregado.web;

import br.com.appcompregado.config.WhatsappProperties;
import br.com.appcompregado.domain.MensagemSaida;
import br.com.appcompregado.domain.enums.ChipMensagem;
import br.com.appcompregado.service.FilaMensagemService;
import br.com.appcompregado.web.dto.MensagemFilaResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** O PC busca a fila. A VPS não abre conexão até a casa da pessoa. */
@RestController
public class RoboController {

    private final WhatsappProperties propriedades;
    private final FilaMensagemService fila;

    public RoboController(WhatsappProperties propriedades, FilaMensagemService fila) {
        this.propriedades = propriedades;
        this.fila = fila;
    }

    @GetMapping("/api/robo/fila")
    public List<MensagemFilaResponse> fila(
            @RequestHeader(name = "X-Robo-Token", required = false) String token,
            @RequestParam(name = "chip", required = false) String chip) {
        validarToken(token);
        ChipMensagem filtro = chip == null || chip.isBlank() ? null : lerChip(chip);
        return fila.pendentes(filtro).stream().map(RoboController::resposta).toList();
    }

    @PostMapping("/api/robo/fila/{id}/enviada")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enviada(
            @RequestHeader(name = "X-Robo-Token", required = false) String token,
            @PathVariable Long id) {
        validarToken(token);
        fila.marcarEnviada(id);
    }

    @PostMapping("/api/robo/fila/{id}/falhou")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void falhou(
            @RequestHeader(name = "X-Robo-Token", required = false) String token,
            @PathVariable Long id) {
        validarToken(token);
        fila.marcarFalha(id);
    }

    private static ChipMensagem lerChip(String chip) {
        try {
            return ChipMensagem.valueOf(chip.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chip desconhecido.");
        }
    }

    private static MensagemFilaResponse resposta(MensagemSaida mensagem) {
        return new MensagemFilaResponse(
                mensagem.getId(),
                mensagem.getChip().name(),
                "55" + mensagem.getTelefone(),
                mensagem.getTexto());
    }

    private void validarToken(String token) {
        String esperado = propriedades.getRoboToken();
        if (esperado == null || esperado.isBlank()) {
            esperado = propriedades.getWebhookToken();
        }
        if (esperado == null || esperado.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Robô não configurado.");
        }
        if (token == null || !MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
