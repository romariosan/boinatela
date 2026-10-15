package br.com.appcompregado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.repository.UsuarioRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AlterarSenhaTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void trocaASenhaDevolveTokenNovoEDerrubaOsAntigos() throws Exception {
        String tokenAntigo = cadastrar("61983330001", "antiga1");

        String resposta = alterar(tokenAntigo, "antiga1", "nova1234", "nova1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String tokenNovo = json.readTree(resposta).get("token").asText();

        entrar("61983330001", "nova1234").andExpect(status().isOk());
        entrar("61983330001", "antiga1").andExpect(status().isUnauthorized());
        meusAnuncios(tokenAntigo).andExpect(status().isForbidden());
        meusAnuncios(tokenNovo).andExpect(status().isOk());
    }

    @Test
    void senhaAtualErradaDa422ENaoTroca() throws Exception {
        String token = cadastrar("61983330002", "antiga1");
        alterar(token, "errada1", "nova1234", "nova1234").andExpect(status().isUnprocessableEntity());
        entrar("61983330002", "antiga1").andExpect(status().isOk());
        meusAnuncios(token).andExpect(status().isOk());
    }

    @Test
    void confirmacaoDiferenteOuSenhaIgualDa400() throws Exception {
        String token = cadastrar("61983330003", "antiga1");
        alterar(token, "antiga1", "nova1234", "nova9999").andExpect(status().isBadRequest());
        alterar(token, "antiga1", "antiga1", "antiga1").andExpect(status().isBadRequest());
        entrar("61983330003", "antiga1").andExpect(status().isOk());
    }

    @Test
    void senhaCurtaDa400SemMostrarASenhaNoLog(CapturedOutput log) throws Exception {
        String token = cadastrar("61983330004", "antiga1");
        alterar(token, "antiga1", "xyz12", "xyz12")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.camposInvalidos").isArray());
        assertThat(log.getAll()).doesNotContain("xyz12").doesNotContain("antiga1");
    }

    @Test
    void cincoErrosSeguidosBloqueiamATrocaPor15Minutos() throws Exception {
        String token = cadastrar("61983330005", "antiga1");
        for (int restantes = 4; restantes >= 1; restantes--) {
            alterar(token, "errada1", "nova1234", "nova1234")
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.tentativasRestantes").value(restantes));
        }
        alterar(token, "errada1", "nova1234", "nova1234")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.minutosRestantes").value(15));

        // Bloqueada, nem a senha certa passa.
        alterar(token, "antiga1", "nova1234", "nova1234").andExpect(status().isTooManyRequests());

        Usuario usuario = usuarios.findByWhatsapp("61983330005").orElseThrow();
        usuario.setTrocaSenhaBloqueadaAte(LocalDateTime.now().minusSeconds(1));
        usuarios.save(usuario);
        alterar(token, "antiga1", "nova1234", "nova1234").andExpect(status().isOk());
    }

    @Test
    void acertarASenhaAtualZeraOsErros() throws Exception {
        String token = cadastrar("61983330006", "antiga1");
        for (int i = 0; i < 4; i++) {
            alterar(token, "errada1", "nova1234", "nova1234").andExpect(status().isUnprocessableEntity());
        }
        alterar(token, "antiga1", "nova1234", "outra999").andExpect(status().isBadRequest());
        alterar(token, "errada1", "nova1234", "nova1234")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.tentativasRestantes").value(4));
    }

    @Test
    void cadastroComSenhaCurtaDa400SemMostrarASenhaNoLog(CapturedOutput log) throws Exception {
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(
                                Map.of("nome", "Teste", "whatsapp", "61983330007", "senha", "qwe45"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.camposInvalidos[0]").value("senha"));
        assertThat(log.getAll()).doesNotContain("qwe45");
    }

    @Test
    void contaAntigaComSenhaDe4CaracteresContinuaEntrandoETrocando() throws Exception {
        Usuario antigo = new Usuario();
        antigo.setNome("Conta antiga");
        antigo.setWhatsapp("61983330008");
        antigo.setSenha(passwordEncoder.encode("abcd"));
        usuarios.save(antigo);

        String resposta = entrar("61983330008", "abcd")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(resposta).get("token").asText();
        alterar(token, "abcd", "nova1234", "nova1234").andExpect(status().isOk());
    }

    @Test
    void semLoginDa403() throws Exception {
        mvc.perform(put("/api/usuarios/alterar-senha").contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("antiga1", "nova1234", "nova1234")))
                .andExpect(status().isForbidden());
    }

    private String cadastrar(String whatsapp, String senha) throws Exception {
        String resposta = mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Teste", "whatsapp", whatsapp, "senha", senha))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode corpo = json.readTree(resposta);
        return corpo.get("token").asText();
    }

    private ResultActions alterar(String token, String atual, String nova, String confirmacao) throws Exception {
        return mvc.perform(put("/api/usuarios/alterar-senha")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo(atual, nova, confirmacao)));
    }

    private String corpo(String atual, String nova, String confirmacao) throws Exception {
        return json.writeValueAsString(
                Map.of("senhaAtual", atual, "novaSenha", nova, "confirmarNovaSenha", confirmacao));
    }

    private ResultActions entrar(String whatsapp, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("whatsapp", whatsapp, "senha", senha))));
    }

    private ResultActions meusAnuncios(String token) throws Exception {
        return mvc.perform(get("/api/anuncios/meus").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
