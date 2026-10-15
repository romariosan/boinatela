package br.com.appcompregado.domain.enums;

public enum AnuncioStatus {
    ATIVO,
    /** Fora da vitrine porque o plano PRO venceu e passou do limite gratuito; o dono pode reativar. */
    PAUSADO,
    VENDIDO
}
