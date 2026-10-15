package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A senha atual só tem limite máximo: contas antigas têm senhas de 4 e 5 caracteres e precisam conseguir trocar. */
public record AlterarSenhaRequest(
        @NotBlank @Size(max = RegrasSenha.MAXIMO) String senhaAtual,
        @NotBlank @Size(min = RegrasSenha.MINIMO, max = RegrasSenha.MAXIMO) String novaSenha,
        @NotBlank @Size(min = RegrasSenha.MINIMO, max = RegrasSenha.MAXIMO) String confirmarNovaSenha) {

    /** Senhas nunca em texto: o toString padrão do record as colocaria em qualquer log. */
    @Override
    public String toString() {
        return "AlterarSenhaRequest[senhaAtual=***, novaSenha=***, confirmarNovaSenha=***]";
    }
}
