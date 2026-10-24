package br.com.appcompregado.service;

import br.com.appcompregado.domain.LeadConversa;
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
        enfileirar(chip, telefone, texto, validade, null, null);
    }

    @Transactional
    public void enfileirar(
            ChipMensagem chip,
            String telefone,
            String texto,
            Duration validade,
            LocalDateTime dataAgendamento,
            LeadConversa lead) {
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
        mensagem.setDataAgendamento(dataAgendamento);
        mensagem.setLead(lead);
        mensagens.save(mensagem);
    }

    /** Marca as pendências vencidas como reservadas. A mesma linha não volta no próximo GET. */
    @Transactional
    public List<MensagemSaida> reservar(ChipMensagem chip) {
        LocalDateTime agora = LocalDateTime.now();
        mensagens.expirarPendentes(agora);
        PageRequest pagina = PageRequest.of(0, LIMITE);
        List<MensagemSaida> lote = chip == null
                ? mensagens.reservaveis(agora, pagina)
                : mensagens.reservaveisDoChip(chip, agora, pagina);
        for (MensagemSaida mensagem : lote) {
            mensagem.setStatus(StatusMensagem.RESERVADA);
        }
        return lote;
    }

    @Transactional
    public EnvioConfirmado marcarEnviada(Long id) {
        MensagemSaida mensagem = aberta(id);
        mensagem.setStatus(StatusMensagem.ENVIADA);
        mensagem.setEnviadaEm(LocalDateTime.now());
        Long leadId = mensagem.getLead() == null ? null : mensagem.getLead().getId();
        return new EnvioConfirmado(mensagem.getId(), leadId, mensagem.getTexto());
    }

    /** Devolve a reserva para PENDENTE. Não marca a abordagem como feita. */
    @Transactional
    public void marcarFalha(Long id) {
        MensagemSaida mensagem = mensagens.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (mensagem.getStatus() == StatusMensagem.ENVIADA || mensagem.getStatus() == StatusMensagem.EXPIRADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mensagem já encerrada.");
        }
        mensagem.setStatus(StatusMensagem.PENDENTE);
    }

    @Transactional
    public void cancelarPendentesDaConversa(Long leadId) {
        mensagens.cancelarDaConversa(leadId);
    }

    @Transactional
    public void cancelarConsultorPendente(String telefone) {
        mensagens.cancelarConsultor(PhoneUtils.normalizar(telefone));
    }

    private MensagemSaida aberta(Long id) {
        MensagemSaida mensagem = mensagens.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (mensagem.getStatus() != StatusMensagem.PENDENTE && mensagem.getStatus() != StatusMensagem.RESERVADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mensagem já encerrada.");
        }
        return mensagem;
    }

    public record EnvioConfirmado(Long mensagemId, Long leadId, String texto) {
    }
}
