package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.repository.AnuncioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Todo dia às 9h: pergunta ao vendedor se o lote de 15 dias já foi vendido. */
@Service
public class AnuncioRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnuncioRetentionScheduler.class);
    private static final String FUSO = AssinaturaJobService.FUSO;

    private static final String[] SAUDACAO = {"Fala", "Oi", "E aí"};
    private static final String[] CUMPRIMENTO = {"tudo bom?", "tudo certo?", "como vai?"};
    private static final String[] CORPO = {
        "Vi que seu lote de %s já está anunciado há alguns dias no CompreGado.",
        "Faz uns dias que seu lote de %s está no ar no CompreGado.",
        "Seu lote de %s segue publicado no CompreGado há um tempo."
    };
    private static final String[] PERGUNTA = {
        "Você já conseguiu vender?",
        "O lote já saiu?",
        "Já fechou a venda?"
    };
    private static final String[] FECHO = {
        "Se sim, clique aqui para marcar como VENDIDO no sistema e não receber mais ligações:",
        "Se já vendeu, marque como VENDIDO por aqui e o anúncio sai da vitrine:",
        "Se o gado já foi, use este link para tirar o lote do ar:"
    };

    private final AnuncioRepository anuncios;
    private final WhatsAppConsultantService consultor;
    private final VendidoToken vendidoToken;
    private final TransactionTemplate transacao;
    private final int dias;
    private final String cron;

    public AnuncioRetentionScheduler(
            AnuncioRepository anuncios,
            WhatsAppConsultantService consultor,
            VendidoToken vendidoToken,
            TransactionTemplate transacao,
            @Value("${compregado.retencao.dias:15}") int dias,
            @Value("${compregado.retencao.cron:0 0 9 * * ?}") String cron) {
        this.anuncios = anuncios;
        this.consultor = consultor;
        this.vendidoToken = vendidoToken;
        this.transacao = transacao;
        this.dias = dias;
        this.cron = cron;
    }

    @Scheduled(cron = "${compregado.retencao.cron:0 0 9 * * ?}", zone = FUSO)
    public void agendado() {
        if ("-".equals(cron)) {
            return;
        }
        LocalDateTime limite = LocalDateTime.now().minusDays(dias);
        List<Long> ids = transacao.execute(status ->
                anuncios.idsParaRetencao(limite, PageRequest.of(0, 20)));
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(3_000, 6_001));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                transacao.executeWithoutResult(status -> enviarUm(id));
            } catch (RuntimeException e) {
                log.warn("Falha no lembrete de retenção do anúncio {}: {}", id, e.getMessage());
            }
        }
    }

    private void enviarUm(Long id) {
        Anuncio anuncio = anuncios.findByIdWithVendedor(id).orElse(null);
        if (anuncio == null
                || !anuncio.isAtivo()
                || anuncio.getStatus() == AnuncioStatus.VENDIDO
                || anuncio.isMensagemRetencaoEnviada()) {
            return;
        }
        String telefone = InteracaoContatoService.telefoneDoVendedor(anuncio);
        if (telefone == null) {
            anuncio.setMensagemRetencaoEnviada(true);
            return;
        }
        consultor.enviar(telefone, mensagem(anuncio));
        anuncio.setMensagemRetencaoEnviada(true);
    }

    String mensagem(Anuncio anuncio) {
        String raca = anuncio.getRaca() == null
                ? "gado"
                : anuncio.getRaca().name().toLowerCase(Locale.ROOT);
        raca = Character.toUpperCase(raca.charAt(0)) + raca.substring(1);
        return escolher(SAUDACAO) + " " + nome(anuncio.getVendedor()) + ", " + escolher(CUMPRIMENTO) + " "
                + escolher(CORPO).formatted(raca) + " " + escolher(PERGUNTA) + " "
                + escolher(FECHO) + " " + vendidoToken.link(anuncio.getId());
    }

    private static String nome(Usuario vendedor) {
        if (vendedor == null || ContaCaptura.WHATSAPP.equals(vendedor.getWhatsapp())) {
            return "produtor";
        }
        String nome = vendedor.getNome() == null ? "" : vendedor.getNome().trim();
        if (nome.isBlank() || "CompreGado".equalsIgnoreCase(nome)) {
            return "produtor";
        }
        return nome.split("\\s+")[0];
    }

    private static String escolher(String[] opcoes) {
        return opcoes[ThreadLocalRandom.current().nextInt(opcoes.length)];
    }
}
