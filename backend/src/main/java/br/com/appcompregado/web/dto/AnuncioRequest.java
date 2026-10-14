package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.TipoVenda;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * Dados do anúncio enviados na parte "dados" do multipart.
 * {@code fotosMantidas} só vale na edição: URLs das fotos atuais que continuam, na ordem (a primeira é a capa);
 * as fotos novas entram depois delas. Nulo na edição = manter todas.
 */
public record AnuncioRequest(
        @NotBlank @Size(max = 120) String titulo,
        @Size(max = 4000) String descricao,
        @NotNull @Positive @Digits(integer = 12, fraction = 2) BigDecimal preco,
        @NotBlank @Size(max = 120) @Pattern(regexp = "^\\S.* - [A-Z]{2}$") String cidade,
        @NotNull CategoriaAnimal categoriaAnimal,
        @NotNull Aptidao aptidao,
        @NotNull Raca raca,
        @NotNull TipoVenda tipoVenda,
        @Size(max = 5) List<String> fotosMantidas) {
}
