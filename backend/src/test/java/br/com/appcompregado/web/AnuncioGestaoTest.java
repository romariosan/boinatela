package br.com.appcompregado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.LeadContatoRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.security.JwtService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnuncioGestaoTest {

    private static final String FOTO = "http://localhost/uploads/foto-de-teste.jpg";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private AnuncioRepository anuncios;

    @Autowired
    private LeadContatoRepository leads;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JwtService jwt;

    private Usuario dono;
    private Usuario outro;
    private Long anuncioId;

    @BeforeEach
    void preparar() {
        dono = usuario("61911110001");
        outro = usuario("61911110002");
        Anuncio anuncio = new Anuncio();
        anuncio.setTitulo("Garrotes Brahman");
        anuncio.setDescricao("");
        anuncio.setPreco(new BigDecimal("3000.00"));
        anuncio.setCidade("Formosa - GO");
        anuncio.setCategoriaAnimal(CategoriaAnimal.GARROTE);
        anuncio.setAptidao(Aptidao.CORTE);
        anuncio.setRaca(Raca.BRAHMAN);
        anuncio.setTipoVenda(TipoVenda.CABECA);
        anuncio.getFotos().add(FOTO);
        anuncio.setVendedor(dono);
        anuncioId = anuncios.save(anuncio).getId();
    }

    @AfterEach
    void limpar() {
        transacao.executeWithoutResult(status -> {
            leads.deleteByAnuncioId(anuncioId);
            anuncios.findById(anuncioId).ifPresent(anuncios::delete);
        });
    }

    @Test
    void quemNaoEDonoNaoAlteraNemExclui() throws Exception {
        mvc.perform(patch("/api/anuncios/{id}/status", anuncioId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(outro))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VENDIDO\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/anuncios/{id}", anuncioId).header(HttpHeaders.AUTHORIZATION, bearer(outro)))
                .andExpect(status().isForbidden());
        mvc.perform(editar(dadosValidos("[\"" + FOTO + "\"]")).header(HttpHeaders.AUTHORIZATION, bearer(outro)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/anuncios/{id}", anuncioId))
                .andExpect(status().isForbidden());
    }

    @Test
    void vendidoSaiDaVitrineEBloqueiaContato() throws Exception {
        mvc.perform(patch("/api/anuncios/{id}/status", anuncioId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(dono))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VENDIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VENDIDO"));

        mvc.perform(get("/api/anuncios"))
                .andExpect(jsonPath("$[*].id", not(hasItem(anuncioId.intValue()))));
        mvc.perform(get("/api/anuncios/{id}", anuncioId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VENDIDO"));
        mvc.perform(post("/api/anuncios/{id}/contato", anuncioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsapp\":\"61988887777\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void contatoSemSenhaGravaOLeadEAbreOWhatsapp() throws Exception {
        mvc.perform(post("/api/anuncios/{id}/contato", anuncioId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsapp\":\"(61) 98888-7777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensagemSugerida").value(containsString("Garrotes Brahman")))
                .andExpect(jsonPath("$.urlWhatsapp").value(containsString("wa.me/55")));

        assertThat(leads.countByAnuncioId(anuncioId)).isEqualTo(1);
    }

    @Test
    void donoEditaMantendoSoFotosDoProprioAnuncio() throws Exception {
        mvc.perform(editar(dadosValidos("[\"" + FOTO + "\"]")).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Garrotes Brahman editado"))
                .andExpect(jsonPath("$.fotos[0]").value(FOTO));

        mvc.perform(editar(dadosValidos("[\"http://outro-site/foto.jpg\"]"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isBadRequest());
        mvc.perform(editar(dadosValidos("[]")).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void quantidadeEPesoSaoOpcionaisEValidados() throws Exception {
        String comLote = dadosValidos("null").replace("\"tipoVenda\":\"CABECA\"",
                "\"tipoVenda\":\"CABECA\",\"quantidade\":20,\"pesoMedioKg\":180");
        mvc.perform(editar(comLote).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(20))
                .andExpect(jsonPath("$.pesoMedioKg").value(180));

        mvc.perform(editar(dadosValidos("null")).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(nullValue()));

        String pesoNegativo = dadosValidos("null").replace("\"tipoVenda\":\"CABECA\"",
                "\"tipoVenda\":\"CABECA\",\"pesoMedioKg\":-5");
        mvc.perform(editar(pesoNegativo).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cidadeForaDoPadraoERecusada() throws Exception {
        String dados = dadosValidos("null").replace("Formosa - GO", "formosa");
        mvc.perform(editar(dados).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void donoExclui() throws Exception {
        mvc.perform(delete("/api/anuncios/{id}", anuncioId).header(HttpHeaders.AUTHORIZATION, bearer(dono)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/anuncios/{id}", anuncioId))
                .andExpect(status().isNotFound());
    }

    private MockMultipartHttpServletRequestBuilder editar(String dados) {
        return multipart(HttpMethod.PUT, "/api/anuncios/{id}", anuncioId)
                .file(new MockMultipartFile("dados", "", MediaType.APPLICATION_JSON_VALUE,
                        dados.getBytes(StandardCharsets.UTF_8)));
    }

    private static String dadosValidos(String fotosMantidas) {
        return """
                {"titulo":"Garrotes Brahman editado","descricao":"","preco":3200,"cidade":"Formosa - GO",
                 "categoriaAnimal":"GARROTE","aptidao":"CORTE","raca":"BRAHMAN","tipoVenda":"CABECA",
                 "fotosMantidas":%s}
                """.formatted(fotosMantidas);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwt.gerarToken(usuario);
    }

    private Usuario usuario(String whatsapp) {
        return usuarios.findByWhatsapp(whatsapp).orElseGet(() -> {
            Usuario novo = new Usuario();
            novo.setNome("Teste " + whatsapp);
            novo.setWhatsapp(whatsapp);
            novo.setSenha("x");
            novo.setPremium(false);
            return usuarios.save(novo);
        });
    }
}
