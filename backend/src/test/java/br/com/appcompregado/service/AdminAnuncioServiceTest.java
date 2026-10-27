package br.com.appcompregado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusContato;
import br.com.appcompregado.domain.enums.StatusPublicacao;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.web.dto.AdminAnuncioEditarRequest;
import br.com.appcompregado.web.dto.AdminAnuncioResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminAnuncioServiceTest {

    @Mock
    AnuncioRepository anuncios;

    AdminAnuncioService service;
    Usuario admin;
    Usuario comum;

    @BeforeEach
    void preparar() {
        service = new AdminAnuncioService(anuncios);
        admin = new Usuario();
        admin.setId(1L);
        admin.setNome("Admin");
        admin.setWhatsapp("61999990001");
        admin.setAdmin(true);
        comum = new Usuario();
        comum.setId(2L);
        comum.setNome("Comum");
        comum.setWhatsapp("61988887777");
        comum.setAdmin(false);
    }

    @Test
    void naoAdminRecebe400() {
        assertThatThrownBy(() -> service.pendentesModeracao(comum))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("administrador");
    }

    @Test
    void aprovarMoveParaProspeccao() {
        Anuncio a = lote(10L, StatusPublicacao.PENDENTE, StatusContato.NAO_CONTATADO);
        a.setUrlFotoPrincipal("https://appcompregado.com.br/uploads/x.jpg");
        when(anuncios.findByIdWithVendedor(10L)).thenReturn(Optional.of(a));
        when(anuncios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminAnuncioResponse resp = service.alterarPublicacao(admin, 10L, StatusPublicacao.APROVADO);

        assertThat(resp.statusPublicacao()).isEqualTo(StatusPublicacao.APROVADO);
        assertThat(resp.aptoVitrine()).isTrue();
        assertThat(resp.mensagemWhatsapp()).contains("CompreGado");
        ArgumentCaptor<Anuncio> cap = ArgumentCaptor.forClass(Anuncio.class);
        verify(anuncios).save(cap.capture());
        assertThat(cap.getValue().getStatusPublicacao()).isEqualTo(StatusPublicacao.APROVADO);
    }

    @Test
    void aprovarSemFotoFalha() {
        Anuncio a = lote(12L, StatusPublicacao.PENDENTE, StatusContato.NAO_CONTATADO);
        a.setCidade("Local a combinar");
        a.setUrlFotoPrincipal(null);
        when(anuncios.findByIdWithVendedor(12L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.alterarPublicacao(admin, 12L, StatusPublicacao.APROVADO))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("foto");
    }

    @Test
    void aprovarSemCidadeLegadoNormalizaParaLocalACombinar() {
        Anuncio a = lote(13L, StatusPublicacao.PENDENTE, StatusContato.NAO_CONTATADO);
        a.setCidade("SEM_CIDADE");
        a.setUrlFotoPrincipal("https://appcompregado.com.br/uploads/x.jpg");
        when(anuncios.findByIdWithVendedor(13L)).thenReturn(Optional.of(a));
        when(anuncios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminAnuncioResponse resp = service.alterarPublicacao(admin, 13L, StatusPublicacao.APROVADO);

        assertThat(resp.statusPublicacao()).isEqualTo(StatusPublicacao.APROVADO);
        assertThat(resp.cidade()).isEqualTo("Local a combinar");
        assertThat(resp.aptoVitrine()).isTrue();
    }

    @Test
    void marcarContatado() {
        Anuncio a = lote(11L, StatusPublicacao.APROVADO, StatusContato.NAO_CONTATADO);
        when(anuncios.findByIdWithVendedor(11L)).thenReturn(Optional.of(a));
        when(anuncios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminAnuncioResponse resp = service.alterarContato(admin, 11L, StatusContato.CONTATADO);

        assertThat(resp.statusContato()).isEqualTo(StatusContato.CONTATADO);
    }

    @Test
    void listarPendentes() {
        when(anuncios.findPendentesPublicacao())
                .thenReturn(List.of(lote(1L, StatusPublicacao.PENDENTE, StatusContato.NAO_CONTATADO)));
        assertThat(service.pendentesModeracao(admin)).hasSize(1);
    }

    @Test
    void editarAtualizaCamposSemMudarPublicacao() {
        Anuncio a = lote(20L, StatusPublicacao.PENDENTE, StatusContato.NAO_CONTATADO);
        a.setPreco(BigDecimal.valueOf(5000));
        when(anuncios.findByIdWithVendedor(20L)).thenReturn(Optional.of(a));
        when(anuncios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminAnuncioEditarRequest body = new AdminAnuncioEditarRequest(
                "Lote editado de vacas",
                "Descrição nova",
                BigDecimal.valueOf(6700),
                "Cabeceiras - GO",
                5,
                CategoriaAnimal.VACA,
                Aptidao.CORTE,
                Raca.NELORE);

        AdminAnuncioResponse resp = service.editar(admin, 20L, body);

        assertThat(resp.titulo()).isEqualTo("Lote editado de vacas");
        assertThat(resp.descricao()).isEqualTo("Descrição nova");
        assertThat(resp.preco()).isEqualByComparingTo("6700");
        assertThat(resp.cidade()).isEqualTo("Cabeceiras - GO");
        assertThat(resp.quantidade()).isEqualTo(5);
        assertThat(resp.categoriaAnimal()).isEqualTo(CategoriaAnimal.VACA);
        assertThat(resp.aptidao()).isEqualTo(Aptidao.CORTE);
        assertThat(resp.raca()).isEqualTo(Raca.NELORE);
        assertThat(resp.statusPublicacao()).isEqualTo(StatusPublicacao.PENDENTE);
    }

    @Test
    void editarCidadeVaziaViraLocalACombinar() {
        Anuncio a = lote(21L, StatusPublicacao.APROVADO, StatusContato.NAO_CONTATADO);
        when(anuncios.findByIdWithVendedor(21L)).thenReturn(Optional.of(a));
        when(anuncios.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminAnuncioEditarRequest body = new AdminAnuncioEditarRequest(
                null, null, null, "  ", null, null, null, null);

        AdminAnuncioResponse resp = service.editar(admin, 21L, body);

        assertThat(resp.cidade()).isEqualTo("Local a combinar");
        assertThat(resp.statusPublicacao()).isEqualTo(StatusPublicacao.APROVADO);
    }

    @Test
    void editarTituloVazioFalha() {
        Anuncio a = lote(22L, StatusPublicacao.PENDENTE, StatusContato.NAO_CONTATADO);
        when(anuncios.findByIdWithVendedor(22L)).thenReturn(Optional.of(a));

        AdminAnuncioEditarRequest body = new AdminAnuncioEditarRequest(
                "   ", null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.editar(admin, 22L, body))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Título");
    }

    private Anuncio lote(Long id, StatusPublicacao pub, StatusContato contato) {
        Anuncio a = new Anuncio();
        a.setId(id);
        a.setTitulo("Lote de vacas");
        a.setCidade("Formosa - GO");
        a.setQuantidade(2);
        a.setTelefoneVendedorOriginal("61998411786");
        a.setStatusPublicacao(pub);
        a.setStatusContato(contato);
        a.setVendedor(comum);
        return a;
    }
}
