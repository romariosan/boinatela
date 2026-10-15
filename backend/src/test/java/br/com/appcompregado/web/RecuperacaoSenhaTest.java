package br.com.appcompregado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.appcompregado.repository.CodigoRecuperacaoRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.service.WhatsappBotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecuperacaoSenhaTest {

    private static final Pattern CODIGO = Pattern.compile("\\b(\\d{4})\\b");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private CodigoRecuperacaoRepository codigos;

    @MockitoBean
    private WhatsappBotService bot;

    @Test
    void numeroNaoCadastradoDa404() throws Exception {
        pedir("61900009999").andExpect(status().isNotFound());
    }

    @Test
    void codigoCertoTrocaASenhaEntraEVerificaOTelefone() throws Exception {
        cadastrar("61982220001", "antiga1");
        pedir("(61) 98222-0001").andExpect(status().isOk()).andExpect(jsonPath("$.reenviarEmSegundos").value(60));
        String codigo = codigoEnviado("61982220001");

        redefinir("61982220001", outroCodigo(codigo), "nova123").andExpect(status().isBadRequest());
        redefinir("61982220001", codigo, "nova123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.telefoneVerificado").value(true));

        entrar("61982220001", "nova123").andExpect(status().isOk());
        entrar("61982220001", "antiga1").andExpect(status().isUnauthorized());
        redefinir("61982220001", codigo, "outra99").andExpect(status().isGone());
    }

    @Test
    void novaSenhaComMenosDe6CaracteresDa400() throws Exception {
        redefinir("61982220006", "1234", "abc12").andExpect(status().isBadRequest());
    }

    @Test
    void novoPedidoAntesDeUmMinutoDa429() throws Exception {
        cadastrar("61982220002", "antiga1");
        pedir("61982220002").andExpect(status().isOk());
        pedir("61982220002").andExpect(status().isTooManyRequests());
    }

    @Test
    void cincoErrosInvalidamOCodigo() throws Exception {
        cadastrar("61982220003", "antiga1");
        pedir("61982220003").andExpect(status().isOk());
        String codigo = codigoEnviado("61982220003");
        for (int i = 0; i < 4; i++) {
            redefinir("61982220003", outroCodigo(codigo), "nova123").andExpect(status().isBadRequest());
        }
        redefinir("61982220003", outroCodigo(codigo), "nova123").andExpect(status().isGone());
        redefinir("61982220003", codigo, "nova123").andExpect(status().isGone());
    }

    @Test
    void codigoExpiradoDa410() throws Exception {
        cadastrar("61982220004", "antiga1");
        pedir("61982220004").andExpect(status().isOk());
        String codigo = codigoEnviado("61982220004");
        codigos.findAll().stream()
                .filter(c -> c.getWhatsapp().equals("61982220004"))
                .forEach(c -> {
                    c.setDataExpiracao(LocalDateTime.now().minusSeconds(1));
                    codigos.save(c);
                });
        redefinir("61982220004", codigo, "nova123").andExpect(status().isGone());
    }

    @Test
    void falhaNoBotNaoGuardaCodigoNemBloqueiaNovoPedido() throws Exception {
        cadastrar("61982220005", "antiga1");
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY))
                .when(bot).enviar(eq("61982220005"), anyString());
        pedir("61982220005").andExpect(status().isBadGateway());
        assertThat(codigos.findAll()).noneMatch(c -> c.getWhatsapp().equals("61982220005"));
    }

    private String codigoEnviado(String whatsapp) {
        ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
        verify(bot).enviar(eq(whatsapp), mensagem.capture());
        clearInvocations(bot);
        Matcher matcher = CODIGO.matcher(mensagem.getValue());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static String outroCodigo(String codigo) {
        return "%04d".formatted((Integer.parseInt(codigo) + 1) % 10_000);
    }

    private void cadastrar(String whatsapp, String senha) throws Exception {
        if (usuarios.existsByWhatsapp(whatsapp)) {
            return;
        }
        mvc.perform(post("/api/auth/cadastro").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("nome", "Teste", "whatsapp", whatsapp, "senha", senha))))
                .andExpect(status().isCreated());
    }

    private ResultActions pedir(String whatsapp) throws Exception {
        return mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("whatsapp", whatsapp))));
    }

    private ResultActions redefinir(String whatsapp, String codigo, String novaSenha) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("whatsapp", whatsapp, "codigo", codigo, "novaSenha", novaSenha))));
    }

    private ResultActions entrar(String whatsapp, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("whatsapp", whatsapp, "senha", senha))));
    }
}
