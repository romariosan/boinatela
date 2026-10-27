package br.com.appcompregado.service;

import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.service.CidadesGoias.Local;
import br.com.appcompregado.service.GeminiExtractionService.ExtracaoAnuncio;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê preço, cidade, quantidade e um título curto do texto do grupo quando a Gemini
 * falha (cota 429, timeout, 503). Não inventa preço: só usa o que o produtor escreveu.
 */
final class ExtracaoLocal {

    /** Número com milhar (8.000) ou inteiro completo (8000) — nunca corta 9000 em 900. */
    private static final String NUMERO = "(\\d{1,3}(?:[.\\s]\\d{3})+|\\d+)";
    private static final Pattern PRECO_MIL = Pattern.compile(
            "(?i)(?:r\\$\\s*)?" + NUMERO + "\\s*mil(?:\\s*(?:reais|cada|a\\s+cabe[cç]a))?");
    private static final Pattern PRECO_CIFRA = Pattern.compile(
            "(?i)r\\$\\s*(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{2})?|\\d+(?:,\\d{2})?)");
    private static final Pattern PRECO_REAIS = Pattern.compile(
            "(?i)" + NUMERO + "\\s*reais");
    private static final Pattern PRECO_CADA = Pattern.compile(
            "(?i)(?:cada|cadas)\\s+uma(?:\\s+de)?\\s+(?:r\\$\\s*)?" + NUMERO);
    /** "6700 cadas uma" / "8.000 cada uma" — número antes do "cada". */
    private static final Pattern PRECO_ANTES_CADA = Pattern.compile(
            "(?i)(?:r\\$\\s*)?" + NUMERO + "\\s*(?:cada|cadas)\\s+uma");
    private static final Pattern QUANTIDADE = Pattern.compile(
            "(?i)\\b(\\d{1,4})\\s*(vacas?|novilhas?|bezerros?|bezerra?s?|garrotes?|touros?|cabe[cç]as?|animais)\\b");

    private ExtracaoLocal() {
    }

    static Optional<ExtracaoAnuncio> extrair(String texto, String cidadePadrao, String ufPadrao) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        BigDecimal preco = lerPreco(texto);
        Integer quantidade = lerQuantidade(texto);
        Optional<Local> local = CidadesGoias.inferirLocal(texto);
        String cidade = local.map(Local::cidade).orElse(null);
        String uf = local.map(Local::uf).orElse(null);
        Raca raca = lerRaca(texto);
        String titulo = montarTitulo(texto, quantidade, raca);
        if (titulo == null && preco == null && cidade == null) {
            return Optional.empty();
        }
        if (titulo == null || titulo.isBlank()) {
            titulo = "Lote de gado";
        }
        // Cidade/UF só quando o texto cita; quem grava usa o padrão do grupo se vier null.
        return Optional.of(new ExtracaoAnuncio(
                titulo,
                preco,
                raca,
                quantidade,
                cidade,
                uf,
                null,
                "WHATSAPP"));
    }

    static BigDecimal lerPreco(String texto) {
        Matcher mil = PRECO_MIL.matcher(texto);
        if (mil.find()) {
            return multiplicarMil(mil.group(1));
        }
        Matcher cifra = PRECO_CIFRA.matcher(texto);
        if (cifra.find()) {
            return lerNumero(cifra.group(1));
        }
        Matcher reais = PRECO_REAIS.matcher(texto);
        if (reais.find()) {
            return lerNumero(reais.group(1));
        }
        Matcher cada = PRECO_CADA.matcher(texto);
        if (cada.find()) {
            return lerNumero(cada.group(1));
        }
        Matcher antesCada = PRECO_ANTES_CADA.matcher(texto);
        if (antesCada.find()) {
            return lerNumero(antesCada.group(1));
        }
        return null;
    }

    private static BigDecimal multiplicarMil(String bruto) {
        String digitos = bruto == null ? "" : bruto.replaceAll("[.\\s]", "");
        if (digitos.isBlank()) {
            return null;
        }
        return new BigDecimal(digitos).multiply(BigDecimal.valueOf(1000));
    }

    private static BigDecimal lerNumero(String bruto) {
        if (bruto == null || bruto.isBlank()) {
            return null;
        }
        String limpo = bruto.replaceAll("\\s", "").replace(".", "");
        if (limpo.contains(",")) {
            limpo = limpo.replace(',', '.');
        }
        if (limpo.isBlank()) {
            return null;
        }
        return new BigDecimal(limpo);
    }

    static Integer lerQuantidade(String texto) {
        Matcher m = QUANTIDADE.matcher(texto);
        if (!m.find()) {
            return null;
        }
        int valor = Integer.parseInt(m.group(1));
        return valor > 0 && valor <= 100_000 ? valor : null;
    }

    static Raca lerRaca(String texto) {
        String normal = CidadesGoias.normalizar(texto);
        for (Raca raca : Raca.values()) {
            if (raca == Raca.OUTRA || raca == Raca.MISTO) {
                continue;
            }
            String nome = CidadesGoias.normalizar(raca.name());
            if (normal.contains(nome)) {
                return raca;
            }
        }
        return Raca.OUTRA;
    }

    static String montarTitulo(String texto, Integer quantidade, Raca raca) {
        String normal = CidadesGoias.normalizar(texto);
        String animal;
        if (normal.contains("vaca") || normal.contains("novilha")) {
            animal = normal.contains("leite") ? "vacas de leite" : "vacas";
        } else if (normal.contains("garrote") || normal.contains("novilho")) {
            animal = "garrotes";
        } else if (normal.contains("touro") || normal.contains("reprodutor")) {
            animal = "touros";
        } else if (normal.contains("bezerro") || normal.contains("bezerra")) {
            animal = "bezerros";
        } else if (quantidade == null && (raca == null || raca == Raca.OUTRA)) {
            return null;
        } else {
            animal = "gado";
        }
        StringBuilder titulo = new StringBuilder();
        if (quantidade != null && quantidade > 0) {
            titulo.append(quantidade).append(' ');
        } else {
            titulo.append("Lote de ");
        }
        titulo.append(animal);
        if (raca != null && raca != Raca.OUTRA) {
            String nome = raca.name().charAt(0) + raca.name().substring(1).toLowerCase(Locale.ROOT);
            titulo.append(' ').append(nome);
        }
        return titulo.toString();
    }
}
