package br.com.appcompregado.service;

import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.AnuncioStatus;
import br.com.appcompregado.repository.AnuncioRepository;
import br.com.appcompregado.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Fim do plano PRO: tira o Premium de quem venceu e pausa os anúncios que passam do limite gratuito. */
@Service
public class AssinaturaJobService {

    private static final Logger log = LoggerFactory.getLogger(AssinaturaJobService.class);

    /** O container roda em UTC; sem o fuso, "00:01" seria 21:01 da véspera para o vendedor. */
    static final String FUSO = "America/Sao_Paulo";

    private final UsuarioRepository usuarioRepository;
    private final AnuncioRepository anuncioRepository;
    private final TransactionTemplate transacao;
    private final int limiteAnunciosGratis;

    public AssinaturaJobService(
            UsuarioRepository usuarioRepository,
            AnuncioRepository anuncioRepository,
            TransactionTemplate transacao,
            @Value("${compregado.limite.anuncios.gratis:3}") int limiteAnunciosGratis) {
        this.usuarioRepository = usuarioRepository;
        this.anuncioRepository = anuncioRepository;
        this.transacao = transacao;
        this.limiteAnunciosGratis = limiteAnunciosGratis;
    }

    /**
     * Todo dia às 00:01. "Before hoje": quem tem expiração em 15/10 usa o PRO o dia 15 inteiro e cai na
     * madrugada do dia 16. Se a API estiver fora do ar nesse horário, a próxima execução pega os atrasados.
     */
    @Scheduled(cron = "${compregado.assinatura.cron:0 1 0 * * ?}", zone = FUSO)
    public void expirarPlanosVencidos() {
        LocalDate hoje = LocalDate.now(ZoneId.of(FUSO));
        List<Long> vencidos = usuarioRepository.findByIsPremiumTrueAndDataExpiracaoPremiumBefore(hoje).stream()
                .map(Usuario::getId)
                .toList();
        int rebaixados = 0;
        int pausados = 0;
        for (Long usuarioId : vencidos) {
            // Uma transação por vendedor: erro em um não desfaz os outros nem trava a rotina.
            try {
                Integer pausadosDoVendedor = transacao.execute(status -> rebaixar(usuarioId, hoje));
                if (pausadosDoVendedor != null && pausadosDoVendedor >= 0) {
                    rebaixados++;
                    pausados += pausadosDoVendedor;
                }
            } catch (RuntimeException e) {
                log.error("Falha ao encerrar o plano PRO do usuário {}", usuarioId, e);
            }
        }
        log.info("Planos PRO vencidos: {} encerrado(s), {} anúncio(s) pausado(s).", rebaixados, pausados);
    }

    /** @return quantos anúncios foram pausados, ou -1 se o plano foi renovado no meio do caminho. */
    private int rebaixar(Long usuarioId, LocalDate hoje) {
        // Trava a linha: a mesma do limite de publicação, então ninguém publica no meio do rebaixamento.
        Usuario usuario = usuarioRepository.travarPorId(usuarioId).orElse(null);
        if (usuario == null
                || !usuario.isPremium()
                || usuario.getDataExpiracaoPremium() == null
                || !usuario.getDataExpiracaoPremium().isBefore(hoje)) {
            return -1;
        }
        usuario.setPremium(false);
        usuario.setDataExpiracaoPremium(null);
        usuarioRepository.save(usuario);

        List<Anuncio> ativos =
                anuncioRepository.findByVendedorIdAndStatusOrderByDataCriacaoDesc(usuarioId, AnuncioStatus.ATIVO);
        if (ativos.size() <= limiteAnunciosGratis) {
            return 0;
        }
        // Lista do mais novo para o mais antigo: os índices 0..limite-1 continuam no ar, o resto pausa.
        List<Anuncio> excedentes = ativos.subList(limiteAnunciosGratis, ativos.size());
        excedentes.forEach(anuncio -> anuncio.setStatus(AnuncioStatus.PAUSADO));
        anuncioRepository.saveAll(excedentes);
        return excedentes.size();
    }
}
