package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.LeadConversa;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.ChipMensagem;
import br.com.appcompregado.domain.enums.IntencaoProdutor;
import br.com.appcompregado.domain.enums.StatusLeadConversa;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.repository.LeadConversaRepository;
import br.com.appcompregado.repository.MensagemSaidaRepository;
import br.com.appcompregado.service.GeminiDialogService.Classificacao;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Abordagem do lote capturado no grupo. A Gemini só classifica; o texto fixo sai daqui. */
@Service
public class LeadAbordagemService {

    private static final Logger log = LoggerFactory.getLogger(LeadAbordagemService.class);
    private static final Duration VALIDADE = Duration.ofHours(36);

    private final LeadConversaRepository conversas;
    private final MensagemSaidaRepository mensagens;
    private final FilaMensagemService fila;
    private final GeminiDialogService gemini;
    private final TransactionTemplate transacao;

    public LeadAbordagemService(
            LeadConversaRepository conversas,
            MensagemSaidaRepository mensagens,
            FilaMensagemService fila,
            GeminiDialogService gemini,
            TransactionTemplate transacao) {
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.fila = fila;
        this.gemini = gemini;
        this.transacao = transacao;
    }

    /** Lote novo, já gravado, com extração real e celular válido. Genérico não chega aqui. */
    @Transactional
    public void agendarEstreia(Anuncio anuncio, String nomeGrupo) {
        if (anuncio == null || anuncio.getId() == null) {
            return;
        }
        if (anuncio.getStatusProcessamento() != StatusProcessamento.PUBLICADO) {
            return;
        }
        if (!"WHATSAPP".equalsIgnoreCase(anuncio.getOrigem())) {
            return;
        }
        String telefone = PhoneUtils.normalizar(anuncio.getTelefoneVendedorOriginal());
        if (!PhoneUtils.celularBrasileiro(telefone)) {
            return;
        }
        if (conversas.existsByTelefoneAndStatus(telefone, StatusLeadConversa.REJEITADO)) {
            fila.cancelarConsultorPendente(telefone);
            log.info("Anúncio {}: telefone com conversa rejeitada. Sem abordagem.", anuncio.getId());
            return;
        }
        if (conversas.findByAnuncioId(anuncio.getId()).isPresent()) {
            return;
        }
        Optional<LeadConversa> anterior = conversas.anteriores(telefone, PageRequest.of(0, 1)).stream().findFirst();
        String grupo = limitar(nomeGrupo, 160);
        LocalDateTime agora = LocalDateTime.now();
        LeadConversa conversa = new LeadConversa();
        conversa.setAnuncio(anuncio);
        conversa.setTelefone(telefone);
        conversa.setNomeGrupo(grupo);
        conversa.setStatus(StatusLeadConversa.CAPTURADO);
        conversa.setDataCaptura(agora);
        conversa.setDataUltimaInteracao(agora);
        LeadConversa salva = conversas.save(conversa);

        ZonedDateTime slot = HorarioComercial.primeiraMensagem(ZonedDateTime.now(HorarioComercial.ZONA));
        String rotulo = TextoAbordagem.rotulo(anuncio);
        String texto = anterior
                .map(prev -> TextoAbordagem.outroLote(
                        anuncio.getQuantidade(),
                        rotulo,
                        TextoAbordagem.lote(prev.getAnuncio().getQuantidade(), TextoAbordagem.rotulo(prev.getAnuncio()))))
                .orElseGet(() -> TextoAbordagem.estreia(
                        slot.getHour(), anuncio.getQuantidade(), rotulo, anuncio.getCidade(), grupo));
        if (TextoAbordagem.contemUrl(texto)) {
            throw new IllegalStateException("A estreia não pode levar link.");
        }
        fila.enfileirar(
                ChipMensagem.CONSULTOR,
                telefone,
                texto,
                VALIDADE,
                slot.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
                salva);
        salva.setStatus(StatusLeadConversa.AGUARDANDO_ABORDAGEM);
    }

    /** Uma segunda mensagem no dia seguinte, se a quebra de gelo ficou sem resposta. */
    public void lembretesSemResposta() {
        ZonedDateTime inicioHoje = LocalDate.now(HorarioComercial.ZONA).atStartOfDay(HorarioComercial.ZONA);
        LocalDateTime limite = inicioHoje.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        var ids = transacao.execute(status -> conversas.paraLembrete(limite, PageRequest.of(0, 20)).stream()
                .map(LeadConversa::getId)
                .toList());
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            try {
                transacao.executeWithoutResult(status -> agendarLembrete(id));
            } catch (RuntimeException e) {
                log.warn("Falha ao agendar lembrete da conversa {}: {}", id, e.getMessage());
            }
        }
    }

    /** Grava a resposta e só então classifica, com a Gemini fora da transação. */
    public void receber(String telefone, String texto, String mensagemId) {
        Long id = transacao.execute(status -> gravarEntrada(telefone, texto, mensagemId));
        if (id != null) {
            responder(id);
        }
    }

    public void responder(Long conversaId) {
        String entrada = transacao.execute(status -> conversas.findById(conversaId)
                .map(LeadConversa::getUltimaEntrada)
                .orElse(null));
        if (entrada == null || entrada.isBlank()) {
            return;
        }
        Optional<Classificacao> classificacao = gemini.classificar(entrada);
        transacao.executeWithoutResult(status -> aplicar(conversaId, classificacao.orElse(null)));
    }

    @Transactional
    public void confirmarEnvio(Long conversaId, String texto) {
        if (conversaId == null) {
            return;
        }
        LeadConversa lead = conversas.detalhe(conversaId).orElse(null);
        if (lead == null || lead.getStatus() == StatusLeadConversa.REJEITADO || lead.isEncerrada()) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        lead.setDataUltimaInteracao(agora);
        Anuncio anuncio = lead.getAnuncio();
        if (lead.getStatus() == StatusLeadConversa.AGUARDANDO_ABORDAGEM) {
            lead.setStatus(StatusLeadConversa.QUEBRA_GELO_ENVIADO);
            anuncio.setMensagemConsultorEnviada(true);
            anuncio.setDataAbordagemConsultor(agora);
            return;
        }
        if (texto != null && anuncio.getId() != null && texto.contains("/lote/" + anuncio.getId())
                && lead.getStatus() != StatusLeadConversa.LINK_ENVIADO) {
            lead.setPodeEnviarLink(true);
            lead.setStatus(StatusLeadConversa.LINK_ENVIADO);
        }
    }

    private void agendarLembrete(Long conversaId) {
        LeadConversa lead = conversas.detalhe(conversaId).orElse(null);
        if (lead == null
                || lead.isEncerrada()
                || lead.isLembreteEnviado()
                || lead.getStatus() != StatusLeadConversa.QUEBRA_GELO_ENVIADO) {
            return;
        }
        if (mensagens.existsByLeadIdAndStatusIn(
                lead.getId(),
                java.util.List.of(
                        br.com.appcompregado.domain.enums.StatusMensagem.PENDENTE,
                        br.com.appcompregado.domain.enums.StatusMensagem.RESERVADA))) {
            return;
        }
        Anuncio anuncio = lead.getAnuncio();
        String texto = TextoAbordagem.semResposta(
                anuncio.getQuantidade(), TextoAbordagem.rotulo(anuncio), lead.getNomeGrupo());
        if (TextoAbordagem.contemUrl(texto)) {
            return;
        }
        ZonedDateTime slot = HorarioComercial.encaixar(ZonedDateTime.now(HorarioComercial.ZONA));
        fila.enfileirar(
                ChipMensagem.CONSULTOR,
                lead.getTelefone(),
                texto,
                VALIDADE,
                slot.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
                lead);
        lead.setLembreteEnviado(true);
        lead.setDataUltimaInteracao(LocalDateTime.now());
    }

    private Long gravarEntrada(String telefone, String texto, String mensagemId) {
        String numero = PhoneUtils.normalizar(telefone);
        if (!PhoneUtils.celularBrasileiro(numero) || texto == null || texto.isBlank()) {
            return null;
        }
        if (conversas.existsByTelefoneAndStatus(numero, StatusLeadConversa.REJEITADO)) {
            fila.cancelarConsultorPendente(numero);
            log.info("Entrada ignorada: telefone com conversa rejeitada.");
            return null;
        }
        LeadConversa lead = conversas.abertas(numero, PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (lead == null) {
            log.info("Entrada sem conversa aberta.");
            return null;
        }
        fila.cancelarPendentesDaConversa(lead.getId());
        if (lead.getStatus() != StatusLeadConversa.LINK_ENVIADO) {
            lead.setStatus(StatusLeadConversa.RESPONDIDO);
        }
        lead.setDataUltimaInteracao(LocalDateTime.now());
        lead.setUltimaEntrada(limitar(texto, 2000));
        lead.setMensagemExternaId(limitar(mensagemId, 80));
        return lead.getId();
    }

    private void aplicar(Long conversaId, Classificacao classificacao) {
        LeadConversa lead = conversas.detalhe(conversaId).orElse(null);
        if (lead == null || lead.getStatus() == StatusLeadConversa.REJEITADO || lead.isEncerrada()) {
            return;
        }
        if (classificacao == null) {
            log.info("Conversa {}: JSON de diálogo inválido. Nada enfileirado.", conversaId);
            return;
        }
        Anuncio anuncio = lead.getAnuncio();
        Contexto ctx = Contexto.de(lead, anuncio);
        String texto = textoPara(classificacao, ctx);
        if (classificacao.intencao() == IntencaoProdutor.VENDIDO) {
            lead.setEncerrada(true);
            lead.setDataUltimaInteracao(LocalDateTime.now());
            retirarSeCaptura(anuncio, true);
        } else if (classificacao.intencao() == IntencaoProdutor.RECUSA) {
            lead.setStatus(StatusLeadConversa.REJEITADO);
            lead.setEncerrada(true);
            lead.setDataUltimaInteracao(LocalDateTime.now());
            fila.cancelarConsultorPendente(lead.getTelefone());
            retirarSeCaptura(anuncio, false);
            log.info("Conversa {} rejeitada. Sem nova abordagem deste telefone.", conversaId);
            return;
        }
        if (texto == null || texto.isBlank()) {
            log.info("Conversa {}: sem texto para {}. Nada enfileirado.", conversaId, classificacao.intencao());
            return;
        }
        boolean permiteUrl = ctx.linkEnviado()
                || (classificacao.intencao() == IntencaoProdutor.SIM_AINDA && ctx.ofereceuLink());
        if (TextoAbordagem.contemUrl(texto) && !permiteUrl) {
            log.info("Conversa {}: URL barrada antes do produtor aceitar o link.", conversaId);
            return;
        }
        if (!anuncio.isMensagemConsultorEnviada() && !texto.contains("CompreGado")) {
            texto = "Aqui é da equipe do CompreGado. " + texto;
        }
        if (classificacao.intencao() == IntencaoProdutor.SIM_AINDA && !ctx.linkEnviado() && !ctx.ofereceuLink()) {
            lead.setOfereceuLink(true);
        }
        if (TextoAbordagem.contemUrl(texto)) {
            lead.setPodeEnviarLink(true);
        }
        lead.setDataUltimaInteracao(LocalDateTime.now());
        fila.enfileirar(ChipMensagem.CONSULTOR, lead.getTelefone(), texto, Duration.ofHours(24), null, lead);
    }

    /**
     * Só mexe em lote da conta de captura, e nunca volta para ATIVO.
     * Anúncio de produtor reivindicado fica como está: a conversa encerra e o log registra.
     */
    private void retirarSeCaptura(Anuncio anuncio, boolean vendido) {
        if (anuncio.getVendedor() == null
                || !ContaCaptura.WHATSAPP.equals(anuncio.getVendedor().getWhatsapp())) {
            log.info("Anúncio {}: conversa encerrada sem alterar o status. Não há método do dono para retirar.", anuncio.getId());
            return;
        }
        if (anuncio.getStatus() == AnuncioStatus.VENDIDO) {
            return;
        }
        if (vendido) {
            anuncio.setStatus(AnuncioStatus.VENDIDO);
            anuncio.setAtivo(false);
            return;
        }
        if (anuncio.getStatus() == AnuncioStatus.ATIVO) {
            anuncio.setStatus(AnuncioStatus.PAUSADO);
        }
    }

    static String textoPara(Classificacao classificacao, Contexto ctx) {
        if (classificacao == null || ctx == null) {
            return null;
        }
        return switch (classificacao.intencao()) {
            case SIM_AINDA -> {
                if (ctx.linkEnviado()) {
                    yield TextoAbordagem.acompanhamento(ctx.quantidade(), ctx.rotulo(), ctx.link());
                }
                if (ctx.ofereceuLink()) {
                    yield TextoAbordagem.link(ctx.link());
                }
                yield TextoAbordagem.ofertaLink();
            }
            case VENDIDO -> TextoAbordagem.vendido();
            case RECUSA -> null;
            case DESCONFIANCA -> TextoAbordagem.desconfianca();
            case DUVIDA, OUTRO -> TextoAbordagem.sugestao(classificacao.replyText(), ctx.podeEnviarLink());
        };
    }

    private static String limitar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.length() <= maximo ? limpo : limpo.substring(0, maximo);
    }

    public record Contexto(
            long anuncioId,
            Integer quantidade,
            String rotulo,
            boolean ofereceuLink,
            boolean linkEnviado,
            boolean podeEnviarLink,
            String link) {

        static Contexto de(LeadConversa lead, Anuncio anuncio) {
            return new Contexto(
                    anuncio.getId(),
                    anuncio.getQuantidade(),
                    TextoAbordagem.rotulo(anuncio),
                    lead.isOfereceuLink(),
                    lead.getStatus() == StatusLeadConversa.LINK_ENVIADO,
                    lead.isPodeEnviarLink(),
                    anuncio.getLinkWeb());
        }
    }
}
