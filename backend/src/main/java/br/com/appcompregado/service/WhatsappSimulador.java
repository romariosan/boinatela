package br.com.appcompregado.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Conversas do "WhatsApp simulado" (desenvolvimento sem bot): guarda em memória o que o bot teria enviado
 * e o que o usuário mandou para ele. Só recebe mensagens com app.whatsapp.envio-no-log ligado.
 */
@Component
public class WhatsappSimulador {

    public enum Direcao { DO_BOT, PARA_O_BOT }

    public record Mensagem(long id, String numero, Direcao direcao, String texto, Instant quando) {
    }

    private static final int LIMITE = 300;

    private final Deque<Mensagem> mensagens = new ArrayDeque<>();
    private long sequencia;

    /** @return o número normalizado (DDD + número), igual ao do cadastro. */
    public synchronized String registrar(String whatsapp, Direcao direcao, String texto) {
        String numero = TelefoneBr.nacional(whatsapp);
        mensagens.addLast(new Mensagem(++sequencia, numero, direcao, texto, Instant.now()));
        while (mensagens.size() > LIMITE) {
            mensagens.removeFirst();
        }
        return numero;
    }

    public synchronized List<Mensagem> listar() {
        return List.copyOf(mensagens);
    }

    public synchronized void limpar() {
        mensagens.clear();
    }
}
