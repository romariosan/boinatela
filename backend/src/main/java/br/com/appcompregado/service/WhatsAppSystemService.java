package br.com.appcompregado.service;

import br.com.appcompregado.config.ChipsWhatsappProperties;
import br.com.appcompregado.config.WhatsappApiProperties;
import org.springframework.stereotype.Service;

/** Chip 1: só OTP, login e senha. Não escuta grupo e não prospecta. */
@Service
public class WhatsAppSystemService {

    private final WhatsappBotService bot;
    private final WhatsappApiProperties api;
    private final ChipsWhatsappProperties chips;

    public WhatsAppSystemService(
            WhatsappBotService bot, WhatsappApiProperties api, ChipsWhatsappProperties chips) {
        this.bot = bot;
        this.api = api;
        this.chips = chips;
    }

    public void enviar(String whatsapp, String mensagem) {
        String instancia = chips.getSystem().getInstance();
        if (api.configurada() && instancia != null && !instancia.isBlank()) {
            bot.enviarNaInstancia(api.getUrl(), api.getKey(), instancia, whatsapp, mensagem);
            return;
        }
        bot.enviar(whatsapp, mensagem);
    }
}
