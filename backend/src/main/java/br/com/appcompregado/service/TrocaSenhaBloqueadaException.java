package br.com.appcompregado.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 429 com os minutos que faltam para poder tentar de novo. */
public class TrocaSenhaBloqueadaException extends ResponseStatusException {

    private final long minutosRestantes;

    public TrocaSenhaBloqueadaException(long minutosRestantes) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas com a senha atual errada.");
        this.minutosRestantes = minutosRestantes;
    }

    public long getMinutosRestantes() {
        return minutosRestantes;
    }
}
