package br.com.appcompregado.config;

import br.com.appcompregado.domain.LinksDoSite;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "compregado.web")
public class SiteProperties {

    private String baseUrl = "https://appcompregado.com.br";

    public SiteProperties() {
        LinksDoSite.definirBaseUrl(baseUrl);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        LinksDoSite.definirBaseUrl(baseUrl);
    }
}
