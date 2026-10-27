package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.appcompregado.config.GeminiProperties;
import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.service.GeminiExtractionService.ExtracaoAnuncio;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class GeminiEnrichmentJobServiceTest {

    @Mock
    AnuncioRepository anuncios;
    @Mock
    GeminiExtractionService gemini;
    @Mock
    TransactionTemplate transacao;
    @Mock
    AlertaNotificador radar;
    @Mock
    LeadAbordagemService abordagem;

    GeminiProperties propriedades = new GeminiProperties();
    GeminiEnrichmentJobService job;

    @BeforeEach
    void preparar() {
        propriedades.setKey("teste-chave");
        job = new GeminiEnrichmentJobService(
                anuncios, gemini, propriedades, transacao, radar, abordagem, "0 * * * * ?");
        lenient().doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> acao = inv.getArgument(0);
            acao.accept(mock(TransactionStatus.class));
            return null;
        }).when(transacao).executeWithoutResult(any());
    }

    @Test
    void backoffCresceComAsTentativas() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 27, 8, 0);
        assertThat(GeminiEnrichmentJobService.proximaTentativa(1, base)).isEqualTo(base.plusMinutes(1));
        assertThat(GeminiEnrichmentJobService.proximaTentativa(2, base)).isEqualTo(base.plusMinutes(5));
        assertThat(GeminiEnrichmentJobService.proximaTentativa(3, base)).isEqualTo(base.plusMinutes(15));
        assertThat(GeminiEnrichmentJobService.proximaTentativa(5, base)).isEqualTo(base.plusMinutes(60));
    }

    @Test
    void marcaPendenteNaCapturaSemChamarGeminiNoWebhook() {
        Anuncio anuncio = rascunhoLocal();
        LocalDateTime agora = LocalDateTime.of(2026, 10, 27, 8, 0);
        ScraperAnuncioService.marcarGeminiPendente(anuncio, agora);
        assertThat(anuncio.isGeminiPendente()).isTrue();
        assertThat(anuncio.getTentativasGemini()).isZero();
        assertThat(anuncio.getProximaTentativaGemini()).isEqualTo(agora);
        assertThat(anuncio.getTitulo()).isEqualTo("Lote de vacas de leite");
        assertThat(anuncio.getPreco()).isEqualByComparingTo("9000");
    }

    @Test
    void jobAplicaCamposDaGeminiEPublicaRascunhoComFoto() {
        Anuncio anuncio = rascunhoLocal();
        anuncio.setGeminiPendente(true);
        anuncio.getFotos().add("/uploads/vaca.jpg");
        anuncio.setUrlFotoPrincipal("/uploads/vaca.jpg");
        when(anuncios.findById(5L)).thenReturn(Optional.of(anuncio));
        when(gemini.extrair(anyString(), anyString(), anyString())).thenReturn(Optional.of(new ExtracaoAnuncio(
                "20 vacas Nelore em Anápolis",
                new BigDecimal("9500"),
                Raca.NELORE,
                20,
                "Anápolis",
                "GO",
                null,
                "WHATSAPP")));

        job.tentarUm(5L);

        assertThat(anuncio.isGeminiPendente()).isFalse();
        assertThat(anuncio.getTitulo()).isEqualTo("20 vacas Nelore em Anápolis");
        assertThat(anuncio.getPreco()).isEqualByComparingTo("9500");
        assertThat(anuncio.getStatusProcessamento()).isEqualTo(StatusProcessamento.PUBLICADO);
        assertThat(anuncio.isAtivo()).isTrue();
        verify(radar).agendar(anuncio);
        verify(anuncios).save(anuncio);
    }

    @Test
    void falhaDaGeminiMantemExtracaoLocalEReagenda() {
        Anuncio anuncio = rascunhoLocal();
        anuncio.setGeminiPendente(true);
        anuncio.setTentativasGemini(0);
        when(anuncios.findById(5L)).thenReturn(Optional.of(anuncio));
        when(gemini.extrair(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        job.tentarUm(5L);

        assertThat(anuncio.isGeminiPendente()).isTrue();
        assertThat(anuncio.getTentativasGemini()).isEqualTo(1);
        assertThat(anuncio.getTitulo()).isEqualTo("Lote de vacas de leite");
        assertThat(anuncio.getPreco()).isEqualByComparingTo("9000");
        assertThat(anuncio.getProximaTentativaGemini()).isNotNull();
        verify(radar, never()).agendar(any());
    }

    private static Anuncio rascunhoLocal() {
        Anuncio a = new Anuncio();
        a.setId(5L);
        a.setTitulo("Lote de vacas de leite");
        a.setDescricao("Vendo vaca de leite a 9 mil em Anápolis GO");
        a.setPreco(new BigDecimal("9000"));
        a.setCidade("Anápolis - GO");
        a.setRaca(Raca.OUTRA);
        a.setCategoriaAnimal(CategoriaAnimal.VACA);
        a.setAptidao(Aptidao.LEITE);
        a.setTipoVenda(TipoVenda.CABECA);
        a.setStatus(AnuncioStatus.ATIVO);
        a.setStatusProcessamento(StatusProcessamento.RASCUNHO_SEM_FOTO);
        a.setAtivo(false);
        a.setOrigem("WHATSAPP");
        a.setTelefoneVendedorOriginal("6198411786");
        return a;
    }
}
