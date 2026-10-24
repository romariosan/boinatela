package br.com.appcompregado.service;

import br.com.appcompregado.domain.enums.ChipMensagem;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Chip 3. A estreia do lote de grupo sai pela LeadAbordagemService, em horário comercial,
 * sem link na primeira frase. Aqui ficam o lembrete do dia seguinte, o clique e a retenção.
 */
@Service
public class WhatsAppConsultantService {

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

    private static final Duration VALIDADE = Duration.ofHours(24);

    private final FilaMensagemService fila;
    private final LeadAbordagemService abordagem;
    private final String cron;

    public WhatsAppConsultantService(
            FilaMensagemService fila,
            LeadAbordagemService abordagem,
            @Value("${compregado.consultor.cron:0 * * * * ?}") String cron) {
        this.fila = fila;
        this.abordagem = abordagem;
        this.cron = cron;
    }

    @Scheduled(cron = "${compregado.consultor.cron:0 * * * * ?}", zone = FUSO)
    public void agendado() {
        if ("-".equals(cron)) {
            return;
        }
        abordagem.lembretesSemResposta();
    }

    /** Chip 3: aviso de clique e lembrete de retenção. A estreia do grupo não passa por aqui. */
    public void enviar(String telefone, String texto) {
        despachar(telefone, texto);
    }

    private void despachar(String telefone, String texto) {
        fila.enfileirar(ChipMensagem.CONSULTOR, telefone, texto, VALIDADE);
    }

    /** Rascunho sem foto não leva link: a pessoa só recebe o pedido da foto. */
    static String conviteFoto(String nome) {
        String pessoa = nome == null || nome.isBlank() ? "produtor" : nome;
        return "Opa " + pessoa + "! Vi seu gado no grupo, mas a vitrine ficou sem foto. "
                + "Manda uma foto do lote aqui neste WhatsApp para publicar grátis. "
                + "O CompreGado não cobra comissão e a venda continua no seu WhatsApp.";
    }

    /**
     * Primeiro contato antigo, ainda coberto por teste. A estreia do grupo usa TextoAbordagem.
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
