package com.boinatela.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VerificacaoWhatsappTest {

    private static final String WEBHOOK = "/api/webhook/whatsapp";
    private static final String TOKEN = "token-de-teste";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void cadastroGeraCodigoEStatusExigeLogin() throws Exception {
        JsonNode conta = cadastrar("(61) 98111-0001");
        assertThat(conta.get("telefoneVerificado").asBoolean()).isFalse();
        String codigo = conta.get("codigoVerificacao").asText();
        assertThat(codigo).matches("BT-\\d{6}");

        mvc.perform(get("/api/auth/verificar-status").header(HttpHeaders.AUTHORIZATION, bearer(conta)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefoneVerificado").value(false))
                .andExpect(jsonPath("$.codigoVerificacao").value(codigo));
        mvc.perform(get("/api/auth/verificar-status")).andExpect(status().isForbidden());
    }

    @Test
    void webhookSemSegredoERecusado() throws Exception {
        String corpo = mensagem("5561981110002", "BT-123456");
        mvc.perform(post(WEBHOOK).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(WEBHOOK).header("X-Webhook-Token", "chute").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void codigoEnviadoDeOutroNumeroNaoVerifica() throws Exception {
        JsonNode conta = cadastrar("61981110003");
        String codigo = conta.get("codigoVerificacao").asText();

        webhook("5561977776666", "Codigo: " + codigo).andExpect(jsonPath("$.verificado").value(false));
        mvc.perform(get("/api/auth/verificar-status").header(HttpHeaders.AUTHORIZATION, bearer(conta)))
                .andExpect(jsonPath("$.telefoneVerificado").value(false));
    }

    @Test
    void codigoDoProprioNumeroVerificaMesmoSemONonoDigito() throws Exception {
        JsonNode conta = cadastrar("61981110004");
        String codigo = conta.get("codigoVerificacao").asText();
        String texto = "Quero confirmar meu cadastro no Boi na Tela. Codigo: " + codigo.toLowerCase();

        webhook("556181110004@s.whatsapp.net", texto).andExpect(jsonPath("$.verificado").value(true));
        mvc.perform(get("/api/auth/verificar-status").header(HttpHeaders.AUTHORIZATION, bearer(conta)))
                .andExpect(jsonPath("$.telefoneVerificado").value(true))
                .andExpect(jsonPath("$.codigoVerificacao").doesNotExist());

        webhook("556181110004@s.whatsapp.net", texto).andExpect(jsonPath("$.verificado").value(false));
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

    private JsonNode cadastrar(String whatsapp) throws Exception {
        String corpo = json.writeValueAsString(Map.of("nome", "Teste", "whatsapp", whatsapp, "senha", "segredo1"));
        String resposta = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(resposta);
    }

    private ResultActions webhook(String remetente, String texto) throws Exception {
        return mvc.perform(post(WEBHOOK).header("X-Webhook-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mensagem(remetente, texto)))
                .andExpect(status().isOk());
    }

    private String mensagem(String remetente, String texto) throws Exception {
        return json.writeValueAsString(Map.of("remetente", remetente, "mensagem", texto));
    }

    private static String bearer(JsonNode conta) {
        return "Bearer " + conta.get("token").asText();
    }
}
