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
import org.springframework.web.client.RestClientResponseException;

/**
 * Lê título, preço, raça e quantidade de um anúncio de grupo. Qualquer falha volta vazio:
 * quem chama grava o texto original e não perde o lead.
 */
@Service
public class GeminiExtractionService {

    private static final Logger log = LoggerFactory.getLogger(GeminiExtractionService.class);
    static final int TENTATIVAS = 2;

    private static final String PEDIDO = """
            Você extrai anúncios de gado de WhatsApp. NÃO invente localidade.
            Fontes de cidade (só estas, nesta ordem rigorosa):
            1) Local explícito no corpo da MENSAGEM (município; se não houver, estado/DF tipo "estou no DF").
            2) Só se a mensagem NÃO tiver local: município explícito no TÍTULO DO GRUPO.
            3) Se ainda assim não houver: cidade DEVE ser exatamente "Local a combinar" e uf null.
            PROIBIDO: inventar Formosa, Entorno, Goiás genérico ou qualquer cidade "provável".
            Referência do grupo (pode ser "Local a combinar"; não use se a mensagem já trouxe local): cidade=%s uf=%s.
            Responda só JSON: titulo (curto), preco (número em reais ou null),
            raca (NELORE|ANGUS|GIROLANDO|BRAHMAN|SENEPOL|BRANGUS|GUZERA|TABAPUA|GIR|HOLANDES|CARACU|CANCHIM|MISTO|OUTRA),
            quantidade (inteiro ou null), cidade (nome sem UF, ou "Local a combinar"), uf (sigla ou null),
            telefoneVendedorExtraido (só dígitos ou null), origem (WHATSAPP|FACEBOOK).
            Não invente preço.
            Se a primeira linha for 'FB:' + telefone, origem=FACEBOOK e telefoneVendedorExtraido=dígitos; senão origem=WHATSAPP.
            Mensagem:
            """;

    private final GeminiProperties propriedades;
    private final RestClient http;
    private final ObjectMapper json;

    public GeminiExtractionService(GeminiProperties propriedades, RestClient.Builder builder, ObjectMapper json) {
        this.propriedades = propriedades;
        this.http = GeminiCorpo.cliente(builder);
        this.json = json;
    }

    public Optional<ExtracaoAnuncio> extrair(String texto, String cidadePadrao, String ufPadrao) {
        if (propriedades.getKey() == null || propriedades.getKey().isBlank()) {
            return Optional.empty();
        }
        String cidade = CidadesGoias.ehSemCidade(cidadePadrao) ? CidadesGoias.SEM_CIDADE : cidadePadrao.trim();
        String uf = ufPadrao == null || !ufPadrao.matches("(?i)[a-z]{2}") ? "" : ufPadrao.trim().toUpperCase();
        for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
            try {
                String corpo = GeminiCorpo.ler(http.post()
                        .uri(propriedades.getUrl())
                        .header("x-goog-api-key", propriedades.getKey())
                        .header("Accept", "application/json")
                        .header("Accept-Encoding", "identity")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "contents", new Object[] {
                                        Map.of("parts", new Object[] {
                                                Map.of("text", PEDIDO.formatted(cidade, uf.isBlank() ? "?" : uf) + texto)
                                        })
                                },
                                "generationConfig", Map.of("temperature", 0.1, "responseMimeType", "application/json"))));
                ExtracaoAnuncio lido = interpretarResposta(corpo, json, cidade, uf);
                return Optional.of(priorizarLugarDoTexto(lido, texto, cidade));
            } catch (RestClientResponseException e) {
                int codigo = e.getStatusCode().value();
                if ((codigo == 429 || codigo == 503) && tentativa < TENTATIVAS) {
                    // 429/503 costumam liberar em poucos segundos; 1s e 2s cabem no proxy de 60s.
                    dormir(1000L * tentativa);
                    continue;
                }
                log.warn("Gemini indisponível ou resposta ilegível: {}", GeminiCorpo.detalhe(e));
                return Optional.empty();
            } catch (RestClientException e) {
                if (tentativa < TENTATIVAS) {
                    dormir(1000L * tentativa);
                    continue;
                }
                log.warn("Gemini indisponível ou resposta ilegível: {}", GeminiCorpo.detalhe(e));
                return Optional.empty();
            } catch (IllegalArgumentException e) {
                log.warn("Gemini indisponível ou resposta ilegível: {}", GeminiCorpo.detalhe(e));
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Texto da mensagem primeiro (município, depois estado). Sem local no texto, mantém o lido
     * (padrão do grupo / SEM_CIDADE). Nunca inventa Formosa aqui.
     */
    static ExtracaoAnuncio priorizarLugarDoTexto(ExtracaoAnuncio lido, String texto, String cidadePadrao) {
        if (lido == null) {
            return null;
        }
        Optional<CidadesGoias.Local> municipio = CidadesGoias.inferirMunicipio(texto);
        if (municipio.isPresent()) {
            CidadesGoias.Local local = municipio.get();
            return new ExtracaoAnuncio(
                    lido.titulo(),
                    lido.preco(),
                    lido.raca(),
                    lido.quantidade(),
                    local.cidade(),
                    local.uf(),
                    lido.telefoneVendedorExtraido(),
                    lido.origem());
        }
        Optional<CidadesGoias.Local> estado = CidadesGoias.inferirEstado(texto);
        if (estado.isPresent()) {
            CidadesGoias.Local local = estado.get();
            return new ExtracaoAnuncio(
                    lido.titulo(),
                    lido.preco(),
                    lido.raca(),
                    lido.quantidade(),
                    local.cidade(),
                    local.uf(),
                    lido.telefoneVendedorExtraido(),
                    lido.origem());
        }
        if (CidadesGoias.ehSemCidade(lido.cidade()) || CidadesGoias.ehSemCidade(cidadePadrao)) {
            return new ExtracaoAnuncio(
                    lido.titulo(),
                    lido.preco(),
                    lido.raca(),
                    lido.quantidade(),
                    CidadesGoias.SEM_CIDADE,
                    null,
                    lido.telefoneVendedorExtraido(),
                    lido.origem());
        }
        return lido;
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
            if (cidade.isBlank() || cidade.equalsIgnoreCase("null") || CidadesGoias.ehSemCidade(cidade)) {
                cidade = CidadesGoias.ehSemCidade(cidadePadrao) ? CidadesGoias.SEM_CIDADE : cidadePadrao;
            }
            if (CidadesGoias.ehSemCidade(cidade)) {
                cidade = CidadesGoias.SEM_CIDADE;
                uf = "";
            } else if (!uf.matches("(?i)[a-z]{2}")) {
                uf = ufPadrao != null && ufPadrao.matches("(?i)[a-z]{2}") ? ufPadrao : "";
            }
            String origem = dados.path("origem").asText("").trim();
            origem = "FACEBOOK".equalsIgnoreCase(origem) ? "FACEBOOK" : "WHATSAPP";
            return new ExtracaoAnuncio(
                    titulo,
                    lerPreco(dados.get("preco")),
                    lerRaca(dados.path("raca").asText("")),
                    lerQuantidade(dados.get("quantidade")),
                    cidade,
                    uf.isBlank() ? null : uf.toUpperCase(),
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
