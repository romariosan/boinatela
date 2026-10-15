package br.com.appcompregado.web;

import br.com.appcompregado.service.EvolutionWebhookService;
import br.com.appcompregado.service.WhatsappSimulador;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * "WhatsApp simulado" para testar cadastro e recuperação de senha sem o bot: mostra o que o bot enviaria
 * e entrega mensagens do usuário pelo mesmo caminho do webhook da Evolution. Só existe com
 * app.whatsapp.envio-no-log=true (desligado no perfil prod) e só responde a quem está na própria máquina.
 */
@RestController
@RequestMapping("/dev/whatsapp")
@ConditionalOnProperty(prefix = "app.whatsapp", name = "envio-no-log", havingValue = "true")
public class WhatsappSimuladorController {

    public record EnvioSimulado(@NotBlank String numero, @NotBlank String texto) {
    }

    private final WhatsappSimulador simulador;
    private final EvolutionWebhookService evolution;
    private final ObjectMapper json;

    public WhatsappSimuladorController(
            WhatsappSimulador simulador, EvolutionWebhookService evolution, ObjectMapper json) {
        this.simulador = simulador;
        this.evolution = evolution;
        this.json = json;
    }

    @ModelAttribute
    void apenasLocal(HttpServletRequest request) {
        try {
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        } catch (UnknownHostException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public Resource pagina() {
        return new ClassPathResource("dev/whatsapp-simulado.html");
    }

    @GetMapping("/mensagens")
    public List<WhatsappSimulador.Mensagem> mensagens() {
        return simulador.listar();
    }

    @PostMapping("/mensagens")
    public Map<String, Boolean> enviarAoBot(@Valid @RequestBody EnvioSimulado envio) {
        if (envio.numero().replaceAll("\\D", "").length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o WhatsApp com DDD.");
        }
        String numero = simulador.registrar(envio.numero(), WhatsappSimulador.Direcao.PARA_O_BOT, envio.texto());
        ObjectNode evento = json.createObjectNode().put("event", "messages.upsert");
        ObjectNode dados = evento.putObject("data");
        dados.putObject("key").put("remoteJid", "55" + numero + "@s.whatsapp.net").put("fromMe", false);
        dados.putObject("message").put("conversation", envio.texto());
        return Map.of("verificado", evolution.processar(evento));
    }

    @DeleteMapping("/mensagens")
    public void limpar() {
        simulador.limpar();
    }
}
