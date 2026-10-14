package br.com.appcompregado.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class HttpClientConfig {

    /** Sem celular conectado, a Evolution chega a levar mais de 1 min para responder; não prender a requisição. */
    @Bean
    RestClientCustomizer temposLimite() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(20));
        return builder -> builder.requestFactory(fabrica);
    }
}
