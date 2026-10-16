package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PhoneUtilsTest {

    @Test
    void colocaONonoDigitoETiraMascaraDdiEJid() {
        assertThat(PhoneUtils.normalizar("(61) 9999-8888")).isEqualTo("61999998888");
        assertThat(PhoneUtils.normalizar("+55 61 99999-8888")).isEqualTo("61999998888");
        assertThat(PhoneUtils.normalizar("556199998888@s.whatsapp.net")).isEqualTo("61999998888");
        assertThat(PhoneUtils.normalizar("5561999998888:7@s.whatsapp.net")).isEqualTo("61999998888");
    }

    @Test
    void mesmoNumeroAceitaFormasDiferentesERecusaOutro() {
        assertThat(PhoneUtils.mesmoNumero("61999998888", "556199998888")).isTrue();
        assertThat(PhoneUtils.mesmoNumero("61999998888", "5561999998887")).isFalse();
        assertThat(PhoneUtils.mesmoNumero("", "")).isFalse();
        assertThat(PhoneUtils.mesmoNumero("61999998888", null)).isFalse();
    }

    @Test
    void varianteLegadaServeParaAcharCadastroAntigo() {
        assertThat(PhoneUtils.variantesLegadas("61999998888")).containsExactly("6199998888");
    }

    @Test
    void mensagemComMaisDeDezMinutosNaoEntra() {
        Instant agora = Instant.parse("2026-10-16T15:00:00Z");
        long agoraSegundos = agora.getEpochSecond();
        assertThat(ScraperAnuncioService.recente(agoraSegundos - 9 * 60, agora)).isTrue();
        assertThat(ScraperAnuncioService.recente(agoraSegundos - 11 * 60, agora)).isFalse();
        assertThat(ScraperAnuncioService.recente(null, agora)).isFalse();
    }
}
