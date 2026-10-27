package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.appcompregado.repository.AnuncioBusca;
import org.junit.jupiter.api.Test;

/**
 * Cinco cenários: (1) texto → (2) grupo → (3) "Local a combinar". Nunca Formosa inventada.
 */
class CidadeAntiAlucinacaoTest {

    @Test
    void conflitoTextoGanhaDoGrupo() {
        // 1) Grupo "Gado Formosa", texto "bezerro em Planaltina" → Planaltina
        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                "Vendo bezerro em Planaltina a 5 mil",
                null,
                null,
                "Formosa",
                "GO",
                "Gado Formosa");
        assertThat(gravada).isEqualTo("Planaltina - DF");
        assertThat(gravada).doesNotContain("Formosa");
    }

    @Test
    void soGrupoCabeceiras() {
        // 2) Grupo "Negócios Cabeceiras", "Vendo 10 vacas" → Cabeceiras
        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                "Vendo 10 vacas",
                null,
                null,
                CidadesGoias.SEM_CIDADE,
                "GO",
                "Negócios Cabeceiras");
        assertThat(gravada).isEqualTo("Cabeceiras - GO");
    }

    @Test
    void vazioViraLocalACombinar() {
        // 3) Grupo "Pecuária Forte", "Lote de nelore" → Local a combinar
        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                "Lote de nelore",
                null,
                null,
                "Formosa",
                "GO",
                "Pecuária Forte");
        assertThat(gravada).isEqualTo("Local a combinar");
        assertThat(gravada).doesNotContain("Formosa");
        assertThat(AnuncioBusca.temProva("https://x/foto.jpg", gravada)).isTrue();
    }

    @Test
    void apelidoDfNoTexto() {
        // 4) Grupo "Entorno", texto "Estou no DF" → Distrito Federal - DF
        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                "Estou no DF vendendo 5 vacas",
                null,
                null,
                CidadesGoias.SEM_CIDADE,
                "GO",
                "Entorno");
        assertThat(gravada).isEqualTo("Distrito Federal - DF");
    }

    @Test
    void grupoCabeceirasSemCidadeNoTextoNaoViraFormosa() {
        // 5) Grupo Cabeceiras, texto sem cidade → Cabeceiras (NÃO Formosa)
        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                "Vendo lote de nelore a 8 mil cada",
                null,
                null,
                "Formosa",
                "GO",
                "Cabeceiras");
        assertThat(gravada).isEqualTo("Cabeceiras - GO");
        assertThat(gravada).doesNotContain("Formosa");
    }

    @Test
    void textoCabeceiraGoSemSViraCabeceiras() {
        // Typo real do WhatsApp: "cabeceira go" (sem S) → Cabeceiras - GO
        String frase = "Boa noite estou vendendo gado de corte nelore 6700 cadas uma está em cabeceira go";
        String gravada = ScraperAnuncioService.cidadeDoTextoOuPadrao(
                frase,
                null,
                null,
                CidadesGoias.SEM_CIDADE,
                "GO",
                "Pecuária Forte");
        assertThat(gravada).isEqualTo("Cabeceiras - GO");
        assertThat(CidadesGoias.inferirMunicipio(frase)).map(CidadesGoias.Local::cidade)
                .contains("Cabeceiras");
        assertThat(ExtracaoLocal.extrair(frase, null, null))
                .isPresent()
                .get()
                .satisfies(e -> {
                    assertThat(e.cidade()).isEqualTo("Cabeceiras");
                    assertThat(e.uf()).isEqualTo("GO");
                    assertThat(e.preco()).isEqualByComparingTo("6700");
                });
    }
}
