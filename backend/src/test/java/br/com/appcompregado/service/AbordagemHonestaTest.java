package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.appcompregado.domain.enums.IntencaoProdutor;
import br.com.appcompregado.service.GeminiDialogService.Classificacao;
import br.com.appcompregado.service.LeadAbordagemService.Contexto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class AbordagemHonestaTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void jsonInvalidoNaoViraTextoParaFila() {
        assertThat(GeminiDialogService.interpretarCorpo("não é json", json)).isEmpty();
        assertThat(GeminiDialogService.interpretarCorpo("{\"intencao\":\"TALVEZ\"}", json)).isEmpty();
        assertThat(GeminiDialogService.interpretarCorpo("{\"intencao\":\"DUVIDA\",\"reply_text\":1}", json)).isEmpty();

        Contexto ctx = new Contexto(3L, 12, "Nelore", false, false, false, "https://appcompregado.com.br/lote/3");
        assertThat(LeadAbordagemService.textoPara(null, ctx)).isNull();
        assertThat(LeadAbordagemService.textoPara(new Classificacao(IntencaoProdutor.DUVIDA, "abre https://golpe.test"), ctx))
                .isNull();
        assertThat(LeadAbordagemService.textoPara(new Classificacao(IntencaoProdutor.RECUSA, "tanto faz"), ctx))
                .isNull();
    }

    @Test
    void primeiraMensagemNaoLevaUrl() {
        String texto = TextoAbordagem.estreia(15, 30, "Nelore", "Cristalina - GO", "Pecuária Cristalina");

        assertThat(texto).startsWith("Boa tarde.");
        assertThat(texto).contains("equipe do CompreGado");
        assertThat(texto).contains("em Cristalina");
        assertThat(texto).doesNotContain("em Formosa");
        assertThat(texto).contains("30 Nelore");
        assertThat(texto).contains("no grupo Pecuária Cristalina");
        assertThat(texto).contains("Ainda tá na mão?");
        assertThat(TextoAbordagem.contemUrl(texto)).isFalse();
        assertThat(texto).doesNotContain("consultor");
    }

    @Test
    void estreiaEsperaDuasHorasDentroDaJanela() {
        ZonedDateTime noite = ZonedDateTime.of(2026, 10, 23, 20, 15, 0, 0, HorarioComercial.ZONA);
        assertThat(HorarioComercial.primeiraMensagem(noite))
                .isEqualTo(ZonedDateTime.of(2026, 10, 24, 8, 0, 0, 0, HorarioComercial.ZONA));

        ZonedDateTime manha = ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, HorarioComercial.ZONA);
        ZonedDateTime slot = HorarioComercial.primeiraMensagem(manha);
        assertThat(slot).isEqualTo(manha.plusHours(2));
        assertThat(slot.getHour()).isBetween(8, 17);
    }
}
