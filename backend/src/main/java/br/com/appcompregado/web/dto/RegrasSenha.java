package br.com.appcompregado.web.dto;

/**
 * Vale para toda senha nova (cadastro, recuperação e troca). O login não usa: contas antigas foram
 * criadas com mínimo de 4 e continuam entrando. O máximo de 72 é o que o BCrypt considera.
 */
public final class RegrasSenha {

    public static final int MINIMO = 6;
    public static final int MAXIMO = 72;

    private RegrasSenha() {
    }
}
