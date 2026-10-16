package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RadarRetencaoTest {

    @Test
    void radarDescreveQuantidadeRacaCidadeELink() {
        String texto = AlertaMatchService.mensagem(new AlertaNotificador.NovoAnuncio(
                9L, "NELORE", "Formosa - GO", "BEZERRO", 20, "https://appcompregado.com.br/lote/9", "61999998888"));

        assertThat(texto).contains("Formosa - GO");
        assertThat(texto).contains("20 cab. de Nelore");
        assertThat(texto).contains("https://appcompregado.com.br/lote/9");
    }

    @Test
    void tokenDeVendidoNaoValeParaOutroAnuncio() {
        String token = VendidoToken.assinar("segredo-de-teste", 15L);
        assertThat(token).isNotBlank();
        assertThat(VendidoToken.assinar("segredo-de-teste", 15L)).isEqualTo(token);
        assertThat(VendidoToken.assinar("segredo-de-teste", 16L)).isNotEqualTo(token);
    }
}
