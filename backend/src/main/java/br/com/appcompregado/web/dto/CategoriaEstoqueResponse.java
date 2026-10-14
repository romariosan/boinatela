package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.enums.CategoriaAnimal;

public record CategoriaEstoqueResponse(CategoriaAnimal categoria, long count) {
}
