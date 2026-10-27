package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.TipoVenda;
import br.com.appcompregado.repository.AnuncioBusca;
import br.com.appcompregado.service.GeminiExtractionService.ExtracaoAnuncio;
import br.com.appcompregado.web.dto.AnuncioResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrupoQualidadeTest {

    @Test
    void nomeDoGrupoApontaACidadeMaisEspecifica() {
        assertThat(CidadesGoias.inferir("Compra e venda Formosa GO")).contains("Formosa");
        assertThat(CidadesGoias.inferir("Gado em Águas Lindas de Goiás")).contains("Águas Lindas de Goiás");
        assertThat(CidadesGoias.inferir("Grupo do estado de Goiás")).isEmpty();
        assertThat(CidadesGoias.inferirLocal("em guara df"))
                .hasValueSatisfying(local -> {
                    assertThat(local.cidade()).isEqualTo("Guará");
                    assertThat(local.uf()).isEqualTo("DF");
                });
        assertThat(CidadesGoias.inferirLocal("vendo gado em Minas Gerais"))
                .hasValueSatisfying(local -> {
                    assertThat(local.cidade()).isEqualTo("Minas Gerais");
                    assertThat(local.uf()).isEqualTo("MG");
                });
        assertThat(CidadesGoias.inferirLocal("lote em Unaí MG"))
                .hasValueSatisfying(local -> {
                    assertThat(local.cidade()).isEqualTo("Unaí");
                    assertThat(local.uf()).isEqualTo("MG");
                });
    }

    @Test
    void textoComMinasGeraisNaoViraFormosa() {
        String texto = "Boa tarde vendo vaca de leite a 10 mil cada em Minas Gerais";
        var extraido = ExtracaoLocal.extrair(texto, "Formosa", "GO").orElseThrow();

        assertThat(extraido.cidade()).isEqualTo("Minas Gerais");
        assertThat(extraido.uf()).isEqualTo("MG");
        assertThat(extraido.cidade()).isNotEqualTo("Formosa");

        // Grupo sem município no nome: estado do texto vale (não inventa Formosa).
        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                texto, extraido.cidade(), extraido.uf(), "Formosa", "GO", "Compra e venda de gado");
        assertThat(gravada).isEqualTo("Minas Gerais - MG");
        assertThat(gravada).doesNotContain("Formosa");

        Anuncio existente = new Anuncio();
        existente.setTitulo("Lote de vacas");
        existente.setPreco(new BigDecimal("8000"));
        existente.setCidade("Formosa - GO");
        ScraperAnuncioService.aplicarExtracao(
                existente, extraido, "Formosa", "GO", texto, false, "Compra e venda de gado");
        assertThat(existente.getCidade()).isEqualTo("Minas Gerais - MG");
    }

    @Test
    void textoComEstadoGanhaDoGrupoQuandoNaoHaMunicipioNaMensagem() {
        // Ordem fixa: texto primeiro. "em Minas Gerais" vence o município só no nome do grupo.
        String texto = "Vendo vaca de leite a 9 mil cada em Minas Gerais";
        String grupo = "Compra e venda Luziânia Goiás";

        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                texto, "Minas Gerais", "MG", "Formosa", "GO", grupo);
        assertThat(gravada).isEqualTo("Minas Gerais - MG");

        // Sem local no texto → aí o grupo (Luziânia) entra.
        String soGrupo = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                "Vendo vaca de leite a 9 mil cada", null, null, "Formosa", "GO", grupo);
        assertThat(soGrupo).isEqualTo("Luziânia - GO");
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

        // Sem cidade no JSON: usa o padrão do grupo se for município real (ex.: Cabeceiras).
        var extraido = GeminiExtractionService.interpretarResposta(corpo, new ObjectMapper(), "Cabeceiras", "GO");

        assertThat(extraido.cidade()).isEqualTo("Cabeceiras");
        assertThat(extraido.uf()).isEqualTo("GO");
        assertThat(extraido.titulo()).isEqualTo("20 bezerros");
        assertThat(extraido.origem()).isEqualTo("WHATSAPP");
        assertThat(extraido.telefoneVendedorExtraido()).isNull();

        var vazio = GeminiExtractionService.interpretarResposta(corpo, new ObjectMapper(), CidadesGoias.SEM_CIDADE, "");
        assertThat(vazio.cidade()).isEqualTo(CidadesGoias.SEM_CIDADE);
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
        String texto = WhatsAppConsultantService.conviteFoto("produtor");

        assertThat(texto).contains("Opa produtor!");
        assertThat(texto).contains("sem foto");
        assertThat(texto).doesNotContain("http");
        assertThat(texto).doesNotContain("consultor");
    }

    @Test
    void apresentacaoCitaOLoteENaoCobraComissao() {
        String texto = WhatsAppConsultantService.montarApresentacao(
                "Romario", 12, "NELORE", "https://appcompregado.com.br/lote/3");

        assertThat(texto).contains("Romario");
        assertThat(texto).contains("dos 12 Nelore");
        assertThat(texto).contains("não cobra comissão");
        assertThat(texto).contains("https://appcompregado.com.br/lote/3");
        assertThat(texto).doesNotContain("consultor");
    }

    @Test
    void fotoDoRadarAnexaMasNaoPublicaRascunhoGenerico() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var comFoto = json.readTree("{\"texto\":\"\",\"midias\":[{\"tipo\":\"image/jpeg\",\"base64\":\"abc\"}]}");
        var semFoto = json.readTree("{\"texto\":\"vendo vaca\"}");

        assertThat(ScraperAnuncioService.temMidia(comFoto, comFoto)).isTrue();
        assertThat(ScraperAnuncioService.temMidia(semFoto, semFoto)).isFalse();

        Anuncio rascunho = new Anuncio();
        rascunho.setTitulo("Anúncio de gado");
        rascunho.setPreco(BigDecimal.ZERO);
        rascunho.setAtivo(false);
        rascunho.setStatus(AnuncioStatus.ATIVO);
        rascunho.setStatusProcessamento(StatusProcessamento.RASCUNHO_SEM_FOTO);

        assertThat(ScraperAnuncioService.anexarFotos(rascunho, List.of("/uploads/vaca.jpg"))).isFalse();
        assertThat(rascunho.isAtivo()).isFalse();
        assertThat(rascunho.getStatusProcessamento()).isEqualTo(StatusProcessamento.RASCUNHO_SEM_FOTO);
        assertThat(rascunho.getUrlFotoPrincipal()).isEqualTo("/uploads/vaca.jpg");
        assertThat(rascunho.getFotos()).containsExactly("/uploads/vaca.jpg");

        assertThat(ScraperAnuncioService.anexarFotos(rascunho, List.of("/uploads/vaca.jpg", "/uploads/lote.jpg"))).isFalse();
        assertThat(rascunho.getFotos()).containsExactly("/uploads/vaca.jpg", "/uploads/lote.jpg");
    }

    @Test
    void extracaoLocalLePrecoCidadeETituloDoTexto() {
        String texto = "Boa noite pessoal estou vendendo vaca de leite está a 15 mil cada está em Cabeceiras goias";

        var extraido = ExtracaoLocal.extrair(texto, "Formosa", "GO").orElseThrow();

        assertThat(extraido.preco()).isEqualByComparingTo("15000");
        assertThat(extraido.cidade()).isEqualTo("Cabeceiras");
        assertThat(extraido.uf()).isEqualTo("GO");
        assertThat(extraido.titulo()).containsIgnoringCase("vaca");
        assertThat(extraido.titulo()).isNotEqualTo("Anúncio de gado");
    }

    @Test
    void extracaoLocalLeOitoMilReaisEGuaraDf() {
        String texto = "Vendo vaca de cortede 8000 reais cadas uma em guara df";

        var extraido = ExtracaoLocal.extrair(texto, "Formosa", "GO").orElseThrow();

        assertThat(extraido.preco()).isEqualByComparingTo("8000");
        assertThat(extraido.cidade()).isEqualTo("Guará");
        assertThat(extraido.uf()).isEqualTo("DF");
        assertThat(extraido.titulo()).containsIgnoringCase("vaca");

        assertThat(ExtracaoLocal.lerPreco("vaca a 8.000 reais cada")).isEqualByComparingTo("8000");
        assertThat(ExtracaoLocal.lerPreco("lote R$ 8.000")).isEqualByComparingTo("8000");
        assertThat(ExtracaoLocal.lerPreco("está a 8 mil")).isEqualByComparingTo("8000");
        assertThat(ExtracaoLocal.lerPreco("cadas uma 9000")).isEqualByComparingTo("9000");
    }

    @Test
    void atualizacaoTrocaPrecoAntigoPeloNovoDoTexto() {
        Anuncio existente = new Anuncio();
        existente.setTitulo("Lote de vacas de leite");
        existente.setPreco(new BigDecimal("15000"));
        existente.setCidade("Cabeceiras - GO");
        existente.setRaca(Raca.OUTRA);
        existente.setAtivo(true);
        existente.setStatus(AnuncioStatus.ATIVO);
        existente.setStatusProcessamento(StatusProcessamento.PUBLICADO);

        String texto = "Vendo vaca de cortede 8000 reais cadas uma em guara df";
        var extraido = ExtracaoLocal.extrair(texto, "Formosa", "GO").orElseThrow();

        ScraperAnuncioService.aplicarExtracao(existente, extraido, "Formosa", "GO", texto, false);

        assertThat(existente.getPreco()).isEqualByComparingTo("8000");
        assertThat(existente.getCidade()).isEqualTo("Guará - DF");
        assertThat(existente.getTitulo()).containsIgnoringCase("vaca");
        assertThat(existente.getAptidao()).isEqualTo(Aptidao.CORTE);
    }

    @Test
    void atualizacaoAplicaExtracaoENaoApagaTituloBom() {
        Anuncio rascunho = new Anuncio();
        rascunho.setTitulo("Anúncio de gado");
        rascunho.setPreco(BigDecimal.ZERO);
        rascunho.setCidade("Formosa - GO");
        rascunho.setRaca(Raca.OUTRA);
        rascunho.setAtivo(false);
        rascunho.setStatus(AnuncioStatus.ATIVO);
        rascunho.setStatusProcessamento(StatusProcessamento.RASCUNHO_SEM_FOTO);

        ExtracaoAnuncio extraido = new ExtracaoAnuncio(
                "Vaca de leite",
                new BigDecimal("12000"),
                Raca.GIROLANDO,
                3,
                "Cabeceiras",
                "GO",
                null,
                "WHATSAPP");
        String texto = "estou vendendo vaca de leite está a 12 mil cada está em Cabeceiras";

        ScraperAnuncioService.aplicarExtracao(rascunho, extraido, "Formosa", "GO", texto, false);
        assertThat(ScraperAnuncioService.anexarFotos(rascunho, List.of("/uploads/vaca.jpg"))).isTrue();

        assertThat(rascunho.getTitulo()).isEqualTo("Vaca de leite");
        assertThat(rascunho.getPreco()).isEqualByComparingTo("12000");
        assertThat(rascunho.getCidade()).isEqualTo("Cabeceiras - GO");
        assertThat(rascunho.getQuantidade()).isEqualTo(3);
        assertThat(rascunho.getRaca()).isEqualTo(Raca.GIROLANDO);
        assertThat(rascunho.getCategoriaAnimal()).isEqualTo(CategoriaAnimal.VACA);
        assertThat(rascunho.getAptidao()).isEqualTo(Aptidao.LEITE);
        assertThat(rascunho.getTipoVenda()).isEqualTo(TipoVenda.LOTE);
        assertThat(rascunho.isAtivo()).isTrue();
        assertThat(rascunho.getStatusProcessamento()).isEqualTo(StatusProcessamento.PUBLICADO);
        assertThat(rascunho.getUrlFotoPrincipal()).isEqualTo("/uploads/vaca.jpg");

        ScraperAnuncioService.aplicarExtracao(rascunho, null, "Formosa", "GO", "", false);

        assertThat(rascunho.getTitulo()).isEqualTo("Vaca de leite");
        assertThat(rascunho.getPreco()).isEqualByComparingTo("12000");
        assertThat(rascunho.getCidade()).isEqualTo("Cabeceiras - GO");
        assertThat(ScraperAnuncioService.tituloProntoParaAbordagem(rascunho)).isTrue();
    }

    @Test
    void vitrineExigeFotoECidadeReal() {
        assertThat(AnuncioBusca.temProva("http://localhost/foto.jpg", "Formosa - GO")).isTrue();
        assertThat(AnuncioBusca.temProva("http://localhost/foto.jpg", "Local a combinar")).isTrue();
        assertThat(AnuncioBusca.temProva(null, "Formosa - GO")).isFalse();
        assertThat(AnuncioBusca.temProva("http://localhost/foto.jpg", "A confirmar - GO")).isFalse();
        assertThat(AnuncioBusca.temProva("http://localhost/foto.jpg", "SEM_CIDADE")).isFalse();
        assertThat(AnuncioBusca.temProva("http://localhost/foto.jpg", "  ")).isFalse();
    }
}
