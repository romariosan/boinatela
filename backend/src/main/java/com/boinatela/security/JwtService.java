package com.boinatela.security;

import com.boinatela.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String gerarToken(Long usuarioId, String whatsapp) {
        Date agora = new Date();
        Date expira = new Date(agora.getTime() + properties.getExpirationMs());
        return Jwts.builder()
                .subject(whatsapp)
                .claim("uid", usuarioId)
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
}
