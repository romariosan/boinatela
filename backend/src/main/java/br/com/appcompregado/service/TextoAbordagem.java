package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.Raca;
import java.util.Locale;

/** Frases fixas da abordagem. A Gemini não escreve a estreia nem o link. */
public final class TextoAbordagem {

    /** Quem fala no chip Consultor. O lote vem antes do nome. */
    public static final String PERSONAGEM = "Antonio";

    private TextoAbordagem() {
    }

    public static String estreia(int hora, Integer quantidade, String rotulo, String cidade, String grupo) {
        return HorarioComercial.saudacao(hora)
                + ". Vi seu lote de "
                + lote(quantidade, rotulo) + emCidade(cidade) + " " + noGrupo(grupo)
                + ". Sou o " + PERSONAGEM + ", do CompreGado. Ainda tá na mão?";
    }

    public static String ofertaLink() {
        return "Que bom. A gente montou esse lote na vitrine, com as fotos do grupo, "
                + "pra quem procura na região achar mais fácil. Quer que eu te mande o link pra você ver se ficou certo?";
    }

    /**
     * Texto do botão "Chamar no WhatsApp" no painel admin (prospecção).
     * Identifica o CompreGado na primeira frase — sem persona falsa.
     */
    public static String mensagemAdminProspeccao(Anuncio anuncio) {
        String rotulo = rotulo(anuncio);
        String cidade = anuncio.getCidade();
        return "Olá! Aqui é da equipe do CompreGado. Organizei seu lote de "
                + lote(anuncio.getQuantidade(), rotulo)
                + emCidade(cidade).replaceFirst(",$", "")
                + " e coloquei no app pra quem procura na região achar mais fácil. "
                + "Quer que eu te mande o link pra você conferir?";
    }

    public static String link(String url) {
        return "Tá aqui: " + url + ". Se o preço ou o telefone tiver errado, me fala que eu ajusto. Abraço e boa venda.";
    }

    public static String vendido() {
        return "Fechou, então. Tiro o lote da vitrine pra não chegar curioso à toa. Abraço.";
    }

    public static String desconfianca() {
        return "Sou o " + PERSONAGEM + ", do CompreGado. É classificado de gado no Brasil. "
                + "Vi o lote no grupo e coloquei na vitrine. Se não quiser, eu tiro.";
    }

    public static String semResposta(Integer quantidade, String rotulo, String grupo) {
        String assunto = quantidade != null && quantidade > 0
                ? "as " + quantidade + " " + rotulo
                : "o lote de " + rotulo;
        String onde = grupo == null || grupo.isBlank() ? "do grupo" : "do grupo " + grupo.trim();
        return "Aqui é o " + PERSONAGEM + ", do CompreGado, de novo, sobre " + assunto + " " + onde
                + ". Se não quiser o anúncio no ar, é só falar que eu tiro.";
    }

    public static String acompanhamento(Integer quantidade, String rotulo, String url) {
        String cabecas = quantidade != null && quantidade > 0
                ? "As " + quantidade + " " + rotulo
                : "O lote de " + rotulo;
        return cabecas + " continuam na vitrine. Se vendeu, me avisa que eu tiro. Se o preço mudou, eu ajusto. " + url;
    }

    public static String outroLote(Integer quantidade, String rotulo, String loteAnterior) {
        String anterior = loteAnterior == null || loteAnterior.isBlank() ? "o lote anterior" : loteAnterior;
        return "Sou o " + PERSONAGEM + " de novo. Vi outro lote seu no grupo, " + lote(quantidade, rotulo)
                + ". É o mesmo caso das " + anterior + ": posso colocar na vitrine e te mandar o link?";
    }

    public static String lote(Integer quantidade, String rotulo) {
        String nome = rotulo == null || rotulo.isBlank() ? "gado" : rotulo.trim();
        if (quantidade != null && quantidade > 0) {
            return quantidade + " " + nome;
        }
        return nome;
    }

    public static String rotulo(Anuncio anuncio) {
        if (anuncio.getRaca() != null && anuncio.getRaca() != Raca.OUTRA) {
            return titulo(anuncio.getRaca().name());
        }
        if (anuncio.getCategoriaAnimal() != null) {
            return titulo(anuncio.getCategoriaAnimal().name());
        }
        return "gado";
    }

    public static boolean contemUrl(String texto) {
        if (texto == null || texto.isBlank()) {
            return false;
        }
        String normal = texto.toLowerCase(Locale.ROOT);
        return normal.contains("http://") || normal.contains("https://") || normal.contains("www.");
    }

    /** Sugestão da Gemini em dúvida ou outro. URL fora de hora e palavra proibida não viram mensagem. */
    public static String sugestao(String reply, boolean podeEnviarLink) {
        if (reply == null || reply.isBlank()) {
            return null;
        }
        String limpo = reply.trim();
        if (limpo.length() > 400) {
            limpo = limpo.substring(0, 400).trim();
        }
        if (contemUrl(limpo) && !podeEnviarLink) {
            return null;
        }
        if (palavraProibida(limpo)) {
            return null;
        }
        return limpo;
    }

    private static boolean palavraProibida(String texto) {
        String normal = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normal.contains("consultor")
                || normal.contains("plataforma")
                || normal.contains("assessoria")
                || normal.contains("startup")
                || normal.contains("otimizar");
    }

    /** "Cabeceiras - GO" vira " em Cabeceiras,". Sem cidade, a frase não inventa Formosa. */
    private static String emCidade(String cidade) {
        if (cidade == null || cidade.isBlank()) {
            return "";
        }
        String nome = cidade.trim();
        int corte = nome.length() - 5;
        if (nome.length() >= 5 && nome.regionMatches(true, corte, " - ", 0, 3)) {
            nome = nome.substring(0, corte).trim();
        }
        if (nome.isBlank()) {
            return "";
        }
        return " em " + nome + ",";
    }

    private static String noGrupo(String grupo) {
        if (grupo == null || grupo.isBlank()) {
            return "no grupo";
        }
        return "no grupo " + grupo.trim();
    }

    private static String titulo(String enumNome) {
        String nome = enumNome.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(nome.charAt(0)) + nome.substring(1);
    }
}
