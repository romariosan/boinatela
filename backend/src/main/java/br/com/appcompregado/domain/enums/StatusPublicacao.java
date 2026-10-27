package br.com.appcompregado.domain.enums;

/**
 * Moderação antes da vitrine pública.
 * Captura do Radar/scraper entra como {@link #PENDENTE}; só {@link #APROVADO} aparece na lista.
 */
public enum StatusPublicacao {
    PENDENTE,
    APROVADO,
    REJEITADO
}
