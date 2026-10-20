package br.com.appcompregado.service;

import br.com.appcompregado.domain.MensagemSaida;
import br.com.appcompregado.domain.enums.ChipMensagem;
import br.com.appcompregado.domain.enums.StatusMensagem;
import br.com.appcompregado.repository.MensagemSaidaRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Guarda o texto até o robô local buscar e enviar pelo WhatsApp Web. */
@Service
public class FilaMensagemService {

    private static final int LIMITE = 20;

    private final MensagemSaidaRepository mensagens;

    public FilaMensagemService(MensagemSaidaRepository mensagens) {
        this.mensagens = mensagens;
    }

    @Transactional
    public void enfileirar(ChipMensagem chip, String telefone, String texto, Duration validade) {
        String numero = PhoneUtils.normalizar(telefone);
        if (!PhoneUtils.celularBrasileiro(numero)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um celular com DDD.");
        }
        if (texto == null || texto.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mensagem vazia.");
        }
        LocalDateTime agora = LocalDateTime.now();
        MensagemSaida mensagem = new MensagemSaida();
        mensagem.setChip(chip);
        mensagem.setTelefone(numero);
        mensagem.setTexto(texto.trim());
        mensagem.setStatus(StatusMensagem.PENDENTE);
        mensagem.setCriadaEm(agora);
        mensagem.setExpiraEm(agora.plus(validade));
        mensagens.save(mensagem);
    }

    @Transactional
    public List<MensagemSaida> pendentes(ChipMensagem chip) {
        mensagens.expirarPendentes(LocalDateTime.now());
        LocalDateTime agora = LocalDateTime.now();
        PageRequest pagina = PageRequest.of(0, LIMITE);
        if (chip == null) {
            return mensagens.findByStatusAndExpiraEmAfterOrderByIdAsc(StatusMensagem.PENDENTE, agora, pagina);
        }
        return mensagens.findByStatusAndChipAndExpiraEmAfterOrderByIdAsc(
                StatusMensagem.PENDENTE, chip, agora, pagina);
    }

    @Transactional
    public void marcarEnviada(Long id) {
        MensagemSaida mensagem = aberta(id);
        mensagem.setStatus(StatusMensagem.ENVIADA);
        mensagem.setEnviadaEm(LocalDateTime.now());
    }

    @Transactional
    public void marcarFalha(Long id) {
        MensagemSaida mensagem = aberta(id);
        mensagem.setStatus(StatusMensagem.FALHOU);
    }

    private MensagemSaida aberta(Long id) {
        MensagemSaida mensagem = mensagens.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (mensagem.getStatus() != StatusMensagem.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mensagem já encerrada.");
        }
        return mensagem;
    }
}
