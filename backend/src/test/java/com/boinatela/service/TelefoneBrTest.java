package com.boinatela.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TelefoneBrTest {

    @Test
    void aceitaDdiJidENonoDigito() {
        assertThat(TelefoneBr.mesmoNumero("61999998888", "5561999998888")).isTrue();
        assertThat(TelefoneBr.mesmoNumero("61999998888", "556199998888@s.whatsapp.net")).isTrue();
        assertThat(TelefoneBr.mesmoNumero("61999998888", "5561999998888:7@s.whatsapp.net")).isTrue();
        assertThat(TelefoneBr.mesmoNumero("(61) 99999-8888", "+55 61 99999-8888")).isTrue();
    }

    @Test
    void recusaOutroNumeroOuDdd() {
        assertThat(TelefoneBr.mesmoNumero("61999998888", "5561999998887")).isFalse();
        assertThat(TelefoneBr.mesmoNumero("61999998888", "5562999998888")).isFalse();
        assertThat(TelefoneBr.mesmoNumero("", "")).isFalse();
        assertThat(TelefoneBr.mesmoNumero("61999998888", null)).isFalse();
    }

    @Test
    void extraiCodigoDoTextoDoBot() {
        assertThat(VerificacaoWhatsappService.extrairCodigo("Quero confirmar. Codigo: bt-004211"))
                .contains("BT-004211");
        assertThat(VerificacaoWhatsappService.extrairCodigo("BT-12345")).isEmpty();
        assertThat(VerificacaoWhatsappService.gerarCodigo()).matches("BT-\\d{6}");
    }
}
