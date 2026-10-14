package br.com.appcompregado.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mensagem recebida pelo bot. {@code remetente} aceita número com ou sem DDI ou o JID do WhatsApp
 * ("5561999999999@s.whatsapp.net").
 */
public record WhatsappWebhookRequest(
        @NotBlank @Size(max = 64) String remetente,
        @NotBlank @Size(max = 4096) String mensagem) {
}
