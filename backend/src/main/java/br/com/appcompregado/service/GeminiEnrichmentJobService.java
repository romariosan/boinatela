package br.com.appcompregado.service;

import br.com.appcompregado.config.GeminiProperties;
import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.repository.AnuncioRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Depois da captura rápida (ExtracaoLocal), tenta a Gemini sem bloquear o webhook.
 * Falha permanente mantém o que já estava gravado — o anúncio não some.
 */
@Service
public class GeminiEnrichmentJobService {

    private static final Logger log = LoggerFactory.getLogger(GeminiEnrichmentJobService.class);
    static final String FUSO = "America/Sao_Paulo";
    /** Tentativas no job (além da captura local). */
    static final int MAXIMO_TENTATIVAS = 10;
    private static final int LOTE = 5;

    private final AnuncioRepository anuncios;
    private final GeminiExtractionService gemini;
    private final GeminiProperties propriedades;
    private final TransactionTemplate transacao;
    private final AlertaNotificador radar;
    private final LeadAbordagemService abordagem;
    private final String cron;

    public GeminiEnrichmentJobService(
            AnuncioRepository anuncios,
            GeminiExtractionService gemini,
            GeminiProperties propriedades,
            TransactionTemplate transacao,
            AlertaNotificador radar,
            LeadAbordagemService abordagem,
            @Value("${compregado.gemini.enriquecimento.cron:0 * * * * ?}") String cron) {
        this.anuncios = anuncios;
        this.gemini = gemini;
        this.propriedades = propriedades;
        this.transacao = transacao;
        this.radar = radar;
        this.abordagem = abordagem;
        this.cron = cron;
    }

    @Scheduled(cron = "${compregado.gemini.enriquecimento.cron:0 * * * * ?}", zone = FUSO)
    public void processarFila() {
        if ("-".equals(cron) || !chaveConfigurada()) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now(ZoneId.of(FUSO));
        List<Long> ids = anuncios.idsGeminiPendente(agora, MAXIMO_TENTATIVAS, PageRequest.of(0, LOTE));
        for (Long id : ids) {
            try {
                tentarUm(id);
            } catch (RuntimeException e) {
                log.warn("Falha ao enriquecer anúncio {} com Gemini: {}", id, e.toString());
            }
        }
    }

    /** Chamada imediata após a captura (não bloqueia a resposta do webhook). */
    public void tentarUm(Long anuncioId) {
        if (anuncioId == null || !chaveConfigurada()) {
            return;
        }
        transacao.executeWithoutResult(status -> enriquecer(anuncioId));
    }

    private void enriquecer(Long anuncioId) {
        Anuncio anuncio = anuncios.findById(anuncioId).orElse(null);
        if (anuncio == null || !anuncio.isGeminiPendente()) {
            return;
        }
        if (anuncio.getTentativasGemini() >= MAXIMO_TENTATIVAS) {
            anuncio.setGeminiPendente(false);
            anuncio.setProximaTentativaGemini(null);
            anuncios.save(anuncio);
            return;
        }
        LocalDateTime agora = LocalDateTime.now(ZoneId.of(FUSO));
        if (anuncio.getProximaTentativaGemini() != null && anuncio.getProximaTentativaGemini().isAfter(agora)) {
            return;
        }
        String texto = anuncio.getDescricao();
        if (texto == null || texto.isBlank()) {
            anuncio.setGeminiPendente(false);
            anuncios.save(anuncio);
            return;
        }
        String cidadePadrao = cidadePadraoDe(anuncio);
        String ufPadrao = ufPadraoDe(anuncio);
        anuncio.setTentativasGemini(anuncio.getTentativasGemini() + 1);
        var lido = gemini.extrair(texto, cidadePadrao, ufPadrao);
        if (lido.isEmpty()) {
            if (anuncio.getTentativasGemini() >= MAXIMO_TENTATIVAS) {
                anuncio.setGeminiPendente(false);
                anuncio.setProximaTentativaGemini(null);
                log.info("Gemini desistiu do anúncio {} após {} tentativa(s); mantém extração local.",
                        anuncioId, anuncio.getTentativasGemini());
            } else {
                anuncio.setProximaTentativaGemini(proximaTentativa(anuncio.getTentativasGemini(), agora));
            }
            anuncios.save(anuncio);
            return;
        }
        boolean eraRascunho = anuncio.getStatusProcessamento() == StatusProcessamento.RASCUNHO_SEM_FOTO;
        ScraperAnuncioService.aplicarExtracao(
                anuncio, lido.get(), cidadePadrao, ufPadrao, texto, "FACEBOOK".equals(anuncio.getOrigem()));
        anuncio.setGeminiPendente(false);
        anuncio.setProximaTentativaGemini(null);
        boolean publicouAgora = false;
        if (eraRascunho
                && anuncio.getStatus() != AnuncioStatus.VENDIDO
                && anuncio.getFotos() != null
                && !anuncio.getFotos().isEmpty()
                && ScraperAnuncioService.prontoParaVitrine(anuncio)) {
            anuncio.setAtivo(true);
            anuncio.setStatus(AnuncioStatus.ATIVO);
            anuncio.setStatusProcessamento(StatusProcessamento.PUBLICADO);
            publicouAgora = true;
        }
        anuncios.save(anuncio);
        if (publicouAgora) {
            radar.agendar(anuncio);
            if (ScraperAnuncioService.tituloProntoParaAbordagem(anuncio)
                    && PhoneUtils.celularBrasileiro(anuncio.getTelefoneVendedorOriginal())) {
                abordagem.agendarEstreia(anuncio, null);
            }
        }
        log.info("Gemini enriqueceu o anúncio {} na tentativa {}.", anuncioId, anuncio.getTentativasGemini());
    }

    static LocalDateTime proximaTentativa(int tentativasFeitas, LocalDateTime agora) {
        // 1ª falha → +1 min; 2ª → +5; 3ª → +15; 4ª → +30; depois +60 min.
        long minutos = switch (Math.max(1, tentativasFeitas)) {
            case 1 -> 1;
            case 2 -> 5;
            case 3 -> 15;
            case 4 -> 30;
            default -> 60;
        };
        return agora.plusMinutes(minutos);
    }

    private boolean chaveConfigurada() {
        return propriedades.getKey() != null && !propriedades.getKey().isBlank();
    }

    private static String cidadePadraoDe(Anuncio anuncio) {
        String cidade = anuncio.getCidade();
        if (cidade != null && cidade.length() >= 5 && cidade.regionMatches(true, cidade.length() - 5, " - ", 0, 3)) {
            return cidade.substring(0, cidade.length() - 5).trim();
        }
        return CidadesGoias.ehSemCidade(cidade) ? CidadesGoias.SEM_CIDADE : cidade.trim();
    }

    private static String ufPadraoDe(Anuncio anuncio) {
        if (anuncio.getUf() != null && anuncio.getUf().matches("(?i)[a-z]{2}")) {
            return anuncio.getUf().toUpperCase();
        }
        String cidade = anuncio.getCidade();
        if (cidade != null && cidade.length() >= 2 && cidade.matches("(?i).* - [a-z]{2}$")) {
            return cidade.substring(cidade.length() - 2).toUpperCase();
        }
        return "GO";
    }

}
