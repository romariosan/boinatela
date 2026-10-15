package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;

/** Sem tamanho mínimo de propósito: contas antigas têm senhas de 4 e 5 caracteres (ver {@link RegrasSenha}). */
public record LoginRequest(
        @NotBlank String whatsapp,
        @NotBlank String senha) {

    @Override
    public String toString() {
        return "LoginRequest[whatsapp=" + whatsapp + ", senha=***]";
    }
}
