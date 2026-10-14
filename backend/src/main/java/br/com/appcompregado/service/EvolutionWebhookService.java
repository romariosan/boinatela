package br.com.appcompregado.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Traduz o webhook "messages.upsert" da Evolution API para a validação reversa e responde ao usuário
 * pelo próprio WhatsApp. Só reage a mensagens com código "CG-": o número do bot pode receber outras conversas.
 */
@Service
public class EvolutionWebhookService {

    static final String CONFIRMADO =
            "✅ Pronto! Seu WhatsApp foi confirmado no CompreGado. Pode voltar ao app e publicar seu anúncio.";
    static final String NAO_CONFIRMADO =
            "Não conseguimos confirmar com esse código. Se você já confirmou, é só voltar ao app. "
                    + "Se não, confira se o WhatsApp cadastrado no app é este mesmo e toque de novo em "
                    + "\"Confirmar no meu WhatsApp\".";

    private static final Logger log = LoggerFactory.getLogger(EvolutionWebhookService.class);

    private final VerificacaoWhatsappService verificacao;
    private final WhatsappBotService bot;
    private final TaskExecutor executor;

    public EvolutionWebhookService(
            VerificacaoWhatsappService verificacao, WhatsappBotService bot, TaskExecutor executor) {
        this.verificacao = verificacao;
        this.bot = bot;
        this.executor = executor;
    }

    public boolean processar(JsonNode evento) {
        if (!"messages.upsert".equalsIgnoreCase(evento.path("event").asText())) {
            return false;
        }
        JsonNode dados = evento.path("data");
        if (!dados.isArray()) {
            return processarMensagem(dados);
        }
        boolean algumVerificado = false;
        for (JsonNode mensagem : dados) {
            algumVerificado |= processarMensagem(mensagem);
        }
        return algumVerificado;
    }

    private boolean processarMensagem(JsonNode mensagem) {
        JsonNode chave = mensagem.path("key");
        if (chave.path("fromMe").asBoolean(false)) {
            return false;
        }
        String texto = texto(mensagem.path("message"));
        if (VerificacaoWhatsappService.extrairCodigo(texto).isEmpty()) {
            return false;
        }
        Optional<String> remetente = remetente(chave);
        if (remetente.isEmpty()) {
            log.info("Código de verificação recebido sem número de telefone identificável.");
            return false;
        }
        boolean verificado = verificacao.processarMensagem(remetente.get(), texto);
        // A resposta sai depois: a Evolution espera o retorno do webhook e reenviaria se ele demorasse.
        String resposta = verificado ? CONFIRMADO : NAO_CONFIRMADO;
        executor.execute(() -> bot.tentarEnviar(remetente.get(), resposta));
        return verificado;
    }

    /**
     * Contatos com identificador privado chegam como "...@lid"; o telefone real vem em remoteJidAlt
     * (ou senderPn, em versões anteriores). Grupos e listas de transmissão ficam de fora.
     */
    static Optional<String> remetente(JsonNode chave) {
        String jid = chave.path("remoteJid").asText("");
        if (jid.endsWith("@s.whatsapp.net")) {
            return Optional.of(jid);
        }
        if (jid.endsWith("@lid")) {
            for (String campo : List.of("remoteJidAlt", "senderPn")) {
                String alternativo = chave.path(campo).asText("");
                if (alternativo.endsWith("@s.whatsapp.net") || alternativo.matches("\\d{10,15}")) {
                    return Optional.of(alternativo);
                }
            }
        }
        return Optional.empty();
    }

    private static String texto(JsonNode conteudo) {
        if (conteudo.hasNonNull("conversation")) {
            return conteudo.get("conversation").asText();
        }
        if (conteudo.path("extendedTextMessage").hasNonNull("text")) {
            return conteudo.path("extendedTextMessage").get("text").asText();
        }
        JsonNode temporaria = conteudo.path("ephemeralMessage").path("message");
        return temporaria.isMissingNode() ? "" : texto(temporaria);
    }
}
