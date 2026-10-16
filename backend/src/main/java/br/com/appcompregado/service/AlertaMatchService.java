package br.com.appcompregado.service;

import br.com.appcompregado.domain.AlertaBusca;
import br.com.appcompregado.domain.enums.Raca;
import br.com.appcompregado.repository.AlertaBuscaRepository;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Avisa compradores com radar ativo. Quem chama deve estar fora da transação do anúncio. */
@Service
public class AlertaMatchService {

    private static final Logger log = LoggerFactory.getLogger(AlertaMatchService.class);
    private static final String NADA = "\u0000";

    private final AlertaBuscaRepository alertas;
    private final WhatsAppSystemService chipSistema;
    private final boolean habilitado;

    public AlertaMatchService(
            AlertaBuscaRepository alertas,
            WhatsAppSystemService chipSistema,
            @Value("${compregado.radar.enabled:true}") boolean habilitado) {
        this.alertas = alertas;
        this.chipSistema = chipSistema;
        this.habilitado = habilitado;
    }

    public void avisar(AlertaNotificador.NovoAnuncio anuncio) {
        if (!habilitado || anuncio == null) {
            return;
        }
        List<AlertaBusca> combinados = alertas.combinar(
                ouNada(anuncio.raca()), ouNada(anuncio.cidade()), ouNada(anuncio.categoria()));
        for (AlertaBusca alerta : combinados) {
            if (PhoneUtils.mesmoNumero(alerta.getWhatsapp(), anuncio.telefoneVendedor())) {
                continue;
            }
            try {
                chipSistema.enviar(alerta.getWhatsapp(), mensagem(anuncio));
            } catch (RuntimeException e) {
                log.warn("Radar não avisou {}: {}", alerta.getWhatsapp(), e.getMessage());
            }
        }
    }

    static String mensagem(AlertaNotificador.NovoAnuncio anuncio) {
        String quantidade = anuncio.quantidade() != null && anuncio.quantidade() > 0
                ? anuncio.quantidade() + " cab. de " + rotulo(anuncio.raca())
                : "um lote de " + rotulo(anuncio.raca());
        String cidade = anuncio.cidade() == null || anuncio.cidade().isBlank() ? "sua região" : anuncio.cidade();
        return "Opa! O Radar do CompreGado achou o gado que você procurava em " + cidade + ": "
                + quantidade + ". Confira no link antes que seja vendido: " + anuncio.link();
    }

    private static String rotulo(String raca) {
        if (raca == null || raca.isBlank()) {
            return "gado";
        }
        try {
            Raca valor = Raca.valueOf(raca.trim().toUpperCase(Locale.ROOT));
            String nome = valor.name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(nome.charAt(0)) + nome.substring(1);
        } catch (IllegalArgumentException e) {
            return raca;
        }
    }

    private static String ouNada(String valor) {
        return valor == null || valor.isBlank() ? NADA : valor;
    }
}
