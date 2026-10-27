package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.appcompregado.config.GeminiProperties;
import br.com.appcompregado.domain.enums.IntencaoProdutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiCorpoTest {

    private static final MediaType OCTET_SEM_CHARSET = new MediaType("application", "octet-stream");

    @Test
    void jsonSemCharsetAindaExtraiTituloPrecoECidade() {
        GeminiExtractionService service = extracao(envelope(), MediaType.APPLICATION_JSON);

        var extraido = service.extrair("vendo vaca de leite a 10 mil no Goiás", "Formosa", "GO").orElseThrow();

        assertThat(extraido.titulo()).isEqualTo("Vaca de leite");
        assertThat(extraido.preco()).isEqualByComparingTo("10000");
        assertThat(extraido.cidade()).isEqualTo("Goiás");
    }

    @Test
    void octetStreamAindaExtraiTituloPrecoECidade() {
        GeminiExtractionService service = extracao(envelope(), OCTET_SEM_CHARSET);

        var extraido = service.extrair("vendo vaca de leite a 10 mil no Goiás", "Formosa", "GO").orElseThrow();

        assertThat(extraido.titulo()).isEqualTo("Vaca de leite");
        assertThat(extraido.preco()).isEqualByComparingTo("10000");
        assertThat(extraido.cidade()).isEqualTo("Goiás");
    }

    @Test
    void gzipOctetStreamAindaExtraiOsMesmosCampos() throws Exception {
        GeminiExtractionService service = extracao(gzip(envelope()), OCTET_SEM_CHARSET);

        var extraido = service.extrair("vendo vaca de leite a 10 mil no Goiás", "Formosa", "GO").orElseThrow();

        assertThat(extraido.titulo()).isEqualTo("Vaca de leite");
        assertThat(extraido.preco()).isEqualByComparingTo("10000");
        assertThat(extraido.cidade()).isEqualTo("Goiás");
    }

    @Test
    void indisponivelDepoisAindaExtraiOJson() {
        byte[] erro = """
                {"error":{"code":503,"message":"alta demanda","status":"UNAVAILABLE"}}
                """.getBytes(StandardCharsets.UTF_8);
        GeminiProperties props = propriedades();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(props.getUrl())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body(erro).contentType(OCTET_SEM_CHARSET));
        server.expect(requestTo(props.getUrl())).andRespond(withSuccess(envelope(), OCTET_SEM_CHARSET));
        GeminiExtractionService service = new GeminiExtractionService(props, builder, new ObjectMapper());

        var extraido = service.extrair("vendo vaca de leite a 10 mil no Goiás", "Formosa", "GO").orElseThrow();

        assertThat(extraido.titulo()).isEqualTo("Vaca de leite");
        assertThat(extraido.preco()).isEqualByComparingTo("10000");
        assertThat(extraido.cidade()).isEqualTo("Goiás");
        server.verify();
    }

    @Test
    void esperaTotalCabeNoProxyDoNginx() {
        assertThat(GeminiExtractionService.TENTATIVAS * GeminiCorpo.LEITURA_SEGUNDOS).isLessThan(50);
        assertThat(GeminiCorpo.LEITURA_SEGUNDOS).isGreaterThan(7);
    }

    @Test
    void leituraEstouradaDepoisAindaExtraiOJson() throws Exception {
        GeminiProperties props = propriedades();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(props.getUrl())).andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(requestTo(props.getUrl())).andRespond(withSuccess(envelope(), OCTET_SEM_CHARSET));
        GeminiExtractionService service = new GeminiExtractionService(props, builder, new ObjectMapper());

        var extraido = service.extrair("vendo vaca de leite a 10 mil no Goiás", "Formosa", "GO").orElseThrow();

        assertThat(extraido.titulo()).isEqualTo("Vaca de leite");
        assertThat(extraido.preco()).isEqualByComparingTo("10000");
        assertThat(extraido.cidade()).isEqualTo("Goiás");
        server.verify();
    }

    @Test
    void octetStreamAindaClassificaAIntencao() {
        String json = """
                {"candidates":[{"content":{"parts":[{"text":"{\\"intencao\\":\\"SIM_AINDA\\",\\"reply_text\\":\\"\\"}"}]}}]}
                """;
        GeminiDialogService service = dialogo(json.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM);

        var classificacao = service.classificar("ainda está na mão").orElseThrow();

        assertThat(classificacao.intencao()).isEqualTo(IntencaoProdutor.SIM_AINDA);
        assertThat(classificacao.replyText()).isEmpty();
    }

    private static byte[] envelope() {
        String interno = """
                {"titulo":"Vaca de leite","preco":10000,"raca":"GIR","quantidade":1,"cidade":"Goiás","uf":"GO"}""";
        String corpo = """
                {"candidates":[{"content":{"parts":[{"text":%s}]}}]}"""
                .formatted(aspas(interno));
        return corpo.getBytes(StandardCharsets.UTF_8);
    }

    private static String aspas(String json) {
        return "\"" + json.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static byte[] gzip(byte[] cru) throws Exception {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(saida)) {
            gzip.write(cru);
        }
        return saida.toByteArray();
    }

    private static GeminiExtractionService extracao(byte[] corpo, MediaType tipo) {
        GeminiProperties props = propriedades();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(props.getUrl())).andRespond(withSuccess(corpo, tipo));
        return new GeminiExtractionService(props, builder, new ObjectMapper());
    }

    private static GeminiDialogService dialogo(byte[] corpo, MediaType tipo) {
        GeminiProperties props = propriedades();
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(props.getUrl())).andRespond(withSuccess(corpo, tipo));
        return new GeminiDialogService(props, builder, new ObjectMapper());
    }

    private static GeminiProperties propriedades() {
        GeminiProperties props = new GeminiProperties();
        props.setKey("teste");
        props.setUrl("https://gemini.test/gerar");
        return props;
    }
}
