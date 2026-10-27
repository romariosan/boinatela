package br.com.appcompregado.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Lê o corpo da Gemini como UTF-8. O modelo pode responder application/octet-stream,
 * sem charset, ou com os bytes compactados. A mensagem de falha inclui a causa.
 */
final class GeminiCorpo {

    /** Uma leitura saudável na IPv6 leva cerca de 7s. Duas leituras cabem no proxy de 60s do Nginx. */
    static final int LEITURA_SEGUNDOS = 12;

    static {
        // generativelanguage.googleapis.com na IPv4 devolve 503 ou trava; a IPv6 responde JSON.
        System.setProperty("java.net.preferIPv6Addresses", "true");
    }

    private GeminiCorpo() {
    }

    static RestClient cliente(RestClient.Builder builder) {
        if (!fabricaDeTeste(builder)) {
            SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
            fabrica.setConnectTimeout(Duration.ofSeconds(5));
            fabrica.setReadTimeout(Duration.ofSeconds(LEITURA_SEGUNDOS));
            builder.requestFactory(fabrica);
        }
        return builder.build();
    }

    /** O mock do teste já instalou a fábrica. Trocar aqui faria a chamada sair para a rede. */
    private static boolean fabricaDeTeste(RestClient.Builder builder) {
        try {
            Field campo = builder.getClass().getDeclaredField("requestFactory");
            campo.setAccessible(true);
            Object atual = campo.get(builder);
            return atual instanceof ClientHttpRequestFactory
                    && atual.getClass().getName().contains("Mock");
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    static String ler(RestClient.RequestHeadersSpec<?> pedido) {
        byte[] bytes = pedido.exchange((request, response) -> {
            byte[] cru;
            try {
                InputStream corpo = response.getBody();
                cru = corpo == null ? new byte[0] : corpo.readAllBytes();
            } catch (IOException e) {
                throw new RestClientException(detalhe("corpo ilegível", e), e);
            }
            if (response.getStatusCode().isError()) {
                throw new RestClientResponseException(
                        "HTTP " + response.getStatusCode().value(),
                        response.getStatusCode().value(),
                        response.getStatusText(),
                        response.getHeaders(),
                        cru,
                        StandardCharsets.UTF_8);
            }
            return cru;
        });
        return decodificar(bytes);
    }

    /** JSON puro, com BOM, ou gzip dos mesmos bytes. */
    static String decodificar(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new RestClientException("corpo vazio");
        }
        byte[] dados = gzip(bytes) ? descompactar(bytes) : bytes;
        int inicio = bom(dados) ? 3 : 0;
        return new String(dados, inicio, dados.length - inicio, StandardCharsets.UTF_8);
    }

    static String detalhe(Throwable erro) {
        return detalhe("Gemini", erro);
    }

    private static String detalhe(String prefixo, Throwable erro) {
        StringBuilder texto = new StringBuilder(prefixo);
        Throwable atual = erro;
        while (atual != null) {
            texto.append(" | ").append(atual.getClass().getSimpleName());
            if (atual.getMessage() != null && !atual.getMessage().isBlank()) {
                texto.append(": ").append(atual.getMessage());
            }
            atual = atual.getCause();
        }
        return texto.toString();
    }

    private static boolean gzip(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == 0x1F && (bytes[1] & 0xFF) == 0x8B;
    }

    private static boolean bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private static byte[] descompactar(byte[] bytes) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return gzip.readAllBytes();
        } catch (IOException e) {
            throw new RestClientException(detalhe("corpo ilegível", e), e);
        }
    }
}
