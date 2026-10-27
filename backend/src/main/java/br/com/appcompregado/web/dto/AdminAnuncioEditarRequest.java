package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import java.math.BigDecimal;

/** Campos editáveis no painel admin. Nulos = não alterar. */
public record AdminAnuncioEditarRequest(
        String titulo,
        String descricao,
        BigDecimal preco,
        String cidade,
        Integer quantidade,
        CategoriaAnimal categoriaAnimal,
        Aptidao aptidao,
        Raca raca) {
}
