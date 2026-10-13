package com.boinatela.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnuncioBuscaTest {

    @Test
    void separaTermosIgnorandoPontuacaoEPalavrasCurtas() {
        assertThat(AnuncioBusca.termos("Bezerros Nelore, Formosa - GO"))
                .containsExactly("bezerros", "nelore", "formosa");
    }

    @Test
    void buscaVaziaNaoGeraTermos() {
        assertThat(AnuncioBusca.termos("   ")).isEmpty();
        assertThat(AnuncioBusca.termos(null)).isEmpty();
    }

    @Test
    void mantemAcentosERemoveRepetidos() {
        assertThat(AnuncioBusca.termos("Reprodução reprodução")).containsExactly("reprodução");
    }
}
