package br.com.appcompregado.web.dto;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.Aptidao;
import br.com.appcompregado.domain.enums.CategoriaAnimal;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.domain.enums.TipoVenda;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

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
        Integer quantidade,
        Integer pesoMedioKg,
        AnuncioStatus status,
        Long vendedorId,
        String vendedorNome,
        boolean reivindicado,
        boolean capturado,
        boolean ativo,
        String precoFormatado,
        String cidadeFormatada,
        String uf,
        String urlFotoPrincipal,
        StatusProcessamento statusProcessamento) {

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
                anuncio.getQuantidade(),
                anuncio.getPesoMedioKg(),
                anuncio.getStatus(),
                anuncio.getVendedor().getId(),
                anuncio.getVendedor().getNome(),
                anuncio.isReivindicado(),
                anuncio.getDataCaptura() != null,
                anuncio.isAtivo(),
                formatarPreco(anuncio.getPreco()),
                formatarCidade(anuncio),
                anuncio.getUf(),
                anuncio.getUrlFotoPrincipal(),
                anuncio.getStatusProcessamento());
    }

    public static String formatarPreco(BigDecimal preco) {
        if (preco == null || preco.signum() == 0) {
            return "Preço a Combinar";
        }
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(preco);
    }

    public static String formatarCidade(Anuncio anuncio) {
        String cidade = anuncio.getCidade() == null ? "" : anuncio.getCidade().trim();
        String uf = anuncio.getUf();
        if (!cidade.isBlank() && !cidade.matches("(?i).* - [a-z]{2}$") && uf != null && !uf.isBlank()) {
            cidade = cidade + " - " + uf;
        }
        if (cidade.isBlank()) {
            return "📍 " + (uf == null || uf.isBlank() ? "GO" : uf);
        }
        return "📍 " + cidade;
    }
}
