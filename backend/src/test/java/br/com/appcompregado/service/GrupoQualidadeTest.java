package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.web.dto.AnuncioResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GrupoQualidadeTest {

    @Test
    void nomeDoGrupoApontaACidadeMaisEspecifica() {
        assertThat(CidadesGoias.inferir("Compra e venda Formosa GO")).contains("Formosa");
        assertThat(CidadesGoias.inferir("Gado em Águas Lindas de Goiás")).contains("Águas Lindas de Goiás");
        assertThat(CidadesGoias.inferir("Grupo do estado de Goiás")).isEmpty();
    }

    @Test
    void precoZeroViraACombinarECidadeGanhaOMarcador() {
        Anuncio anuncio = new Anuncio();
        anuncio.setPreco(BigDecimal.ZERO);
        anuncio.setCidade("Cristalina - GO");

        assertThat(AnuncioResponse.formatarPreco(anuncio.getPreco())).isEqualTo("Preço a Combinar");
        assertThat(AnuncioResponse.formatarCidade(anuncio)).isEqualTo("📍 Cristalina - GO");
        assertThat(anuncio.getUf()).isEqualTo("GO");
    }

    @Test
    void geminiUsaACidadeDoGrupoQuandoAMensagemNaoCita() throws Exception {
        String corpo = """
                {"titulo":"20 bezerros","preco":null,"raca":"NELORE","quantidade":20,"cidade":"","uf":""}
                """;

        var extraido = GeminiExtractionService.interpretarResposta(corpo, new ObjectMapper(), "Formosa", "GO");

        assertThat(extraido.cidade()).isEqualTo("Formosa");
        assertThat(extraido.uf()).isEqualTo("GO");
        assertThat(extraido.titulo()).isEqualTo("20 bezerros");
        assertThat(extraido.origem()).isEqualTo("WHATSAPP");
        assertThat(extraido.telefoneVendedorExtraido()).isNull();
    }

    @Test
    void tagFbViraOTelefoneDoProdutorENaoDoCurador() throws Exception {
        String corpo = """
                {"titulo":"Bezerras","preco":null,"raca":"GIROLANDO","quantidade":12,"cidade":"Formosa","uf":"GO","telefoneVendedorExtraido":"61988887777","origem":"FACEBOOK"}
                """;
        var extraido = GeminiExtractionService.interpretarResposta(corpo, new ObjectMapper(), "Formosa", "GO");
        String texto = "FB: 61 98888-7777\n12 bezerras girolando em Formosa";

        assertThat(extraido.origem()).isEqualTo("FACEBOOK");
        assertThat(ScraperAnuncioService.telefoneDaCuradoria(extraido, texto)).isEqualTo("61988887777");
        assertThat(ScraperAnuncioService.telefoneDaCuradoria(null, texto)).isEqualTo("61988887777");
        assertThat(ScraperAnuncioService.telefoneDaCuradoria(null, "Vendo 10 bezerros nelore")).isNull();
    }

    @Test
    void consultorDeFacebookCitaAOrigemEOLote() {
        String texto = WhatsAppConsultantService.montarFacebook(
                "produtor", 12, "GIROLANDO", "https://appcompregado.com.br/lote/8");

        assertThat(texto).contains("produtor?");
        assertThat(texto).contains("dos 12 Girolando");
        assertThat(texto).contains("lá no Facebook");
        assertThat(texto).contains("https://appcompregado.com.br/lote/8");
        assertThat(texto).doesNotContain("no grupo");
    }

    @Test
    void conviteDoConsultorPedeFoto() {
        String texto = WhatsAppConsultantService.conviteFoto("produtor", "https://appcompregado.com.br/lote/4");

        assertThat(texto).contains("Opa produtor!");
        assertThat(texto).contains("sem foto");
        assertThat(texto).contains("https://appcompregado.com.br/lote/4");
    }
}
