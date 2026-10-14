package br.com.appcompregado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsappProperties {

    /** Segredo que o bot manda no header X-Webhook-Token. Vazio = webhook desligado. */
    private String webhookToken = "";

    /** URL base da Evolution API, ex.: http://evolution:8080. Vazio = sem envio. */
    private String botUrl = "";

    /** Chave da Evolution API (header apikey). */
    private String botToken = "";

    /** Nome da instância da Evolution conectada ao WhatsApp do CompreGado. */
    private String botInstancia = "compregado";

    /** Número do WhatsApp do bot com DDI (ex.: 5561999999999), para o app abrir o wa.me. */
    private String botNumero = "";

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

    public String getBotInstancia() {
        return botInstancia;
    }

    public void setBotInstancia(String botInstancia) {
        this.botInstancia = botInstancia;
    }

    public String getBotNumero() {
        return botNumero;
    }

    public void setBotNumero(String botNumero) {
        this.botNumero = botNumero;
    }

    public boolean isEnvioNoLog() {
        return envioNoLog;
    }

    public void setEnvioNoLog(boolean envioNoLog) {
        this.envioNoLog = envioNoLog;
    }
}
