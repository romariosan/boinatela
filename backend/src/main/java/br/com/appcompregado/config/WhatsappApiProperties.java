package br.com.appcompregado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "whatsapp.api")
public class WhatsappApiProperties {

    private String url = "";

    private String key = "";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url == null ? "" : url;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key == null ? "" : key;
    }

    public boolean configurada() {
        return !url.isBlank() && !key.isBlank();
    }
}
