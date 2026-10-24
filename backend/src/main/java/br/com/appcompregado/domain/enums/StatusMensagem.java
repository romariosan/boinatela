package br.com.appcompregado.domain.enums;

public enum StatusMensagem {
    PENDENTE,
    /** O robô já levou a linha. Outro GET não devolve a mesma pendência. */
    RESERVADA,
    ENVIADA,
    FALHOU,
    EXPIRADA
}
