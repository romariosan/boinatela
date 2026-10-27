package br.com.appcompregado.service;

import br.com.appcompregado.config.GeminiProperties;
import br.com.appcompregado.domain.enums.IntencaoProdutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Classifica a resposta livre do produtor. Não extrai anúncio e não inventa a estreia nem o link.
 * O retry de 429 e 503 fica neste cliente, fora de transação.
 */
@Service
public class GeminiDialogService {

    private static final Logger log = LoggerFactory.getLogger(GeminiDialogService.class);
    private static final int TENTATIVAS = 2;

    private static final String SISTEMA = """
            Você é o %s, do CompreGado, classificado de gado no Brasil.
            Fala humilde e direta, com vocabulário de quem mexe com gado, sem caricatura.
            Na primeira fala você se apresenta como %s. Nunca mande link na primeira mensagem.
            Não use as palavras consultor, plataforma, assessoria, startup ou otimizar.
            Classifique só a mensagem do produtor. Responda um JSON com intencao e reply_text.
            intencao é uma de: SIM_AINDA, VENDIDO, RECUSA, DESCONFIANCA, DUVIDA, OUTRO.
            SIM_AINDA: o lote ainda está com ele, ou ele aceita ver o anúncio.
            VENDIDO: ele já vendeu.
            RECUSA: não quer o anúncio, pediu para tirar, ou mandou parar.
            DESCONFIANCA: pergunta quem é, desconfia de golpe ou não entende o contato.
            DUVIDA: pergunta objetiva sobre o anúncio.
            OUTRO: o resto.
            reply_text só em DUVIDA e OUTRO, curto, em português, sem link. Nos outros casos, reply_text vazio.
            """.formatted(TextoAbordagem.PERSONAGEM, TextoAbordagem.PERSONAGEM);

    private final GeminiProperties propriedades;
    private final RestClient http;
    private final ObjectMapper json;

    public GeminiDialogService(GeminiProperties propriedades, RestClient.Builder builder, ObjectMapper json) {
        this.propriedades = propriedades;
        this.http = GeminiCorpo.cliente(builder);
        this.json = json;
    }

    public Optional<Classificacao> classificar(String textoProdutor) {
        if (propriedades.getKey() == null || propriedades.getKey().isBlank()) {
            log.info("Diálogo sem chave. Nada classificado.");
            return Optional.empty();
        }
        if (textoProdutor == null || textoProdutor.isBlank()) {
            return Optional.empty();
        }
        String corpo = postar(Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", SISTEMA))),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", "Mensagem do produtor:\n" + textoProdutor.trim())))),
                "generationConfig", Map.of("temperature", 0.2, "responseMimeType", "application/json")));
        if (corpo == null || corpo.isBlank()) {
            return Optional.empty();
        }
        return interpretarCorpo(corpo, json);
    }

    static Optional<Classificacao> interpretarCorpo(String corpo, ObjectMapper json) {
        try {
            JsonNode raiz = json.readTree(corpo);
            String texto = raiz.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (texto.isBlank()) {
                texto = corpo;
            }
            int inicio = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (inicio < 0 || fim <= inicio) {
                return Optional.empty();
            }
            JsonNode dados = json.readTree(texto.substring(inicio, fim + 1));
            if (!dados.hasNonNull("intencao") || !dados.get("intencao").isTextual()) {
                return Optional.empty();
            }
            IntencaoProdutor intencao;
            try {
                intencao = IntencaoProdutor.valueOf(dados.get("intencao").asText().trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
            String reply = "";
            if (dados.has("reply_text") && !dados.get("reply_text").isNull()) {
                if (!dados.get("reply_text").isTextual()) {
                    return Optional.empty();
                }
                reply = dados.get("reply_text").asText("");
            }
            return Optional.of(new Classificacao(intencao, reply));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String postar(Map<String, Object> corpo) {
        RestClientResponseException ultima = null;
        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                return GeminiCorpo.ler(http.post()
                        .uri(propriedades.getUrl())
                        .header("x-goog-api-key", propriedades.getKey())
                        .header("Accept", "application/json")
                        .header("Accept-Encoding", "identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(corpo));
            } catch (RestClientResponseException e) {
                int codigo = e.getStatusCode().value();
                if ((codigo == 429 || codigo == 503) && tentativa < TENTATIVAS) {
                    ultima = e;
                    dormir(200L * tentativa);
                    continue;
                }
                log.warn("Gemini diálogo HTTP {}", codigo);
                return null;
            } catch (RestClientException e) {
                log.warn("Gemini diálogo indisponível: {}", GeminiCorpo.detalhe(e));
                return null;
            }
        }
        if (ultima != null) {
            log.warn("Gemini diálogo HTTP {} depois das tentativas", ultima.getStatusCode().value());
        }
        return null;
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record Classificacao(IntencaoProdutor intencao, String replyText) {
    }
}
