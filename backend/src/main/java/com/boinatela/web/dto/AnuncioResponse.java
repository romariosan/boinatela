package com.boinatela.web.dto;

import com.boinatela.domain.Anuncio;
import com.boinatela.domain.enums.AnuncioStatus;
import com.boinatela.domain.enums.Aptidao;
import com.boinatela.domain.enums.CategoriaAnimal;
import com.boinatela.domain.enums.Raca;
import com.boinatela.domain.enums.TipoVenda;
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
