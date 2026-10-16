package br.com.appcompregado.service;

import br.com.appcompregado.config.ChipsWhatsappProperties;
import br.com.appcompregado.config.WhatsappApiProperties;
import br.com.appcompregado.domain.Anuncio;
import br.com.appcompregado.domain.Usuario;
import br.com.appcompregado.domain.enums.StatusProcessamento;
import br.com.appcompregado.repository.AnuncioRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Chip 3. Espera 30 minutos depois da captura e manda o link do lote, com texto variado.
 * O atraso aleatório fica fora da transação para não segurar conexão com o banco.
 */
@Service
public class WhatsAppConsultantService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConsultantService.class);
    private static final String FUSO = AssinaturaJobService.FUSO;

    private static final String[] SAUDACAO = {"Olá", "Oi", "Bom dia", "Boa tarde"};
    private static final String[] INTRO_APRESENTACAO = {
        "Aqui é do CompreGado, classificado de gado da região.",
        "Sou do CompreGado. A gente publica lote de gado para comprador da região achar.",
        "Falo do CompreGado, o app de compra e venda de gado."
    };
    private static final String[] CORPO_APRESENTACAO = {
        "Vi seu anúncio e deixei o lote nesta página:",
        "Separei o que você anunciou num link, para o comprador abrir direto:",
        "Publiquei o seu lote. Quem tiver interesse entra por aqui:"
    };
    private static final String[] FECHO_APRESENTACAO = {
        "Se o gado for seu, reivindique pelo link. É grátis.",
        "Se for você o dono, dá para reivindicar no link e ajustar o anúncio.",
        "O link é seu para confirmar o lote e completar os dados."
    };
    private static final String[] INTRO_MANUTENCAO = {
        "Passando para avisar que seu lote continua no CompreGado.",
        "Seu anúncio segue no ar no CompreGado.",
        "Lembrete do CompreGado: seu lote ainda está publicado."
    };
    private static final String[] CORPO_MANUTENCAO = {
        "O link segue o mesmo:",
        "Quem for comprar abre por aqui:",
        "Se quiser ajustar preço ou foto, o anúncio está neste endereço:"
    };
    private static final String[] FECHO_MANUTENCAO = {
        "Qualquer ajuste é pelo próprio link.",
        "Se já vendeu, dá para marcar por lá.",
        "Fico à disposição por esse anúncio."
    };

    private final AnuncioRepository anuncios;
    private final AuthService authService;
    private final WhatsappBotService bot;
    private final WhatsappApiProperties api;
    private final ChipsWhatsappProperties chips;
    private final TransactionTemplate transacao;
    private final String cron;

    public WhatsAppConsultantService(
            AnuncioRepository anuncios,
            AuthService authService,
            WhatsappBotService bot,
            WhatsappApiProperties api,
            ChipsWhatsappProperties chips,
            TransactionTemplate transacao,
            @Value("${compregado.consultor.cron:0 * * * * ?}") String cron) {
        this.anuncios = anuncios;
        this.authService = authService;
        this.bot = bot;
        this.api = api;
        this.chips = chips;
        this.transacao = transacao;
        this.cron = cron;
    }

    @Scheduled(cron = "${compregado.consultor.cron:0 * * * * ?}", zone = FUSO)
    public void agendado() {
        if ("-".equals(cron)) {
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        List<Long> ids = transacao.execute(status -> anuncios.idsParaAbordagem(
                agora.minusHours(2), agora.minusMinutes(30), PageRequest.of(0, 5)));
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
                log.warn("Falha ao abordar o anúncio {}: {}", id, e.getMessage());
            }
        }
    }

    private void enviarUm(Long id) {
        Anuncio anuncio = anuncios.findByIdWithVendedor(id).orElse(null);
        if (anuncio == null
                || anuncio.isMensagemConsultorEnviada()
                || anuncio.isReivindicado()
                || anuncio.getDataCaptura() == null
                || anuncio.getTelefoneVendedorOriginal() == null) {
            return;
        }
        String telefone = anuncio.getTelefoneVendedorOriginal();
        boolean manutencao = abordadoNosUltimos30Dias(telefone);
        String texto = textoDaAbordagem(anuncio, manutencao);
        despachar(telefone, texto);

        LocalDateTime agora = LocalDateTime.now();
        anuncio.setMensagemConsultorEnviada(true);
        anuncio.setDataAbordagemConsultor(agora);
        authService.localizarPorTelefone(telefone).ifPresent(usuario -> usuario.setDataUltimaAbordagemBot(agora));
    }

    private boolean abordadoNosUltimos30Dias(String telefone) {
        LocalDateTime limite = LocalDateTime.now().minusDays(30);
        Usuario usuario = authService.localizarPorTelefone(telefone).orElse(null);
        if (usuario != null
                && usuario.getDataUltimaAbordagemBot() != null
                && usuario.getDataUltimaAbordagemBot().isAfter(limite)) {
            return true;
        }
        List<String> formas = new ArrayList<>();
        formas.add(PhoneUtils.normalizar(telefone));
        formas.addAll(PhoneUtils.variantesLegadas(telefone));
        LocalDateTime ultima = anuncios.ultimaAbordagem(formas);
        return ultima != null && ultima.isAfter(limite);
    }

    /** Chip 3: prospecção, aviso de clique e lembrete de retenção. */
    public void enviar(String telefone, String texto) {
        despachar(telefone, texto);
    }

    private void despachar(String telefone, String texto) {
        String instancia = chips.getConsultant().getInstance();
        if (api.configurada() && instancia != null && !instancia.isBlank()) {
            bot.enviarNaInstancia(api.getUrl(), api.getKey(), instancia, telefone, texto);
            return;
        }
        bot.enviar(telefone, texto);
    }

    /** Rascunho sem foto não leva link: a pessoa só recebe o pedido da foto. */
    static String conviteFoto(String nome) {
        String pessoa = nome == null || nome.isBlank() ? "produtor" : nome;
        return "Opa " + pessoa + "! Vi seu gado no grupo, mas a vitrine ficou sem foto. "
                + "Manda uma foto do lote aqui neste WhatsApp para publicar grátis. "
                + "O CompreGado não cobra comissão e a venda continua no seu WhatsApp.";
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

    private static String textoDaAbordagem(Anuncio anuncio, boolean manutencao) {
        if (anuncio.getStatusProcessamento() == StatusProcessamento.RASCUNHO_SEM_FOTO) {
            return conviteFoto(nome(anuncio.getVendedor()));
        }
        if ("FACEBOOK".equals(anuncio.getOrigem())) {
            return montarFacebook(
                    nome(anuncio.getVendedor()),
                    anuncio.getQuantidade(),
                    anuncio.getRaca() == null ? null : anuncio.getRaca().name(),
                    anuncio.getLinkWeb());
        }
        if (manutencao) {
            return montar(true, anuncio.getLinkWeb());
        }
        return montarApresentacao(
                nome(anuncio.getVendedor()),
                anuncio.getQuantidade(),
                anuncio.getRaca() == null ? null : anuncio.getRaca().name(),
                anuncio.getLinkWeb());
    }

    /**
     * Primeiro contato. Cita o lote, deixa claro que não há comissão e manda o link para reivindicar.
     */
    static String montarApresentacao(String nome, Integer quantidade, String raca, String link) {
        String pessoa = nome == null || nome.isBlank() ? "produtor" : nome;
        String lote = quantidade != null && quantidade > 0
                ? "dos " + quantidade + " " + rotuloRaca(raca)
                : "de " + rotuloRaca(raca);
        return escolher(new String[] {"Opa", "Fala"}) + " " + pessoa + "! Seu post " + lote
                + " já está no ar na vitrine da nossa região. "
                + "O CompreGado não cobra comissão e a venda continua direto no seu WhatsApp. "
                + "Dá uma olhada como ficou e clica pra reivindicar (é grátis): " + link;
    }

    static String montarFacebook(String nome, Integer quantidade, String raca, String link) {
        String pessoa = nome == null || nome.isBlank() ? "produtor" : nome;
        String lote = quantidade != null && quantidade > 0
                ? "dos " + quantidade + " " + rotuloRaca(raca)
                : "de " + rotuloRaca(raca);
        return escolher(new String[] {"Opa", "Fala", "Tudo bem,"}) + " " + pessoa + "? "
                + escolher(new String[] {"Vi", "Acompanhei"}) + " o seu anúncio " + lote + " lá no Facebook! "
                + "Como o pessoal aqui da região usa muito o WhatsApp pra negociar gado, "
                + escolher(new String[] {"organizei", "montei"}) + " uma vitrine "
                + escolher(new String[] {"gratuita", "organizada"})
                + " pra sua boiada no aplicativo CompreGado pra "
                + escolher(new String[] {"te ajudar a", "facilitar"})
                + " vender mais rápido. O CompreGado não cobra comissão e a venda continua direto no seu WhatsApp. "
                + "Dá uma olhada: " + link
                + ". Se precisar editar alguma coisa, é só clicar no botão no final da página!";
    }

    private static String rotuloRaca(String raca) {
        if (raca == null || raca.isBlank()) {
            return "gado";
        }
        String nome = raca.trim().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(nome.charAt(0)) + nome.substring(1);
    }

    static String montar(boolean manutencao, String link) {
        if (manutencao) {
            return juntar(SAUDACAO, INTRO_MANUTENCAO, CORPO_MANUTENCAO, FECHO_MANUTENCAO, link);
        }
        return juntar(SAUDACAO, INTRO_APRESENTACAO, CORPO_APRESENTACAO, FECHO_APRESENTACAO, link);
    }

    private static String juntar(String[] saudacao, String[] intro, String[] corpo, String[] fecho, String link) {
        return escolher(saudacao) + "! " + escolher(intro) + " " + escolher(corpo) + " " + link
                + " " + escolher(fecho);
    }

    private static String escolher(String[] opcoes) {
        return opcoes[ThreadLocalRandom.current().nextInt(opcoes.length)];
    }
}
