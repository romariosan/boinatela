package br.com.appcompregado.service;

import br.com.appcompregado.domain.enums.ChipMensagem;
import java.time.Duration;
import org.springframework.stereotype.Service;

/** Chip 1: código de cadastro, de senha e de reivindicação. Não entra em grupo. */
@Service
public class WhatsAppSystemService {

    private static final Duration VALIDADE = Duration.ofMinutes(10);

    private final FilaMensagemService fila;

    public WhatsAppSystemService(FilaMensagemService fila) {
        this.fila = fila;
    }

    public void enviar(String whatsapp, String mensagem) {
        fila.enfileirar(ChipMensagem.SISTEMA, whatsapp, mensagem, VALIDADE);
    }
}
