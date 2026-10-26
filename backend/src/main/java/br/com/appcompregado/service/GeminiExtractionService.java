package br.com.appcompregado.service;

import br.com.appcompregado.config.GeminiProperties;
import br.com.appcompregado.domain.enums.Raca;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Lê título, preço, raça e quantidade de um anúncio de grupo. Qualquer falha volta vazio:
 * quem chama grava o texto original e não perde o lead.
 */
@Service
public class GeminiExtractionService {

    private static final Logger log = LoggerFactory.getLogger(GeminiExtractionService.class);

    private static final String PEDIDO = """
            Você é especialista na geografia do Brasil.
            Extraia um anúncio de gado desta mensagem de WhatsApp.
            O grupo de origem tem cidade padrão %s e UF %s.
            Se a mensagem citar uma cidade, use essa cidade, em qualquer estado. Se não citar, use a cidade padrão do grupo.
            A UF é a sigla de duas letras do estado dessa cidade.
            Responda só um JSON com as chaves titulo (string curta), preco (número em reais, ou null),
            raca (uma de NELORE, ANGUS, GIROLANDO, BRAHMAN, SENEPOL, BRANGUS, GUZERA, TABAPUA, GIR,
            HOLANDES, CARACU, CANCHIM, MISTO, OUTRA), quantidade (inteiro de cabeças, ou null),
            cidade (só o nome, sem a UF) e uf (sigla).
            Não invente preço. Não troque a cidade padrão se a mensagem não disser outra.
            Atenção: Se a primeira linha do texto começar com a tag 'FB:' seguida de um número de telefone (ex: 'FB: 61999998888' ou 'FB: 61 99999-8888'), isso significa que o anúncio foi curado do Facebook. Nesse caso, extraia esse número e retorne no campo 'telefoneVendedorExtraido' (apenas números) e defina o campo 'origem' como 'FACEBOOK'. Caso contrário, retorne null no telefoneExtraido e 'WHATSAPP' na origem.
            O JSON também tem as chaves telefoneVendedorExtraido (string só com dígitos, ou null) e origem (WHATSAPP ou FACEBOOK).
            Mensagem:
            """;

    private final GeminiProperties propriedades;
    private final RestClient http;
    private final ObjectMapper json;

    public GeminiExtractionService(GeminiProperties propriedades, RestClient.Builder builder, ObjectMapper json) {
        this.propriedades = propriedades;
        this.http = builder.build();
        this.json = json;
    }

    public Optional<ExtracaoAnuncio> extrair(String texto, String cidadePadrao, String ufPadrao) {
        if (propriedades.getKey() == null || propriedades.getKey().isBlank()) {
            return Optional.empty();
        }
        String cidade = cidadePadrao == null || cidadePadrao.isBlank() ? "Formosa" : cidadePadrao.trim();
        String uf = ufPadrao == null || !ufPadrao.matches("(?i)[a-z]{2}") ? "GO" : ufPadrao.trim().toUpperCase();
        try {
            String corpo = http.post()
                    .uri(propriedades.getUrl())
                    .header("x-goog-api-key", propriedades.getKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "contents", new Object[] {
                                    Map.of("parts", new Object[] {
                                            Map.of("text", PEDIDO.formatted(cidade, uf) + texto)
                                    })
                            },
                            "generationConfig", Map.of("temperature", 0.1, "responseMimeType", "application/json")))
                    .retrieve()
                    .body(String.class);
            return Optional.of(interpretarResposta(corpo, json, cidade, uf));
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Gemini indisponível ou resposta ilegível: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static ExtracaoAnuncio interpretarResposta(String corpo, ObjectMapper json, String cidadePadrao, String ufPadrao) {
        try {
            JsonNode raiz = json.readTree(corpo);
            String texto = raiz.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            if (texto.isBlank()) {
                texto = corpo;
            }
            int inicio = texto.indexOf('{');
            int fim = texto.lastIndexOf('}');
            if (inicio < 0 || fim <= inicio) {
                throw new IllegalArgumentException("JSON ausente");
            }
            JsonNode dados = json.readTree(texto.substring(inicio, fim + 1));
            String titulo = dados.path("titulo").asText("").trim();
            if (titulo.isBlank()) {
                throw new IllegalArgumentException("titulo vazio");
            }
            String cidade = dados.path("cidade").asText("").trim();
            String uf = dados.path("uf").asText("").trim();
            if (cidade.isBlank() || cidade.equalsIgnoreCase("null")) {
                cidade = cidadePadrao;
            }
            if (!uf.matches("(?i)[a-z]{2}")) {
                uf = ufPadrao;
            }
            String origem = dados.path("origem").asText("").trim();
            origem = "FACEBOOK".equalsIgnoreCase(origem) ? "FACEBOOK" : "WHATSAPP";
            return new ExtracaoAnuncio(
                    titulo,
                    lerPreco(dados.get("preco")),
                    lerRaca(dados.path("raca").asText("")),
                    lerQuantidade(dados.get("quantidade")),
                    cidade,
                    uf.toUpperCase(),
                    lerTelefoneExtraido(dados.get("telefoneVendedorExtraido")),
                    origem);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static BigDecimal lerPreco(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        String texto = node.asText("").trim();
        if (texto.isBlank()) {
            return null;
        }
        String limpo = texto.replaceAll("[^0-9,.-]", "");
        if (limpo.contains(",") && limpo.lastIndexOf(',') > limpo.lastIndexOf('.')) {
            limpo = limpo.replace(".", "").replace(',', '.');
        } else {
            limpo = limpo.replace(",", "");
        }
        return new BigDecimal(limpo);
    }

    static Raca lerRaca(String valor) {
        if (valor == null || valor.isBlank()) {
            return Raca.OUTRA;
        }
        String chave = Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z]", "")
                .toUpperCase();
        try {
            return Raca.valueOf(chave);
        } catch (IllegalArgumentException e) {
            return Raca.OUTRA;
        }
    }

    private static String lerTelefoneExtraido(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String texto = node.asText("").replaceAll("\\D", "");
        return texto.isBlank() ? null : texto;
    }

    private static Integer lerQuantidade(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        int valor = node.asInt();
        return valor > 0 && valor <= 100_000 ? valor : null;
    }

    /** Extração do Gemini. origem WHATSAPP no grupo; FACEBOOK na curadoria com a tag FB:. */
    public record ExtracaoAnuncio(
            String titulo,
            BigDecimal preco,
            Raca raca,
            Integer quantidade,
            String cidade,
            String uf,
            String telefoneVendedorExtraido,
            String origem) {
    }
}
