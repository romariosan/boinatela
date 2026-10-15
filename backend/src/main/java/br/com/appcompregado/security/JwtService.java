package br.com.appcompregado.security;

import br.com.appcompregado.config.JwtProperties;
import br.com.appcompregado.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    static final String CLAIM_VERSAO = "ver";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String gerarToken(Usuario usuario) {
        Date agora = new Date();
        Date expira = new Date(agora.getTime() + properties.getExpirationMs());
        return Jwts.builder()
                .subject(usuario.getWhatsapp())
                .claim("uid", usuario.getId())
                .claim(CLAIM_VERSAO, usuario.getVersaoSessao())
                .issuedAt(agora)
                .expiration(expira)
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Tokens emitidos antes da versão existir não têm o claim: valem como versão 0. */
    public boolean sessaoValida(Claims claims, Usuario usuario) {
        Integer versao = claims.get(CLAIM_VERSAO, Integer.class);
        return (versao == null ? 0 : versao) == usuario.getVersaoSessao();
    }
}
