package br.com.appcompregado.web.dto;

public record AuthResponse(
        String token,
        Long usuarioId,
        String nome,
        String whatsapp,
        boolean telefoneVerificado,
        String codigoVerificacao,
        boolean admin) {
}
