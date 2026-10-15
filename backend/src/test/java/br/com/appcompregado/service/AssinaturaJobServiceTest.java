package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import br.com.appcompregado.security.JwtService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssinaturaJobServiceTest {

    private static final String[] NUMEROS = {"61944400001", "61944400002", "61944400003", "61944400004"};

    @Autowired
    private AssinaturaJobService job;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private AnuncioRepository anuncios;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwt;

    @Autowired
    private MockMvc mvc;

    private final LocalDate hoje = LocalDate.now(ZoneId.of(AssinaturaJobService.FUSO));
    private final LocalDateTime agora = LocalDateTime.now();

    @AfterEach
    void limpar() {
        for (String whatsapp : NUMEROS) {
            usuarios.findByWhatsapp(whatsapp).ifPresent(u -> anuncios.deleteAll(anuncios.doVendedor(u.getId())));
        }
    }

    @Test
    void vencidoFicaComOsTresMaisRecentesEPausaOsMaisAntigos() {
        Usuario vendedor = vendedor(NUMEROS[0], true, hoje.minusDays(1));
        // Ids fora da ordem das datas: quem decide é a data de criação, não a ordem de inserção.
        Long umDia = anuncio(vendedor, "1 dia", agora.minusDays(1), AnuncioStatus.ATIVO);
        Long cincoDias = anuncio(vendedor, "5 dias", agora.minusDays(5), AnuncioStatus.ATIVO);
        Long doisDias = anuncio(vendedor, "2 dias", agora.minusDays(2), AnuncioStatus.ATIVO);
        Long quatroDias = anuncio(vendedor, "4 dias", agora.minusDays(4), AnuncioStatus.ATIVO);
        Long semData = anuncio(vendedor, "anterior à coluna", agora, AnuncioStatus.ATIVO);
        jdbc.update("UPDATE anuncio SET data_criacao = NULL WHERE id = ?", semData);
        Long tresDias = anuncio(vendedor, "3 dias", agora.minusDays(3), AnuncioStatus.ATIVO);
        Long vendido = anuncio(vendedor, "vendido", agora, AnuncioStatus.VENDIDO);

        job.expirarPlanosVencidos();

        Usuario depois = usuarios.findById(vendedor.getId()).orElseThrow();
        assertThat(depois.isPremium()).isFalse();
        assertThat(depois.getDataExpiracaoPremium()).isNull();
        assertThat(statusPorId(vendedor)).isEqualTo(Map.of(
                umDia, AnuncioStatus.ATIVO,
                doisDias, AnuncioStatus.ATIVO,
                tresDias, AnuncioStatus.ATIVO,
                quatroDias, AnuncioStatus.PAUSADO,
                cincoDias, AnuncioStatus.PAUSADO,
                semData, AnuncioStatus.PAUSADO,
                vendido, AnuncioStatus.VENDIDO));
    }

    @Test
    void dentroDoLimiteSoPerdeOPro() {
        Usuario vendedor = vendedor(NUMEROS[1], true, hoje.minusDays(3));
        anuncio(vendedor, "a", agora.minusDays(1), AnuncioStatus.ATIVO);
        anuncio(vendedor, "b", agora.minusDays(2), AnuncioStatus.ATIVO);

        job.expirarPlanosVencidos();

        assertThat(usuarios.findById(vendedor.getId()).orElseThrow().isPremium()).isFalse();
        assertThat(statusPorId(vendedor).values()).containsOnly(AnuncioStatus.ATIVO);
    }

    @Test
    void quemVenceHojeOuNaoTemDataContinuaPro() {
        Usuario venceHoje = vendedor(NUMEROS[2], true, hoje);
        Usuario semData = vendedor(NUMEROS[3], true, null);
        for (int i = 1; i <= 4; i++) {
            anuncio(venceHoje, "hoje " + i, agora.minusDays(i), AnuncioStatus.ATIVO);
            anuncio(semData, "sem data " + i, agora.minusDays(i), AnuncioStatus.ATIVO);
        }

        job.expirarPlanosVencidos();

        assertThat(usuarios.findById(venceHoje.getId()).orElseThrow().isPremium()).isTrue();
        assertThat(usuarios.findById(semData.getId()).orElseThrow().isPremium()).isTrue();
        assertThat(statusPorId(venceHoje).values()).containsOnly(AnuncioStatus.ATIVO);
        assertThat(statusPorId(semData).values()).containsOnly(AnuncioStatus.ATIVO);
    }

    @Test
    void pausadoSomeDaVitrineNaoRecebeContatoESoVoltaDentroDoLimite() throws Exception {
        Usuario vendedor = vendedor(NUMEROS[0], true, hoje.minusDays(1));
        Long maisNovo = anuncio(vendedor, "novo", agora.minusDays(1), AnuncioStatus.ATIVO);
        anuncio(vendedor, "meio 1", agora.minusDays(2), AnuncioStatus.ATIVO);
        anuncio(vendedor, "meio 2", agora.minusDays(3), AnuncioStatus.ATIVO);
        Long maisAntigo = anuncio(vendedor, "antigo", agora.minusDays(4), AnuncioStatus.ATIVO);

        job.expirarPlanosVencidos();

        mvc.perform(get("/api/anuncios"))
                .andExpect(jsonPath("$[*].id", hasItem(maisNovo.intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(maisAntigo.intValue()))));
        Usuario comprador = vendedor(NUMEROS[1], false, null);
        mvc.perform(post("/api/anuncios/{id}/contato", maisAntigo).header(HttpHeaders.AUTHORIZATION, bearer(comprador)))
                .andExpect(status().isConflict());

        mudarStatus(vendedor, maisAntigo, "ATIVO")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("LIMITE_ANUNCIOS_ATINGIDO"));
        mudarStatus(vendedor, maisNovo, "VENDIDO").andExpect(status().isOk());
        mudarStatus(vendedor, maisAntigo, "ATIVO").andExpect(status().isOk());
    }

    private ResultActions mudarStatus(Usuario vendedor, Long id, String status) throws Exception {
        return mvc.perform(patch("/api/anuncios/{id}/status", id)
                .header(HttpHeaders.AUTHORIZATION, bearer(vendedor))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\"}"));
    }

    private Map<Long, AnuncioStatus> statusPorId(Usuario vendedor) {
        return anuncios.doVendedor(vendedor.getId()).stream()
                .collect(Collectors.toMap(Anuncio::getId, Anuncio::getStatus));
    }

    private Long anuncio(Usuario vendedor, String titulo, LocalDateTime criadoEm, AnuncioStatus status) {
        Anuncio anuncio = new Anuncio();
        anuncio.setTitulo(titulo);
        anuncio.setDescricao("");
        anuncio.setPreco(new BigDecimal("2500.00"));
        anuncio.setCidade("Formosa - GO");
        anuncio.setCategoriaAnimal(CategoriaAnimal.BEZERRO);
        anuncio.setAptidao(Aptidao.CORTE);
        anuncio.setRaca(Raca.NELORE);
        anuncio.setTipoVenda(TipoVenda.CABECA);
        anuncio.getFotos().add("http://localhost/uploads/teste.jpg");
        anuncio.setStatus(status);
        anuncio.setDataCriacao(criadoEm);
        anuncio.setVendedor(vendedor);
        return anuncios.save(anuncio).getId();
    }

    private Usuario vendedor(String whatsapp, boolean premium, LocalDate expiraEm) {
        Usuario usuario = usuarios.findByWhatsapp(whatsapp).orElseGet(Usuario::new);
        usuario.setNome("Teste " + whatsapp);
        usuario.setWhatsapp(whatsapp);
        usuario.setSenha("x");
        usuario.setTelefoneVerificado(true);
        usuario.setPremium(premium);
        usuario.setDataExpiracaoPremium(expiraEm);
        return usuarios.save(usuario);
    }

    private String bearer(Usuario usuario) {
        return "Bearer " + jwt.gerarToken(usuario);
    }
}
