package br.com.appcompregado.service;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.com.appcompregado.config.WhatsappProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class WhatsappBotServiceTest {

    @Test
    void enviaNoFormatoDaEvolution() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer evolution = MockRestServiceServer.bindTo(builder).build();
        WhatsappProperties propriedades = new WhatsappProperties();
        propriedades.setBotUrl("http://evolution:8080");
        propriedades.setBotToken("chave-da-evolution");
        propriedades.setBotInstancia("compregado");

        evolution.expect(requestTo("http://evolution:8080/message/sendText/compregado"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "chave-da-evolution"))
                .andExpect(jsonPath("$.number").value("5561981110001"))
                .andExpect(jsonPath("$.text").value("Seu código é 1234"))
                .andRespond(withSuccess());

        new WhatsappBotService(propriedades, builder).enviar("(61) 98111-0001", "Seu código é 1234");
        evolution.verify();
    }
}
