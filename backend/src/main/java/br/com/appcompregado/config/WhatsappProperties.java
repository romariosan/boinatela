package br.com.appcompregado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsappProperties {

    /** Segredo que o robô manda no header X-Webhook-Token ao publicar oferta de grupo. */
    private String webhookToken = "";

    /** Segredo do robô no header X-Robo-Token. Se vazio, vale o webhook-token. */
    private String roboToken = "";

    /** Número do WhatsApp do sistema com DDI, só para a tela antiga de status. */
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

    public String getRoboToken() {
        return roboToken;
    }

    public void setRoboToken(String roboToken) {
        this.roboToken = roboToken;
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
