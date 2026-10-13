package com.boinatela.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsappProperties {

    /** Segredo que o bot manda no header X-Webhook-Token. Vazio = webhook desligado. */
    private String webhookToken = "";

    /** Endpoint do bot que dispara mensagens (POST {"numero","mensagem"}). Vazio = sem envio. */
    private String botUrl = "";

    /** Segredo que a API manda ao bot no header X-Bot-Token. */
    private String botToken = "";

    /**
     * Só para desenvolvimento sem bot: escreve a mensagem (com o código) no log em vez de enviar.
     * Nunca ligar em produção.
     */
    private boolean envioNoLog = false;

    public String getWebhookToken() {
        return webhookToken;
    }

    public void setWebhookToken(String webhookToken) {
        this.webhookToken = webhookToken;
    }

    public String getBotUrl() {
        return botUrl;
    }

    public void setBotUrl(String botUrl) {
        this.botUrl = botUrl;
    }

    public String getBotToken() {
        return botToken;
    }

    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }

    public boolean isEnvioNoLog() {
        return envioNoLog;
    }

    public void setEnvioNoLog(boolean envioNoLog) {
        this.envioNoLog = envioNoLog;
    }
}
