package br.com.appcompregado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.appcompregado.domain.MensagemSaida;
import br.com.appcompregado.repository.MensagemSaidaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacaoWhatsappTest {

    private static final Pattern SEIS = Pattern.compile("(\\d{6})");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private MensagemSaidaRepository mensagens;

    @Test
    void cadastroGeraCodigoEStatusExigeLogin() throws Exception {
        JsonNode conta = cadastrar("(61) 98111-0001");
        assertThat(conta.get("telefoneVerificado").asBoolean()).isFalse();
        String codigo = conta.get("codigoVerificacao").asText();
        assertThat(codigo).matches("CG-\\d{6}");

        mvc.perform(get("/api/auth/verificar-status").header(HttpHeaders.AUTHORIZATION, bearer(conta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefoneVerificado").value(false))
                .andExpect(jsonPath("$.codigoVerificacao").value(codigo));
        mvc.perform(get("/api/auth/verificar-status")).andExpect(status().isForbidden());
    }

    @Test
    void codigoDigitadoConfirmaAContaLogada() throws Exception {
        JsonNode conta = cadastrar("61981110008");

        mvc.perform(post("/api/auth/verificar/solicitar").header(HttpHeaders.AUTHORIZATION, bearer(conta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reenviarEmSegundos").value(60));
        String codigo = codigoNaFila("61981110008");

        mvc.perform(post("/api/auth/verificar/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(conta))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigo", "000000".equals(codigo) ? "000001" : "000000"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/verificar/confirmar")
                        .header(HttpHeaders.AUTHORIZATION, bearer(conta))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("codigo", codigo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefoneVerificado").value(true));
        mvc.perform(get("/api/auth/verificar-status").header(HttpHeaders.AUTHORIZATION, bearer(conta)))
                .andExpect(jsonPath("$.telefoneVerificado").value(true));
    }

    @Test
    void contaSoNasceDepoisDoCodigo() throws Exception {
        String whatsapp = "61981110020";
        mvc.perform(post("/api/auth/cadastro/solicitar").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Nova", "whatsapp", whatsapp, "senha", "segredo1"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("whatsapp", whatsapp, "senha", "segredo1"))))
                .andExpect(status().isUnauthorized());

        String codigo = codigoNaFila(whatsapp);
        mvc.perform(post("/api/auth/cadastro/confirmar").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("whatsapp", whatsapp, "codigo", codigo))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.telefoneVerificado").value(true));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("whatsapp", whatsapp, "senha", "segredo1"))))
                .andExpect(status().isOk());
    }

    @Test
    void naoVerificadoNaoPublica() throws Exception {
        JsonNode conta = cadastrar("61981110005");
        String dados = """
                {"titulo":"Bezerros","descricao":"","preco":1500,"cidade":"Formosa - GO",
                 "categoriaAnimal":"BEZERRO","aptidao":"CORTE","raca":"NELORE","tipoVenda":"CABECA"}
                """;
        mvc.perform(multipart("/api/anuncios")
                        .file(new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                                dados.getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("imagens", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1}))
                        .header(HttpHeaders.AUTHORIZATION, bearer(conta)))
                .andExpect(status().isPreconditionRequired());
    }

    private String codigoNaFila(String whatsapp) {
        String texto = mensagens.findAll().stream()
                .filter(mensagem -> whatsapp.equals(mensagem.getTelefone()))
                .max(Comparator.comparing(MensagemSaida::getId))
                .orElseThrow()
                .getTexto();
        Matcher matcher = SEIS.matcher(texto);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private JsonNode cadastrar(String whatsapp) throws Exception {
        String corpo = json.writeValueAsString(Map.of("nome", "Teste", "whatsapp", whatsapp, "senha", "segredo1"));
        String resposta = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(resposta);
    }

    private static String bearer(JsonNode conta) {
        return "Bearer " + conta.get("token").asText();
    }
}
