package br.com.appcompregado.config;

import br.com.appcompregado.service.ArmazenamentoLocalService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class UploadsWebConfig implements WebMvcConfigurer {

    private final ArmazenamentoLocalService armazenamento;

    public UploadsWebConfig(ArmazenamentoLocalService armazenamento) {
        this.armazenamento = armazenamento;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String local = armazenamento.getPasta().toUri().toString();
        if (!local.endsWith("/")) {
            local = local + "/";
        }
        registry.addResourceHandler(ArmazenamentoLocalService.CAMINHO_PUBLICO + "**")
                .addResourceLocations(local);
    }
}
