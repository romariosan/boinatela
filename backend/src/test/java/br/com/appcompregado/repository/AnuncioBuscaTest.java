package br.com.appcompregado.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.StatusPublicacao;
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

    @Test
    void vitrineExigeAprovadoAlemDeAtivoEPublicado() {
        Anuncio a = new Anuncio();
        a.setAtivo(true);
        a.setStatus(AnuncioStatus.ATIVO);
        a.setStatusProcessamento(StatusProcessamento.PUBLICADO);
        a.setStatusPublicacao(StatusPublicacao.PENDENTE);
        a.setUrlFotoPrincipal("https://appcompregado.com.br/uploads/x.jpg");
        a.setCidade("Cabeceiras - GO");
        assertThat(a.publicadoNaVitrine()).isFalse();

        a.setStatusPublicacao(StatusPublicacao.APROVADO);
        assertThat(a.publicadoNaVitrine()).isTrue();

        a.setCidade("SEM_CIDADE");
        assertThat(a.publicadoNaVitrine()).isFalse();

        a.setCidade("Local a combinar");
        assertThat(a.publicadoNaVitrine()).isTrue();
    }
}

