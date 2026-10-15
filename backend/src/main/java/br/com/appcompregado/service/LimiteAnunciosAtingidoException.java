package br.com.appcompregado.service;

/** Vendedor sem plano PRO tentando ter mais anúncios ativos do que o limite gratuito. */
public class LimiteAnunciosAtingidoException extends RuntimeException {

    private final int limite;

    public LimiteAnunciosAtingidoException(int limite) {
        super("Você atingiu o limite de " + limite + " anúncios gratuitos.");
        this.limite = limite;
    }

    public int getLimite() {
        return limite;
    }
}
