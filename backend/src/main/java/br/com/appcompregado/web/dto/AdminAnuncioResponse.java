package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusContato;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.StatusPublicacao;
import br.com.appcompregado.repository.AnuncioBusca;
import br.com.appcompregado.service.TextoAbordagem;
import java.math.BigDecimal;
import java.util.List;

/** Card do limbo / fila de prospecção. Inclui telefone e texto honesto para o wa.me. */
public record AdminAnuncioResponse(
        Long id,
        String titulo,
        String descricao,
        BigDecimal preco,
        String precoFormatado,
        String cidade,
        Integer quantidade,
        CategoriaAnimal categoriaAnimal,
        Aptidao aptidao,
        Raca raca,
        String urlFotoPrincipal,
        List<String> fotos,
        String telefoneVendedor,
        String vendedorNome,
        StatusPublicacao statusPublicacao,
        StatusContato statusContato,
        StatusProcessamento statusProcessamento,
        boolean aptoVitrine,
        String mensagemWhatsapp) {

    public static AdminAnuncioResponse from(Anuncio anuncio) {
        String telefone = anuncio.getTelefoneVendedorOriginal();
        if (telefone == null || telefone.isBlank()) {
            telefone = anuncio.getVendedor() != null ? anuncio.getVendedor().getWhatsapp() : null;
        }
        boolean apto = AnuncioBusca.temProva(anuncio.getUrlFotoPrincipal(), anuncio.getCidade());
        return new AdminAnuncioResponse(
                anuncio.getId(),
                anuncio.getTitulo(),
                anuncio.getDescricao(),
                anuncio.getPreco(),
                AnuncioResponse.formatarPreco(anuncio.getPreco()),
                anuncio.getCidade(),
                anuncio.getQuantidade(),
                anuncio.getCategoriaAnimal(),
                anuncio.getAptidao(),
                anuncio.getRaca(),
                anuncio.getUrlFotoPrincipal(),
                List.copyOf(anuncio.getFotos()),
                telefone,
                anuncio.getVendedor() != null ? anuncio.getVendedor().getNome() : null,
                anuncio.getStatusPublicacao(),
                anuncio.getStatusContato(),
                anuncio.getStatusProcessamento(),
                apto,
                TextoAbordagem.mensagemAdminProspeccao(anuncio));
    }
}
