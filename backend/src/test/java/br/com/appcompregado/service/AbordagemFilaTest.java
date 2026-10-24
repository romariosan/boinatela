package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.LeadConversa;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusLeadConversa;
import br.com.appcompregado.domain.enums.StatusMensagem;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.LeadConversaRepository;
import br.com.appcompregado.repository.MensagemSaidaRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AbordagemFilaTest {

    private static final String TEL = "61955550111";
    private static final String TOKEN = "token-de-teste";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private LeadAbordagemService abordagem;

    @Autowired
    private UsuarioRepository usuarios;

    @Autowired
    private AnuncioRepository anuncios;

    @Autowired
    private LeadConversaRepository conversas;

    @Autowired
    private MensagemSaidaRepository mensagens;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM mensagem_saida WHERE telefone = ?", TEL);
        jdbc.update("DELETE FROM lead_conversa WHERE telefone = ?", TEL);
        usuarios.findByWhatsapp(TEL).ifPresent(u -> anuncios.deleteAll(anuncios.doVendedor(u.getId())));
        usuarios.findByWhatsapp(TEL).ifPresent(usuarios::delete);
    }

    @Test
    void reservaNaoDevolveAMesmaLinhaESoMarcaAbordagemNoAck() throws Exception {
        Anuncio lote = lote("12 nelore", 12);
        abordagem.agendarEstreia(lote, "Gado Formosa");

        Anuncio gravado = anuncios.findById(lote.getId()).orElseThrow();
        assertThat(gravado.isMensagemConsultorEnviada()).isFalse();
        LeadConversa conversa = conversas.findByAnuncioId(lote.getId()).orElseThrow();
        assertThat(conversa.getStatus()).isEqualTo(StatusLeadConversa.AGUARDANDO_ABORDAGEM);
        var saida = mensagens.findAll().stream()
                .filter(m -> TEL.equals(m.getTelefone()))
                .findFirst()
                .orElseThrow();
        assertThat(saida.getTexto()).doesNotContain("http");
        assertThat(saida.getStatus()).isEqualTo(StatusMensagem.PENDENTE);

        jdbc.update(
                "UPDATE mensagem_saida SET data_agendamento = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(1)),
                saida.getId());

        mvc.perform(get("/api/robo/fila").param("chip", "CONSULTOR").header("X-Robo-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(saida.getId().intValue()))
                .andExpect(jsonPath("$[0].texto", org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("http"))));
        mvc.perform(get("/api/robo/fila").param("chip", "CONSULTOR").header("X-Robo-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mvc.perform(post("/api/robo/fila/{id}/falhou", saida.getId()).header("X-Robo-Token", TOKEN))
                .andExpect(status().isNoContent());
        assertThat(anuncios.findById(lote.getId()).orElseThrow().isMensagemConsultorEnviada()).isFalse();
        assertThat(conversas.findByAnuncioId(lote.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusLeadConversa.AGUARDANDO_ABORDAGEM);
        assertThat(mensagens.findById(saida.getId()).orElseThrow().getStatus()).isEqualTo(StatusMensagem.PENDENTE);

        mvc.perform(get("/api/robo/fila").param("chip", "CONSULTOR").header("X-Robo-Token", TOKEN))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(post("/api/robo/fila/{id}/enviada", saida.getId()).header("X-Robo-Token", TOKEN))
                .andExpect(status().isNoContent());

        assertThat(anuncios.findById(lote.getId()).orElseThrow().isMensagemConsultorEnviada()).isTrue();
        assertThat(conversas.findByAnuncioId(lote.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusLeadConversa.QUEBRA_GELO_ENVIADO);
    }

    @Test
    void rejeitadoNaoReagenda() {
        Anuncio primeiro = lote("primeiro", 8);
        LocalDateTime agora = LocalDateTime.now();
        LeadConversa recusa = new LeadConversa();
        recusa.setAnuncio(primeiro);
        recusa.setTelefone(TEL);
        recusa.setStatus(StatusLeadConversa.REJEITADO);
        recusa.setDataCaptura(agora);
        recusa.setDataUltimaInteracao(agora);
        recusa.setEncerrada(true);
        conversas.save(recusa);

        Anuncio segundo = lote("segundo", 4);
        abordagem.agendarEstreia(segundo, "Gado Formosa");

        assertThat(conversas.findByAnuncioId(segundo.getId())).isEmpty();
        assertThat(mensagens.findAll().stream().filter(m -> TEL.equals(m.getTelefone()))).isEmpty();
        assertThat(anuncios.findById(segundo.getId()).orElseThrow().isMensagemConsultorEnviada()).isFalse();
    }

    @Test
    void dialogoSemClassificacaoNaoEnfileira() {
        Anuncio lote = lote("lote", 5);
        LocalDateTime agora = LocalDateTime.now();
        LeadConversa conversa = new LeadConversa();
        conversa.setAnuncio(lote);
        conversa.setTelefone(TEL);
        conversa.setStatus(StatusLeadConversa.RESPONDIDO);
        conversa.setDataCaptura(agora);
        conversa.setDataUltimaInteracao(agora);
        conversa.setUltimaEntrada("ainda tenho o gado");
        conversas.save(conversa);

        abordagem.responder(conversa.getId());

        assertThat(mensagens.findAll().stream().filter(m -> TEL.equals(m.getTelefone()))).isEmpty();
    }

    private Anuncio lote(String titulo, int quantidade) {
        Usuario vendedor = usuarios.findByWhatsapp(TEL).orElseGet(Usuario::new);
        vendedor.setNome("Produtor teste");
        vendedor.setWhatsapp(TEL);
        vendedor.setSenha("x");
        vendedor.setTelefoneVerificado(true);
        vendedor = usuarios.save(vendedor);

        Anuncio anuncio = new Anuncio();
        anuncio.setTitulo(titulo);
        anuncio.setDescricao("lote de teste");
        anuncio.setPreco(new BigDecimal("2500.00"));
        anuncio.setCidade("Formosa - GO");
        anuncio.setCategoriaAnimal(CategoriaAnimal.BEZERRO);
        anuncio.setAptidao(Aptidao.CORTE);
        anuncio.setRaca(Raca.NELORE);
        anuncio.setTipoVenda(TipoVenda.LOTE);
        anuncio.setQuantidade(quantidade);
        anuncio.setStatus(AnuncioStatus.ATIVO);
        anuncio.setStatusProcessamento(StatusProcessamento.PUBLICADO);
        anuncio.setOrigem("WHATSAPP");
        anuncio.setTelefoneVendedorOriginal(TEL);
        anuncio.setDataCaptura(LocalDateTime.now());
        anuncio.setAtivo(true);
        anuncio.setUrlFotoPrincipal("http://localhost/uploads/teste.jpg");
        anuncio.getFotos().add("http://localhost/uploads/teste.jpg");
        anuncio.setVendedor(vendedor);
        return anuncios.save(anuncio);
    }
}
