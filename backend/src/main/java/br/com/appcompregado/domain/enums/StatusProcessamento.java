package br.com.appcompregado.domain.enums;

public enum StatusProcessamento {
    /** Pode ir para a vitrine e para o Radar. */
    PUBLICADO,
    /** Ouvido no grupo sem foto utilizável. Fica fora da vitrine até o dono completar. */
    RASCUNHO_SEM_FOTO
}
