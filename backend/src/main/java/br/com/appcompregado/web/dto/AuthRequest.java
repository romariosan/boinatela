package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthRequest(
        @NotBlank String nome,
        @NotBlank @Size(min = 10, max = 20) String whatsapp,
        @NotBlank @Size(min = RegrasSenha.MINIMO, max = RegrasSenha.MAXIMO) String senha) {

    @Override
    public String toString() {
        return "AuthRequest[nome=" + nome + ", whatsapp=" + whatsapp + ", senha=***]";
    }
}
