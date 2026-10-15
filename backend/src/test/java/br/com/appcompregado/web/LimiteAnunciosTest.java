package br.com.appcompregado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
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
class LimiteAnunciosTest {

    private static final String RESPOSTA_LIMITE = """
            {"erro":"LIMITE_ANUNCIOS_ATINGIDO","mensagem":"Você atingiu o limite de 3 anúncios gratuitos."}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private AnuncioRepository anuncios;

    @Autowired
    private JwtService jwt;

    @Autowired
    private ObjectMapper json;

    @AfterEach
    void limpar() {
        for (String whatsapp : new String[] {"61933300001", "61933300002", "61933300003", "61933300004"}) {
            usuarios.findByWhatsapp(whatsapp).ifPresent(usuario -> anuncios.deleteAll(anuncios.doVendedor(usuario.getId())));
        }
    }

    @Test
    void gratuitoPublicaTresEOQuartoDa400ComOAvisoDoPlanoPro() throws Exception {
        Usuario vendedor = vendedor("61933300001", false);
        for (int i = 1; i <= 3; i++) {
            publicar(vendedor, "Bezerros " + i).andExpect(status().isCreated());
        }

        publicar(vendedor, "Bezerros 4")
                .andExpect(status().isBadRequest())
                .andExpect(content().json(RESPOSTA_LIMITE, true));
        assertThat(anuncios.countByVendedorIdAndStatus(vendedor.getId(), AnuncioStatus.ATIVO)).isEqualTo(3);
    }

    @Test
    void vendidoLiberaAVagaMasReativarAcimaDoLimiteDa400() throws Exception {
        Usuario vendedor = vendedor("61933300002", false);
        long primeiro = idDe(publicar(vendedor, "Garrotes 1"));
        publicar(vendedor, "Garrotes 2").andExpect(status().isCreated());
        publicar(vendedor, "Garrotes 3").andExpect(status().isCreated());

        mudarStatus(vendedor, primeiro, "VENDIDO").andExpect(status().isOk());
        publicar(vendedor, "Garrotes 4").andExpect(status().isCreated());

        mudarStatus(vendedor, primeiro, "ATIVO")
                .andExpect(status().isBadRequest())
                .andExpect(content().json(RESPOSTA_LIMITE, true));
        assertThat(anuncios.findById(primeiro).orElseThrow().getStatus()).isEqualTo(AnuncioStatus.VENDIDO);
    }

    @Test
    void premiumNaoTemLimite() throws Exception {
        Usuario vendedor = vendedor("61933300003", true);
        for (int i = 1; i <= 4; i++) {
            publicar(vendedor, "Vacas " + i).andExpect(status().isCreated());
        }
    }

    @Test
    void quemViraProDepoisDeEntrarJaPublicaSemSairDaConta() throws Exception {
        Usuario vendedor = vendedor("61933300004", false);
        String token = bearer(vendedor);
        for (int i = 1; i <= 3; i++) {
            publicar(token, "Touros " + i).andExpect(status().isCreated());
        }
        publicar(token, "Touros 4").andExpect(status().isBadRequest());

        Usuario pro = usuarios.findById(vendedor.getId()).orElseThrow();
        pro.setPremium(true);
        usuarios.save(pro);
        publicar(token, "Touros 4").andExpect(status().isCreated());
    }

    private ResultActions publicar(Usuario vendedor, String titulo) throws Exception {
        return publicar(bearer(vendedor), titulo);
    }

    private ResultActions publicar(String token, String titulo) throws Exception {
        String dados = """
                {"titulo":"%s","descricao":"","preco":2500,"cidade":"Formosa - GO",
                 "categoriaAnimal":"BEZERRO","aptidao":"CORTE","raca":"NELORE","tipoVenda":"CABECA"}
                """.formatted(titulo);
        return mvc.perform(multipart("/api/anuncios")
                .file(new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                        dados.getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("imagens", "foto.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3}))
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions mudarStatus(Usuario vendedor, long id, String status) throws Exception {
        return mvc.perform(patch("/api/anuncios/{id}/status", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(vendedor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private long idDe(ResultActions resultado) throws Exception {
        String corpo = resultado.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo).get("id").asLong();
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwt.gerarToken(usuario);
    }

    private Usuario vendedor(String whatsapp, boolean premium) {
        Usuario usuario = usuarios.findByWhatsapp(whatsapp).orElseGet(Usuario::new);
        usuario.setNome("Teste " + whatsapp);
        usuario.setWhatsapp(whatsapp);
        usuario.setSenha("x");
        usuario.setTelefoneVerificado(true);
        usuario.setPremium(premium);
        return usuarios.save(usuario);
    }
}
