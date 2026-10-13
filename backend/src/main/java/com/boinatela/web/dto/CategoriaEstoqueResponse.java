package com.boinatela.web.dto;

import com.boinatela.domain.enums.CategoriaAnimal;

public record CategoriaEstoqueResponse(CategoriaAnimal categoria, long count) {
}
