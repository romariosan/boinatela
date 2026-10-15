package br.com.appcompregado.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 422 com quantas tentativas faltam até o bloqueio, para o app avisar antes de bloquear. */
public class SenhaAtualIncorretaException extends ResponseStatusException {

    private final int tentativasRestantes;

    public SenhaAtualIncorretaException(int tentativasRestantes) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Senha atual incorreta.");
        this.tentativasRestantes = tentativasRestantes;
    }

    public int getTentativasRestantes() {
        return tentativasRestantes;
    }
}
