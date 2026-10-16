package br.com.appcompregado.service;

import br.com.appcompregado.config.JwtProperties;
import br.com.appcompregado.domain.LinksDoSite;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Link do WhatsApp para o vendedor marcar o lote como vendido, sem login. */
@Component
public class VendidoToken {

    private final JwtProperties jwt;

    public VendidoToken(JwtProperties jwt) {
        this.jwt = jwt;
    }

    public String link(Long anuncioId) {
        return LinksDoSite.baseUrl() + "/lote/" + anuncioId + "/vendido?token=" + assinar(jwt.getSecret(), anuncioId);
    }

    public boolean confere(Long anuncioId, String token) {
        if (anuncioId == null || token == null || token.isBlank() || jwt.getSecret() == null) {
            return false;
        }
        return MessageDigest.isEqual(
                assinar(jwt.getSecret(), anuncioId).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    static String assinar(String segredo, Long anuncioId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bruto = mac.doFinal(("vendido:" + anuncioId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bruto);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível assinar o link de vendido.", e);
        }
    }
}
