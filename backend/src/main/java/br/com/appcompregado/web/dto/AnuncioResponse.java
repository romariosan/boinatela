package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.TipoVenda;
import java.math.BigDecimal;
import java.util.List;

public record AnuncioResponse(
        Long id,
        String titulo,
        String descricao,
        BigDecimal preco,
        String cidade,
        List<String> fotos,
        CategoriaAnimal categoriaAnimal,
        Aptidao aptidao,
        Raca raca,
        TipoVenda tipoVenda,
        AnuncioStatus status,
        Long vendedorId,
        String vendedorNome) {

    public static AnuncioResponse from(Anuncio anuncio) {
        return new AnuncioResponse(
                anuncio.getId(),
                anuncio.getTitulo(),
                anuncio.getDescricao(),
                anuncio.getPreco(),
                anuncio.getCidade(),
                List.copyOf(anuncio.getFotos()),
                anuncio.getCategoriaAnimal(),
                anuncio.getAptidao(),
                anuncio.getRaca(),
                anuncio.getTipoVenda(),
                anuncio.getStatus(),
                anuncio.getVendedor().getId(),
                anuncio.getVendedor().getNome());
    }
}
