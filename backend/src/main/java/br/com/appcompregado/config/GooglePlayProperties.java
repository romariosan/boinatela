package br.com.appcompregado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "compregado.app.googleplay")
public class GooglePlayProperties {

    private String url = "https://play.google.com/store/apps/details?id=br.com.appcompregado";

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}